#!/bin/sh
# Cross-compile the Rust JNI shim to the four Android ABIs and package
# alongside the Kotlin classes as an AAR for shipping in an Android
# app via Maven Local / Gradle dep.
#
# Status: NOT IMPLEMENTED. Host build (scripts/build-host-jni.sh) is
# enough for `./gradlew test` and JVM development. AAR packaging is
# the artifact pipeline for shipping to a real Android app.
#
# When implemented:
#
#   1. For each ABI in {arm64-v8a, armeabi-v7a, x86, x86_64}:
#      a. cargo build --release --target aarch64-linux-android (etc.)
#         The build.rs in rust-jni/ will recursively build the four
#         sep-*-ffi crates for the target.
#      b. Copy libonym_sdk_jni.so into src/main/jniLibs/<abi>/
#   2. Switch Gradle from `kotlin("jvm")` plugin to
#      `com.android.library` plugin (with abiFilters).
#   3. ./gradlew :assembleRelease produces an .aar that bundles
#      classes.jar + jni/<abi>/*.so + AndroidManifest.xml.
#
# Prerequisites:
#   - Android NDK (sdkmanager "ndk;27.0.12077973" or similar)
#   - rustup target add aarch64-linux-android armv7-linux-androideabi
#                       x86_64-linux-android i686-linux-android
#   - cargo-ndk crate, OR a per-ABI [target.<triple>] linker config in
#     ~/.cargo/config.toml pointing at the NDK's clang wrappers.

set -eu
echo "scripts/build-android-jni.sh — NOT IMPLEMENTED" >&2
echo "" >&2
echo "Host JVM development uses scripts/build-host-jni.sh (works today)." >&2
echo "Android-AAR cross-compile is a follow-up." >&2
exit 1
