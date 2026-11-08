package nl.iptvplayer.app.data

import nl.iptvplayer.app.i18n.S
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.iptvplayer.app.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String?)

/**
 * Updates via GitHub: de nieuwste release in Thom96/iptvplayer-releases bevat version.json en de APK.
 * Publishing is done by the workflow .github/workflows/publish.yml (see README).
 */
class Updater(private val context: Context, private val http: OkHttpClient) {

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Geeft de nieuwere versie terug, of null als je al de nieuwste hebt. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = http.newCall(Request.Builder().url(VERSION_URL).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(S.updateServerError(resp.code))
            JSONObject(resp.body?.string().orEmpty().trimStart('﻿'))
        }
        val info = UpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.optString("versionName"),
            apkUrl = json.getString("apkUrl"),
            notes = json.optString("notes").takeIf { it.isNotBlank() },
        )
        info.takeIf { it.versionCode > currentVersionCode }
    }

    /** Downloadt de APK naar de cache; [onProgress] krijgt 0..1. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "IPTVPlayer-${info.versionCode}.apk")
        http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException(S.downloadFailed(resp.code))
            val body = resp.body ?: throw IOException(S.emptyDownload)
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total != null) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        target
    }

    /** Android vraagt eenmalig toestemming om apps te installeren vanuit deze app. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Opent het installatiescherm van Android voor de gedownloade APK. */
    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private companion object {
        const val VERSION_URL = "https://github.com/Thom96/iptvplayer-releases/releases/latest/download/version.json"
    }
}
