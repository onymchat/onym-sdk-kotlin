package chat.onym.sdk

import java.nio.ByteBuffer

/**
 * 32-byte BE encoding of a small Long — cheap way to build BLS12-381
 * Fr scalars in tests without bringing in arkworks. The high 24 bytes
 * are zero; low 8 bytes are the value in big-endian.
 */
fun fr(value: Long): ByteArray {
    val out = ByteArray(32)
    ByteBuffer.wrap(out, 24, 8).putLong(value)
    return out
}

/**
 * 32 BE Fr leaf hashes for keys 1..=8, packed. Mirrors the canonical
 * witness shape used by the prover crate's round-trip tests so an
 * FFI proof produced here verifies under the same baked VK.
 *
 * Computed by calling [Common.leafHash] on each secret key — i.e. via
 * the FFI itself rather than re-implementing Poseidon in Kotlin.
 */
fun canonicalLeafHashes(): ByteArray {
    val out = ByteArray(8 * 32)
    var offset = 0
    for (sk in 1L..8L) {
        val leaf = Common.leafHash(fr(sk))
        check(leaf.size == 32) { "leafHash returned ${leaf.size} bytes, expected 32" }
        leaf.copyInto(out, offset)
        offset += 32
    }
    return out
}
