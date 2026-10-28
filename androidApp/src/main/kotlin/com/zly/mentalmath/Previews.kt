package com.zly.mentalmath

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.Operator
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.Question
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.TimeLimitOption
import com.zly.mentalmath.ui.HomeScreen
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

@Preview(name = "PracticeScreen (Voice) - Phone", showBackground = true, widthDp = 390, heightDp = 844)
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

