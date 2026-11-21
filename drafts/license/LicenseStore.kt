package com.solidsketchapp.steel.license

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/** Stored record: key, instance id, time of the last successful check, plan. */
data class LicenseRecord(val key: String, val instance: String, val at: Long, val plan: String)

interface LicenseStore {
    fun load(): LicenseRecord?
    fun save(r: LicenseRecord)
    fun delete()
    /** Persistent 6-char device id ("Android " + id is the instance name). */
    fun deviceId(): String?
    fun setDeviceId(id: String)
}

/** Encrypted on disk (EncryptedSharedPreferences, AES-256, key in Android Keystore). */
class EncryptedLicenseStore(context: Context) : LicenseStore {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "ss_license_v1",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): LicenseRecord? {
        val key = prefs.getString("key", null) ?: return null
        val instance = prefs.getString("instance", null) ?: return null
        return LicenseRecord(key, instance, prefs.getLong("at", 0L), prefs.getString("plan", null) ?: "")
    }

    override fun save(r: LicenseRecord) {
        prefs.edit().putString("key", r.key).putString("instance", r.instance)
            .putLong("at", r.at).putString("plan", r.plan).apply()
    }

    override fun delete() {
        prefs.edit().remove("key").remove("instance").remove("at").remove("plan").apply()
    }

    override fun deviceId(): String? = prefs.getString("device", null)
    override fun setDeviceId(id: String) {
        prefs.edit().putString("device", id).apply()
    }
}
