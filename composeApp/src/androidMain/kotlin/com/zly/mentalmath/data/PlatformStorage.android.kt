package com.zly.mentalmath.data

import android.content.Context
import android.content.SharedPreferences

actual object PlatformStorage {
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(
                "mental_math_store",
                Context.MODE_PRIVATE
            )
        }
    }

    actual fun currentTimeMillis(): Long = System.currentTimeMillis()

    actual fun getString(key: String): String? = prefs?.getString(key, null)

    actual fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }
}
