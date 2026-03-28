package com.bananalab.app

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authSessionDataStore by preferencesDataStore(name = "banana_lab_auth_session")

data class PersistedAuthSession(
    val serverUrl: String,
    val savedAtMillis: Long,
    val cookiesJson: String,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return nowMillis - savedAtMillis >= AUTH_SESSION_TTL_MILLIS
    }
}

class AuthSessionStore(private val context: Context) {
    suspend fun save(serverUrl: String, cookiesJson: String, savedAtMillis: Long = System.currentTimeMillis()) {
        context.authSessionDataStore.edit { prefs ->
            prefs[Keys.serverUrl] = serverUrl
            prefs[Keys.savedAtMillis] = savedAtMillis
            prefs[Keys.cookiesJson] = cookiesJson
        }
    }

    suspend fun load(): PersistedAuthSession? {
        val prefs = context.authSessionDataStore.data.first()
        val serverUrl = prefs[Keys.serverUrl] ?: return null
        val savedAtMillis = prefs[Keys.savedAtMillis] ?: return null
        val cookiesJson = prefs[Keys.cookiesJson] ?: return null
        return PersistedAuthSession(
            serverUrl = serverUrl,
            savedAtMillis = savedAtMillis,
            cookiesJson = cookiesJson,
        )
    }

    suspend fun clear() {
        context.authSessionDataStore.edit { prefs ->
            prefs.remove(Keys.serverUrl)
            prefs.remove(Keys.savedAtMillis)
            prefs.remove(Keys.cookiesJson)
        }
    }

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val savedAtMillis = longPreferencesKey("saved_at_millis")
        val cookiesJson = stringPreferencesKey("cookies_json")
    }
}

private const val AUTH_SESSION_TTL_MILLIS = 24L * 60L * 60L * 1000L
