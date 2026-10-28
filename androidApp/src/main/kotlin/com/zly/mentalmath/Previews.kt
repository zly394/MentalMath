package com.zly.mentalmath

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.TimeLimitOption
import com.zly.mentalmath.ui.HomeScreen

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
