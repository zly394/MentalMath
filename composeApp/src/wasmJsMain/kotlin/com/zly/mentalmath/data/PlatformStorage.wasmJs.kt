package com.zly.mentalmath.data

import kotlinx.browser.window

private fun nowMillis(): Double = js("Date.now()")

actual object PlatformStorage {
    actual fun currentTimeMillis(): Long = nowMillis().toLong()

    actual fun getString(key: String): String? =
        window.localStorage.getItem("mental_math_$key")

    actual fun putString(key: String, value: String) {
        window.localStorage.setItem("mental_math_$key", value)
    }
}
