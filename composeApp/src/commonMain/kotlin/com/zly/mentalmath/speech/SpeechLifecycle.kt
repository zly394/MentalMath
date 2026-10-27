package com.zly.mentalmath.speech

object SpeechLifecycle {
    var prepareFromGesture: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
}
