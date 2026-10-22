package com.zly.mentalmath.data

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

actual object PlatformStorage {
    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    actual fun currentTimeMillis(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    actual fun getString(key: String): String? =
        defaults.stringForKey(key)

    actual fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
    }
}
