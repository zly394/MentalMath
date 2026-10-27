package com.zly.mentalmath

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import com.zly.mentalmath.data.MathRepository
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.navigation.PlatformBackHandler
import com.zly.mentalmath.speech.SpeechEngine
import com.zly.mentalmath.speech.SpeechLifecycle
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

private data class PracticeLaunch(val overrideMode: QuestionMode?)

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
    var practiceLaunch by remember { mutableStateOf<PracticeLaunch?>(null) }
    PlatformBackHandler(enabled = currentRoute != AppRoute.HOME) {
        when (currentRoute) {
            AppRoute.HOME -> Unit
            AppRoute.PRACTICE -> {
                if (practiceLaunch == null) viewModel.stopSession()
                practiceLaunch = null
                currentRoute = AppRoute.HOME
            }
            AppRoute.ANALYSIS -> currentRoute = AppRoute.HOME
        }
    }

    LaunchedEffect(currentRoute, practiceLaunch) {
        if (currentRoute == AppRoute.PRACTICE && practiceLaunch != null) {
            withFrameNanos { }
            withFrameNanos { }
            val launch = practiceLaunch ?: return@LaunchedEffect
            viewModel.startSession(overrideMode = launch.overrideMode)
            practiceLaunch = null
        }
    }

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
                                SpeechLifecycle.prepareFromGesture?.invoke()
                                practiceLaunch = PracticeLaunch(overrideMode = null)
                                currentRoute = AppRoute.PRACTICE
                            },
                            onStartMistakeReview = {
                                SpeechLifecycle.prepareFromGesture?.invoke()
                                practiceLaunch = PracticeLaunch(QuestionMode.MISTAKE_REVIEW)
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
                            isStarting = practiceLaunch != null,
                            onToggleInputMode = viewModel::updateInputMode,
                            onKeypadSubmit = viewModel::submitKeypadAnswer,
                            onRestartSession = {
                                SpeechLifecycle.prepareFromGesture?.invoke()
                                viewModel.startSession()
                            },
                            onResumeSession = {
                                SpeechLifecycle.prepareFromGesture?.invoke()
                                viewModel.resumeSession()
                            },
                            onOpenAnalysis = {
                                viewModel.stopSession()
                                currentRoute = AppRoute.ANALYSIS
                            },
                            onExit = {
                                if (practiceLaunch == null) viewModel.stopSession()
                                practiceLaunch = null
                                currentRoute = AppRoute.HOME
                            }
                        )
                    }

                    AppRoute.ANALYSIS -> {
                        AnalysisScreen(
                            statsMap = statsMap,
                            sessions = sessions,
                            onStartMistakePractice = {
                                SpeechLifecycle.prepareFromGesture?.invoke()
                                practiceLaunch = PracticeLaunch(QuestionMode.MISTAKE_REVIEW)
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
