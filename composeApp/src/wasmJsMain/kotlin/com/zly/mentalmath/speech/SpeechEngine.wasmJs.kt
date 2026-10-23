package com.zly.mentalmath.speech

import com.zly.mentalmath.domain.NumberVoiceParser

@JsModule("vosk-browser")
private external object VoskModule : JsAny

private fun installOfflineVosk(module: JsAny): Unit =
    js("globalThis.mentalMathVosk = module")

private external class MentalMathBrowserSpeech : JsAny {
    fun hasRecordPermission(): Boolean
    fun requestRecordPermission(onResult: (Boolean) -> Unit)
    fun setVoiceMode(enabled: Boolean)
    fun speak(text: String, onDone: () -> Unit, onError: (String) -> Unit)
    fun startListening(
        onListeningReady: () -> Unit,
        onSpeechDetected: () -> Unit,
        onCandidates: (String) -> Boolean,
        onError: (String) -> Unit
    )
    fun stopAll()
    fun release()
}

actual class SpeechEngine actual constructor() {
    init {
        installOfflineVosk(VoskModule)
    }

    private val browser = MentalMathBrowserSpeech()

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
        browser.startListening(
            onListeningReady,
            onSpeechDetected,
            { raw ->
                val candidates = raw.split("\n")
                val number = NumberVoiceParser.parseSpokenNumber(candidates, expectedAnswer)
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

    actual fun stopAll() {
        browser.stopAll()
    }

    actual fun release() {
        browser.release()
    }
}
