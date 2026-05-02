package chat.onym.sdk

import chat.onym.sdk.internal.OnymJni

/**
 * The SEP-OneOnOne contract type — single-tier (depth=5) two-party
 * founding circuit. Group is immutable post-create (no update path).
 *
 * Both parties' secret keys appear in the witness by design — the
 * founding ceremony IS when both keys are present in one place. The
 * FFI rejects `secretKey0 == secretKey1` to prevent one-person "1v1"
 * groups.
 *
 * The returned commitment is bit-identical to a depth-5 membership
 * commitment over the same `(root, epoch=0, salt)` triple, so 1v1
 * groups remain membership-verifiable later under the shared
 * `Anarchy.bakeMembershipVK(depth = 5)` VK.
 */
object OneOnOne {

    data class CreateProof(val proof: ByteArray, val commitment: ByteArray) {
        override fun equals(other: Any?): Boolean = this === other ||
            (other is CreateProof && proof.contentEquals(other.proof) &&
             commitment.contentEquals(other.commitment))
        override fun hashCode(): Int = 31 * proof.contentHashCode() + commitment.contentHashCode()
    }

    /** Bake the depth-5 oneonone-create VK. Output: 3002 bytes. */
    fun bakeCreateVK(): ByteArray =
        OnymJni.oneOnOneBakeCreateVk()

    /**
     * Generate a TurboPlonk oneonone-create proof.
     *
     * @param secretKey0 32 BE Fr — must differ from [secretKey1].
     * @param secretKey1 32 BE Fr — must differ from [secretKey0].
     * @param salt 32 bytes; LE-mod-r in-circuit.
     */
    fun proveCreate(
        secretKey0: ByteArray, secretKey1: ByteArray, salt: ByteArray
    ): CreateProof {
        val concat = OnymJni.oneOnOneProveCreate(secretKey0, secretKey1, salt)
        val (proof, commitment) = OnymJni.splitTwoBuffers(concat)
        return CreateProof(proof, commitment)
    }
}
