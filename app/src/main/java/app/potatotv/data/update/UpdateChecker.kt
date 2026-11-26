package app.potatotv.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.potatotv.data.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class UpdateInfo(
    val version: String,
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val publishedAt: String
)

@Serializable
private data class Release(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val published_at: String = "",
    val html_url: String = "",
    val assets: List<Asset> = emptyList()
)

@Serializable
private data class Asset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0
)

/** In-app updates from GitHub Releases. */
class UpdateChecker(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun latest(repo: String): UpdateInfo? {
        val clean = repo.trim().removePrefix("https://github.com/").trim('/')
        if (clean.count { it == '/' } != 1) error("Repo must look like owner/name.")
        val body = Http.getText(
            "https://api.github.com/repos/$clean/releases/latest",
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "X-GitHub-Api-Version" to "2022-11-28"
            ),
            userAgent = "PotatoTV/1.0"
        )
        val release = json.decodeFromString(Release.serializer(), body)
        val wanted = if (app.potatotv.BuildConfig.FLAVOR == "mobile") "mobile" else "tv"
        val apk = release.assets.firstOrNull {
            it.name.endsWith(".apk", true) && it.name.contains(wanted, true)
        } ?: release.assets.firstOrNull { it.name.endsWith(".apk", true) }
            ?: return null
        return UpdateInfo(
            version = release.tag_name.removePrefix("v"),
            tag = release.tag_name,
            notes = release.body,
            apkUrl = apk.browser_download_url,
            apkName = apk.name,
            publishedAt = release.published_at
        )
    }

    fun isNewer(remote: String, current: String): Boolean {
        val a = remote.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val b = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, info.apkName.ifBlank { "PotatoTV.apk" })
        Http.http.newCall(Http.request(info.apkUrl, userAgent = "PotatoTV/1.0")).execute().use { response ->
            if (!response.isSuccessful) error("Download failed (HTTP ${response.code}).")
            response.body?.byteStream()?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Empty update file.")
        }
        target
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(settings) }
        }
    }
}
