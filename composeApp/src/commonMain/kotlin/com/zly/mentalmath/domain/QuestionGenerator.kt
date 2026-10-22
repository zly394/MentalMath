package com.zly.mentalmath.domain

import com.zly.mentalmath.model.Operator
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.Question
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.QuestionStat
import kotlin.random.Random

/**
 * 10 以内加减法题库与智能加权出题器
 */
object QuestionGenerator {

    /**
     * 全量 132 道原子题目（加法 66 道 + 减法 66 道）
     */
    val allQuestions: List<Question> by lazy {
        buildList {
            // 10 以内加法：a in 0..10, b in 0..(10 - a)
            for (a in 0..10) {
                for (b in 0..(10 - a)) {
                    add(Question(a = a, op = Operator.ADD, b = b))
                }
            }
            // 10 以内减法：a in 0..10, b in 0..a
            for (a in 0..10) {
                for (b in 0..a) {
                    add(Question(a = a, op = Operator.SUBTRACT, b = b))
                }
            }
        }
    }

    private val questionByKey: Map<String, Question> by lazy {
        allQuestions.associateBy { it.key }
    }

    fun findByKey(key: String): Question? = questionByKey[key]

    /**
     * 根据当前配置筛选候选池
     */
    fun getCandidatePool(
        config: PracticeConfig,
        statsMap: Map<String, QuestionStat>
    ): List<Question> {
        val baseFiltered = allQuestions.filter { q ->
            when (config.questionMode) {
                QuestionMode.MIXED -> true
                QuestionMode.ADDITION -> q.op == Operator.ADD
                QuestionMode.SUBTRACTION -> q.op == Operator.SUBTRACT
                QuestionMode.MISTAKE_REVIEW -> true
            }
        }

        if (config.questionMode == QuestionMode.MISTAKE_REVIEW) {
            // 错题专项：优先取错题本中的题 + 历史平均耗时 > 1500ms 或正确率 < 85% 的题
            val mistakeQuestions = baseFiltered.filter { q ->
                val stat = statsMap[q.key] ?: return@filter false
                stat.isInMistakeBook || stat.accuracy < 0.85f || stat.avgResponseMs > 1500L
            }
            if (mistakeQuestions.isNotEmpty()) {
                return mistakeQuestions
            }
        }
        return baseFiltered
    }

    /**
     * 基于间隔重复与薄弱权重的下一题抽取算法（保证不与上一题重复）
     */
    fun nextQuestion(
        config: PracticeConfig,
        statsMap: Map<String, QuestionStat>,
        lastQuestion: Question?
    ): Question {
        val pool = getCandidatePool(config, statsMap)
        val candidates = if (pool.size > 1 && lastQuestion != null) {
            pool.filter { it.key != lastQuestion.key }
        } else {
            pool
        }

        // 计算每道候选题目被抽中的权重
        val weights = candidates.map { q ->
            val stat = statsMap[q.key]
            when {
                stat == null || stat.totalAttempts == 0 -> 3.0 // 未练过的新题优先覆盖
                stat.isInMistakeBook -> 6.0 + stat.mistakeStreakToClear * 1.5 // 错题本最高优先级
                stat.accuracy < 0.75f -> 4.5 // 易错题
                stat.avgResponseMs > 1600L -> 3.5 // 反应偏慢题（还在扳手指/默数）
                stat.avgResponseMs > 1100L -> 2.0 // 中等熟练度
                else -> 1.0 // 已秒杀熟练题，保持基础复习概率
            }
        }

        val totalWeight = weights.sum()
        var randomPoint = Random.nextDouble() * totalWeight
        for (i in candidates.indices) {
            randomPoint -= weights[i]
            if (randomPoint <= 0.0) {
                return candidates[i]
            }
        }
        return candidates.last()
    }
}
