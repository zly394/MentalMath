package com.zly.mentalmath.speech

import com.zly.mentalmath.domain.VoiceAnswerConfirmation

@JsModule("vosk-browser")
private external object VoskModule : JsAny

private fun installOfflineVosk(module: JsAny): Unit =
    js("globalThis.mentalMathVosk = module")

private fun monotonicTimeMs(): Double = js("performance.now()")

private external class MentalMathBrowserSpeech : JsAny {
    fun prepareFromGesture()
    fun setPauseListener(listener: () -> Unit)
    fun hasRecordPermission(): Boolean
    fun requestRecordPermission(onResult: (Boolean) -> Unit)
    fun setVoiceMode(enabled: Boolean)
    fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit)
    fun startListening(
        onListeningReady: () -> Unit,
        onSpeechDetected: () -> Unit,
        onCandidates: (String, Boolean) -> Boolean,
        onError: (String) -> Unit
    )
    fun playCorrectSound()
    fun playWrongSound()
    fun stopAll()
    fun release()
}

actual class SpeechEngine actual constructor() {
    init {
        installOfflineVosk(VoskModule)
    }

    private val browser = MentalMathBrowserSpeech()

    init {
        SpeechLifecycle.prepareFromGesture = { browser.prepareFromGesture() }
        browser.setPauseListener { SpeechLifecycle.onPause?.invoke() }
    }

    actual fun hasRecordPermission(): Boolean = browser.hasRecordPermission()

    actual fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        browser.requestRecordPermission(onResult)
    }

    actual fun setVoiceMode(enabled: Boolean) {
        browser.setVoiceMode(enabled)
    }

    actual fun speak(text: String, onError: (String) -> Unit, onDone: () -> Unit) {
        browser.speak(text, onDone, onError)
    }

    actual fun startListening(
        expectedAnswer: Int,
        onListeningReady: () -> Unit,
        onSpeechDetected: () -> Unit,
        onRecognizedNumber: (number: Int, rawText: String) -> Unit,
        onErrorOrEmpty: (reason: String) -> Unit
    ) {
        val confirmation = VoiceAnswerConfirmation()
        browser.startListening(
            onListeningReady,
            onSpeechDetected,
            { raw, isFinal ->
                val candidates = raw.split("\n")
                val number = confirmation.observe(candidates, isFinal, monotonicTimeMs().toLong())
                if (number != null) {
                    onRecognizedNumber(number, candidates.first())
                    true
                } else {
                    false
                }
            },
            onErrorOrEmpty
        )
    }

    actual fun playCorrectSound() {
        browser.playCorrectSound()
    }

    actual fun playWrongSound() {
        browser.playWrongSound()
    }

    actual fun stopAll() {
        browser.stopAll()
    }

    actual fun release() {
        browser.release()
    }
}
