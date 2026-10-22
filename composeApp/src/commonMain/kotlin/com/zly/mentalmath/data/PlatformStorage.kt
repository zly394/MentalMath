package com.zly.mentalmath.data

/**
 * 轻量级跨平台键值存储与系统时钟接口
 */
expect object PlatformStorage {
    fun currentTimeMillis(): Long
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
