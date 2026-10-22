package com.zly.mentalmath

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.zly.mentalmath.data.MathRepository
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.speech.SpeechEngine
import com.zly.mentalmath.ui.AnalysisScreen
import com.zly.mentalmath.ui.HomeScreen
import com.zly.mentalmath.ui.PracticeScreen
import com.zly.mentalmath.ui.PracticeViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview

private enum class AppRoute {
    HOME,
    PRACTICE,
    ANALYSIS
}

private fun Typography.withFontFamily(fontFamily: FontFamily): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)

@Composable
@Preview
fun App(customFontFamily: FontFamily? = null) {
    val repository = remember { MathRepository() }
    val speechEngine = remember { SpeechEngine() }
    val viewModel = remember { PracticeViewModel(repository, speechEngine) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clear()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val statsMap by repository.statsMap.collectAsState()
    val sessions by repository.sessions.collectAsState()

    var currentRoute by remember { mutableStateOf(AppRoute.HOME) }
    val baseTypography = MaterialTheme.typography
    val appTypography = remember(customFontFamily, baseTypography) {
        if (customFontFamily != null) baseTypography.withFontFamily(customFontFamily) else baseTypography
    }

    MaterialTheme(typography = appTypography) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentRoute) {
                    AppRoute.HOME -> {
                        HomeScreen(
                            config = uiState.config,
                            statsMap = statsMap,
                            sessions = sessions,
                            onSelectQuestionMode = viewModel::updateQuestionMode,
                            onSelectTimeLimit = viewModel::updateTimeLimit,
                            onSelectInputMode = viewModel::updateInputMode,
                            onStartPractice = {
                                viewModel.startSession()
                                currentRoute = AppRoute.PRACTICE
                            },
                            onStartMistakeReview = {
                                viewModel.startSession(overrideMode = QuestionMode.MISTAKE_REVIEW)
                                currentRoute = AppRoute.PRACTICE
                            },
                            onOpenAnalysis = {
                                currentRoute = AppRoute.ANALYSIS
                            }
                        )
                    }

                    AppRoute.PRACTICE -> {
                        PracticeScreen(
                            state = uiState,
                            onToggleInputMode = viewModel::updateInputMode,
                            onKeypadSubmit = viewModel::submitKeypadAnswer,
                            onRestartSession = {
                                viewModel.startSession()
                            },
                            onOpenAnalysis = {
                                viewModel.stopSession()
                                currentRoute = AppRoute.ANALYSIS
                            },
                            onExit = {
                                viewModel.stopSession()
                                currentRoute = AppRoute.HOME
                            }
                        )
                    }

                    AppRoute.ANALYSIS -> {
                        AnalysisScreen(
                            statsMap = statsMap,
                            sessions = sessions,
                            onStartMistakePractice = {
                                viewModel.startSession(overrideMode = QuestionMode.MISTAKE_REVIEW)
                                currentRoute = AppRoute.PRACTICE
                            },
                            onRemoveMistake = repository::removeFromMistakeBook,
                            onBack = {
                                currentRoute = AppRoute.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}
