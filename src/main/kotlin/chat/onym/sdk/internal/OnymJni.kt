package chat.onym.sdk.internal

/**
 * Raw JNI declarations matching `Java_chat_onym_sdk_internal_OnymJni_*`
 * exports in `rust-jni/src/lib.rs`. Internal to the SDK — every public
 * call site lives in `Common`, `Anarchy`, `OneOnOne`, or `Tyranny`.
 *
 * Two-buffer outputs (proof + commitment, proof + public_inputs) are
 * returned as a single length-prefixed `ByteArray`:
 *
 *     ┌────────────┬────────────────┬────────────────┐
 *     │ uint32 BE  │ first buffer   │ second buffer  │
 *     │ = a.length │ (a.length B)   │ (rest)         │
 *     └────────────┴────────────────┴────────────────┘
 *
 * `splitTwoBuffers()` is the canonical decoder used by the namespace
 * wrappers above.
 */
internal object OnymJni {

    init {
        // Loads libonym_sdk_jni.{so,dylib} from one of:
        //   * java.library.path system property (set by Gradle test task)
        //   * the platform's default LD_LIBRARY_PATH / DYLD_LIBRARY_PATH
        // Throws UnsatisfiedLinkError if missing — make sure
        // ./scripts/build-host-jni.sh ran before invoking the SDK.
        System.loadLibrary("onym_sdk_jni")
    }

    // ----- Common (sep-common-ffi) -----

    @JvmStatic external fun computeLeafHash(secretKey: ByteArray): ByteArray
    @JvmStatic external fun computePublicKey(secretKey: ByteArray): ByteArray
    @JvmStatic external fun computeMerkleRoot(leafHashes: ByteArray, depth: Int): ByteArray
    @JvmStatic external fun computeSha256Commitment(
        root: ByteArray, epoch: Long, salt: ByteArray
    ): ByteArray
    @JvmStatic external fun computePoseidonCommitment(
        root: ByteArray, epoch: Long, salt: ByteArray
    ): ByteArray
    @JvmStatic external fun parsePlonkProof(proof: ByteArray): ByteArray
    @JvmStatic external fun nostrDerivePublicKey(secretKey: ByteArray): ByteArray
    @JvmStatic external fun nostrSignEventId(secretKey: ByteArray, eventId: ByteArray): ByteArray
    @JvmStatic external fun nostrVerifyEventSignature(
        publicKey: ByteArray, eventId: ByteArray, signature: ByteArray
    ): Boolean

    // ----- Anarchy (sep-anarchy-ffi) -----

    @JvmStatic external fun anarchyBakeMembershipVk(depth: Int): ByteArray
    @JvmStatic external fun anarchyBakeUpdateVk(depth: Int): ByteArray
    @JvmStatic external fun anarchyPinnedMembershipVkSha256Hex(depth: Int): ByteArray
    @JvmStatic external fun anarchyPinnedUpdateVkSha256Hex(depth: Int): ByteArray
    /** Returns length-prefixed concat — see class doc. */
    @JvmStatic external fun anarchyProveMembership(
        depth: Int,
        leaves: ByteArray,
        proverSk: ByteArray,
        proverIndex: Int,
        epoch: Long,
        salt: ByteArray,
    ): ByteArray
    /** Returns length-prefixed concat — see class doc. */
    @JvmStatic external fun anarchyProveUpdate(
        depth: Int,
        leavesOld: ByteArray,
        leavesNew: ByteArray?,         // null = reuse old roster sentinel
        proverSk: ByteArray,
        proverIndexOld: Int,
        epochOld: Long,
        saltOld: ByteArray,
        saltNew: ByteArray,
    ): ByteArray

    // ----- OneOnOne (sep-oneonone-ffi) -----

    @JvmStatic external fun oneOnOneBakeCreateVk(): ByteArray
    /** Returns length-prefixed concat — see class doc. */
    @JvmStatic external fun oneOnOneProveCreate(
        sk0: ByteArray, sk1: ByteArray, salt: ByteArray
    ): ByteArray

    // ----- Tyranny (sep-tyranny-ffi) -----

    @JvmStatic external fun tyrannyBakeCreateVk(depth: Int): ByteArray
    @JvmStatic external fun tyrannyBakeUpdateVk(depth: Int): ByteArray
    @JvmStatic external fun tyrannyPinnedCreateVkSha256Hex(depth: Int): ByteArray
    @JvmStatic external fun tyrannyPinnedUpdateVkSha256Hex(depth: Int): ByteArray
    /** Returns length-prefixed concat — see class doc. */
    @JvmStatic external fun tyrannyProveCreate(
        depth: Int,
        leaves: ByteArray,
        adminSk: ByteArray,
        adminIndex: Int,
        groupIdFr: ByteArray,
        salt: ByteArray,
    ): ByteArray
    /** Returns length-prefixed concat — see class doc. */
    @JvmStatic external fun tyrannyProveUpdate(
        depth: Int,
        leavesOld: ByteArray,
        adminSk: ByteArray,
        adminIndexOld: Int,
        epochOld: Long,
        memberRootNew: ByteArray,
        groupIdFr: ByteArray,
        saltOld: ByteArray,
        saltNew: ByteArray,
    ): ByteArray

    /**
     * Decode the length-prefixed two-buffer concat the JNI shim
     * returns from prove_* paths. Format: `[u32 BE = a.len] || a || b`.
     * Total length = 4 + a.size + b.size.
     */
    @JvmStatic
    fun splitTwoBuffers(concat: ByteArray): Pair<ByteArray, ByteArray> {
        require(concat.size >= 4) {
            "two-buffer concat too short: ${concat.size} bytes"
        }
        val aLen = ((concat[0].toInt() and 0xFF) shl 24) or
                   ((concat[1].toInt() and 0xFF) shl 16) or
                   ((concat[2].toInt() and 0xFF) shl 8) or
                   (concat[3].toInt() and 0xFF)
        require(4 + aLen <= concat.size) {
            "two-buffer concat truncated: aLen=$aLen, total=${concat.size}"
        }
        val a = concat.copyOfRange(4, 4 + aLen)
        val b = concat.copyOfRange(4 + aLen, concat.size)
        return a to b
    }
}
