package com.zly.mentalmath.ui

import com.zly.mentalmath.data.MathRepository
import com.zly.mentalmath.data.PlatformStorage
import com.zly.mentalmath.domain.QuestionGenerator
import com.zly.mentalmath.model.AnswerOutcome
import com.zly.mentalmath.model.AttemptRecord
import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.Question
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.TimeLimitOption
import com.zly.mentalmath.speech.SpeechDiagnostics
import com.zly.mentalmath.speech.SpeechEngine
import com.zly.mentalmath.speech.SpeechLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 单题所处阶段
 */
enum class QuestionPhase {
    SPEAKING,         // 正在语音播报题目（纯算式，不带“等于”）
    PREPARING_LISTENING,
    COUNTDOWN,        // 正在限时抢答读秒
    SPEECH_DETECTED,  // 已检测到孩子开口，锁表等待系统语音识别文本返回
    FEEDBACK,         // 单题正误/超时视觉反馈展示
    SESSION_SUMMARY   // 本轮全部完成，展示结算成绩单
}

/**
 * 训练页 UI 状态
 */
data class PracticeUiState(
    val config: PracticeConfig = PracticeConfig(),
    val isPracticing: Boolean = false,
    val isPaused: Boolean = false,
    val currentIndex: Int = 0,
    val currentQuestion: Question? = null,
    val phase: QuestionPhase = QuestionPhase.SPEAKING,
    val remainingMs: Long = 2000L,
    val lockedElapsedMs: Long? = null,
    val comboCount: Int = 0,
    val maxCombo: Int = 0,
    val lastAttempt: AttemptRecord? = null,
    val voiceHint: String = "",
    val roundAttempts: List<AttemptRecord> = emptyList()
) {
    val timeProgress: Float
        get() = if (config.timeLimit.millis <= 0L) {
            0f
        } else {
            (remainingMs.toFloat() / config.timeLimit.millis.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * 极速口算训练核心状态机
 */
class PracticeViewModel(
    val repository: MathRepository,
    private val speechEngine: SpeechEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var feedbackJob: Job? = null
    private var questionStartTimeMs: Long = 0L
    private var currentQuestionSettled: Boolean = false
    private var activeQuestionToken: Long = 0L
    private var sessionRequestToken = 0L
    private var pendingSessionConfig: PracticeConfig? = null

    private val _uiState = MutableStateFlow(
        repository.loadPracticeConfig().let { saved ->
            PracticeUiState(
                config = saved,
                remainingMs = saved.timeLimit.millis
            )
        }
    )
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()
    private val pauseListener: () -> Unit = { pauseSession() }

    init {
        SpeechLifecycle.onPause = pauseListener
    }

    private fun pauseSession() {
        sessionRequestToken++
        pendingSessionConfig?.let { config ->
            _uiState.value = PracticeUiState(config = config, isPracticing = true)
            currentQuestionSettled = false
        }
        if (!_uiState.value.isPracticing || _uiState.value.phase == QuestionPhase.SESSION_SUMMARY) return
        activeQuestionToken++
        timerJob?.cancel()
        feedbackJob?.cancel()
        speechEngine.release()
        _uiState.update { it.copy(isPaused = true, voiceHint = "训练已暂停，点击继续恢复音频并答题") }
    }

    fun resumeSession() {
        val state = _uiState.value
        if (!state.isPaused || !state.isPracticing) return
        _uiState.update { it.copy(isPaused = false) }
        if (pendingSessionConfig != null) {
            startSession()
            return
        }
        speechEngine.setVoiceMode(state.config.inputMode == InputMode.VOICE)
        if (currentQuestionSettled) {
            advanceOrFinish()
        } else {
            val question = state.currentQuestion ?: return
            speakQuestion(state.currentIndex, question)
        }
    }

    fun updateQuestionMode(mode: QuestionMode) {
        _uiState.update { current ->
            val updatedConfig = current.config.copy(questionMode = mode)
            repository.savePracticeConfig(updatedConfig)
            current.copy(config = updatedConfig)
        }
    }

    fun updateTimeLimit(limit: TimeLimitOption) {
        _uiState.update { current ->
            val updatedConfig = current.config.copy(timeLimit = limit)
            repository.savePracticeConfig(updatedConfig)
            current.copy(
                config = updatedConfig,
                remainingMs = limit.millis
            )
        }
    }

    fun updateInputMode(mode: InputMode) {
        val prev = _uiState.value.config.inputMode
        if (prev == mode) return

        if (mode == InputMode.VOICE && !speechEngine.hasRecordPermission()) {
            val token = activeQuestionToken
            speechEngine.requestRecordPermission { granted ->
                if (token != activeQuestionToken) return@requestRecordPermission
                val targetMode = if (granted) InputMode.VOICE else InputMode.KEYPAD
                applyInputModeSwitch(targetMode, if (!granted) "未授予麦克风权限，已保持按键模式" else "")
            }
        } else {
            applyInputModeSwitch(mode, "")
        }
    }

    private fun applyInputModeSwitch(mode: InputMode, hint: String) {
        _uiState.update { current ->
            val updatedConfig = current.config.copy(inputMode = mode)
            repository.savePracticeConfig(updatedConfig)
            current.copy(
                config = updatedConfig,
                voiceHint = hint
            )
        }
        val state = _uiState.value
        speechEngine.setVoiceMode(
            state.isPracticing && state.phase != QuestionPhase.SESSION_SUMMARY && mode == InputMode.VOICE
        )
        if (state.isPracticing && (state.phase == QuestionPhase.COUNTDOWN ||
                state.phase == QuestionPhase.PREPARING_LISTENING)) {
            val q = state.currentQuestion ?: return
            if (mode == InputMode.VOICE) {
                startVoiceListeningForQuestion(q, activeQuestionToken)
            } else {
                speechEngine.stopAll()
                if (state.phase == QuestionPhase.PREPARING_LISTENING) {
                    beginCountdown(activeQuestionToken)
                }
            }
        }
    }

    /**
     * 开始新一轮训练
     */
    fun startSession(overrideMode: QuestionMode? = null) {
        val requestToken = ++sessionRequestToken
        val currentConfig = _uiState.value.config
        val finalConfig = if (overrideMode != null) {
            currentConfig.copy(questionMode = overrideMode)
        } else {
            currentConfig
        }

        if (finalConfig.inputMode == InputMode.VOICE && !speechEngine.hasRecordPermission()) {
            pendingSessionConfig = finalConfig
            speechEngine.requestRecordPermission { granted ->
                if (requestToken != sessionRequestToken) return@requestRecordPermission
                pendingSessionConfig = null
                val actualConfig = if (granted) {
                    finalConfig
                } else {
                    pendingSessionConfig = null
                    finalConfig.copy(inputMode = InputMode.KEYPAD)
                }
                launchSessionInternal(
                    actualConfig,
                    if (!granted) "未获麦克风权限，已自动启用『听音按键』模式" else ""
                )
            }
        } else {
            launchSessionInternal(finalConfig, "")
        }
    }

    private fun launchSessionInternal(config: PracticeConfig, initialHint: String) {
        SpeechDiagnostics.record("session_start", config.inputMode.name)
        timerJob?.cancel()
        feedbackJob?.cancel()
        speechEngine.stopAll()
        speechEngine.setVoiceMode(config.inputMode == InputMode.VOICE)
        _uiState.value = PracticeUiState(
            config = config,
            isPracticing = true,
            currentIndex = 0,
            remainingMs = config.timeLimit.millis,
            voiceHint = initialHint
        )
        loadAndSpeakQuestion(index = 0, lastQuestion = null)
    }

    /**
     * 退出当前训练返回首页（彻底释放常驻麦克风硬件流）
     */
    fun stopSession() {
        sessionRequestToken++
        pendingSessionConfig = null
        SpeechDiagnostics.record("session_stop")
        activeQuestionToken++
        timerJob?.cancel()
        feedbackJob?.cancel()
        speechEngine.release()
        _uiState.update { it.copy(isPracticing = false, isPaused = false) }
    }

    /**
     * 加载第 [index] 题并极速播报纯算式（如 "六加二"，不带“等于”）
     */
    private fun loadAndSpeakQuestion(index: Int, lastQuestion: Question?) {
        val nextQ = QuestionGenerator.nextQuestion(
            config = _uiState.value.config,
            statsMap = repository.statsMap.value,
            lastQuestion = lastQuestion
        )
        speakQuestion(index, nextQ)
    }

    private fun speakQuestion(index: Int, nextQ: Question) {
        SpeechDiagnostics.record("question_start", index.toString())
        val token = ++activeQuestionToken
        timerJob?.cancel()
        speechEngine.stopAll()
        currentQuestionSettled = false
        questionStartTimeMs = 0L

        val config = _uiState.value.config
        val currentCombo = _uiState.value.comboCount
        val milestonePrefix = when {
            currentCombo > 0 && currentCombo % 5 == 0 -> "${currentCombo}连对！"
            currentCombo == 3 -> "3连对！"
            else -> ""
        }
        val spokenText = if (milestonePrefix.isNotEmpty()) {
            "$milestonePrefix ${nextQ.spokenQuestion}"
        } else {
            nextQ.spokenQuestion
        }

        _uiState.update {
            it.copy(
                currentIndex = index,
                currentQuestion = nextQ,
                phase = QuestionPhase.SPEAKING,
                remainingMs = config.timeLimit.millis,
                lockedElapsedMs = null,
                voiceHint = if (milestonePrefix.isNotEmpty()) "$milestonePrefix 正在提问：${nextQ.spokenQuestion}" else "正在提问：${nextQ.spokenQuestion}"
            )
        }

        speechEngine.speak(spokenText, onError = { reason ->
            if (_uiState.value.isPracticing && !currentQuestionSettled && token == activeQuestionToken) {
                if (SpeechLifecycle.prepareFromGesture != null) {
                    pauseSession()
                    _uiState.update { it.copy(voiceHint = "$reason，请点击继续重试，或返回首页切换按键模式") }
                } else {
                    fallbackToKeypad(token, "$reason，已切换按键模式，请看题作答")
                }
            }
        }) {
            if (!_uiState.value.isPracticing || currentQuestionSettled || token != activeQuestionToken) return@speak
            if (_uiState.value.config.inputMode == InputMode.VOICE) {
                startVoiceListeningForQuestion(nextQ, token)
            } else {
                beginCountdown(token)
            }
        }
    }

    /**
     * 按键模式播报后计时，Web 语音模式实际开始收音后计时。
     */
    private fun beginCountdown(token: Long, hint: String? = null) {
        if (token != activeQuestionToken || currentQuestionSettled) return
        SpeechDiagnostics.record("countdown_start")
        val limitMs = _uiState.value.config.timeLimit.millis
        questionStartTimeMs = PlatformStorage.currentTimeMillis()

        _uiState.update {
            it.copy(
                phase = QuestionPhase.COUNTDOWN,
                remainingMs = limitMs,
                lockedElapsedMs = null,
                voiceHint = hint ?: if (it.config.inputMode == InputMode.VOICE) {
                    "请直接喊出答案 (0~10)"
                } else {
                    "请点击数字键盘作答"
                }
            )
        }

        timerJob?.cancel()
        timerJob = scope.launch {
            while (!currentQuestionSettled && token == activeQuestionToken) {
                delay(25L)
                if (token != activeQuestionToken || currentQuestionSettled) break
                val now = PlatformStorage.currentTimeMillis()
                val state = _uiState.value

                if (state.phase == QuestionPhase.COUNTDOWN) {
                    val elapsed = (now - questionStartTimeMs).coerceAtLeast(0L)
                    val remain = (limitMs - elapsed).coerceAtLeast(0L)
                    _uiState.update { it.copy(remainingMs = remain) }
                    if (remain <= 0L) {
                        SpeechDiagnostics.record("answer_timeout")
                        settleQuestion(
                            userAnswer = null,
                            outcome = AnswerOutcome.TIMEOUT,
                            elapsedMs = limitMs
                        )
                        break
                    }
                } else if (state.phase == QuestionPhase.SPEECH_DETECTED) {
                    val lockedAt = state.lockedElapsedMs ?: limitMs
                    // 中间结果不能缩短原限时；临近截止开口仍保留识别缓冲。
                    val recognitionDeadlineMs = questionStartTimeMs + maxOf(limitMs, lockedAt + 1200L)
                    if (now >= recognitionDeadlineMs) {
                        SpeechDiagnostics.record("recognition_wait_timeout")
                        settleQuestion(
                            userAnswer = null,
                            outcome = AnswerOutcome.TIMEOUT,
                            elapsedMs = limitMs
                        )
                        break
                    }
                }
            }
        }
    }

    private fun startVoiceListeningForQuestion(question: Question, token: Long) {
        val waitingForReady = _uiState.value.phase != QuestionPhase.COUNTDOWN
        if (waitingForReady) {
            _uiState.update {
                it.copy(
                    phase = QuestionPhase.PREPARING_LISTENING,
                    voiceHint = "正在准备识别与麦克风；首次离线模型加载可能较久，准备完成后才计时"
                )
            }
            SpeechDiagnostics.record("listening_preparing")
            timerJob?.cancel()
            timerJob = scope.launch {
                delay(120_000L)
                if (token == activeQuestionToken && !currentQuestionSettled &&
                    _uiState.value.phase == QuestionPhase.PREPARING_LISTENING) {
                    SpeechDiagnostics.record("listening_start_timeout")
                    fallbackToKeypad(token, "语音识别准备超时，已切换按键模式")
                }
            }
        }
        speechEngine.startListening(
            expectedAnswer = question.answer,
            onListeningReady = {
                if (token == activeQuestionToken && !currentQuestionSettled &&
                    _uiState.value.phase == QuestionPhase.PREPARING_LISTENING &&
                    _uiState.value.config.inputMode == InputMode.VOICE) {
                    SpeechDiagnostics.record("listening_ready")
                    beginCountdown(token)
                }
            },
            onSpeechDetected = {
                if (token == activeQuestionToken && !currentQuestionSettled && _uiState.value.phase == QuestionPhase.COUNTDOWN) {
                    SpeechDiagnostics.record("voice_detected")
                    val now = PlatformStorage.currentTimeMillis()
                    val locked = (now - questionStartTimeMs)
                        .coerceIn(100L, _uiState.value.config.timeLimit.millis)
                    _uiState.update {
                        it.copy(
                            phase = QuestionPhase.SPEECH_DETECTED,
                            lockedElapsedMs = locked,
                            voiceHint = "已听见声音 (${locked / 100 / 10f}s)，识别中..."
                        )
                    }
                }
            },
            onRecognizedNumber = { number, rawText ->
                if (token == activeQuestionToken && !currentQuestionSettled) {
                    SpeechDiagnostics.record("number_parsed")
                    val elapsed = _uiState.value.lockedElapsedMs
                        ?: (PlatformStorage.currentTimeMillis() - questionStartTimeMs)
                            .coerceIn(100L, _uiState.value.config.timeLimit.millis)
                    val outcome = if (number == question.answer) {
                        AnswerOutcome.CORRECT
                    } else {
                        AnswerOutcome.WRONG
                    }
                    _uiState.update { it.copy(voiceHint = "识别：「$rawText」→ $number") }
                    settleQuestion(
                        userAnswer = number,
                        outcome = outcome,
                        elapsedMs = elapsed
                    )
                }
            },
            onErrorOrEmpty = { reason ->
                if (token == activeQuestionToken && !currentQuestionSettled) {
                    SpeechDiagnostics.record("recognition_failure")
                    if (_uiState.value.phase == QuestionPhase.PREPARING_LISTENING) {
                        fallbackToKeypad(token, "$reason，已切换按键模式")
                    } else {
                        _uiState.update {
                            it.copy(voiceHint = "$reason（可点下方按键作答）")
                        }
                    }
                }
            }
        )
    }

    private fun fallbackToKeypad(token: Long, reason: String) {
        speechEngine.stopAll()
        speechEngine.setVoiceMode(false)
        _uiState.update { it.copy(config = it.config.copy(inputMode = InputMode.KEYPAD)) }
        beginCountdown(token, reason)
    }

    /**
     * 用户通过屏幕 0~10 大数字按键提交答案
     */
    fun submitKeypadAnswer(answer: Int) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (currentQuestionSettled || state.isPaused) return
        if (state.phase != QuestionPhase.COUNTDOWN &&
            state.phase != QuestionPhase.SPEECH_DETECTED &&
            state.phase != QuestionPhase.PREPARING_LISTENING &&
            state.phase != QuestionPhase.SPEAKING
        ) {
            return
        }

        val now = PlatformStorage.currentTimeMillis()
        val elapsed = if (questionStartTimeMs > 0L) {
            (now - questionStartTimeMs).coerceIn(80L, state.config.timeLimit.millis)
        } else {
            300L
        }
        val outcome = if (answer == question.answer) {
            AnswerOutcome.CORRECT
        } else {
            AnswerOutcome.WRONG
        }
        settleQuestion(
            userAnswer = answer,
            outcome = outcome,
            elapsedMs = elapsed
        )
    }

    /**
     * 结算单题结果，记录入库，不播报带“等于”的冗长纠错，直接以视觉高亮提示后推进下一题
     */
    private fun settleQuestion(
        userAnswer: Int?,
        outcome: AnswerOutcome,
        elapsedMs: Long
    ) {
        if (currentQuestionSettled) return
        SpeechDiagnostics.record("question_settled", outcome.name)
        currentQuestionSettled = true
        activeQuestionToken++
        timerJob?.cancel()
        speechEngine.stopAll()

        val state = _uiState.value
        val question = state.currentQuestion ?: return

        val attempt = AttemptRecord(
            question = question,
            userAnswer = userAnswer,
            outcome = outcome,
            elapsedMs = elapsedMs,
            timestampMs = PlatformStorage.currentTimeMillis()
        )

        repository.recordAttempt(attempt, state.config.timeLimit.millis)

        val newCombo = if (outcome == AnswerOutcome.CORRECT) state.comboCount + 1 else 0
        val newMaxCombo = maxOf(state.maxCombo, newCombo)
        val updatedAttempts = state.roundAttempts + attempt

        _uiState.update {
            it.copy(
                phase = QuestionPhase.FEEDBACK,
                comboCount = newCombo,
                maxCombo = newMaxCombo,
                lastAttempt = attempt,
                roundAttempts = updatedAttempts
            )
        }

        feedbackJob = scope.launch {
            val feedbackDurationMs = if (outcome == AnswerOutcome.CORRECT) 500L else 950L
            delay(feedbackDurationMs)
            advanceOrFinish()
        }
    }

    private fun advanceOrFinish() {
        val state = _uiState.value
        if (!state.isPracticing || state.isPaused) return
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.config.totalQuestions) {
            speechEngine.release()
            repository.recordSession(state.config, state.roundAttempts)
            _uiState.update { it.copy(phase = QuestionPhase.SESSION_SUMMARY) }
        } else {
            loadAndSpeakQuestion(index = nextIndex, lastQuestion = state.currentQuestion)
        }
    }

    fun clear() {
        sessionRequestToken++
        activeQuestionToken++
        if (SpeechLifecycle.onPause === pauseListener) SpeechLifecycle.onPause = null
        timerJob?.cancel()
        feedbackJob?.cancel()
        speechEngine.release()
        scope.cancel()
    }
}
