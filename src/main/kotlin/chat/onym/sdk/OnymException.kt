package chat.onym.sdk

/**
 * Thrown by every fallible OnymSDK call when the underlying FFI
 * returns false. The [message] is verbatim from the FFI — already
 * names the offending parameter, expected length, etc.
 *
 * Examples:
 *   "out_proof pointer was null"
 *   "depth 7 is not a supported tier; valid tiers: 5 (Small), …"
 *   "prover_secret_key does not match member_leaf_hashes[3]: …"
 */
class OnymException(message: String) : RuntimeException(message)
