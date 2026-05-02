package chat.onym.sdk

import chat.onym.sdk.internal.OnymJni

/**
 * The SEP-Tyranny contract type — single-admin per-group governance.
 * Only the pinned admin can advance state. Both create and update
 * circuits add admin binding (`admin_pubkey_commitment =
 * Poseidon(Poseidon(admin_sk), group_id_fr)`) on top of the shared
 * anarchy membership/update circuits; the per-group `groupIdFr` scalar
 * closes cross-group linkability.
 *
 * Supported tiers: depth ∈ {5, 8, 11}.
 */
object Tyranny {

    /**
     * `(proof, publicInputs)` returned by [proveCreate]. publicInputs
     * is 128 B = `commitment(32) || Fr(0)(32) ||
     * admin_pubkey_commitment(32) || group_id_fr(32)`.
     */
    data class CreateProof(val proof: ByteArray, val publicInputs: ByteArray) {
        override fun equals(other: Any?): Boolean = this === other ||
            (other is CreateProof && proof.contentEquals(other.proof) &&
             publicInputs.contentEquals(other.publicInputs))
        override fun hashCode(): Int = 31 * proof.contentHashCode() + publicInputs.contentHashCode()
    }

    /**
     * `(proof, publicInputs)` returned by [proveUpdate]. publicInputs
     * is 160 B = `c_old(32) || Fr(epoch_old)(32) || c_new(32) ||
     * admin_pubkey_commitment(32) || group_id_fr(32)`.
     */
    data class UpdateProof(val proof: ByteArray, val publicInputs: ByteArray) {
        override fun equals(other: Any?): Boolean = this === other ||
            (other is UpdateProof && proof.contentEquals(other.proof) &&
             publicInputs.contentEquals(other.publicInputs))
        override fun hashCode(): Int = 31 * proof.contentHashCode() + publicInputs.contentHashCode()
    }

    // MARK: - Bake VK

    fun bakeCreateVK(depth: Int): ByteArray =
        OnymJni.tyrannyBakeCreateVk(depth)

    fun bakeUpdateVK(depth: Int): ByteArray =
        OnymJni.tyrannyBakeUpdateVk(depth)

    // MARK: - Pinned VK SHA-256

    fun pinnedCreateVKSha256Hex(depth: Int): String =
        OnymJni.tyrannyPinnedCreateVkSha256Hex(depth).decodeToString()

    fun pinnedUpdateVKSha256Hex(depth: Int): String =
        OnymJni.tyrannyPinnedUpdateVkSha256Hex(depth).decodeToString()

    // MARK: - Prove

    /**
     * Generate a TurboPlonk tyranny-create proof.
     *
     * @param adminSecretKey admin's own 32 BE Fr (sanity-checked
     *   against `memberLeafHashes[adminIndex]` by the FFI).
     * @param groupIdFr 32 BE Fr per-group binding scalar.
     * @param salt 32 bytes; LE-mod-r in-circuit.
     */
    fun proveCreate(
        depth: Int,
        memberLeafHashes: ByteArray,
        adminSecretKey: ByteArray,
        adminIndex: Int,
        groupIdFr: ByteArray,
        salt: ByteArray,
    ): CreateProof {
        val concat = OnymJni.tyrannyProveCreate(
            depth, memberLeafHashes, adminSecretKey, adminIndex, groupIdFr, salt
        )
        val (proof, publicInputs) = OnymJni.splitTwoBuffers(concat)
        return CreateProof(proof, publicInputs)
    }

    /**
     * Generate a TurboPlonk tyranny-update proof.
     *
     * @param memberRootNew 32 BE Fr; new tree's root, supplied
     *   directly because admin needn't know the full new roster
     *   (binding-only).
     * @param epochOld only-epoch PI; new epoch is implicit
     *   `epoch_old + 1`.
     */
    fun proveUpdate(
        depth: Int,
        memberLeafHashesOld: ByteArray,
        adminSecretKey: ByteArray,
        adminIndexOld: Int,
        epochOld: Long,
        memberRootNew: ByteArray,
        groupIdFr: ByteArray,
        saltOld: ByteArray,
        saltNew: ByteArray,
    ): UpdateProof {
        val concat = OnymJni.tyrannyProveUpdate(
            depth, memberLeafHashesOld, adminSecretKey, adminIndexOld,
            epochOld, memberRootNew, groupIdFr, saltOld, saltNew
        )
        val (proof, publicInputs) = OnymJni.splitTwoBuffers(concat)
        return UpdateProof(proof, publicInputs)
    }
}
