package nl.iptvplayer.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant

/** Status van dit apparaat bij de Gallo-server (zie `web/` in de repository). */
enum class LicenseStatus(val code: String) {
    TRIAL("trial"), ACTIVE("active"), FREE("free"), EXPIRED("expired"), BLOCKED("blocked");

    companion object {
        fun fromCode(code: String?): LicenseStatus = entries.firstOrNull { it.code == code } ?: EXPIRED
    }
}

/**
 * Wat de server de laatste keer over dit apparaat zei.
 * [validUntil]: einde van de proef of het abonnement (ms), null = geen einddatum.
 * [checkedAt]: wanneer de server dit zei (ms, klok van dit apparaat).
 */
data class License(
    val code: String,
    val status: LicenseStatus,
    /** "lifetime" of "yearly" als er ooit betaald is; null bij alleen een proef of gratis. */
    val plan: String?,
    val validUntil: Long?,
    val activateUrl: String,
    val checkedAt: Long,
) {
    /** Mag de app gebruikt worden? Een verlopen proef of abonnement telt meteen, ook zonder nieuwe controle. */
    fun allowsUse(now: Long): Boolean = when (status) {
        LicenseStatus.ACTIVE, LicenseStatus.FREE, LicenseStatus.TRIAL -> validUntil == null || validUntil > now
        LicenseStatus.EXPIRED, LicenseStatus.BLOCKED -> false
    }

    /** Hoe lang de laatste bekende status geldt als de server niet te bereiken is. */
    fun isFresh(now: Long): Boolean = now - checkedAt in 0..OFFLINE_GRACE_MS

    /** Hele dagen tot [validUntil], naar boven afgerond. */
    fun daysLeft(now: Long): Int? = validUntil?.let { ((it - now + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(0) }

    fun toJson(): String = JSONObject()
        .put("code", code).put("status", status.code).put("plan", plan ?: JSONObject.NULL).put("validUntil", validUntil ?: JSONObject.NULL)
        .put("activateUrl", activateUrl).put("checkedAt", checkedAt)
        .toString()

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val OFFLINE_GRACE_MS = 3 * DAY_MS

        fun fromJson(json: String?): License? = json?.let {
            runCatching {
                val o = JSONObject(it)
                License(
                    code = o.getString("code"),
                    status = LicenseStatus.fromCode(o.getString("status")),
                    plan = if (o.isNull("plan")) null else o.optString("plan"),
                    validUntil = if (o.isNull("validUntil")) null else o.getLong("validUntil"),
                    activateUrl = o.getString("activateUrl"),
                    checkedAt = o.getLong("checkedAt"),
                )
            }.getOrNull()
        }

        /** Antwoord van `POST /api/device`. */
        fun fromServer(body: String, now: Long): License {
            val o = JSONObject(body)
            val until = o.optString("validUntil").takeIf { it.isNotEmpty() && it != "null" }
            return License(
                code = o.getString("code"),
                status = LicenseStatus.fromCode(o.getString("status")),
                plan = if (o.isNull("plan")) null else o.optString("plan").takeIf { it.isNotEmpty() },
                validUntil = until?.let { Instant.parse(it).toEpochMilli() },
                activateUrl = o.getString("activateUrl"),
                checkedAt = now,
            )
        }
    }
}

/** Meldt dit apparaat bij de Gallo-server en vraagt de status op. */
class LicenseClient(private val http: OkHttpClient, private val baseUrl: String = SERVER) {

    suspend fun check(deviceId: String, appVersion: String, model: String): License = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("deviceId", deviceId).put("appVersion", appVersion).put("model", model)
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$baseUrl/api/device").post(body).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            License.fromServer(response.body?.string().orEmpty(), System.currentTimeMillis())
        }
    }

    companion object {
        const val SERVER = "https://gallo-heermoes-13.vercel.app"
    }
}
