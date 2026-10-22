package com.zly.mentalmath.domain

import com.zly.mentalmath.model.CategoryDiagnosis
import com.zly.mentalmath.model.MasteryLevel
import com.zly.mentalmath.model.Operator
import com.zly.mentalmath.model.Question
import com.zly.mentalmath.model.QuestionStat
import com.zly.mentalmath.model.SkillCategory

/**
 * 薄弱环节诊断与熟练度热力图分析引擎
 */
object WeaknessAnalyzer {

    /**
     * 将任意一道 10 以内加减法归类到最具代表性的认知维度
     */
    fun classifyQuestion(q: Question): SkillCategory {
        // 1. 含 0 或 1 的直觉运算（如 5+0, 0+8, 7-0, 7-1, 4+1）
        if (q.a == 0 || q.b == 0 || q.a == 1 || q.b == 1) {
            // 但 9+1=10 和 1+9=10、10-1=9 归入凑十/十的减法更有教学意义
            if (q.answer == 10 || (q.op == Operator.SUBTRACT && q.a == 10)) {
                return SkillCategory.MAKE_AND_BREAK_TEN
            }
            return SkillCategory.ZERO_AND_ONE
        }

        // 2. 凑十与 10 的减法（如 2+8, 3+7, 4+6, 10-3, 10-7）
        if (q.answer == 10 || (q.op == Operator.SUBTRACT && q.a == 10)) {
            return SkillCategory.MAKE_AND_BREAK_TEN
        }

        // 3. 双数加倍与相同数相减归零（如 2+2, 3+3, 4+4, 5+5, 6-6）
        if (q.a == q.b) {
            return SkillCategory.DOUBLES_AND_EQUAL
        }

        // 4. 5 以内基础加减（操作数与结果均 <= 5）
        if (q.a <= 5 && q.b <= 5 && q.answer <= 5) {
            return SkillCategory.WITHIN_FIVE
        }

        // 5. 其余均为 6~9 的分合加减（如 2+4, 3+5, 8-3, 9-4 等一年级核心难点）
        return SkillCategory.SIX_TO_NINE_SPLIT
    }

    /**
     * 生成五大认知维度的薄弱度诊断报告（按薄弱程度从高到低排序）
     */
    fun analyzeCategories(statsMap: Map<String, QuestionStat>): List<CategoryDiagnosis> {
        val grouped = QuestionGenerator.allQuestions.groupBy { classifyQuestion(it) }

        return SkillCategory.entries.map { category ->
            val questions = grouped[category].orEmpty()
            val stats = questions.mapNotNull { statsMap[it.key] }.filter { it.totalAttempts > 0 }

            val practicedCount = stats.size
            val totalAttempts = stats.sumOf { it.totalAttempts }
            val totalCorrect = stats.sumOf { it.correctCount }
            val totalCorrectTime = stats.sumOf { it.totalCorrectTimeMs }
            val mistakeBookCount = stats.count { it.isInMistakeBook }

            val accuracy = if (totalAttempts == 0) 0f else totalCorrect.toFloat() / totalAttempts
            val avgResponseMs = if (totalCorrect == 0) 0L else totalCorrectTime / totalCorrect

            // 计算薄弱指数：错误率占比 + 慢速惩罚 + 错题本存量
            val weaknessScore = if (totalAttempts == 0) {
                0f
            } else {
                val errorPenalty = (1f - accuracy) * 60f
                val speedPenalty = ((avgResponseMs - 900L).coerceAtLeast(0L) / 2000f).coerceAtMost(1f) * 25f
                val mistakePenalty = mistakeBookCount * 5f
                errorPenalty + speedPenalty + mistakePenalty
            }

            CategoryDiagnosis(
                category = category,
                practicedCount = practicedCount,
                totalQuestionsInCategory = questions.size,
                totalAttempts = totalAttempts,
                accuracy = accuracy,
                avgResponseMs = avgResponseMs,
                mistakeBookCount = mistakeBookCount,
                weaknessScore = weaknessScore
            )
        }.sortedByDescending { it.weaknessScore }
    }

    /**
     * 评估单道题目的掌握等级（用于 10x10 热力矩阵与错题本标签）
     */
    fun evaluateMastery(stat: QuestionStat?): MasteryLevel {
        if (stat == null || stat.totalAttempts == 0) return MasteryLevel.UNPRACTICED
        if (stat.isInMistakeBook || stat.accuracy < 0.75f) return MasteryLevel.WEAK
        if (stat.avgResponseMs in 1..1200) return MasteryLevel.MASTERED
        if (stat.avgResponseMs <= 2000) return MasteryLevel.FAMILIAR
        return MasteryLevel.WEAK
    }
}
