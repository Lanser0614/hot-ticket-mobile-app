package com.example.hotticketapplication.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.example.hotticketapplication.BuildConfig

fun json(vararg fields: Pair<String, Any?>) = JSONObject().apply {
    fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.let { array ->
    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
} ?: emptyList()
fun JSONObject.text(key: String, fallback: String = ""): String =
    if (isNull(key)) fallback else optString(key, fallback)
class ApiException(val code: String, override val message: String) : Exception(message)

/** Session secrets are encrypted with a non-exportable Android Keystore key. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("hot-ticket", Context.MODE_PRIVATE)
    var server: String
        get() = prefs.getString("server", BuildConfig.API_BASE_URL) ?: BuildConfig.API_BASE_URL
        set(value) { prefs.edit().putString("server", value).apply() }
    var language: String
        get() = prefs.getString("language", "ru") ?: "ru"
        set(value) { prefs.edit().putString("language", value).apply() }
    var theme: String
        get() = prefs.getString("theme", "auto") ?: "auto"
        set(value) { prefs.edit().putString("theme", value).apply() }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("hot-ticket-session", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("hot-ticket-session",
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun readToken(): String = runCatching {
        val encrypted = prefs.getString("session", null) ?: return ""
        val parts = encrypted.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrElse { clear(); "" }
    fun saveToken(token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("session", encrypted).commit())
    }
    fun clear() { prefs.edit().remove("session").apply() }
}

class HotTicketRepository(val store: SessionStore) {
    var token = store.readToken()
    fun setSession(value: String) { store.saveToken(value); token = value }
    fun clearSession() { store.clear(); token = "" }
    fun validateServer(value: String): String {
        val uri = runCatching { URI(value.trim()) }.getOrNull()
        require(uri?.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.query == null && uri.fragment == null && (uri.path.isNullOrEmpty() || uri.path == "/")) {
            "Укажите HTTPS-адрес сервера, например https://tickets.example.com"
        }
        return value.trim().trimEnd('/')
    }
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, anonymous: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL(validateServer(store.server) + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15000; connection.readTimeout = 20000
                connection.setRequestProperty("Accept", "application/json")
                if (!anonymous) {
                    if (token.isEmpty()) throw ApiException("unauthorized", "Войдите через Telegram")
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val payload = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
                if (status !in 200..299) {
                    val error = payload.optJSONObject("error")
                    throw ApiException(if (status == 401) "unauthorized" else error?.text("code") ?: "http_$status",
                        error?.text("message")?.takeIf { it.isNotEmpty() } ?: "Ошибка сервера ($status)")
                }
                payload
            } finally { connection.disconnect() }
        }
}
