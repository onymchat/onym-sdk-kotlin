package chat.onym.sdk

import chat.onym.sdk.internal.OnymJni

/**
 * Shared primitives across every per-type API. Wraps `sep-common-ffi`:
 * Poseidon hashing, BLS12-381 G1 pubkey derivation, native Merkle
 * root, sha256 + poseidon commitment, plonk proof component slicer,
 * and BIP340 (Nostr) secp256k1 schnorr.
 *
 * All inputs / outputs are `ByteArray`. See each function's doc for
 * the exact byte shape (BE vs LE, expected length).
 */
object Common {

    // MARK: - Hashing

    /**
     * Poseidon leaf hash `Poseidon(sk_fr)`.
     *
     * @param secretKey 32 BE bytes (BLS12-381 Fr scalar).
     * @return 32 BE bytes — suitable as a member leaf in any sep-*
     *   Poseidon Merkle tree.
     */
    fun leafHash(secretKey: ByteArray): ByteArray =
        OnymJni.computeLeafHash(secretKey)

    /**
     * BLS12-381 G1 compressed public key `[sk] · G`.
     *
     * @param secretKey 32 BE bytes.
     * @return 48 bytes (arkworks compressed G1Affine).
     */
    fun publicKey(secretKey: ByteArray): ByteArray =
        OnymJni.computePublicKey(secretKey)

    /**
     * Poseidon Merkle root over `leafHashes` padded with `Fr::ZERO`
     * to a complete tree of `depth`.
     *
     * @param leafHashes packed 32 BE Fr scalars (length must be a
     *   multiple of 32, ≤ 2^depth scalars).
     * @return 32 BE Fr (the root).
     */
    fun merkleRoot(leafHashes: ByteArray, depth: Int): ByteArray =
        OnymJni.computeMerkleRoot(leafHashes, depth)

    // MARK: - Commitments

    /**
     * Legacy v1 SHA-256 commitment: `SHA256(root || epoch_BE8 || salt)`.
     * For clients still talking to v1 sep-xxxx Soroban contracts.
     *
     * @return 32 bytes.
     */
    fun sha256Commitment(poseidonRoot: ByteArray, epoch: Long, salt: ByteArray): ByteArray =
        OnymJni.computeSha256Commitment(poseidonRoot, epoch, salt)

    /**
     * Plonk-era 2-level commitment:
     * `Poseidon(Poseidon(root_fr, Fr(epoch)), salt_fr)` where
     * `salt_fr = Fr::from_le_bytes_mod_order(salt)`.
     *
     * @param salt must be 32 bytes; LE-mod-r in-circuit.
     * @return 32 BE Fr.
     */
    fun poseidonCommitment(poseidonRoot: ByteArray, epoch: Long, salt: ByteArray): ByteArray =
        OnymJni.computePoseidonCommitment(poseidonRoot, epoch, salt)

    // MARK: - Plonk proof slicer

    /**
     * Strip the four `len()` u64 prefixes and trailing `plookup_proof:
     * Option = None` byte from a 1601-byte uncompressed jf-plonk proof.
     *
     * @return 1568-byte concat —
     *   `wires(5×96) ++ prod_perm(96) ++ split_quot(5×96) ++
     *   opening(96) ++ shifted_opening(96) ++ wires_evals(5×32) ++
     *   wire_sigma_evals(4×32) ++ perm_next_eval(32)`.
     */
    fun parsePlonkProof(proof: ByteArray): ByteArray =
        OnymJni.parsePlonkProof(proof)

    // MARK: - Nostr (BIP340 secp256k1 schnorr)

    /** 32-byte BIP340 x-only public key from a 32-byte secret. */
    fun nostrDerivePublicKey(secretKey: ByteArray): ByteArray =
        OnymJni.nostrDerivePublicKey(secretKey)

    /** BIP340 Schnorr signature over a 32-byte event id. Returns 64 bytes. */
    fun nostrSignEventId(secretKey: ByteArray, eventId: ByteArray): ByteArray =
        OnymJni.nostrSignEventId(secretKey, eventId)

    /**
     * Verify a BIP340 Schnorr signature over a 32-byte event id.
     *
     * @return `true` iff the signature is valid; `false` otherwise.
     *   Throws [OnymException] only on malformed input lengths.
     */
    fun nostrVerifyEventSignature(
        publicKey: ByteArray, eventId: ByteArray, signature: ByteArray
    ): Boolean =
        OnymJni.nostrVerifyEventSignature(publicKey, eventId, signature)
}
