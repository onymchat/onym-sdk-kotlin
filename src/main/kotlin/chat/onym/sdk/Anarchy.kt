package chat.onym.sdk

import chat.onym.sdk.internal.OnymJni

/**
 * The SEP-Anarchy contract type — single-signer membership groups.
 * Any member can advance the commitment by producing a TurboPlonk
 * membership / update proof from their own secret key.
 *
 * Supported tiers: depth ∈ {5, 8, 11} (Small / Medium / Large).
 */
object Anarchy {

    /** `(proof, commitment)` returned by [proveMembership]. */
    data class MembershipProof(val proof: ByteArray, val commitment: ByteArray) {
        override fun equals(other: Any?): Boolean = this === other ||
            (other is MembershipProof && proof.contentEquals(other.proof) &&
             commitment.contentEquals(other.commitment))
        override fun hashCode(): Int = 31 * proof.contentHashCode() + commitment.contentHashCode()
    }

    /**
     * `(proof, publicInputs)` returned by [proveUpdate]. publicInputs
     * is 96 B = 3 × 32 BE Fr scalars (`c_old || Fr(epoch_old) || c_new`)
     * — exactly the vector the on-chain plonk verifier consumes.
     */
    data class UpdateProof(val proof: ByteArray, val publicInputs: ByteArray) {
        override fun equals(other: Any?): Boolean = this === other ||
            (other is UpdateProof && proof.contentEquals(other.proof) &&
             publicInputs.contentEquals(other.publicInputs))
        override fun hashCode(): Int = 31 * proof.contentHashCode() + publicInputs.contentHashCode()
    }

    // MARK: - Bake VK

    /** Bake the per-tier membership VK. Output: 3002-byte VK bytes. */
    fun bakeMembershipVK(depth: Int): ByteArray =
        OnymJni.anarchyBakeMembershipVk(depth)

    /** Bake the per-tier update VK. Output: 3002-byte VK bytes. */
    fun bakeUpdateVK(depth: Int): ByteArray =
        OnymJni.anarchyBakeUpdateVk(depth)

    // MARK: - Pinned VK SHA-256

    /**
     * 64-char ASCII hex of the prover's pinned membership-VK SHA-256
     * for `depth`. Throws [OnymException] if `depth` is not a
     * supported tier.
     */
    fun pinnedMembershipVKSha256Hex(depth: Int): String =
        OnymJni.anarchyPinnedMembershipVkSha256Hex(depth).decodeToString()

    fun pinnedUpdateVKSha256Hex(depth: Int): String =
        OnymJni.anarchyPinnedUpdateVkSha256Hex(depth).decodeToString()

    // MARK: - Prove

    /**
     * Generate a TurboPlonk anarchy-membership proof.
     *
     * @param depth 5, 8, or 11.
     * @param memberLeafHashes packed 32 BE Fr per member
     *   (`Poseidon(member_sk)`), ≤ 2^depth entries.
     * @param proverSecretKey prover's own 32 BE Fr.
     * @param proverIndex prover's leaf position in the roster.
     * @param epoch group epoch (commitment-bound public input).
     * @param salt 32 bytes; LE-mod-r in-circuit.
     */
    fun proveMembership(
        depth: Int,
        memberLeafHashes: ByteArray,
        proverSecretKey: ByteArray,
        proverIndex: Int,
        epoch: Long,
        salt: ByteArray,
    ): MembershipProof {
        val concat = OnymJni.anarchyProveMembership(
            depth, memberLeafHashes, proverSecretKey, proverIndex, epoch, salt
        )
        val (proof, commitment) = OnymJni.splitTwoBuffers(concat)
        return MembershipProof(proof, commitment)
    }

    /**
     * Generate a TurboPlonk anarchy-update proof.
     *
     * @param memberLeafHashesNew new-tree leaf hashes. Pass `null` to
     *   reuse the old roster (no roster change). Mixed states (e.g.
     *   empty array but not null) are rejected by the FFI.
     * @param epochOld only-epoch PI; new epoch is implicit
     *   `epoch_old + 1`.
     */
    fun proveUpdate(
        depth: Int,
        memberLeafHashesOld: ByteArray,
        memberLeafHashesNew: ByteArray?,
        proverSecretKey: ByteArray,
        proverIndexOld: Int,
        epochOld: Long,
        saltOld: ByteArray,
        saltNew: ByteArray,
    ): UpdateProof {
        val concat = OnymJni.anarchyProveUpdate(
            depth, memberLeafHashesOld, memberLeafHashesNew,
            proverSecretKey, proverIndexOld, epochOld, saltOld, saltNew
        )
        val (proof, publicInputs) = OnymJni.splitTwoBuffers(concat)
        return UpdateProof(proof, publicInputs)
    }
}
