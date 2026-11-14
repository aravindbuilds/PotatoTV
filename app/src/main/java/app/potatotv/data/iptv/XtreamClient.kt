package app.potatotv.data.iptv

import app.potatotv.data.model.Channel
import app.potatotv.data.net.Http
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Minimal Xtream Codes client: live categories + live streams, compiled into
 * the same Channel model used by iptv-org and the M3U parser.
 */
class XtreamClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun load(base: String, username: String, password: String, playlistName: String): List<Channel> {
        val root = normalize(base)
        val query = "username=${enc(username)}&password=${enc(password)}"
        val categories = runCatching {
            val body = Http.getText("$root/player_api.php?$query&action=get_live_categories")
            parseCategories(body)
        }.getOrDefault(emptyMap())
        val body = Http.getText("$root/player_api.php?$query&action=get_live_streams")
        val array = runCatching { json.parseToJsonElement(body) as JsonArray }.getOrElse {
            error("This server did not return a stream list. Check the URL and login.")
        }

        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val streamId = obj.string("stream_id") ?: return@mapNotNull null
            val name = obj.string("name")?.trim().orEmpty().ifBlank { return@mapNotNull null }
            val icon = obj.string("stream_icon")?.takeIf { it.isNotBlank() }
            val categoryId = obj.string("category_id")
            val categoryName = categoryId?.let { categories[it] }
            val hls = "$root/live/${enc(username)}/${enc(password)}/$streamId.m3u8"
            val ts = "$root/live/${enc(username)}/${enc(password)}/$streamId.ts"
            Channel(
                id = "xtream:$root:$streamId",
                name = name,
                url = hls,
                urls = listOf(ts),
                logo = icon,
                group = categoryName,
                playlistName = playlistName,
                playlistId = root
            )
        }.distinctBy { it.id }
    }

    private fun parseCategories(body: String): Map<String, String> {
        val array = runCatching { json.parseToJsonElement(body) as JsonArray }.getOrNull() ?: return emptyMap()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("category_id") ?: return@mapNotNull null
            val name = obj.string("category_name") ?: return@mapNotNull null
            id to name
        }.toMap()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    private fun normalize(raw: String): String {
        var value = raw.trim().trimEnd('/')
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
            value = "http://$value"
        }
        return value
    }

    private fun enc(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
