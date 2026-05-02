#!/bin/sh
# Cross-compile the rust-jni cdylib to all four Android ABIs and
# stage them under build/android/jniLibs/<abi>/libonym_sdk_jni.so —
# the path the Gradle bundleAar task picks up when assembling the
# AAR.
#
# Usage:
#   ANDROID_NDK_HOME=/path/to/ndk ./scripts/build-android-jni.sh
#
# Outputs:
#   build/android/jniLibs/arm64-v8a/libonym_sdk_jni.so
#   build/android/jniLibs/armeabi-v7a/libonym_sdk_jni.so
#   build/android/jniLibs/x86_64/libonym_sdk_jni.so
#   build/android/jniLibs/x86/libonym_sdk_jni.so
#
# Approach: configure the per-target Cargo linker via
# `CARGO_TARGET_<TARGET>_LINKER` env vars pointing at the NDK's
# clang wrappers. These are inherited by the sub-cargo invocations
# from rust-jni/build.rs (which compiles the four sep-*-ffi
# staticlibs for the same target — see the --target propagation
# fix in build.rs).
#
# Why not cargo-ndk: cargo-ndk wraps the top-level cargo invocation
# but does not propagate its env to build.rs's spawned cargo
# children. Setting the env vars ourselves works for both layers.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

[ -n "${ANDROID_NDK_HOME:-}" ] || {
    echo "set ANDROID_NDK_HOME to the path of your Android NDK install" >&2
    echo "(e.g. \$ANDROID_HOME/ndk/27.x.y)" >&2
    exit 1
}

CONTRACTS_ROOT="$REPO_ROOT/External/onym-contracts"
[ -d "$CONTRACTS_ROOT/plonk" ] || {
    echo "expected onym-contracts submodule at $CONTRACTS_ROOT" >&2
    echo "run: git submodule update --init --recursive" >&2
    exit 1
}

# Pick the NDK toolchain prebuilt dir based on host. The NDK only
# ships darwin-x86_64 (no darwin-arm64 yet as of r27c) so Apple
# Silicon hosts run it under Rosetta — same code path either way.
case "$(uname -s)" in
    Linux)  NDK_HOST="linux-x86_64"  ;;
    Darwin) NDK_HOST="darwin-x86_64" ;;
    *) echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac

TOOLCHAIN_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$NDK_HOST/bin"
[ -d "$TOOLCHAIN_BIN" ] || {
    echo "missing NDK toolchain: $TOOLCHAIN_BIN" >&2
    echo "(check that ANDROID_NDK_HOME points at a valid NDK r25+)" >&2
    exit 1
}

# Android API 24 (Android 7.0 Nougat). Matches Kotlin SDK minSdk
# convention; bump in concert with consumer apps.
API="${ANDROID_API:-24}"

OUT_DIR="$REPO_ROOT/build/android/jniLibs"
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# ABI -> Rust target triple
abi_to_rust_target() {
    case "$1" in
        arm64-v8a)   echo "aarch64-linux-android" ;;
        armeabi-v7a) echo "armv7-linux-androideabi" ;;
        x86_64)      echo "x86_64-linux-android" ;;
        x86)         echo "i686-linux-android" ;;
        *) echo "unknown ABI: $1" >&2; exit 1 ;;
    esac
}

# Rust target triple -> NDK clang wrapper basename. Note armv7 uses
# the "armv7a" prefix in the NDK (not a typo).
rust_target_to_clang() {
    case "$1" in
        armv7-linux-androideabi) echo "armv7a-linux-androideabi${API}-clang" ;;
        *)                       echo "${1}${API}-clang" ;;
    esac
}

ABIS="arm64-v8a armeabi-v7a x86_64 x86"

for abi in $ABIS; do
    rust_target="$(abi_to_rust_target "$abi")"
    clang_basename="$(rust_target_to_clang "$rust_target")"
    clang="$TOOLCHAIN_BIN/$clang_basename"
    [ -x "$clang" ] || {
        echo "missing NDK clang wrapper: $clang" >&2
        echo "(check API level $API is supported by this NDK)" >&2
        exit 1
    }

    # Cargo target env-var name: uppercase, hyphens -> underscores.
    target_env="$(echo "$rust_target" | tr 'a-z-' 'A-Z_')"

    # Per-target cargo linker. Inherited by build.rs's spawned cargo
    # so the FFI staticlib builds use the same linker.
    eval "export CARGO_TARGET_${target_env}_LINKER=\"$clang\""

    # `cc` crate (used by some transitive build.rs scripts in
    # arkworks/jellyfish) honours CC_<rust-target> and AR_<rust-target>.
    # Note: hyphens preserved in the env name (cc crate quirk).
    eval "export CC_${rust_target}=\"$clang\""
    eval "export CXX_${rust_target}=\"${clang}++\""
    eval "export AR_${rust_target}=\"$TOOLCHAIN_BIN/llvm-ar\""

    # Make sure rustup has the target installed for the active
    # toolchain (build.rs will install it for the FFI sub-builds via
    # their own rust-toolchain.toml when needed).
    if ! ( cd "$REPO_ROOT/rust-jni" && rustup target list --installed | grep -q "^${rust_target}\$" ); then
        echo "==> Installing Rust target $rust_target"
        ( cd "$REPO_ROOT/rust-jni" && rustup target add "$rust_target" )
    fi
    # Also install for each FFI crate's pinned toolchain so build.rs's
    # spawned cargo can find it.
    for ffi_crate in sep-common-ffi sep-anarchy-ffi sep-oneonone-ffi sep-tyranny-ffi; do
        ffi_dir="$CONTRACTS_ROOT/plonk/$ffi_crate"
        if ! ( cd "$ffi_dir" && rustup target list --installed | grep -q "^${rust_target}\$" ); then
            echo "==> Installing Rust target $rust_target (toolchain pinned by $ffi_crate)"
            ( cd "$ffi_dir" && rustup target add "$rust_target" )
        fi
    done

    echo "==> cargo build --release --target $rust_target  (rust-jni for $abi)"
    ( cd "$REPO_ROOT/rust-jni" && cargo build --release --target "$rust_target" )

    src="$REPO_ROOT/rust-jni/target/$rust_target/release/libonym_sdk_jni.so"
    [ -f "$src" ] || { echo "missing $src" >&2; exit 1; }

    abi_dir="$OUT_DIR/$abi"
    mkdir -p "$abi_dir"
    cp "$src" "$abi_dir/libonym_sdk_jni.so"

    size="$(ls -lh "$src" | awk '{print $5}')"
    echo "  -> $abi_dir/libonym_sdk_jni.so  ($size)"
done

echo
echo "Android JNI staged at $OUT_DIR:"
ls -lh "$OUT_DIR"/*/libonym_sdk_jni.so | awk '{print "  " $NF "  " $5}'
