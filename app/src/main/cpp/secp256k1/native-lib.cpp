#include <jni.h>
#include <string>

extern "C"
JNIEXPORT jstring JNICALL
Java_bitcoin_acinq_fr_secp256k1test_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++ !!";
    return env->NewStringUTF(hello.c_str());
}
