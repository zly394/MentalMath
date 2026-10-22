package com.zly.mentalmath.speech

import com.zly.mentalmath.domain.NumberVoiceParser

private external class MentalMathBrowserSpeech : JsAny {
    fun hasRecordPermission(): Boolean
    fun requestRecordPermission(onResult: (Boolean) -> Unit)
    fun speak(text: String, onDone: () -> Unit)
    fun startListening(
        onSpeechDetected: () -> Unit,
        onCandidates: (String) -> Boolean,
        onError: (String) -> Unit
    )
    fun stopAll()
}

actual class SpeechEngine actual constructor() {
    private val browser = MentalMathBrowserSpeech()

    actual fun hasRecordPermission(): Boolean = browser.hasRecordPermission()

    actual fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        browser.requestRecordPermission(onResult)
    }

    actual fun speak(text: String, onDone: () -> Unit) {
        browser.speak(text, onDone)
    }

    actual fun startListening(
        expectedAnswer: Int,
        onSpeechDetected: () -> Unit,
        onRecognizedNumber: (number: Int, rawText: String) -> Unit,
        onErrorOrEmpty: (reason: String) -> Unit
    ) {
        browser.startListening(
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
        browser.stopAll()
    }
}
