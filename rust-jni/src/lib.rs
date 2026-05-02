//! JNI bridge from `chat.onym.sdk` Kotlin to the four onym-contracts
//! plonk FFI staticlibs (sep-{common,anarchy,oneonone,tyranny}-ffi).
//!
//! Layout: every Kotlin `external fun` in `chat.onym.sdk.internal.OnymJni`
//! has a matching `Java_chat_onym_sdk_internal_OnymJni_<methodName>`
//! function below. The shape is uniform — read JNI inputs, call the
//! C ABI, throw `RuntimeException` on FFI error, return a Kotlin
//! `ByteArray` (or `String`) on success.
//!
//! Drift between the Kotlin signatures and these JNI symbols surfaces
//! as `UnsatisfiedLinkError` at first call — there's no compile-time
//! cross-check (JNI is dynamic). Tests catch it on the first run.

use std::ffi::{c_char, CStr};
use std::ptr;

use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jstring};
use jni::JNIEnv;

// The four FFI crates are Rust path deps (rlibs in cargo's view) —
// see rust-jni/Cargo.toml. Each rlib `#[no_mangle] pub extern "C" fn
// onym_*` symbol IS in the rlib's compiled object, but rustc's dead-
// code analysis does not consider an `extern "C"` declaration in this
// crate to be a "use" of the rlib's same-named function — they're
// link-resolved, not Rust-resolved. Without a Rust-level reference,
// rustc strips the rlib symbols before linking, and the cdylib link
// fails with "undefined symbol _onym_<...>".
//
// The `_onym_ffi_keepalive` function below takes the address of every
// onym_* symbol via its Rust path. That counts as a Rust-level
// reference, holds the rlib symbols in the dep graph, and the linker
// then resolves the `extern "C"` decls in the JNI bridge functions
// against them. The function itself is `#[no_mangle] pub extern "C"`
// so the linker keeps IT alive (preventing the keep-alive from being
// dead-stripped). It is never called from JNI / Kotlin — pure DCE
// barrier. Returns 25 (the symbol count) so the result isn't elided.
//
// Same pattern as onym-sdk-swift/rust/onym-ffi-umbrella.

#[no_mangle]
pub extern "C" fn _onym_ffi_keepalive() -> usize {
    let table: [usize; 25] = [
        // sep-common-ffi (11 symbols)
        onym_sep_common_ffi::onym_byte_buffer_free            as usize,
        onym_sep_common_ffi::onym_string_free                 as usize,
        onym_sep_common_ffi::onym_compute_leaf_hash           as usize,
        onym_sep_common_ffi::onym_compute_public_key          as usize,
        onym_sep_common_ffi::onym_compute_merkle_root         as usize,
        onym_sep_common_ffi::onym_compute_sha256_commitment   as usize,
        onym_sep_common_ffi::onym_compute_poseidon_commitment as usize,
        onym_sep_common_ffi::onym_parse_plonk_proof           as usize,
        onym_sep_common_ffi::onym_nostr_derive_public_key     as usize,
        onym_sep_common_ffi::onym_nostr_sign_event_id         as usize,
        onym_sep_common_ffi::onym_nostr_verify_event_signature as usize,
        // sep-anarchy-ffi (6 symbols)
        onym_sep_anarchy_ffi::onym_anarchy_bake_membership_vk            as usize,
        onym_sep_anarchy_ffi::onym_anarchy_bake_update_vk                as usize,
        onym_sep_anarchy_ffi::onym_anarchy_pinned_membership_vk_sha256_hex as usize,
        onym_sep_anarchy_ffi::onym_anarchy_pinned_update_vk_sha256_hex     as usize,
        onym_sep_anarchy_ffi::onym_anarchy_prove_membership              as usize,
        onym_sep_anarchy_ffi::onym_anarchy_prove_update                  as usize,
        // sep-oneonone-ffi (2 symbols)
        onym_sep_oneonone_ffi::onym_oneonone_bake_create_vk as usize,
        onym_sep_oneonone_ffi::onym_oneonone_prove_create   as usize,
        // sep-tyranny-ffi (6 symbols)
        onym_sep_tyranny_ffi::onym_tyranny_bake_create_vk             as usize,
        onym_sep_tyranny_ffi::onym_tyranny_bake_update_vk             as usize,
        onym_sep_tyranny_ffi::onym_tyranny_pinned_create_vk_sha256_hex as usize,
        onym_sep_tyranny_ffi::onym_tyranny_pinned_update_vk_sha256_hex as usize,
        onym_sep_tyranny_ffi::onym_tyranny_prove_create               as usize,
        onym_sep_tyranny_ffi::onym_tyranny_prove_update               as usize,
    ];
    // Volatile read prevents fat LTO from reasoning the array away
    // entirely. Address-taking already keeps each symbol alive, but
    // an unused array could otherwise be elided.
    let mut acc: usize = 0;
    for ptr in &table {
        acc = acc.wrapping_add(unsafe { core::ptr::read_volatile(ptr) });
    }
    let _ = acc;
    table.len()
}

// `extern "C"` declarations matching the link-resolved symbols above.
// Cargo path deps make the symbols available; the keep-alive function
// is what holds them through Rust's dead-code analysis.
mod ffi {
    use std::ffi::c_char;

    #[repr(C)]
    pub struct OnymByteBuffer {
        pub ptr: *mut u8,
        pub len: usize,
    }

    extern "C" {
        // sep-common-ffi
        pub fn onym_byte_buffer_free(buffer: OnymByteBuffer);
        pub fn onym_string_free(ptr: *mut c_char);

        pub fn onym_compute_leaf_hash(
            sk_ptr: *const u8, sk_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_compute_public_key(
            sk_ptr: *const u8, sk_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_compute_merkle_root(
            leaf_hashes_ptr: *const u8, leaf_hashes_len: usize, depth: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_compute_sha256_commitment(
            root_ptr: *const u8, root_len: usize, epoch: u64,
            salt_ptr: *const u8, salt_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_compute_poseidon_commitment(
            root_ptr: *const u8, root_len: usize, epoch: u64,
            salt_ptr: *const u8, salt_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_parse_plonk_proof(
            proof_ptr: *const u8, proof_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_nostr_derive_public_key(
            sk_ptr: *const u8, sk_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_nostr_sign_event_id(
            sk_ptr: *const u8, sk_len: usize,
            event_id_ptr: *const u8, event_id_len: usize,
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_nostr_verify_event_signature(
            pk_ptr: *const u8, pk_len: usize,
            event_id_ptr: *const u8, event_id_len: usize,
            sig_ptr: *const u8, sig_len: usize,
            err: *mut *mut c_char,
        ) -> bool;

        // sep-anarchy-ffi
        pub fn onym_anarchy_bake_membership_vk(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_anarchy_bake_update_vk(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_anarchy_pinned_membership_vk_sha256_hex(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_anarchy_pinned_update_vk_sha256_hex(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_anarchy_prove_membership(
            depth: usize,
            leaves_ptr: *const u8, leaves_len: usize,
            sk_ptr: *const u8, sk_len: usize,
            prover_index: usize, epoch: u64,
            salt_ptr: *const u8, salt_len: usize,
            out_proof: *mut OnymByteBuffer,
            out_commitment: *mut OnymByteBuffer,
            err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_anarchy_prove_update(
            depth: usize,
            leaves_old_ptr: *const u8, leaves_old_len: usize,
            leaves_new_ptr: *const u8, leaves_new_len: usize,
            sk_ptr: *const u8, sk_len: usize,
            prover_index_old: usize, epoch_old: u64,
            salt_old_ptr: *const u8, salt_old_len: usize,
            salt_new_ptr: *const u8, salt_new_len: usize,
            out_proof: *mut OnymByteBuffer,
            out_pi: *mut OnymByteBuffer,
            err: *mut *mut c_char,
        ) -> bool;

        // sep-oneonone-ffi
        pub fn onym_oneonone_bake_create_vk(
            out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_oneonone_prove_create(
            sk_0_ptr: *const u8, sk_0_len: usize,
            sk_1_ptr: *const u8, sk_1_len: usize,
            salt_ptr: *const u8, salt_len: usize,
            out_proof: *mut OnymByteBuffer,
            out_commitment: *mut OnymByteBuffer,
            err: *mut *mut c_char,
        ) -> bool;

        // sep-tyranny-ffi
        pub fn onym_tyranny_bake_create_vk(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_tyranny_bake_update_vk(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_tyranny_pinned_create_vk_sha256_hex(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_tyranny_pinned_update_vk_sha256_hex(
            depth: usize, out: *mut OnymByteBuffer, err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_tyranny_prove_create(
            depth: usize,
            leaves_ptr: *const u8, leaves_len: usize,
            admin_sk_ptr: *const u8, admin_sk_len: usize,
            admin_index: usize,
            group_id_fr_ptr: *const u8, group_id_fr_len: usize,
            salt_ptr: *const u8, salt_len: usize,
            out_proof: *mut OnymByteBuffer,
            out_pi: *mut OnymByteBuffer,
            err: *mut *mut c_char,
        ) -> bool;
        pub fn onym_tyranny_prove_update(
            depth: usize,
            leaves_old_ptr: *const u8, leaves_old_len: usize,
            admin_sk_ptr: *const u8, admin_sk_len: usize,
            admin_index_old: usize, epoch_old: u64,
            member_root_new_ptr: *const u8, member_root_new_len: usize,
            group_id_fr_ptr: *const u8, group_id_fr_len: usize,
            salt_old_ptr: *const u8, salt_old_len: usize,
            salt_new_ptr: *const u8, salt_new_len: usize,
            out_proof: *mut OnymByteBuffer,
            out_pi: *mut OnymByteBuffer,
            err: *mut *mut c_char,
        ) -> bool;
    }
}

// ---------------------------------------------------------------------------
// JNI helpers
// ---------------------------------------------------------------------------

const ONYM_EXCEPTION: &str = "chat/onym/sdk/OnymException";

/// Throw `OnymException` with the message `out_error` carries (which
/// the FFI allocated via CString::into_raw — we read + free it). Used
/// only when the FFI returned `false`.
unsafe fn throw_ffi_error(env: &mut JNIEnv, err: *mut c_char) {
    let msg = if err.is_null() {
        "FFI returned false but populated no error message".to_string()
    } else {
        let m = CStr::from_ptr(err).to_string_lossy().into_owned();
        ffi::onym_string_free(err);
        m
    };
    let _ = env.throw_new(ONYM_EXCEPTION, &msg);
}

/// Convert a successfully-populated FFI byte buffer into a Kotlin
/// `byte[]`. Frees the FFI buffer regardless of outcome.
unsafe fn buffer_to_jbytearray(env: &mut JNIEnv, mut buf: ffi::OnymByteBuffer) -> jbyteArray {
    let bytes = if buf.ptr.is_null() || buf.len == 0 {
        ffi::onym_byte_buffer_free(buf);
        return env
            .byte_array_from_slice(&[])
            .map(|a| a.into_raw())
            .unwrap_or_else(|_| ptr::null_mut());
    } else {
        std::slice::from_raw_parts(buf.ptr, buf.len).to_vec()
    };
    ffi::onym_byte_buffer_free(buf);
    env.byte_array_from_slice(&bytes)
        .map(|a| a.into_raw())
        .unwrap_or_else(|_| ptr::null_mut())
}

/// Pull a Kotlin `byte[]` into a Rust `Vec<u8>`. Returns empty vec on
/// JNI conversion failure (which usually means a pending exception is
/// already set).
unsafe fn jbytearray_to_vec(env: &mut JNIEnv, arr: &JByteArray) -> Vec<u8> {
    env.convert_byte_array(arr).unwrap_or_default()
}

/// Macro: a JNI export that takes one `byte[]` input, calls a 4-arg
/// FFI function (`fn(ptr, len, out, err) -> bool`), and returns the
/// FFI's output buffer as `byte[]`.
macro_rules! jni_one_in_one_out {
    ($fn_name:ident, $ffi_fn:ident) => {
        #[no_mangle]
        pub extern "system" fn $fn_name<'local>(
            mut env: JNIEnv<'local>,
            _class: JClass<'local>,
            input: JByteArray<'local>,
        ) -> jbyteArray {
            unsafe {
                let bytes = jbytearray_to_vec(&mut env, &input);
                let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
                let mut err: *mut c_char = ptr::null_mut();
                let ok = ffi::$ffi_fn(bytes.as_ptr(), bytes.len(), &mut out, &mut err);
                if !ok {
                    throw_ffi_error(&mut env, err);
                    return ptr::null_mut();
                }
                buffer_to_jbytearray(&mut env, out)
            }
        }
    };
}

// ---------------------------------------------------------------------------
// Common (sep-common-ffi)
// ---------------------------------------------------------------------------

jni_one_in_one_out!(
    Java_chat_onym_sdk_internal_OnymJni_computeLeafHash,
    onym_compute_leaf_hash
);
jni_one_in_one_out!(
    Java_chat_onym_sdk_internal_OnymJni_computePublicKey,
    onym_compute_public_key
);
jni_one_in_one_out!(
    Java_chat_onym_sdk_internal_OnymJni_parsePlonkProof,
    onym_parse_plonk_proof
);
jni_one_in_one_out!(
    Java_chat_onym_sdk_internal_OnymJni_nostrDerivePublicKey,
    onym_nostr_derive_public_key
);

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_computeMerkleRoot<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    leaf_hashes: JByteArray<'local>,
    depth: jint,
) -> jbyteArray {
    unsafe {
        let leaves = jbytearray_to_vec(&mut env, &leaf_hashes);
        let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_compute_merkle_root(
            leaves.as_ptr(), leaves.len(), depth as usize, &mut out, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        buffer_to_jbytearray(&mut env, out)
    }
}

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_computeSha256Commitment<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: JByteArray<'local>,
    epoch: jlong,
    salt: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let root_v = jbytearray_to_vec(&mut env, &root);
        let salt_v = jbytearray_to_vec(&mut env, &salt);
        let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_compute_sha256_commitment(
            root_v.as_ptr(), root_v.len(), epoch as u64,
            salt_v.as_ptr(), salt_v.len(), &mut out, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        buffer_to_jbytearray(&mut env, out)
    }
}

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_computePoseidonCommitment<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    root: JByteArray<'local>,
    epoch: jlong,
    salt: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let root_v = jbytearray_to_vec(&mut env, &root);
        let salt_v = jbytearray_to_vec(&mut env, &salt);
        let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_compute_poseidon_commitment(
            root_v.as_ptr(), root_v.len(), epoch as u64,
            salt_v.as_ptr(), salt_v.len(), &mut out, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        buffer_to_jbytearray(&mut env, out)
    }
}

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_nostrSignEventId<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    secret_key: JByteArray<'local>,
    event_id: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let sk = jbytearray_to_vec(&mut env, &secret_key);
        let id = jbytearray_to_vec(&mut env, &event_id);
        let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_nostr_sign_event_id(
            sk.as_ptr(), sk.len(), id.as_ptr(), id.len(), &mut out, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        buffer_to_jbytearray(&mut env, out)
    }
}

/// Returns `true` if the signature verifies, `false` if it doesn't.
/// Throws `OnymException` on malformed inputs (length errors, etc.) —
/// mirrors the upstream FFI which uses error messages for both
/// "invalid sig" and "bad input length". Distinguishing them in
/// Kotlin is a follow-up if needed.
#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_nostrVerifyEventSignature<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    public_key: JByteArray<'local>,
    event_id: JByteArray<'local>,
    signature: JByteArray<'local>,
) -> jboolean {
    unsafe {
        let pk = jbytearray_to_vec(&mut env, &public_key);
        let id = jbytearray_to_vec(&mut env, &event_id);
        let sig = jbytearray_to_vec(&mut env, &signature);
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_nostr_verify_event_signature(
            pk.as_ptr(), pk.len(),
            id.as_ptr(), id.len(),
            sig.as_ptr(), sig.len(),
            &mut err,
        );
        if !ok {
            // Free the error msg but don't throw — the Kotlin side
            // surfaces verification failure as `false`.
            if !err.is_null() {
                ffi::onym_string_free(err);
            }
            return 0;
        }
        1
    }
}

// ---------------------------------------------------------------------------
// Anarchy (sep-anarchy-ffi)
// ---------------------------------------------------------------------------

macro_rules! jni_bake_or_pinned {
    ($fn_name:ident, $ffi_fn:ident) => {
        #[no_mangle]
        pub extern "system" fn $fn_name<'local>(
            mut env: JNIEnv<'local>,
            _class: JClass<'local>,
            depth: jint,
        ) -> jbyteArray {
            unsafe {
                let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
                let mut err: *mut c_char = ptr::null_mut();
                let ok = ffi::$ffi_fn(depth as usize, &mut out, &mut err);
                if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
                buffer_to_jbytearray(&mut env, out)
            }
        }
    };
}

jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_anarchyBakeMembershipVk,
    onym_anarchy_bake_membership_vk
);
jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_anarchyBakeUpdateVk,
    onym_anarchy_bake_update_vk
);
jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_anarchyPinnedMembershipVkSha256Hex,
    onym_anarchy_pinned_membership_vk_sha256_hex
);
jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_anarchyPinnedUpdateVkSha256Hex,
    onym_anarchy_pinned_update_vk_sha256_hex
);

/// Returns a 2-element `byte[][]`: [0] = proof (1601 B), [1] = commitment (32 B).
/// JNI doesn't have a clean "tuple" type, so a 2-deep ByteArray array
/// is the simplest cross-FFI shape. Kotlin wrapper unpacks into a
/// data class.
#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_anarchyProveMembership<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    depth: jint,
    leaves: JByteArray<'local>,
    prover_sk: JByteArray<'local>,
    prover_index: jint,
    epoch: jlong,
    salt: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let leaves_v = jbytearray_to_vec(&mut env, &leaves);
        let sk_v = jbytearray_to_vec(&mut env, &prover_sk);
        let salt_v = jbytearray_to_vec(&mut env, &salt);
        let mut proof = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut commit = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_anarchy_prove_membership(
            depth as usize,
            leaves_v.as_ptr(), leaves_v.len(),
            sk_v.as_ptr(), sk_v.len(),
            prover_index as usize, epoch as u64,
            salt_v.as_ptr(), salt_v.len(),
            &mut proof, &mut commit, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        two_buffers_to_concat(&mut env, proof, commit)
    }
}

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_anarchyProveUpdate<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    depth: jint,
    leaves_old: JByteArray<'local>,
    leaves_new: JByteArray<'local>,
    prover_sk: JByteArray<'local>,
    prover_index_old: jint,
    epoch_old: jlong,
    salt_old: JByteArray<'local>,
    salt_new: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let old_v = jbytearray_to_vec(&mut env, &leaves_old);
        // Map Java null reference → (NULL, 0) — the FFI's "reuse old
        // roster" sentinel.
        // Map non-null Java array → (real ptr, real len), even when
        // len == 0. This lets the FFI's strict-mixed-state guard
        // reject `byte[0]` ("valid_ptr + zero_len") with a clear
        // error rather than silently treating it as "reuse old".
        // Vec::as_ptr() is non-null even for empty Vecs (Rust's
        // dangling-but-aligned guarantee), so we never accidentally
        // hand the FFI a NULL ptr for a non-null Java array.
        let new_v_opt: Option<Vec<u8>> = if leaves_new.is_null() {
            None
        } else {
            Some(jbytearray_to_vec(&mut env, &leaves_new))
        };
        let (new_ptr, new_len): (*const u8, usize) = match &new_v_opt {
            None => (ptr::null(), 0),
            Some(v) => (v.as_ptr(), v.len()),
        };
        let sk_v = jbytearray_to_vec(&mut env, &prover_sk);
        let salt_old_v = jbytearray_to_vec(&mut env, &salt_old);
        let salt_new_v = jbytearray_to_vec(&mut env, &salt_new);
        let mut proof = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut pi = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_anarchy_prove_update(
            depth as usize,
            old_v.as_ptr(), old_v.len(),
            new_ptr, new_len,
            sk_v.as_ptr(), sk_v.len(),
            prover_index_old as usize, epoch_old as u64,
            salt_old_v.as_ptr(), salt_old_v.len(),
            salt_new_v.as_ptr(), salt_new_v.len(),
            &mut proof, &mut pi, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        two_buffers_to_concat(&mut env, proof, pi)
    }
}

// ---------------------------------------------------------------------------
// OneOnOne (sep-oneonone-ffi)
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_oneOnOneBakeCreateVk<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jbyteArray {
    unsafe {
        let mut out = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_oneonone_bake_create_vk(&mut out, &mut err);
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        buffer_to_jbytearray(&mut env, out)
    }
}

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_oneOnOneProveCreate<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    sk_0: JByteArray<'local>,
    sk_1: JByteArray<'local>,
    salt: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let sk_0_v = jbytearray_to_vec(&mut env, &sk_0);
        let sk_1_v = jbytearray_to_vec(&mut env, &sk_1);
        let salt_v = jbytearray_to_vec(&mut env, &salt);
        let mut proof = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut commit = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_oneonone_prove_create(
            sk_0_v.as_ptr(), sk_0_v.len(),
            sk_1_v.as_ptr(), sk_1_v.len(),
            salt_v.as_ptr(), salt_v.len(),
            &mut proof, &mut commit, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        two_buffers_to_concat(&mut env, proof, commit)
    }
}

// ---------------------------------------------------------------------------
// Tyranny (sep-tyranny-ffi)
// ---------------------------------------------------------------------------

jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_tyrannyBakeCreateVk,
    onym_tyranny_bake_create_vk
);
jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_tyrannyBakeUpdateVk,
    onym_tyranny_bake_update_vk
);
jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_tyrannyPinnedCreateVkSha256Hex,
    onym_tyranny_pinned_create_vk_sha256_hex
);
jni_bake_or_pinned!(
    Java_chat_onym_sdk_internal_OnymJni_tyrannyPinnedUpdateVkSha256Hex,
    onym_tyranny_pinned_update_vk_sha256_hex
);

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_tyrannyProveCreate<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    depth: jint,
    leaves: JByteArray<'local>,
    admin_sk: JByteArray<'local>,
    admin_index: jint,
    group_id_fr: JByteArray<'local>,
    salt: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let leaves_v = jbytearray_to_vec(&mut env, &leaves);
        let admin_v = jbytearray_to_vec(&mut env, &admin_sk);
        let gid_v = jbytearray_to_vec(&mut env, &group_id_fr);
        let salt_v = jbytearray_to_vec(&mut env, &salt);
        let mut proof = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut pi = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_tyranny_prove_create(
            depth as usize,
            leaves_v.as_ptr(), leaves_v.len(),
            admin_v.as_ptr(), admin_v.len(),
            admin_index as usize,
            gid_v.as_ptr(), gid_v.len(),
            salt_v.as_ptr(), salt_v.len(),
            &mut proof, &mut pi, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        two_buffers_to_concat(&mut env, proof, pi)
    }
}

#[no_mangle]
pub extern "system" fn Java_chat_onym_sdk_internal_OnymJni_tyrannyProveUpdate<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    depth: jint,
    leaves_old: JByteArray<'local>,
    admin_sk: JByteArray<'local>,
    admin_index_old: jint,
    epoch_old: jlong,
    member_root_new: JByteArray<'local>,
    group_id_fr: JByteArray<'local>,
    salt_old: JByteArray<'local>,
    salt_new: JByteArray<'local>,
) -> jbyteArray {
    unsafe {
        let old_v = jbytearray_to_vec(&mut env, &leaves_old);
        let admin_v = jbytearray_to_vec(&mut env, &admin_sk);
        let mrn_v = jbytearray_to_vec(&mut env, &member_root_new);
        let gid_v = jbytearray_to_vec(&mut env, &group_id_fr);
        let salt_old_v = jbytearray_to_vec(&mut env, &salt_old);
        let salt_new_v = jbytearray_to_vec(&mut env, &salt_new);
        let mut proof = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut pi = ffi::OnymByteBuffer { ptr: ptr::null_mut(), len: 0 };
        let mut err: *mut c_char = ptr::null_mut();
        let ok = ffi::onym_tyranny_prove_update(
            depth as usize,
            old_v.as_ptr(), old_v.len(),
            admin_v.as_ptr(), admin_v.len(),
            admin_index_old as usize, epoch_old as u64,
            mrn_v.as_ptr(), mrn_v.len(),
            gid_v.as_ptr(), gid_v.len(),
            salt_old_v.as_ptr(), salt_old_v.len(),
            salt_new_v.as_ptr(), salt_new_v.len(),
            &mut proof, &mut pi, &mut err,
        );
        if !ok { throw_ffi_error(&mut env, err); return ptr::null_mut(); }
        two_buffers_to_concat(&mut env, proof, pi)
    }
}

// ---------------------------------------------------------------------------
// Two-buffer helper: concat as `len_a:u32_be ++ a ++ b` so Kotlin can
// split. JNI byte[][] is awkward to construct from Rust; a length-
// prefixed concat is simpler and the prefix is fixed-size.
// ---------------------------------------------------------------------------

unsafe fn two_buffers_to_concat(
    env: &mut JNIEnv,
    a: ffi::OnymByteBuffer,
    b: ffi::OnymByteBuffer,
) -> jbyteArray {
    let a_slice = if a.ptr.is_null() || a.len == 0 {
        &[][..]
    } else {
        std::slice::from_raw_parts(a.ptr, a.len)
    };
    let b_slice = if b.ptr.is_null() || b.len == 0 {
        &[][..]
    } else {
        std::slice::from_raw_parts(b.ptr, b.len)
    };

    let mut out = Vec::with_capacity(4 + a_slice.len() + b_slice.len());
    out.extend_from_slice(&(a_slice.len() as u32).to_be_bytes());
    out.extend_from_slice(a_slice);
    out.extend_from_slice(b_slice);

    ffi::onym_byte_buffer_free(a);
    ffi::onym_byte_buffer_free(b);

    env.byte_array_from_slice(&out)
        .map(|arr| arr.into_raw())
        .unwrap_or_else(|_| ptr::null_mut())
}

// Suppress unused-import warning if jstring / JString aren't used by
// the active set of exports.
#[allow(dead_code)]
fn _silence_unused_jstring() -> Option<jstring> {
    let _: Option<JString> = None;
    None
}
