
use std::ffi::CStr;
use std::os::raw::c_char;
use std::panic;

const ORDINARY_FILE: i32 = 0;
const REVIEW_CANDIDATE: i32 = 1;
const INVALID_INPUT: i32 = -1;

// Return values:
//  0 = ordinary file
//  1 = candidate for user review
// -1 = invalid input
//
// This function only classifies a file name and size.
// It does not access, modify, or delete files.
#[no_mangle]
pub unsafe extern "C" fn rust_classify_file(
    filename: *const c_char,
    file_size: u64,
) -> i32 {
    if filename.is_null() {
        return INVALID_INPUT;
    }

    match panic::catch_unwind(|| {
        let name = CStr::from_ptr(filename)
            .to_string_lossy()
            .to_ascii_lowercase();

        if name.is_empty() {
            return INVALID_INPUT;
        }

        // These extensions are only suggestions for review.
        // Their presence does not prove a file is junk.
        let review_extensions = [
            ".tmp",
            ".temp",
            ".log",
            ".bak",
            ".old",
            ".cache",
        ];

        let extension_candidate = review_extensions
            .iter()
            .any(|extension| name.ends_with(extension));

        // Large files are candidates for review, not automatic deletion.
        const LARGE_FILE_BYTES: u64 = 100 * 1024 * 1024;

        if extension_candidate || file_size >= LARGE_FILE_BYTES {
            REVIEW_CANDIDATE
        } else {
            ORDINARY_FILE
        }
    }) {
        Ok(result) => result,
        Err(_) => INVALID_INPUT,
    }
}
