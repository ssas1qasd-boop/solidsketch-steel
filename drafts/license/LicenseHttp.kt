package com.solidsketchapp.steel.license

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** The fields the gate reads from a Lemon Squeezy License API reply. */
data class LsReply(
    val activated: Boolean = false,
    val valid: Boolean = false,
    val status: String? = null,      // license_key.status
    val error: String? = null,
    val variantId: Long? = null,     // meta.variant_id
    val instanceId: String? = null,  // instance.id
)

/** HTTP layer, faked in unit tests. */
fun interface LicenseHttp {
    /**
     * POSTs [params] form-encoded to API_BASE + [path]. Returns the parsed reply, also for 4xx.
     * @throws IOException on network or parse failure (like the web gate's rejected promise).
     */
    @Throws(IOException::class)
    fun post(path: String, params: Map<String, String>): LsReply
}

/** Real implementation: HttpURLConnection, no API key, 12 s timeout. */
class UrlConnectionLicenseHttp(private val base: String = LicenseConfig.API_BASE) : LicenseHttp {
    override fun post(path: String, params: Map<String, String>): LsReply {
        val conn = URL(base + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = LicenseConfig.TIMEOUT_MS
            conn.readTimeout = LicenseConfig.TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            val body = params.entries.joinToString("&") { (k, v) ->
                URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code < 400) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: throw IOException("Empty reply ($code)")
            return try {
                parse(JSONObject(text))
            } catch (e: org.json.JSONException) {
                throw IOException("Bad reply ($code)", e)
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(j: JSONObject): LsReply {
        val meta = j.optJSONObject("meta")
        return LsReply(
            activated = j.optBoolean("activated"),
            valid = j.optBoolean("valid"),
            status = j.optJSONObject("license_key")?.optString("status")?.ifEmpty { null },
            error = if (j.isNull("error")) null else j.optString("error").ifEmpty { null },
            variantId = if (meta == null || meta.isNull("variant_id")) null else meta.optLong("variant_id"),
            instanceId = j.optJSONObject("instance")?.optString("id")?.ifEmpty { null },
        )
    }
}
