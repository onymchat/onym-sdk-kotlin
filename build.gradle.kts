// Pure-JVM Kotlin library that wraps the Rust JNI shim in
// rust-jni/target/<host-triple>/release/libonym_sdk_jni.{so,dylib}.
//
// Build matrix:
//   Host JVM dev/test loop:
//     ./scripts/build-host-jni.sh && ./gradlew test
//   Android AAR (per-ABI .so + classes.jar in one .aar):
//     ANDROID_NDK_HOME=... ./scripts/build-android-jni.sh
//     ./gradlew bundleAar
//   Maven publication (writes Maven layout to a local repo dir):
//     ./gradlew publishAarPublicationToReleaseRepository \
//         -PreleaseRepoUrl=file:///abs/path/to/maven-repo

plugins {
    kotlin("jvm") version "1.9.22"
    `maven-publish`
}

group = "chat.onym"
version = "0.0.1"

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

    // The JVM's System.loadLibrary("onym_sdk_jni") looks under
    // java.library.path. rust-jni/build.rs now propagates --target
    // to the FFI sub-builds, but the rust-jni cdylib itself still
    // lands at target/release/ for plain `cargo build --release`
    // (i.e. the host build path). Keep that wiring.
    val nativeDir = file("$projectDir/rust-jni/target/release").absolutePath
    systemProperty("java.library.path", nativeDir)

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

// ─── AAR assembly ────────────────────────────────────────────────
//
// AAR is a zip with a known internal layout. Required:
//   AndroidManifest.xml   (minimal stub at aar/AndroidManifest.xml)
//   classes.jar           (from `tasks.jar`)
//   R.txt                 (empty stub at aar/R.txt)
// Optional, included here:
//   jni/<abi>/*.so        (cross-compiled per-ABI native libs;
//                          produced by scripts/build-android-jni.sh
//                          into build/android/jniLibs/<abi>/)
//
// The bundleAar task does NOT itself invoke the Rust cross-compile
// — it expects the .so files to already be staged. Keeps Gradle out
// of Rust toolchain concerns.
val bundleAar = tasks.register<Zip>("bundleAar") {
    archiveBaseName.set("onym-sdk")
    archiveExtension.set("aar")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))

    from(tasks.named("jar")) {
        rename { "classes.jar" }
    }
    from(layout.projectDirectory.file("aar/AndroidManifest.xml"))
    from(layout.projectDirectory.file("aar/R.txt"))
    from(layout.projectDirectory.dir("build/android/jniLibs")) {
        into("jni")
    }
}

// ─── Publishing ──────────────────────────────────────────────────
//
// `./gradlew publishAarPublicationToReleaseRepository \
//     -PreleaseRepoUrl=file:///abs/path/to/maven-repo`
//
// Writes the Maven directory tree consumers resolve from:
//   chat/onym/onym-sdk/0.0.1/onym-sdk-0.0.1.aar
//                            onym-sdk-0.0.1.pom
//                            onym-sdk-0.0.1.module
//                            <each>.{md5,sha1,sha256,sha512}
//
// scripts/publish-to-releases-branch.sh then takes that tree and
// commits it to the long-lived `releases` branch (overwriting any
// existing content for the same version) along with a regenerated
// maven-metadata.xml that lists every published version.
publishing {
    publications {
        create<MavenPublication>("aar") {
            groupId = project.group.toString()
            artifactId = "onym-sdk"
            version = project.version.toString()

            artifact(bundleAar) {
                extension = "aar"
            }

            pom {
                name.set("OnymSDK")
                description.set(
                    "Kotlin / Android SDK for the per-type plonk FFI " +
                    "surface from onym-contracts (sep-{common,anarchy," +
                    "oneonone,tyranny}-ffi)."
                )
                url.set("https://github.com/onymchat/onym-sdk-kotlin")
                packaging = "aar"
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/onymchat/onym-sdk-kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/onymchat/onym-sdk-kotlin.git")
                    url.set("https://github.com/onymchat/onym-sdk-kotlin")
                }
            }
        }
    }
    repositories {
        maven {
            name = "release"
            // Override at invocation time:
            //   -PreleaseRepoUrl=file:///abs/path/to/maven-repo
            // Default points at build/maven-local for inspection.
            url = uri(
                providers.gradleProperty("releaseRepoUrl").getOrElse(
                    layout.buildDirectory.dir("maven-local").get().asFile.toURI().toString()
                )
            )
        }
    }
}
