package com.solidsketchapp.steel.license

import com.solidsketchapp.core.api.LicenseApi
import java.io.IOException
import kotlin.random.Random

/** What the UI shows. */
sealed interface GateState {
    data object Checking : GateState
    data class Locked(val message: String = "", val isError: Boolean = false) : GateState
    data class Unlocked(val plan: String) : GateState
}

/**
 * Lemon Squeezy license gate: a line-by-line port of the web build's ss-license-gate
 * (boot / activate / recheck), with the main port's rules. Network calls block:
 * call [boot], [activate] and [recheck] off the main thread.
 */
class LicenseManager(
    private val http: LicenseHttp,
    private val store: LicenseStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
    private val variants: Map<Long, String> = LicenseConfig.ALLOWED_VARIANTS,
) : LicenseApi {

    @Volatile
    var state: GateState = GateState.Checking
        private set

    override val isUnlocked: Boolean get() = state is GateState.Unlocked
    override val plan: String? get() = (state as? GateState.Unlocked)?.plan

    fun deviceName(): String {
        val id = store.deviceId() ?: buildString {
            repeat(6) { append(ID_CHARS[random.nextInt(ID_CHARS.length)]) }
        }.also { store.setDeviceId(it) }
        return "Android $id"
    }

    private fun variantOf(r: LsReply): String? = r.variantId?.let { variants[it] }

    /** Start of the app. */
    fun boot(): GateState {
        val rec = store.load() ?: return set(GateState.Locked())
        state = GateState.Checking
        val d = try {
            http.post("validate", mapOf("license_key" to rec.key, "instance_id" to rec.instance))
        } catch (e: IOException) {
            return set(
                if (now() - rec.at < LicenseConfig.GRACE_MS) GateState.Unlocked(rec.plan)
                else GateState.Locked(MSG_OFFLINE_LOCKED, true),
            )
        }
        val v = variantOf(d)
        return if (d.valid && v != null) {
            store.save(rec.copy(at = now(), plan = v))
            set(GateState.Unlocked(v))
        } else {
            store.delete()
            set(GateState.Locked(friendly(d), true))
        }
    }

    /** A typed key from the lock screen. */
    fun activate(rawKey: String): GateState {
        val key = rawKey.trim()
        if (key.isEmpty()) return set(GateState.Locked(MSG_EMPTY_KEY, true))
        val d = try {
            http.post("activate", mapOf("license_key" to key, "instance_name" to deviceName()))
        } catch (e: IOException) {
            return set(GateState.Locked(MSG_OFFLINE_ACTIVATE, true))
        }
        if (!d.activated) return set(GateState.Locked(d.error ?: MSG_NOT_ACCEPTED, true))
        val v = variantOf(d)
        if (v == null || d.instanceId == null) {
            // A valid key, but for some other product: free the slot and refuse.
            d.instanceId?.let { id ->
                try {
                    http.post("deactivate", mapOf("license_key" to key, "instance_id" to id))
                } catch (_: IOException) {
                }
            }
            return set(GateState.Locked(MSG_WRONG_PRODUCT, true))
        }
        store.save(LicenseRecord(key, d.instanceId, now(), v))
        return set(GateState.Unlocked(v))
    }

    /**
     * While the app is open (every 30 min and on return to the foreground).
     * Re-validates when the last check is older than 6 h. Invalid: delete and lock.
     * Offline: keep working until the 7-day grace ends, then lock.
     */
    fun recheck(): GateState {
        if (state !is GateState.Unlocked) return state
        val rec = store.load() ?: return set(GateState.Locked())
        if (now() - rec.at < LicenseConfig.RECHECK_MS) return state
        val d = try {
            http.post("validate", mapOf("license_key" to rec.key, "instance_id" to rec.instance))
        } catch (e: IOException) {
            return if (now() - rec.at < LicenseConfig.GRACE_MS) state
            else set(GateState.Locked(MSG_OFFLINE_LOCKED, true))
        }
        val v = variantOf(d)
        return if (d.valid && v != null) {
            store.save(rec.copy(at = now(), plan = v))
            set(GateState.Unlocked(v))
        } else {
            store.delete()
            set(GateState.Locked(friendly(d), true))
        }
    }

    private fun set(s: GateState): GateState {
        state = s
        return s
    }

    companion object {
        private const val ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789"

        const val MSG_CHECKING = "Checking your license…"
        const val MSG_EMPTY_KEY = "Paste your license key first."
        const val MSG_NOT_ACCEPTED = "That key was not accepted. Check it and try again."
        const val MSG_WRONG_PRODUCT = "This key is not for SolidSketch Pro."
        const val MSG_OFFLINE_ACTIVATE = "Could not reach the license server. Check your connection and try again."
        const val MSG_OFFLINE_LOCKED = "Could not reach the license server. Connect to the internet and try again."
        const val MSG_EXPIRED = "Your subscription has ended. Renew it to keep using SolidSketch."
        const val MSG_DISABLED = "This license key has been disabled. Contact support if you think this is a mistake."
        const val MSG_NOT_ACTIVE = "This license is no longer active."

        fun friendly(d: LsReply): String = when (d.status) {
            "expired" -> MSG_EXPIRED
            "disabled" -> MSG_DISABLED
            else -> d.error ?: MSG_NOT_ACTIVE
        }
    }
}
