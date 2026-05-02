package chat.onym.sdk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AnarchyTests {

    @Test
    fun `bake membership VK returns 3002 bytes`() {
        assertEquals(3002, Anarchy.bakeMembershipVK(depth = 5).size)
    }

    @Test
    fun `bake update VK returns 3002 bytes`() {
        assertEquals(3002, Anarchy.bakeUpdateVK(depth = 5).size)
    }

    @Test
    fun `pinned membership VK hex is 64 chars`() {
        val hex = Anarchy.pinnedMembershipVKSha256Hex(depth = 5)
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `pinned update VK hex is 64 chars`() {
        assertEquals(64, Anarchy.pinnedUpdateVKSha256Hex(depth = 5).length)
    }

    @Test
    fun `unsupported tier throws`() {
        for (badDepth in listOf(0, 1, 4, 6, 7, 9, 10, 12, 16, 31)) {
            assertThrows<OnymException>("depth=$badDepth should be rejected") {
                Anarchy.bakeMembershipVK(depth = badDepth)
            }
            assertThrows<OnymException> {
                Anarchy.pinnedMembershipVKSha256Hex(depth = badDepth)
            }
        }
    }

    @Test
    fun `prove membership self-verifies via FFI`() {
        val leaves = canonicalLeafHashes()
        val proverIndex = 3
        val proverSk = fr((proverIndex + 1).toLong())

        val result = Anarchy.proveMembership(
            depth = 5,
            memberLeafHashes = leaves,
            proverSecretKey = proverSk,
            proverIndex = proverIndex,
            epoch = 1234L,
            salt = ByteArray(32) { 0xEE.toByte() },
        )
        assertEquals(1601, result.proof.size)
        assertEquals(32, result.commitment.size)
    }

    @Test
    fun `prove update with null new-roster sentinel`() {
        val leaves = canonicalLeafHashes()
        val proverIndex = 3
        val proverSk = fr((proverIndex + 1).toLong())

        val result = Anarchy.proveUpdate(
            depth = 5,
            memberLeafHashesOld = leaves,
            memberLeafHashesNew = null,
            proverSecretKey = proverSk,
            proverIndexOld = proverIndex,
            epochOld = 1234L,
            saltOld = ByteArray(32) { 0xEE.toByte() },
            saltNew = ByteArray(32) { 0xFF.toByte() },
        )
        assertEquals(1601, result.proof.size)
        // 96 B = 3 × 32 BE Fr (c_old || Fr(epoch_old) || c_new).
        assertEquals(96, result.publicInputs.size)
    }

    @Test
    fun `prove update empty new-roster array is rejected (not silently treated as reuse)`() {
        // Audit Finding 1 regression: a non-null but empty `byte[]`
        // for memberLeafHashesNew must NOT collapse to the {NULL, 0}
        // sentinel. The doc says "pass null to reuse old roster" —
        // anything else (including empty array) must surface as an
        // error from the FFI's strict-mixed-state guard.
        val leaves = canonicalLeafHashes()
        val proverIndex = 3
        val proverSk = fr((proverIndex + 1).toLong())

        val ex = assertThrows<OnymException> {
            Anarchy.proveUpdate(
                depth = 5,
                memberLeafHashesOld = leaves,
                memberLeafHashesNew = byteArrayOf(),  // non-null, len = 0
                proverSecretKey = proverSk,
                proverIndexOld = proverIndex,
                epochOld = 1234L,
                saltOld = ByteArray(32) { 0xEE.toByte() },
                saltNew = ByteArray(32) { 0xFF.toByte() },
            )
        }
        assertTrue(
            ex.message!!.contains("non-NULL") ||
                ex.message!!.contains("length is 0"),
            "expected mixed-state error, got: ${ex.message}"
        )
    }

    @Test
    fun `prove membership rejects mismatched secret key`() {
        val leaves = canonicalLeafHashes()
        // Lie: claim slot 3 but supply key #5.
        val wrongSk = fr(5)
        val ex = assertThrows<OnymException> {
            Anarchy.proveMembership(
                depth = 5,
                memberLeafHashes = leaves,
                proverSecretKey = wrongSk,
                proverIndex = 3,
                epoch = 0L,
                salt = ByteArray(32),
            )
        }
        assertTrue(ex.message!!.contains("does not match"), "got: ${ex.message}")
    }
}
