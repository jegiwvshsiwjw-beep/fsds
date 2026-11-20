package com.localagentbridge.server

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

object AppPrefs {
    const val PREFS = "local_agent_bridge"
    const val KEY_TOKEN = "api_token"
    const val KEY_UPSTREAM = "upstream_base_url"
    const val KEY_MODEL = "upstream_model"
    const val KEY_API_KEY = "upstream_api_key"
    const val KEY_RUNNING = "server_running"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(context: Context): String {
        val preferences = prefs(context)
        val existing = preferences.getString(KEY_TOKEN, null)
        if (!existing.isNullOrBlank()) return existing
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val generated = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        preferences.edit().putString(KEY_TOKEN, generated).apply()
        return generated
    }

    fun rotateToken(context: Context): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val generated = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        prefs(context).edit().putString(KEY_TOKEN, generated).apply()
        return generated
    }
}
