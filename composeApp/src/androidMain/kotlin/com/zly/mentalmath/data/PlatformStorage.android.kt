package com.zly.mentalmath.data

import android.content.Context
import android.content.SharedPreferences

actual object PlatformStorage {
    private var prefs: SharedPreferences? = null
    private val memoryStore = mutableMapOf<String, String>()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(
                "mental_math_store",
                Context.MODE_PRIVATE
            )
        }
    }

    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun getString(key: String): String? = prefs?.getString(key, null) ?: memoryStore[key]

    actual fun putString(key: String, value: String) {
        val p = prefs
        if (p != null) {
            p.edit().putString(key, value).apply()
        } else {
            memoryStore[key] = value
        }
    }

    fun clearForTesting() {
        memoryStore.clear()
    }
}
