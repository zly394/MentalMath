package com.zly.mentalmath.domain

/**
 * Partial hypotheses must be observed repeatedly, not merely left on a timer.
 */
class VoiceAnswerConfirmation {
    companion object {
        const val STABLE_DURATION_MS = 180L
        private const val MAX_OBSERVATION_GAP_MS = 300L
    }

    private var candidate: Int? = null
    private var candidateSinceMs = 0L
    private var lastObservationMs = 0L
    private var delivered = false

    fun observe(candidates: List<String>, isFinal: Boolean, nowMs: Long): Int? {
        if (delivered) return null
        val number = NumberVoiceParser.parseSpokenNumber(candidates)
        if (number == null) {
            candidate = null
            return null
        }
        if (isFinal) {
            delivered = true
            return number
        }
        if (number != candidate || nowMs < lastObservationMs ||
            nowMs - lastObservationMs > MAX_OBSERVATION_GAP_MS) {
            candidate = number
            candidateSinceMs = nowMs
        }
        lastObservationMs = nowMs
        return if (nowMs - candidateSinceMs >= STABLE_DURATION_MS) {
            delivered = true
            number
        } else {
            null
        }
    }
}
