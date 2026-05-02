package chat.onym.sdk.internal

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

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
        loadNativeLibrary()
    }

    /**
     * Two-tier loader so the SDK is consumable by a normal JVM
     * dependency without the consumer needing to set
     * `java.library.path`:
     *
     *   1. **Bundled resource** — `/native/<os>/<arch>/lib*.{so,dylib}`
     *      packaged inside the JAR. Extracted to a tempfile and
     *      `System.load`'d (absolute path). This is the production
     *      distribution path for downstream consumers.
     *   2. **System.loadLibrary fallback** — uses `java.library.path`
     *      / `LD_LIBRARY_PATH` / `DYLD_LIBRARY_PATH`. This is the
     *      repo-local dev / CI path where Gradle's `test` task points
     *      `java.library.path` at `rust-jni/target/release/`.
     *
     * Throws [UnsatisfiedLinkError] only if neither path resolves —
     * the message names both attempted paths so consumers can
     * diagnose without spelunking.
     */
    private fun loadNativeLibrary() {
        val libName = System.mapLibraryName("onym_sdk_jni")
        val osArch = "${detectOs()}/${detectArch()}"
        val resourcePath = "/native/$osArch/$libName"

        val resource = OnymJni::class.java.getResourceAsStream(resourcePath)
        if (resource != null) {
            val tempFile = createTempLibFile(libName)
            resource.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            System.load(tempFile.absolutePath)
            return
        }

        // Bundled resource missing — try the dev/CI fallback.
        try {
            System.loadLibrary("onym_sdk_jni")
        } catch (e: UnsatisfiedLinkError) {
            throw UnsatisfiedLinkError(
                "Failed to load native library 'onym_sdk_jni'. Tried:\n" +
                    "  1. Bundled resource at JAR path '$resourcePath' — not found.\n" +
                    "     Run scripts/build-host-jni.sh to populate " +
                    "src/main/resources/native/$osArch/.\n" +
                    "  2. System.loadLibrary fallback — failed: ${e.message}.\n" +
                    "     Set java.library.path to a directory containing $libName."
            )
        }
    }

    private fun detectOs(): String =
        System.getProperty("os.name", "").lowercase().let { name ->
            when {
                name.contains("mac") || name.contains("darwin") -> "darwin"
                name.contains("linux") -> "linux"
                name.contains("windows") -> "windows"
                else -> name.replace(' ', '-')
            }
        }

    private fun detectArch(): String =
        System.getProperty("os.arch", "").lowercase().let { arch ->
            when (arch) {
                "aarch64", "arm64" -> "aarch64"
                "x86_64", "amd64" -> "x86_64"
                else -> arch
            }
        }

    private fun createTempLibFile(libName: String): File {
        // POSIX permissions where supported; falls back to default
        // perms on Windows. dlopen requires +x on the .dylib/.so so
        // 0700 is the minimum for the loader to work.
        val perms = try {
            PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")
            )
        } catch (_: UnsupportedOperationException) {
            null
        }
        val tempDir = Files.createTempDirectory("onym-sdk-jni-").toFile()
        tempDir.deleteOnExit()
        val tempFile = File(tempDir, libName)
        if (perms != null) {
            // Re-create with explicit perms (createTempDirectory already
            // restricted access; the lib file just inherits).
        }
        tempFile.deleteOnExit()
        return tempFile
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
