package com.zly.mentalmath.data

import com.zly.mentalmath.model.AnswerOutcome
import com.zly.mentalmath.model.AttemptRecord
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.QuestionStat
import com.zly.mentalmath.model.SessionRecord
import com.zly.mentalmath.model.TimeLimitOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 答题统计、错题本与训练记录仓库
 */
class MathRepository {

    companion object {
        private const val KEY_QUESTION_STATS = "question_stats_v1"
        private const val KEY_SESSION_HISTORY = "session_history_v1"
        private const val KEY_CONFIG = "practice_config_v1"
    }

    private val _statsMap = MutableStateFlow<Map<String, QuestionStat>>(emptyMap())
    val statsMap: StateFlow<Map<String, QuestionStat>> = _statsMap.asStateFlow()

    private val _sessions = MutableStateFlow<List<SessionRecord>>(emptyList())
    val sessions: StateFlow<List<SessionRecord>> = _sessions.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _statsMap.value = loadStats()
        _sessions.value = loadSessions()
    }

    /**
     * 记录单道题作答结果并即时更新错题本与耗时统计
     */
    fun recordAttempt(attempt: AttemptRecord, timeLimitMs: Long) {
        val currentMap = _statsMap.value.toMutableMap()
        val key = attempt.question.key
        val old = currentMap[key] ?: QuestionStat(key = key)

        val isFastCorrect = attempt.outcome == AnswerOutcome.CORRECT &&
            attempt.elapsedMs <= timeLimitMs

        val nextMistakeStreak = when (attempt.outcome) {
            AnswerOutcome.CORRECT -> {
                if (isFastCorrect && old.mistakeStreakToClear > 0) {
                    (old.mistakeStreakToClear - 1).coerceAtLeast(0)
                } else {
                    old.mistakeStreakToClear
                }
            }
            AnswerOutcome.WRONG, AnswerOutcome.TIMEOUT -> {
                // 答错或超时自动加入错题本，需连续秒对 2 次才能移出
                2
            }
        }

        val updated = old.copy(
            totalAttempts = old.totalAttempts + 1,
            correctCount = old.correctCount + if (attempt.outcome == AnswerOutcome.CORRECT) 1 else 0,
            wrongCount = old.wrongCount + if (attempt.outcome == AnswerOutcome.WRONG) 1 else 0,
            timeoutCount = old.timeoutCount + if (attempt.outcome == AnswerOutcome.TIMEOUT) 1 else 0,
            totalCorrectTimeMs = old.totalCorrectTimeMs +
                if (attempt.outcome == AnswerOutcome.CORRECT) attempt.elapsedMs else 0L,
            mistakeStreakToClear = nextMistakeStreak,
            lastAttemptTimeMs = attempt.timestampMs
        )

        currentMap[key] = updated
        _statsMap.value = currentMap
        saveStats(currentMap)
    }

    /**
     * 保存整轮训练会话记录
     */
    fun recordSession(config: PracticeConfig, attempts: List<AttemptRecord>) {
        if (attempts.isEmpty()) return
        val correctList = attempts.filter { it.outcome == AnswerOutcome.CORRECT }
        val avgMs = if (correctList.isEmpty()) {
            0L
        } else {
            correctList.sumOf { it.elapsedMs } / correctList.size
        }

        val record = SessionRecord(
            id = PlatformStorage.currentTimeMillis(),
            modeLabel = config.questionMode.label,
            timeLimitLabel = config.timeLimit.label,
            totalCount = attempts.size,
            correctCount = correctList.size,
            wrongCount = attempts.count { it.outcome == AnswerOutcome.WRONG },
            timeoutCount = attempts.count { it.outcome == AnswerOutcome.TIMEOUT },
            avgResponseMs = avgMs,
            timestampMs = PlatformStorage.currentTimeMillis()
        )

        val updated = (listOf(record) + _sessions.value).take(50) // 保留最近 50 轮记录
        _sessions.value = updated
        saveSessions(updated)
    }

    /**
     * 手动将某道题移出错题本
     */
    fun removeFromMistakeBook(key: String) {
        val currentMap = _statsMap.value.toMutableMap()
        val existing = currentMap[key] ?: return
        currentMap[key] = existing.copy(mistakeStreakToClear = 0)
        _statsMap.value = currentMap
        saveStats(currentMap)
    }

    /**
     * 清空所有历史统计
     */
    fun clearAll() {
        _statsMap.value = emptyMap()
        _sessions.value = emptyList()
        PlatformStorage.putString(KEY_QUESTION_STATS, "")
        PlatformStorage.putString(KEY_SESSION_HISTORY, "")
    }

    private fun loadStats(): Map<String, QuestionStat> {
        val raw = PlatformStorage.getString(KEY_QUESTION_STATS) ?: return emptyMap()
        if (raw.isBlank()) return emptyMap()
        val result = mutableMapOf<String, QuestionStat>()
        raw.lineSequence().forEach { line ->
            val parts = line.split("|")
            if (parts.size >= 8) {
                val stat = QuestionStat(
                    key = parts[0],
                    totalAttempts = parts[1].toIntOrNull() ?: 0,
                    correctCount = parts[2].toIntOrNull() ?: 0,
                    wrongCount = parts[3].toIntOrNull() ?: 0,
                    timeoutCount = parts[4].toIntOrNull() ?: 0,
                    totalCorrectTimeMs = parts[5].toLongOrNull() ?: 0L,
                    mistakeStreakToClear = parts[6].toIntOrNull() ?: 0,
                    lastAttemptTimeMs = parts[7].toLongOrNull() ?: 0L
                )
                result[stat.key] = stat
            }
        }
        return result
    }

    private fun saveStats(map: Map<String, QuestionStat>) {
        val serialized = map.values.joinToString("\n") { s ->
            "${s.key}|${s.totalAttempts}|${s.correctCount}|${s.wrongCount}|${s.timeoutCount}|${s.totalCorrectTimeMs}|${s.mistakeStreakToClear}|${s.lastAttemptTimeMs}"
        }
        PlatformStorage.putString(KEY_QUESTION_STATS, serialized)
    }

    private fun loadSessions(): List<SessionRecord> {
        val raw = PlatformStorage.getString(KEY_SESSION_HISTORY) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 9) {
                SessionRecord(
                    id = parts[0].toLongOrNull() ?: 0L,
                    modeLabel = parts[1],
                    timeLimitLabel = parts[2],
                    totalCount = parts[3].toIntOrNull() ?: 0,
                    correctCount = parts[4].toIntOrNull() ?: 0,
                    wrongCount = parts[5].toIntOrNull() ?: 0,
                    timeoutCount = parts[6].toIntOrNull() ?: 0,
                    avgResponseMs = parts[7].toLongOrNull() ?: 0L,
                    timestampMs = parts[8].toLongOrNull() ?: 0L
                )
            } else {
                null
            }
        }.toList()
    }

    private fun saveSessions(list: List<SessionRecord>) {
        val serialized = list.joinToString("\n") { r ->
            "${r.id}|${r.modeLabel}|${r.timeLimitLabel}|${r.totalCount}|${r.correctCount}|${r.wrongCount}|${r.timeoutCount}|${r.avgResponseMs}|${r.timestampMs}"
        }
        PlatformStorage.putString(KEY_SESSION_HISTORY, serialized)
    }

    /**
     * 读取用户持久化保存的训练偏好配置（题型、限时、作答方式）
     */
    fun loadPracticeConfig(): PracticeConfig {
        val raw = PlatformStorage.getString(KEY_CONFIG) ?: return PracticeConfig()
        if (raw.isBlank()) return PracticeConfig()
        val parts = raw.split("|")
        val mode = parts.getOrNull(0)?.let { name ->
            runCatching { QuestionMode.valueOf(name) }.getOrNull()
        } ?: QuestionMode.MIXED
        val timeLimit = parts.getOrNull(1)?.let { name ->
            runCatching { TimeLimitOption.valueOf(name) }.getOrNull()
        } ?: TimeLimitOption.NORMAL_2S
        val inputMode = parts.getOrNull(2)?.let { name ->
            runCatching { InputMode.valueOf(name) }.getOrNull()
        } ?: PracticeConfig().inputMode

        return PracticeConfig(
            questionMode = mode,
            timeLimit = timeLimit,
            inputMode = inputMode
        )
    }

    /**
     * 保存用户选择的训练配置，用于下次冷启动恢复
     */
    fun savePracticeConfig(config: PracticeConfig) {
        val serialized = "${config.questionMode.name}|${config.timeLimit.name}|${config.inputMode.name}"
        PlatformStorage.putString(KEY_CONFIG, serialized)
    }
}
