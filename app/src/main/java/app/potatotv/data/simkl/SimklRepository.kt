package app.potatotv.data.simkl

import app.potatotv.data.net.Http
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class SimklPin(
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int
)

@Serializable
private data class PinResponse(
    val user_code: String = "",
    val verification_url: String = "",
    val expires_in: Int = 0,
    val interval: Int = 5
)

@Serializable
private data class PinPollResponse(
    val access_token: String = "",
    val result: String = ""
)

/** Optional Simkl scrobbling, exactly like FMHYPlay's connect-with-a-code flow. */
class SimklRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun requestPin(clientId: String): SimklPin {
        if (clientId.isBlank()) error("Add your Simkl API client id in Settings first.")
        val body = Http.getText(
            "https://api.simkl.com/oauth/pin?client_id=${enc(clientId)}",
            headers = mapOf("Accept" to "application/json")
        )
        val parsed = json.decodeFromString(PinResponse.serializer(), body)
        if (parsed.user_code.isBlank()) error("Simkl did not return a pairing code.")
        return SimklPin(
            userCode = parsed.user_code,
            verificationUrl = parsed.verification_url.ifBlank { "https://simkl.com/pin" },
            expiresIn = parsed.expires_in,
            interval = parsed.interval.coerceAtLeast(3)
        )
    }

    /** Returns the token once the user confirms, or null while still pending. */
    suspend fun pollPin(clientId: String, userCode: String): String? {
        val body = Http.getTextOrNull(
            "https://api.simkl.com/oauth/pin/${enc(userCode)}?client_id=${enc(clientId)}",
            headers = mapOf("Accept" to "application/json")
        ) ?: return null
        return runCatching {
            json.decodeFromString(PinPollResponse.serializer(), body).access_token.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun markWatched(
        clientId: String,
        token: String,
        imdbId: String,
        type: String,
        title: String,
        season: Int? = null,
        episode: Int? = null
    ): Boolean {
        if (token.isBlank() || clientId.isBlank() || !imdbId.startsWith("tt")) return false
        val imdb = "\"imdb\":\"$imdbId\""
        val body = if (type == "series" && season != null && episode != null) {
            """{"shows":[{"title":${quote(title)},"ids":{$imdb},"seasons":[{"number":$season,"episodes":[{"number":$episode}]}]}]}"""
        } else {
            """{"movies":[{"title":${quote(title)},"ids":{$imdb}}]}"""
        }
        val response = Http.postJson(
            "https://api.simkl.com/sync/history",
            body = body,
            headers = mapOf(
                "simkl-api-key" to clientId,
                "Authorization" to "Bearer $token",
                "Accept" to "application/json"
            )
        )
        return response.isNotBlank()
    }

    private fun quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun enc(value: String) = java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
