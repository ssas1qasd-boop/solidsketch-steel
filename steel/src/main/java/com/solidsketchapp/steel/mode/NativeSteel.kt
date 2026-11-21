package com.solidsketchapp.steel.mode

/** JNI entry to native/sssteel. */
object NativeSteel {
    init {
        System.loadLibrary("sssteel_jni")
    }

    external fun version(): String
    external fun coreVersion(): String
}
