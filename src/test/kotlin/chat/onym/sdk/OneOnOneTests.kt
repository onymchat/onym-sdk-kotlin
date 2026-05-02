package chat.onym.sdk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OneOnOneTests {

    @Test
    fun `bake create VK returns 3002 bytes`() {
        assertEquals(3002, OneOnOne.bakeCreateVK().size)
    }

    @Test
    fun `prove create self-verifies via FFI`() {
        val result = OneOnOne.proveCreate(
            secretKey0 = fr(1),
            secretKey1 = fr(2),
            salt = ByteArray(32) { 0xEE.toByte() },
        )
        assertEquals(1601, result.proof.size)
        assertEquals(32, result.commitment.size)
    }

    @Test
    fun `prove create rejects identical secret keys`() {
        val same = fr(42)
        val ex = assertThrows<OnymException> {
            OneOnOne.proveCreate(
                secretKey0 = same,
                secretKey1 = same,
                salt = ByteArray(32),
            )
        }
        assertTrue(
            ex.message!!.contains("distinct") || ex.message!!.contains("=="),
            "got: ${ex.message}"
        )
    }
}
