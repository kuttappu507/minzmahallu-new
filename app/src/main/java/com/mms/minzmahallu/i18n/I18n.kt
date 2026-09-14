package com.mms.minzmahallu.i18n

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object I18n {
    private val _lang = MutableStateFlow("en")
    val lang = _lang.asStateFlow()

    private var en: Map<String, String> = emptyMap()
    private var ml: Map<String, String> = emptyMap()

    fun init(ctx: Context) {
        en = load(ctx, "i18n_en.json")
        ml = load(ctx, "i18n_ml.json")
        val prefs = ctx.getSharedPreferences("mms", Context.MODE_PRIVATE)
        _lang.value = prefs.getString("lang", "en") ?: "en"
    }

    private fun load(ctx: Context, asset: String): Map<String, String> {
        return try {
            val text = ctx.assets.open(asset).bufferedReader().use { it.readText() }
            val obj = JSONObject(text)
            buildMap {
                obj.keys().forEach { k -> put(k, obj.optString(k, k)) }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun t(key: String): String {
        val map = if (_lang.value == "ml") ml else en
        return map[key] ?: en[key] ?: key
    }

    fun setLang(ctx: Context, code: String) {
        _lang.value = code
        ctx.getSharedPreferences("mms", Context.MODE_PRIVATE)
            .edit().putString("lang", code).apply()
    }

    fun toggle(ctx: Context) = setLang(ctx, if (_lang.value == "en") "ml" else "en")
}
