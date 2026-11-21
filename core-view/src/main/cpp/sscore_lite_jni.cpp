#include <jni.h>

#include "sscore_lite.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_solidsketchapp_core_view_NativeCore_version(JNIEnv* env, jobject) {
    return env->NewStringUTF(sscore_lite_version());
}

/* Volume of an axis-aligned box built by the core: a smoke test of the mesh path. */
extern "C" JNIEXPORT jdouble JNICALL
Java_com_solidsketchapp_core_view_NativeCore_boxVolume(JNIEnv*, jobject, jfloat sx, jfloat sy, jfloat sz) {
    ss_mesh m{};
    if (ss_box(sx, sy, sz, &m) != SS_OK) return -1.0;
    double v = ss_volume(&m);
    ss_mesh_free(&m);
    return v;
}
