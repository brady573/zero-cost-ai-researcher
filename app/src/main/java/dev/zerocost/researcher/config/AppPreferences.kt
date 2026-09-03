package dev.zerocost.researcher.config

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("researcher_settings", Context.MODE_PRIVATE)

    var tavilyApiKey: String
        get() = prefs.getString(KEY_TAVILY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_TAVILY, value.trim()).apply()

    var searxngBaseUrl: String
        get() = prefs.getString(KEY_SEARX, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_SEARX, value.trim().trimEnd('/')).apply()

    var modelPath: String
        get() = prefs.getString(KEY_MODEL_PATH, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_MODEL_PATH, value).apply()

    var tavilyHardLimit: Int
        get() = prefs.getInt(KEY_TAVILY_LIMIT, DEFAULT_TAVILY_LIMIT)
        set(value) = prefs.edit().putInt(KEY_TAVILY_LIMIT, value.coerceIn(1, 1000)).apply()

    companion object {
        const val DEFAULT_TAVILY_LIMIT = 900
        private const val KEY_TAVILY = "tavily_api_key"
        private const val KEY_SEARX = "searxng_base_url"
        private const val KEY_MODEL_PATH = "model_path"
        private const val KEY_TAVILY_LIMIT = "tavily_hard_limit"
    }
}
