package chat.onym.sdk

/**
 * OnymSDK — Kotlin wrapper around the per-type plonk FFI staticlibs
 * from `onym-contracts/plonk/sep-{common,anarchy,oneonone,tyranny}-ffi`,
 * routed through a Rust JNI shim.
 *
 * Top-level objects are namespaces (no instances):
 *
 * - [Common] — Poseidon hashing, BLS12-381 G1 pubkey, Merkle root,
 *   sha256 / poseidon commitment, plonk proof slicer, BIP340 / Nostr.
 * - [Anarchy] — single-signer membership groups (any member advances
 *   state).
 * - [OneOnOne] — single-tier two-party founding (depth=5; immutable
 *   post-create).
 * - [Tyranny] — single-admin governance (only the pinned admin can
 *   advance).
 *
 * Errors are surfaced as [OnymException] with the verbatim message
 * from the underlying FFI (already names the offending parameter,
 * expected length, etc.).
 *
 * Build prerequisites for host development / `./gradlew test`:
 *
 *     git submodule update --init --recursive
 *     ./scripts/build-host-jni.sh
 *     ./gradlew test
 *
 * Android-AAR cross-compilation is a follow-up — see
 * `scripts/build-android-jni.sh` (currently a stub).
 */
object OnymSDK {
    /** Semantic version of this SDK release. Bump when the public
     *  Kotlin API surface changes; FFI ABI changes (from the submodule)
     *  are tracked separately via the pinned commit. */
    const val VERSION = "0.1.0"
}
