package com.solidsketchapp.steel.license

/**
 * License gate constants. Same values as the web build's `<script id="ss-license-gate">`
 * (reference/solidsketch_v69.html) and the main port's rules.
 */
object LicenseConfig {
    const val API_BASE = "https://api.lemonsqueezy.com/v1/licenses/"
    const val TIMEOUT_MS = 12_000

    /**
     * The ONE list of Lemon Squeezy variants that unlock the app: meta.variant_id -> plan.
     * TODO(owner): which variants unlock Steel is still open (see docs/steel/PROGRESS.md).
     * Default: the SolidSketch Pro variants.
     */
    val ALLOWED_VARIANTS: Map<Long, String> = mapOf(
        2224132L to "Yearly",
        2224187L to "Monthly",
    )

    const val GRACE_MS = 7L * 24 * 3600 * 1000   // offline grace after the last successful check
    const val RECHECK_MS = 6L * 3600 * 1000      // re-validate while the app is open
    const val POLL_MS = 30L * 60 * 1000          // how often to look whether a re-check is due

    const val BUY_MONTHLY = "https://solidsketch.lemonsqueezy.com/checkout/buy/900d7ea4-d7a7-4e41-b821-799d6c94393f?enabled=2224187"
    const val BUY_YEARLY = "https://solidsketch.lemonsqueezy.com/checkout/buy/0c0cde4e-72e9-40f8-854a-c4d752364db7?enabled=2224132"
    const val SITE = "https://solidsketchapp.com/#pricing"
}
