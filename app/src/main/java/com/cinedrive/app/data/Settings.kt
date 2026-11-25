package com.cinedrive.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

data class JellyfinAccount(val server: String, val userId: String, val userName: String, val token: String)

/**
 * App settings. The Jellyfin access token is kept in encrypted storage (Android Keystore);
 * the password itself is never stored.
 */
class Settings(ctx: Context) {

    private val secure: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            ctx, "cinedrive_secure",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Some devices have a broken keystore; fall back to private app storage.
        Log.w("CineDrive", "Encrypted storage unavailable, using private storage")
        ctx.getSharedPreferences("cinedrive_secure_fallback", Context.MODE_PRIVATE)
    }

    private val plain: SharedPreferences = ctx.getSharedPreferences("cinedrive", Context.MODE_PRIVATE)

    var jellyfin: JellyfinAccount?
        get() {
            val server = secure.getString("jf_server", null) ?: return null
            return JellyfinAccount(
                server = server,
                userId = secure.getString("jf_user_id", null) ?: return null,
                userName = secure.getString("jf_user_name", "") ?: "",
                token = secure.getString("jf_token", null) ?: return null,
            )
        }
        set(v) {
            secure.edit().apply {
                if (v == null) {
                    remove("jf_server"); remove("jf_user_id"); remove("jf_user_name"); remove("jf_token")
                } else {
                    putString("jf_server", v.server); putString("jf_user_id", v.userId)
                    putString("jf_user_name", v.userName); putString("jf_token", v.token)
                }
            }.apply()
        }

    val deviceId: String
        get() = plain.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            plain.edit().putString("device_id", it).apply()
        }

    var recentSearches: List<String>
        get() = plain.getString("recent_searches", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = plain.edit().putString("recent_searches", v.take(8).joinToString("\n")).apply()

    fun addRecentSearch(q: String) {
        val t = q.trim()
        if (t.length < 2) return
        recentSearches = listOf(t) + recentSearches.filterNot { it.equals(t, ignoreCase = true) }
    }
}
