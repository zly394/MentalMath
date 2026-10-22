package com.zly.mentalmath.data

import kotlinx.browser.window
import kotlin.js.Date

actual object PlatformStorage {
    actual fun getString(key: String): String? {
        return try {
            window.localStorage.getItem("mental_math_$key")
        } catch (_: Throwable) {
            null
        }
    }

    actual fun putString(key: String, value: String) {
        try {
            window.localStorage.setItem("mental_math_$key", value)
        } catch (_: Throwable) {
        }
    }

    actual fun currentTimeMillis(): Long {
        return Date.now().toLong()
    }
}
