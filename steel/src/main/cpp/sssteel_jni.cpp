#include <jni.h>

#include "sssteel.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_solidsketchapp_steel_mode_NativeSteel_version(JNIEnv* env, jobject) {
    return env->NewStringUTF(sssteel_version());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_solidsketchapp_steel_mode_NativeSteel_coreVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(sssteel_core_version());
}
