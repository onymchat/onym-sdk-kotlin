# OnymSDK (Kotlin)

Kotlin/JVM wrapper around the per-type plonk FFI staticlibs from
[`onym-contracts`](https://github.com/onymchat/onym-contracts) — bake
verifying keys, generate TurboPlonk proofs, hash with Poseidon, sign
Nostr events. Idiomatic `throws OnymException` API on top of a Rust
JNI bridge.

```
                  ARCHITECTURE
                  ════════════

   ┌────────────────────────────────────────────┐
   │   Your Android / JVM app                   │
   └────────────────────┬───────────────────────┘
                        │   import chat.onym.sdk.*
                        ▼
   ┌────────────────────────────────────────────┐
   │   chat.onym.sdk.{Common, Anarchy,          │
   │                  OneOnOne, Tyranny}        │
   │                                            │
   │   ByteArray / Long / Int           (Kotlin)│
   └────────────────────┬───────────────────────┘
                        │   chat.onym.sdk.internal.OnymJni
                        │   System.loadLibrary("onym_sdk_jni")
                        ▼
   ┌────────────────────────────────────────────┐
   │  libonym_sdk_jni.{so,dylib}     (~340 KB)  │
   │                                            │
   │  Java_chat_onym_sdk_internal_OnymJni_*     │
   │  exports (23 entry points)         (Rust)  │
   │     ↓ static-links the four .a's:          │
   │       libonym_sep_common_ffi.a             │
   │       libonym_sep_anarchy_ffi.a            │
   │       libonym_sep_oneonone_ffi.a           │
   │       libonym_sep_tyranny_ffi.a            │
   │                                            │
   │  (built from External/onym-contracts/      │
   │   plonk/sep-*-ffi by build.rs +            │
   │   scripts/build-host-jni.sh)               │
   └────────────────────────────────────────────┘
```

LTO + cdylib dead-code elimination strips everything not transitively
reachable from the JNI exports — the final .so is **~340 KB**, not the
~110 MB you'd expect from concatenating the four standalone staticlibs.

## What's exposed

| Namespace        | Functions                                                              |
|------------------|------------------------------------------------------------------------|
| `Common`         | `leafHash`, `publicKey`, `merkleRoot`, `sha256Commitment`,             |
|                  | `poseidonCommitment`, `parsePlonkProof`, `nostrDerivePublicKey`,       |
|                  | `nostrSignEventId`, `nostrVerifyEventSignature`                        |
| `Anarchy`        | `bakeMembershipVK`, `bakeUpdateVK`, `pinnedMembershipVKSha256Hex`,     |
|                  | `pinnedUpdateVKSha256Hex`, `proveMembership`, `proveUpdate`            |
| `OneOnOne`       | `bakeCreateVK`, `proveCreate`                                          |
| `Tyranny`        | `bakeCreateVK`, `bakeUpdateVK`, `pinnedCreateVKSha256Hex`,             |
|                  | `pinnedUpdateVKSha256Hex`, `proveCreate`, `proveUpdate`                |

= **23 functions**, 1:1 with the underlying JNI surface (which is 1:1
with the user-facing C ABI from onym-contracts).

For the full ABI contract — byte encoding, depth tiers, witness
shape, public-input concat layouts — see the per-crate READMEs
upstream:

- [`sep-common-ffi/README.md`](https://github.com/onymchat/onym-contracts/blob/main/plonk/sep-common-ffi/README.md)
- [`sep-anarchy-ffi/README.md`](https://github.com/onymchat/onym-contracts/blob/main/plonk/sep-anarchy-ffi/README.md)
- [`sep-oneonone-ffi/README.md`](https://github.com/onymchat/onym-contracts/blob/main/plonk/sep-oneonone-ffi/README.md)
- [`sep-tyranny-ffi/README.md`](https://github.com/onymchat/onym-contracts/blob/main/plonk/sep-tyranny-ffi/README.md)

`sep-democracy-ffi` and `sep-oligarchy-ffi` are intentionally not in
this SDK — their K-of-N quorum witnesses can't be safely surfaced
via mobile FFI without a quorum-model redesign. See
[onym-contracts#26](https://github.com/onymchat/onym-contracts/issues/26).

## Quick start

```kotlin
import chat.onym.sdk.*

// 1. Build the visible member tree state (off-chain, public-ish).
val memberSecretKeys: List<ByteArray> = ...   // each 32 BE Fr
val leaves = ByteArray(memberSecretKeys.size * 32)
var offset = 0
for (sk in memberSecretKeys) {
    Common.leafHash(sk).copyInto(leaves, offset); offset += 32
}

// 2. Bake the per-tier membership VK (pin in your contract).
val vk: ByteArray = Anarchy.bakeMembershipVK(depth = 5)

// 3. Prove membership locally.
val mySk = memberSecretKeys[3]
val result: Anarchy.MembershipProof = Anarchy.proveMembership(
    depth = 5,
    memberLeafHashes = leaves,
    proverSecretKey = mySk,
    proverIndex = 3,
    epoch = 0L,
    salt = ByteArray(32) { 0xEE.toByte() },
)
// result.proof       — 1601-byte uncompressed plonk proof
// result.commitment  — 32 BE Fr (the public-input commitment)

// Ship (proof, commitment) to the SEP-Anarchy Soroban contract.
```

Errors are surfaced as `OnymException` with the verbatim message from
the underlying FFI — already names the offending parameter, expected
length, etc.

## Build

```sh
git submodule update --init --recursive
./scripts/build-host-jni.sh        # cargo build → libonym_sdk_jni.{so,dylib}
./gradlew test                     # 25 JUnit cases, ~25s
```

The host build produces a single `libonym_sdk_jni.{so,dylib}` at
`rust-jni/target/release/`. Gradle's `test` task adds that directory
to `java.library.path` so `System.loadLibrary("onym_sdk_jni")`
resolves.

**Android-AAR cross-compilation is a follow-up** — see
`scripts/build-android-jni.sh` (currently a stub).

## Versioning

Two layers move independently:

- **Kotlin API (this repo)**: `OnymSDK.VERSION` — bump when the
  public Kotlin surface changes.
- **FFI ABI (onym-contracts submodule)**: pinned to a specific commit;
  bump the submodule SHA + this README when the C ABI changes.

The per-type Soroban contracts (sep-anarchy, sep-tyranny, sep-oneonone)
have their own release cycles in `onym-contracts`.

## Layout

```
.
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/                 ← gradle 8.14 wrapper jar + props
├── gradlew                         ← bootstrap launcher (bash)
├── rust-jni/
│   ├── Cargo.toml
│   ├── build.rs                    ← builds the 4 sep-*-ffi crates,
│   │                                  emits cargo:rustc-link directives
│   └── src/lib.rs                  ← 23 Java_* exports
├── src/main/kotlin/chat/onym/sdk/
│   ├── OnymSDK.kt                  ← package-level docs
│   ├── OnymException.kt            ← single error type
│   ├── Common.kt                   ← 9 shared primitives
│   ├── Anarchy.kt                  ← 6 anarchy fns
│   ├── OneOnOne.kt                 ← 2 oneonone fns
│   ├── Tyranny.kt                  ← 6 tyranny fns
│   └── internal/OnymJni.kt         ← @JvmStatic external fun decls +
│                                     splitTwoBuffers() helper
├── src/test/kotlin/chat/onym/sdk/  ← 25 JUnit 5 cases
├── scripts/
│   ├── build-host-jni.sh           ← cargo build → .so/.dylib
│   └── build-android-jni.sh        ← TODO (per-ABI cross-compile)
└── External/
    └── onym-contracts/             ← submodule
```

## License

MIT — see `LICENSE`.
