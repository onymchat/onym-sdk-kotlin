//! Build the four sep-*-ffi staticlibs from the onym-contracts
//! submodule, then emit cargo link directives so the JNI cdylib
//! statically links them.
//!
//! Each FFI crate's `cargo build --release --target $TARGET` is
//! invoked in its own directory so:
//!   1. The toolchain pinned by its rust-toolchain.toml (1.88.0)
//!      is honoured.
//!   2. The staticlib lands at the same target triple as the
//!      rust-jni cdylib being built — critical for Android cross-
//!      compile, where omitting --target would silently produce
//!      host-arch staticlibs and the cdylib link would fail (or
//!      worse, silently link host-arch objects into the Android
//!      cdylib).
//!
//! Runs on every `cargo build` of this crate. Cargo's incremental
//! cache + the per-crate target dirs make repeat invocations cheap
//! once jellyfish is built (first cold build is ~30s, warm rebuilds
//! are <1s).

use std::env;
use std::path::PathBuf;
use std::process::Command;

fn main() {
    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let contracts_root = manifest_dir
        .parent()
        .expect("CARGO_MANIFEST_DIR has a parent")
        .join("External/onym-contracts");

    if !contracts_root.join("plonk").is_dir() {
        panic!(
            "expected onym-contracts submodule at {} — run `git submodule update --init --recursive`",
            contracts_root.display()
        );
    }

    // The triple cargo is currently building rust-jni for. For host
    // builds this is the host triple (e.g. aarch64-apple-darwin); for
    // cross builds (`cargo build --target X`) it's X. We propagate
    // the same triple to the FFI sub-builds so all staticlibs match
    // the cdylib's target.
    let target = env::var("TARGET").expect("TARGET set by cargo for build scripts");

    let crates = [
        "sep-common-ffi",
        "sep-anarchy-ffi",
        "sep-oneonone-ffi",
        "sep-tyranny-ffi",
    ];

    for crate_name in &crates {
        let crate_dir = contracts_root.join("plonk").join(crate_name);
        if !crate_dir.is_dir() {
            panic!("expected FFI crate at {}", crate_dir.display());
        }

        // Re-run build.rs if any of the FFI crate's source changes.
        // Coarse — `Cargo.toml` covers dep bumps, `src/` covers
        // implementation. Skip target/ to avoid feedback loops.
        println!("cargo:rerun-if-changed={}/Cargo.toml", crate_dir.display());
        println!("cargo:rerun-if-changed={}/src", crate_dir.display());

        let status = Command::new("cargo")
            .args(["build", "--release", "--target", &target])
            .current_dir(&crate_dir)
            .status()
            .unwrap_or_else(|e| panic!("failed to invoke cargo for {}: {}", crate_name, e));
        assert!(
            status.success(),
            "cargo build --release --target {} failed for {}",
            target,
            crate_name
        );

        // With --target, cargo always writes to target/<TARGET>/release/.
        // The .a may be at the top of that dir or in deps/ depending
        // on the version + crate-type set; check both.
        let target_dir = crate_dir.join("target").join(&target).join("release");
        let lib_name = crate_name.replace('-', "_");
        let staticlib = target_dir.join(format!("libonym_{}.a", lib_name));
        let staticlib = if staticlib.exists() {
            staticlib
        } else {
            target_dir.join("deps").join(format!("libonym_{}.a", lib_name))
        };
        assert!(
            staticlib.exists(),
            "expected staticlib not found: {}",
            staticlib.display()
        );

        println!(
            "cargo:rustc-link-search=native={}",
            staticlib.parent().unwrap().display()
        );
        println!("cargo:rustc-link-lib=static=onym_{}", lib_name);
    }
}
