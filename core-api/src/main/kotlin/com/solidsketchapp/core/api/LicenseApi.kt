package com.solidsketchapp.core.api

/** Whether the app is unlocked. Backed by LicenseManager (Lemon Squeezy). */
interface LicenseApi {
    val isUnlocked: Boolean
    /** Plan name for display (e.g. the variant name), or null when locked. */
    val plan: String?
}
