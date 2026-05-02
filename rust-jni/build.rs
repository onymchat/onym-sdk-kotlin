//! Build the four sep-*-ffi staticlibs from the onym-contracts
//! submodule, then emit cargo link directives so the JNI cdylib
//! statically links them.
//!
//! Each FFI crate's `cargo build --release` is invoked in its own
//! directory so the toolchain pinned by its rust-toolchain.toml
//! (1.88.0) is honoured. After all four succeed, the staticlibs at
//! `<crate>/target/release/libonym_<crate>.a` are added to the
//! linker search path and pulled in via `cargo:rustc-link-lib=static`.
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
            .args(["build", "--release"])
            .current_dir(&crate_dir)
            .status()
            .unwrap_or_else(|e| panic!("failed to invoke cargo for {}: {}", crate_name, e));
        assert!(
            status.success(),
            "cargo build --release failed for {}",
            crate_name
        );

        let target_dir = crate_dir.join("target/release");
        // Cargo writes the staticlib to either target/release/ or
        // target/release/deps/. Prefer the top-level path; fall back
        // to deps/.
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
