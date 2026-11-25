package com.cinedrive.app.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Small JSON-over-HTTP helper shared by providers. Never logs headers (they may hold tokens). */
object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val UA = "CineDrive/0.1 (Android)"

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", UA)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) throw IOException("Server replied ${r.code}")
                JSONObject(r.body?.string() ?: "{}")
            }
        }

    suspend fun postJson(url: String, body: JSONObject, headers: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).header("User-Agent", UA)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    throw IOException(if (r.code == 401) "Wrong username or password" else "Server replied ${r.code}")
                }
                JSONObject(r.body?.string() ?: "{}")
            }
        }
}

// ---- tolerant JSON readers (Internet Archive fields can be a string OR a list) ----

fun JSONObject.text(key: String): String? = when (val v = opt(key)) {
    is String -> v.ifBlank { null }
    is JSONArray -> (0 until v.length()).map { v.optString(it) }.filter { it.isNotBlank() }
        .joinToString("\n").ifBlank { null }
    is Number -> v.toString()
    else -> null
}

fun JSONObject.firstText(key: String): String? = when (val v = opt(key)) {
    is JSONArray -> if (v.length() > 0) v.optString(0).ifBlank { null } else null
    else -> text(key)
}

fun JSONObject.list(key: String): List<String> = when (val v = opt(key)) {
    is JSONArray -> (0 until v.length()).map { v.optString(it) }.filter { it.isNotBlank() }
    is String -> v.split(';', ',').map { it.trim() }.filter { it.isNotBlank() }
    else -> emptyList()
}

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
