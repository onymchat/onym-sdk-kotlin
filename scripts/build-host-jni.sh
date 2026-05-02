#!/bin/sh
# Build the Rust JNI shim cdylib for the current host. The output —
# `rust-jni/target/release/libonym_sdk_jni.{so,dylib}` — is loaded by
# Kotlin via System.loadLibrary("onym_sdk_jni") at first use. Gradle's
# test task sets java.library.path to point at this directory.
#
# Run before `./gradlew test` / `./gradlew build`. Idempotent — Cargo's
# incremental cache + the JNI crate's build.rs reuse cached staticlib
# builds for the four sep-*-ffi crates.
#
# Android cross-compile (per-ABI .so files for arm64-v8a / armeabi-v7a
# / x86 / x86_64, packaged into an AAR alongside the Kotlin classes)
# is a follow-up — see scripts/build-android-jni.sh stub.

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)"

CONTRACTS_ROOT="$REPO_ROOT/External/onym-contracts"
if [ ! -d "$CONTRACTS_ROOT/plonk" ]; then
    echo "expected onym-contracts submodule at $CONTRACTS_ROOT" >&2
    echo "run: git submodule update --init --recursive" >&2
    exit 1
fi

PROFILE="release"
CARGO_FLAGS="--release"
if [ "${1:-}" = "--debug" ]; then
    PROFILE="debug"
    CARGO_FLAGS=""
fi

echo "==> Building rust-jni cdylib ($PROFILE)"
( cd "$REPO_ROOT/rust-jni" && cargo build $CARGO_FLAGS )

# Find the produced cdylib (.dylib on macOS, .so on Linux).
OUT_DIR="$REPO_ROOT/rust-jni/target/$PROFILE"
case "$(uname -s)" in
    Darwin) EXT="dylib" ;;
    Linux)  EXT="so"    ;;
    *)      echo "unsupported host: $(uname -s)" >&2; exit 1 ;;
esac
LIB="$OUT_DIR/libonym_sdk_jni.$EXT"
if [ ! -f "$LIB" ]; then
    echo "expected JNI cdylib not found: $LIB" >&2
    ls -la "$OUT_DIR" 2>&1 | head -10 >&2
    exit 1
fi

SIZE=$(ls -lh "$LIB" | awk '{print $5}')
echo
echo "Built: $LIB  ($SIZE)"
echo "Gradle's test task picks this up via java.library.path."
