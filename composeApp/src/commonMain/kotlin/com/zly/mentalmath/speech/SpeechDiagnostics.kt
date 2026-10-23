package com.zly.mentalmath.speech

object SpeechDiagnostics {
    var listener: ((event: String, detail: String) -> Unit)? = null

    fun record(event: String, detail: String = "") {
        listener?.invoke(event, detail)
    }
}
