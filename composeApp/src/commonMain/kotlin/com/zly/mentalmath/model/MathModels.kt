package com.zly.mentalmath.model

/**
 * 运算符枚举
 */
enum class Operator(val symbol: String, val spokenWord: String) {
    ADD("+", "加"),
    SUBTRACT("-", "减")
}

private fun Int.toChineseSpoken(): String = when (this) {
    0 -> "零"
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "七"
    8 -> "八"
    9 -> "九"
    10 -> "十"
    else -> this.toString()
}

/**
 * 10 以内加减法单道题目
 */
data class Question(
    val a: Int,
    val op: Operator,
    val b: Int
) {
    val answer: Int = when (op) {
        Operator.ADD -> a + b
        Operator.SUBTRACT -> a - b
    }

    /** 唯一标识键，如 "3+5" 或 "9-4" */
    val key: String = "$a${op.symbol}$b"

    /** 算式左半部分，如 "3 + 5" */
    val leftExpression: String = "$a ${op.symbol} $b"

    /** 完整算式（用于错题列表展示），如 "3 + 5 = 8" */
    val fullExpression: String = "$a ${op.symbol} $b = $answer"

    /** 纯中文语音极速提问文本（不带“等于”），如 "三加五"、"十减六" */
    val spokenQuestion: String = "${a.toChineseSpoken()}${op.spokenWord}${b.toChineseSpoken()}"
}

/**
 * 题型模式
 */
enum class QuestionMode(val label: String, val desc: String) {
    MIXED("加减混合", "10以内加减法综合训练"),
    ADDITION("纯加法", "和在10以内的加法专项"),
    SUBTRACTION("纯减法", "被减数10以内的减法专项"),
    MISTAKE_REVIEW("错题消灭", "针对错题本与慢题专项突击")
}

/**
 * 单题限时三档可调
 */
enum class TimeLimitOption(val millis: Long, val label: String, val tag: String) {
    FAST_1S(1000L, "1 秒", "极速反射"),
    NORMAL_2S(2000L, "2 秒", "标准闯关"),
    BEGINNER_3S(3000L, "3 秒", "初学适应")
}

/**
 * 作答交互模式（支持随时切换）
 */
enum class InputMode(val label: String, val desc: String) {
    VOICE("语音抢答", "听题直接喊答案，开口停表"),
    KEYPAD("听音按键", "听题秒按屏幕 0~10 数字键")
}

/**
 * 训练配置
 */
data class PracticeConfig(
    val questionMode: QuestionMode = QuestionMode.MIXED,
    val timeLimit: TimeLimitOption = TimeLimitOption.NORMAL_2S,
    val inputMode: InputMode = InputMode.VOICE,
    val totalQuestions: Int = 20
)

/**
 * 单次作答结果类型
 */
enum class AnswerOutcome {
    CORRECT,    // 限时内回答正确
    WRONG,      // 回答错误
    TIMEOUT     // 超时未答
}

/**
 * 单道题的单次作答明细
 */
data class AttemptRecord(
    val question: Question,
    val userAnswer: Int?,
    val outcome: AnswerOutcome,
    val elapsedMs: Long,
    val timestampMs: Long
)

/**
 * 原子题目的累计统计数据（共 132 道题）
 * 连续秒答正确 2 次即可从错题本中移出
 */
data class QuestionStat(
    val key: String,
    val totalAttempts: Int = 0,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val timeoutCount: Int = 0,
    val totalCorrectTimeMs: Long = 0L,
    val mistakeStreakToClear: Int = 0,
    val lastAttemptTimeMs: Long = 0L
) {
    val isInMistakeBook: Boolean
        get() = mistakeStreakToClear > 0

    val accuracy: Float
        get() = if (totalAttempts == 0) 0f else correctCount.toFloat() / totalAttempts

    val avgResponseMs: Long
        get() = if (correctCount == 0) 0L else totalCorrectTimeMs / correctCount
}

/**
 * 一轮训练会话记录
 */
data class SessionRecord(
    val id: Long,
    val modeLabel: String,
    val timeLimitLabel: String,
    val totalCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val timeoutCount: Int,
    val avgResponseMs: Long,
    val timestampMs: Long
)

/**
 * 10以内加减法五大认知维度分类（用于薄弱环节科学诊断）
 */
enum class SkillCategory(val title: String, val advice: String) {
    ZERO_AND_ONE(
        title = "0 与 1 的直觉运算",
        advice = "理解『加0/减0不变』、『加1往后数一个、减1往前数一个』的直觉规律"
    ),
    WITHIN_FIVE(
        title = "5 以内基础加减",
        advice = "通过一只手的手指映射建立 1~5 数量分合的直接视觉表象"
    ),
    MAKE_AND_BREAK_TEN(
        title = "凑十与 10 的减法",
        advice = "熟记『一九、二八、三七、四六、五五』凑十歌，这是未来20以内进退位的核心基石"
    ),
    DOUBLES_AND_EQUAL(
        title = "双数加倍与相同数相减",
        advice = "强化 2+2、3+3、4+4、5+5 对称加倍记忆，以及相同数相减得 0"
    ),
    SIX_TO_NINE_SPLIT(
        title = "6 ~ 9 的分合加减",
        advice = "一年级最易卡壳区间（如 3+4、8-5），建议重点练习 6、7、8、9 的数的分解与合成"
    )
}

/**
 * 单个认知维度的诊断结果
 */
data class CategoryDiagnosis(
    val category: SkillCategory,
    val practicedCount: Int,
    val totalQuestionsInCategory: Int,
    val totalAttempts: Int,
    val accuracy: Float,
    val avgResponseMs: Long,
    val mistakeBookCount: Int,
    val weaknessScore: Float
)

/**
 * 熟练度等级（用于 10x10 矩阵热力图展示）
 */
enum class MasteryLevel(val label: String) {
    UNPRACTICED("未练"),
    MASTERED("秒杀 (<1.2s)"),
    FAMILIAR("熟练 (1.2~2s)"),
    WEAK("薄弱/错题")
}
