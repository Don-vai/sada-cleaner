
#include <cstdint>

// Implemented in the Rust library.
// 0 = ordinary file
// 1 = candidate for review
// -1 = invalid input
extern "C" int32_t rust_classify_file(
    const char* filename,
    uint64_t file_size
);
