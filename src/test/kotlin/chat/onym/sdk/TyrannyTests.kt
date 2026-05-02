package chat.onym.sdk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TyrannyTests {

    @Test
    fun `bake create + update VKs return 3002 bytes`() {
        assertEquals(3002, Tyranny.bakeCreateVK(depth = 5).size)
        assertEquals(3002, Tyranny.bakeUpdateVK(depth = 5).size)
    }

    @Test
    fun `pinned create + update hex are 64 chars`() {
        assertEquals(64, Tyranny.pinnedCreateVKSha256Hex(depth = 5).length)
        assertEquals(64, Tyranny.pinnedUpdateVKSha256Hex(depth = 5).length)
    }

    @Test
    fun `unsupported tier throws`() {
        for (badDepth in listOf(0, 1, 4, 7, 12, 31)) {
            assertThrows<OnymException> { Tyranny.bakeCreateVK(depth = badDepth) }
            assertThrows<OnymException> { Tyranny.bakeUpdateVK(depth = badDepth) }
            assertThrows<OnymException> { Tyranny.pinnedCreateVKSha256Hex(depth = badDepth) }
        }
    }

    @Test
    fun `prove create self-verifies via FFI`() {
        val leaves = canonicalLeafHashes()
        val result = Tyranny.proveCreate(
            depth = 5,
            memberLeafHashes = leaves,
            adminSecretKey = fr(1),
            adminIndex = 0,
            groupIdFr = fr(0x7777),
            salt = ByteArray(32) { 0xEE.toByte() },
        )
        assertEquals(1601, result.proof.size)
        // 128 B = commitment(32) || Fr(0)(32) || admin_pk_commitment(32) || group_id_fr(32)
        assertEquals(128, result.publicInputs.size)
    }

    @Test
    fun `prove update self-verifies via FFI`() {
        val leaves = canonicalLeafHashes()
        val memberRootNew = Common.merkleRoot(leaves, depth = 5)
        val result = Tyranny.proveUpdate(
            depth = 5,
            memberLeafHashesOld = leaves,
            adminSecretKey = fr(1),
            adminIndexOld = 0,
            epochOld = 1234L,
            memberRootNew = memberRootNew,
            groupIdFr = fr(0x7777),
            saltOld = ByteArray(32) { 0xEE.toByte() },
            saltNew = ByteArray(32) { 0xFF.toByte() },
        )
        assertEquals(1601, result.proof.size)
        // 160 B = c_old(32) || Fr(epoch_old)(32) || c_new(32) || admin_pk_commitment(32) || group_id_fr(32)
        assertEquals(160, result.publicInputs.size)
    }

    @Test
    fun `prove create rejects mismatched admin secret key`() {
        val leaves = canonicalLeafHashes()
        val ex = assertThrows<OnymException> {
            Tyranny.proveCreate(
                depth = 5,
                memberLeafHashes = leaves,
                adminSecretKey = fr(3), // not admin's key
                adminIndex = 0,
                groupIdFr = fr(0x7777),
                salt = ByteArray(32),
            )
        }
        assertTrue(ex.message!!.contains("does not match"), "got: ${ex.message}")
    }
}
