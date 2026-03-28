package com.bananalab.app

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("banana_lab_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var promptText: String
        get() = prefs.getString(KEY_PROMPT_TEXT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROMPT_TEXT, value).apply()

    var aspectRatio: String
        get() = prefs.getString(KEY_ASPECT_RATIO, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_ASPECT_RATIO, value).apply()

    var imageSize: String
        get() = prefs.getString(KEY_IMAGE_SIZE, "4K") ?: "4K"
        set(value) = prefs.edit().putString(KEY_IMAGE_SIZE, value).apply()

    var enableSearch: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_SEARCH, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_SEARCH, value).apply()

    var promptMode: String
        get() = prefs.getString(KEY_PROMPT_MODE, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_PROMPT_MODE, value).apply()

    var selectedPersonaId: String
        get() = prefs.getString(KEY_SELECTED_PERSONA_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_PERSONA_ID, value).apply()

    var selectedTab: String
        get() = prefs.getString(KEY_SELECTED_TAB, AppTab.Generate.name) ?: AppTab.Generate.name
        set(value) = prefs.edit().putString(KEY_SELECTED_TAB, value).apply()

    companion object {
        const val DEFAULT_SERVER_URL = "http://127.0.0.1:8787"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_PROMPT_TEXT = "prompt_text"
        private const val KEY_ASPECT_RATIO = "aspect_ratio"
        private const val KEY_IMAGE_SIZE = "image_size"
        private const val KEY_ENABLE_SEARCH = "enable_search"
        private const val KEY_PROMPT_MODE = "prompt_mode"
        private const val KEY_SELECTED_PERSONA_ID = "selected_persona_id"
        private const val KEY_SELECTED_TAB = "selected_tab"
    }
}

