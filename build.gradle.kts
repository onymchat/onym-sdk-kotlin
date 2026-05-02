// Pure-JVM Kotlin library that wraps the Rust JNI shim in
// rust-jni/target/release/libonym_sdk_jni.{so,dylib}.
//
// `./scripts/build-host-jni.sh` (or just `cargo build --release` in
// rust-jni/) must run before `./gradlew test` so the JVM can load the
// .so/.dylib at startup.
//
// Android-AAR packaging (with cross-compiled .so files for the four
// Android ABIs bundled inside) is a follow-up — see
// scripts/build-android-jni.sh stub.

plugins {
    kotlin("jvm") version "1.9.22"
}

group = "chat.onym"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()

    // Point the JVM at the host-built JNI cdylib. Resolves to:
    //   rust-jni/target/release/libonym_sdk_jni.dylib  (macOS)
    //   rust-jni/target/release/libonym_sdk_jni.so     (Linux)
    val nativeDir = file("$projectDir/rust-jni/target/release").absolutePath
    systemProperty("java.library.path", nativeDir)

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
