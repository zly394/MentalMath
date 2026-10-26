package com.zly.mentalmath.domain

/**
 * Accepts a single explicit answer rather than guessing from incidental words.
 */
object NumberVoiceParser {
    private val numbers = mapOf(
        "零" to 0, "〇" to 0, "一" to 1, "二" to 2, "两" to 2,
        "三" to 3, "四" to 4, "五" to 5, "六" to 6, "七" to 7,
        "八" to 8, "九" to 9, "十" to 10
    )
    private val prefixes = listOf("答案是", "答案", "等于", "得")
    private val punctuation = setOf('，', '。', '！', '？', ',', '.', '!', '?', '、')

    fun parseSpokenNumber(candidates: List<String>): Int? {
        val text = candidates.firstOrNull()?.filterNot { it.isWhitespace() }
            ?.trim { it in punctuation } ?: return null
        val prefix = prefixes.firstOrNull { text.startsWith(it) }
        val answer = if (prefix == null) text else text.removePrefix(prefix)
        return numbers[answer] ?: answer.toIntOrNull()?.takeIf {
            it in 0..10 && answer == it.toString()
        }
    }
}
