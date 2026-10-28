package com.zly.mentalmath

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zly.mentalmath.model.AttemptRecord
import com.zly.mentalmath.model.AnswerOutcome
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.Operator
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.Question
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.TimeLimitOption
import com.zly.mentalmath.ui.HomeScreen
import com.zly.mentalmath.model.QuestionStat
import com.zly.mentalmath.model.SessionRecord
import com.zly.mentalmath.ui.AnalysisScreen
import com.zly.mentalmath.ui.PracticeScreen
import com.zly.mentalmath.ui.PracticeUiState
import com.zly.mentalmath.ui.QuestionPhase

@Preview(name = "HomeScreen - Phone", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "HomeScreen - Pad", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        HomeScreen(
            config = PracticeConfig(
                questionMode = QuestionMode.MIXED,
                timeLimit = TimeLimitOption.NORMAL_2S,
                inputMode = InputMode.VOICE
            ),
            statsMap = emptyMap(),
            sessions = emptyList(),
            onSelectQuestionMode = {},
            onSelectTimeLimit = {},
            onSelectInputMode = {},
            onStartPractice = {},
            onStartMistakeReview = {},
            onOpenAnalysis = {}
        )
    }
}

@Preview(name = "PracticeScreen (Voice) - Phone", showBackground = true, widthDp = 375, heightDp = 812)
@Preview(name = "PracticeScreen (Voice) - Pad", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
fun PracticeScreenVoicePreview() {
    MaterialTheme {
        PracticeScreen(
            state = PracticeUiState(
                config = PracticeConfig(
                    questionMode = QuestionMode.MIXED,
                    timeLimit = TimeLimitOption.NORMAL_2S,
                    inputMode = InputMode.VOICE
                ),
                isPracticing = true,
                isPaused = false,
                currentIndex = 0,
                currentQuestion = Question(a = 3, op = Operator.ADD, b = 7),
                phase = QuestionPhase.FEEDBACK,
                lastAttempt = AttemptRecord(
                    question = Question(a = 3, op = Operator.ADD, b = 7),
                    outcome = AnswerOutcome.CORRECT,
                    userAnswer = 10,
                    elapsedMs = 850L,
                    timestampMs = 1710000000000L
                ),
                comboCount = 5,
                maxCombo = 5,
                voiceHint = ""
            ),
            isStarting = false,
            onToggleInputMode = {},
            onKeypadSubmit = {},
            onRestartSession = {},
            onResumeSession = {},
            onOpenAnalysis = {},
            onExit = {}
        )
    }
}

@Preview(name = "PracticeScreen (Voice No Combo) - Phone", showBackground = true, widthDp = 375, heightDp = 812)
@Composable
fun PracticeScreenVoiceNoComboPreview() {
    MaterialTheme {
        PracticeScreen(
            state = PracticeUiState(
                config = PracticeConfig(
                    questionMode = QuestionMode.MIXED,
                    timeLimit = TimeLimitOption.NORMAL_2S,
                    inputMode = InputMode.VOICE
                ),
                isPracticing = true,
                isPaused = false,
                currentIndex = 0,
                currentQuestion = Question(a = 3, op = Operator.ADD, b = 7),
                phase = QuestionPhase.FEEDBACK,
                lastAttempt = AttemptRecord(
                    question = Question(a = 3, op = Operator.ADD, b = 7),
                    outcome = AnswerOutcome.CORRECT,
                    userAnswer = 10,
                    elapsedMs = 850L,
                    timestampMs = 1710000000000L
                ),
                comboCount = 0,
                maxCombo = 0,
                voiceHint = ""
            ),
            isStarting = false,
            onToggleInputMode = {},
            onKeypadSubmit = {},
            onRestartSession = {},
            onResumeSession = {},
            onOpenAnalysis = {},
            onExit = {}
        )
    }
}

@Preview(name = "PracticeScreen (Keypad) - Phone", showBackground = true, widthDp = 412, heightDp = 892)
@Preview(name = "PracticeScreen (Keypad) - Pad", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
fun PracticeScreenKeypadPreview() {
    MaterialTheme {
        PracticeScreen(
            state = PracticeUiState(
                config = PracticeConfig(
                    questionMode = QuestionMode.MIXED,
                    timeLimit = TimeLimitOption.NORMAL_2S,
                    inputMode = InputMode.KEYPAD
                ),
                isPracticing = true,
                isPaused = false,
                currentIndex = 6,
                currentQuestion = Question(a = 3, op = Operator.ADD, b = 5),
                phase = QuestionPhase.COUNTDOWN,
                remainingMs = 1400L,
                comboCount = 5,
                maxCombo = 5,
                voiceHint = ""
            ),
            isStarting = false,
            onToggleInputMode = {},
            onKeypadSubmit = {},
            onRestartSession = {},
            onResumeSession = {},
            onOpenAnalysis = {},
            onExit = {}
        )
    }
}

private val sampleStats = mapOf(
    "3+5" to QuestionStat("3+5", totalAttempts = 5, correctCount = 5, totalCorrectTimeMs = 4500L),
    "9-4" to QuestionStat("9-4", totalAttempts = 4, correctCount = 4, totalCorrectTimeMs = 3800L),
    "7+3" to QuestionStat("7+3", totalAttempts = 6, correctCount = 6, totalCorrectTimeMs = 4800L),
    "8-5" to QuestionStat("8-5", totalAttempts = 4, correctCount = 2, wrongCount = 2, mistakeStreakToClear = 2),
    "6+4" to QuestionStat("6+4", totalAttempts = 5, correctCount = 3, timeoutCount = 2, mistakeStreakToClear = 1),
    "2+8" to QuestionStat("2+8", totalAttempts = 3, correctCount = 3, totalCorrectTimeMs = 2400L),
    "10-7" to QuestionStat("10-7", totalAttempts = 4, correctCount = 2, wrongCount = 2, mistakeStreakToClear = 2)
)

private val sampleSessions = listOf(
    SessionRecord(
        id = 1L,
        modeLabel = "加减混合",
        timeLimitLabel = "2.0秒",
        totalCount = 20,
        correctCount = 18,
        wrongCount = 1,
        timeoutCount = 1,
        avgResponseMs = 920L,
        timestampMs = 1710000000000L
    ),
    SessionRecord(
        id = 2L,
        modeLabel = "加法专练",
        timeLimitLabel = "1.5秒",
        totalCount = 15,
        correctCount = 15,
        wrongCount = 0,
        timeoutCount = 0,
        avgResponseMs = 780L,
        timestampMs = 1710003600000L
    )
)

@Preview(name = "AnalysisScreen - Phone", showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "AnalysisScreen - Pad", showBackground = true, widthDp = 800, heightDp = 1000)
@Composable
fun AnalysisScreenPreview() {
    MaterialTheme {
        AnalysisScreen(
            statsMap = sampleStats,
            sessions = sampleSessions,
            onStartMistakePractice = {},
            onRemoveMistake = {},
            onBack = {}
        )
    }
}
