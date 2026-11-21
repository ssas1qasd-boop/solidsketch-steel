package com.solidsketchapp.steel.license

import com.solidsketchapp.core.api.LicenseApi

/**
 * License gate: POSTPONED (owner decision, 2026-11-21). Always allows.
 * The Lemon Squeezy gate (rules in docs/steel/PROGRESS.md) plugs in here later.
 */
interface LicenseGate : LicenseApi {
    override val isUnlocked: Boolean get() = true
    override val plan: String? get() = null
}

/** The only gate for now: everything allowed. */
object OpenLicenseGate : LicenseGate
