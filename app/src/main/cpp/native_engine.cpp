
#include <jni.h>
#include <string>
#include <cstdint>
#include <limits>

extern "C" {
    int32_t rust_classify_file(
        const char* filename,
        uint64_t file_size
    );
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sadaregon_cleaner_MainActivity_nativeGetEngineStatus(
    JNIEnv* env,
    jobject /* thiz */
) {
    const std::string status =
        "SADA Native Engine ready (C++17)";
    return env->NewStringUTF(status.c_str());
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_sadaregon_cleaner_MainActivity_nativeClassifyFile(
    JNIEnv* env,
    jobject /* thiz */,
    jstring filename,
    jlong fileSize
) {
    if (filename == nullptr || fileSize < 0) {
        return -1;
    }

    const char* name = env->GetStringUTFChars(filename, nullptr);
    if (name == nullptr) {
        return -1;
    }

    const int32_t result = rust_classify_file(
        name,
        static_cast<uint64_t>(fileSize)
    );

    env->ReleaseStringUTFChars(filename, name);
    return static_cast<jint>(result);
}
