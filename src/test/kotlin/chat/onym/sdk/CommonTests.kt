package chat.onym.sdk

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import java.security.MessageDigest

class CommonTests {

    @Test
    fun `leafHash is deterministic and 32 bytes`() {
        val a = Common.leafHash(fr(7))
        val b = Common.leafHash(fr(7))
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(Common.leafHash(fr(8))))
    }

    @Test
    fun `publicKey is 48 bytes and deterministic`() {
        val a = Common.publicKey(fr(42))
        val b = Common.publicKey(fr(42))
        assertEquals(48, a.size)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(Common.publicKey(fr(43))))
    }

    @Test
    fun `merkleRoot pads and is deterministic`() {
        val leaves = canonicalLeafHashes().copyOfRange(0, 3 * 32)
        val root = Common.merkleRoot(leaves, depth = 5)
        assertEquals(32, root.size)
        assertArrayEquals(root, Common.merkleRoot(leaves, depth = 5))
    }

    @Test
    fun `sha256Commitment matches hand-rolled SHA256`() {
        val root = ByteArray(32) { 0xAA.toByte() }
        val salt = ByteArray(32) { 0xBB.toByte() }
        val epoch = 0x0102030405060708L
        val commitment = Common.sha256Commitment(root, epoch, salt)
        assertEquals(32, commitment.size)

        val md = MessageDigest.getInstance("SHA-256")
        md.update(root)
        val epochBE = ByteArray(8)
        ByteBuffer.wrap(epochBE).putLong(epoch)
        md.update(epochBE)
        md.update(salt)
        val expected = md.digest()
        assertArrayEquals(expected, commitment)
    }

    @Test
    fun `poseidonCommitment is deterministic`() {
        val root = Common.leafHash(fr(99))
        val salt = ByteArray(32) { 0x33 }
        val a = Common.poseidonCommitment(root, 42L, salt)
        val b = Common.poseidonCommitment(root, 42L, salt)
        assertEquals(32, a.size)
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(Common.poseidonCommitment(root, 43L, salt)))
    }

    @Test
    fun `nostr sign + verify round trip`() {
        val sk = ByteArray(32) { 0x42 }
        val pk = Common.nostrDerivePublicKey(sk)
        assertEquals(32, pk.size)

        val eventId = ByteArray(32) { 0x55 }
        val sig = Common.nostrSignEventId(sk, eventId)
        assertEquals(64, sig.size)

        // Valid sig verifies.
        assertTrue(Common.nostrVerifyEventSignature(pk, eventId, sig))

        // Tampered event id is rejected (returns false, not throws).
        val tampered = eventId.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(Common.nostrVerifyEventSignature(pk, tampered, sig))
    }

    @Test
    fun `parsePlonkProof strips length prefixes`() {
        // Synthetic 1601-byte proof with distinctive byte patterns
        // per region; verify the slicer extracts the right ranges.
        val proof = ByteArray(1601)
        for (i in 8 until 488) proof[i] = 0x11
        for (i in 488 until 584) proof[i] = 0x22
        for (i in 592 until 1072) proof[i] = 0x33
        for (i in 1072 until 1168) proof[i] = 0x44
        for (i in 1168 until 1264) proof[i] = 0x55
        for (i in 1272 until 1432) proof[i] = 0x66
        for (i in 1440 until 1568) proof[i] = 0x77
        for (i in 1568 until 1600) proof[i] = 0x88.toByte()

        val parsed = Common.parsePlonkProof(proof)
        assertEquals(1568, parsed.size)
        assertTrue(parsed.copyOfRange(0, 480).all { it == 0x11.toByte() })
        assertTrue(parsed.copyOfRange(480, 576).all { it == 0x22.toByte() })
        assertTrue(parsed.copyOfRange(576, 1056).all { it == 0x33.toByte() })
        assertTrue(parsed.copyOfRange(1056, 1152).all { it == 0x44.toByte() })
        assertTrue(parsed.copyOfRange(1152, 1248).all { it == 0x55.toByte() })
        assertTrue(parsed.copyOfRange(1248, 1408).all { it == 0x66.toByte() })
        assertTrue(parsed.copyOfRange(1408, 1536).all { it == 0x77.toByte() })
        assertTrue(parsed.copyOfRange(1536, 1568).all { it == 0x88.toByte() })
    }

    @Test
    fun `nostr verify returns false for malformed inputs (does not throw)`() {
        // Audit Finding 3 regression: the FFI conflates "verification
        // failed" with "input length wrong" into a single bool return.
        // The wrapper docs reflect this — both must yield `false`,
        // not throw.
        val tooShortPk = ByteArray(31)
        val eventId = ByteArray(32)
        val sig = ByteArray(64)
        // Should NOT throw OnymException for the malformed pubkey.
        val result = Common.nostrVerifyEventSignature(tooShortPk, eventId, sig)
        assertFalse(result, "malformed input must return false (not throw)")
    }

    @Test
    fun `invalid secretKey length throws`() {
        val ex = assertThrows<OnymException> {
            Common.leafHash(byteArrayOf(0x01, 0x02))
        }
        assertTrue(ex.message!!.contains("secret_key"), "got: ${ex.message}")
    }
}
