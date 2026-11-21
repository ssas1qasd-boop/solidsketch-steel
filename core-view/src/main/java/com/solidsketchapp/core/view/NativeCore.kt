package com.solidsketchapp.core.view

/** JNI entry to native/sscore-lite. Booleans arrive in S2. */
object NativeCore {
    init {
        System.loadLibrary("sscore_lite_jni")
    }

    external fun version(): String
    external fun boxVolume(sx: Float, sy: Float, sz: Float): Double
}
