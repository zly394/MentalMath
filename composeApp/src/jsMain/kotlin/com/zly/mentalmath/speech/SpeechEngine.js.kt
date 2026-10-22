package com.zly.mentalmath.speech

import com.zly.mentalmath.domain.NumberVoiceParser
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.DataView
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/**
 * Web PWA 端语音引擎：
 * 1. 出题播报：直接加载与 Android/iOS 相同的晓晓 132 句离线语音包 `phrases.pak`，
 *    通过 Web Audio API (AudioContext + AudioBufferSourceNode) 零延迟播放真人女声。
 * 2. 语音识别：使用浏览器 SpeechRecognition / webkitSpeechRecognition 实时识别 0~10 中文数字。
 */
actual class SpeechEngine actual constructor() {

    private class PcmSlice(val offset: Int, val length: Int)

    companion object {
        private const val SAMPLE_RATE = 16000
        private var pcmDataOffset: Int = 0
        private var pakBuffer: ArrayBuffer? = null
        private var phraseIndex: Map<String, PcmSlice> = emptyMap()
        private var loadingStarted = false
        private var sharedAudioCtx: dynamic = null

        private fun getOrCreateAudioContext(): dynamic {
            if (sharedAudioCtx == null) {
                sharedAudioCtx = js("new (window.AudioContext || window.webkitAudioContext)()")
            }
            if (sharedAudioCtx.state == "suspended") {
                sharedAudioCtx.resume()
            }
            return sharedAudioCtx
        }

        fun preloadVoicePack() {
            if (loadingStarted) return
            loadingStarted = true
            window.fetch("phrases.pak").then { response ->
                if (response.ok) {
                    response.arrayBuffer().then { buffer ->
                        if (buffer.byteLength > 8) {
                            parseMpakBuffer(buffer)
                        }
                    }
                }
            }.catch { _ ->
                // Fallback to speechSynthesis if offline pack fails to load
            }
        }

        private fun parseMpakBuffer(buffer: ArrayBuffer) {
            val view = DataView(buffer)
            val m0 = view.getUint8(0).toInt().toChar()
            val m1 = view.getUint8(1).toInt().toChar()
            val m2 = view.getUint8(2).toInt().toChar()
            val m3 = view.getUint8(3).toInt().toChar()
            if (m0 != 'M' || m1 != 'P' || m2 != 'A' || m3 != 'K') return

            val count = view.getInt32(4, false)
            var pos = 8
            val map = HashMap<String, PcmSlice>(count * 2)

            for (i in 0 until count) {
                val keyLen = view.getUint16(pos, false).toInt()
                pos += 2
                val keyBytes = ByteArray(keyLen) { b -> view.getInt8(pos + b) }
                val key = keyBytes.decodeToString()
                pos += keyLen
                val offset = view.getInt32(pos, false)
                pos += 4
                val length = view.getInt32(pos, false)
                pos += 4
                map[key] = PcmSlice(offset, length)
            }

            pcmDataOffset = pos
            phraseIndex = map
            pakBuffer = buffer
        }
    }

    private var speakToken: Long = 0L
    private var listenToken: Long = 0L
    private var activeSourceNode: dynamic = null
    private var activeRecognition: dynamic = null

    init {
        preloadVoicePack()
    }

    actual fun hasRecordPermission(): Boolean = true

    actual fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        try {
            getOrCreateAudioContext()
        } catch (_: Throwable) {
        }
        onResult(true)
    }

    actual fun speak(text: String, onDone: () -> Unit) {
        stopAll()
        val myToken = ++speakToken

        val buffer = pakBuffer
        val slice = phraseIndex[text]
        if (buffer != null && slice != null && slice.length > 0) {
            try {
                val audioCtx = getOrCreateAudioContext()
                val sampleCount = slice.length / 2
                val audioBuffer = audioCtx.createBuffer(1, sampleCount, SAMPLE_RATE)
                val channelData = audioBuffer.getChannelData(0)
                val view = DataView(buffer, pcmDataOffset + slice.offset, slice.length)

                for (i in 0 until sampleCount) {
                    val s16 = view.getInt16(i * 2, true).toInt()
                    channelData[i] = s16 / 32768.0f
                }

                val source = audioCtx.createBufferSource()
                source.buffer = audioBuffer
                source.connect(audioCtx.destination)
                activeSourceNode = source

                source.onended = {
                    if (speakToken == myToken) {
                        activeSourceNode = null
                        onDone()
                    }
                }
                source.start(0)
                return
            } catch (_: Throwable) {
                // Fall through to Web SpeechSynthesis
            }
        }

        // Fallback: Web SpeechSynthesis API
        try {
            val synth = window.asDynamic().speechSynthesis
            if (synth != null) {
                synth.cancel()
                val utter = js("new SpeechSynthesisUtterance(text)")
                utter.lang = "zh-CN"
                utter.rate = 1.15
                utter.onend = {
                    if (speakToken == myToken) onDone()
                }
                utter.onerror = {
                    if (speakToken == myToken) onDone()
                }
                synth.speak(utter)
                return
            }
        } catch (_: Throwable) {
        }

        window.setTimeout({
            if (speakToken == myToken) onDone()
        }, 500)
    }

    actual fun startListening(
        expectedAnswer: Int,
        onSpeechDetected: () -> Unit,
        onRecognizedNumber: (number: Int, rawText: String) -> Unit,
        onErrorOrEmpty: (reason: String) -> Unit
    ) {
        stopListeningInternal()
        val myListenToken = ++listenToken

        val speechRecCtor = js("window.SpeechRecognition || window.webkitSpeechRecognition")
        if (speechRecCtor == null) {
            onErrorOrEmpty("当前浏览器不支持语音识别，请切换『听音按键』模式")
            return
        }

        try {
            val recognition = js("new (window.SpeechRecognition || window.webkitSpeechRecognition)()")
            activeRecognition = recognition
            recognition.lang = "zh-CN"
            recognition.continuous = false
            recognition.interimResults = true
            recognition.maxAlternatives = 5

            var speechDetectedTriggered = false
            var delivered = false

            recognition.onspeechstart = {
                if (listenToken == myListenToken && !speechDetectedTriggered && !delivered) {
                    speechDetectedTriggered = true
                    onSpeechDetected()
                }
            }

            recognition.onresult = { event: dynamic ->
                if (listenToken == myListenToken && !delivered) {
                    val candidates = mutableListOf<String>()
                    val results = event.results
                    val len = results.length as Int
                    for (i in 0 until len) {
                        val res = results[i]
                        val altLen = res.length as Int
                        for (j in 0 until altLen) {
                            val transcript = (res[j].transcript as? String)?.trim()
                            if (!transcript.isNullOrEmpty()) {
                                candidates.add(transcript)
                            }
                        }
                    }

                    if (candidates.isNotEmpty()) {
                        if (!speechDetectedTriggered) {
                            speechDetectedTriggered = true
                            onSpeechDetected()
                        }
                        val number = NumberVoiceParser.parseSpokenNumber(candidates, expectedAnswer)
                        if (number != null) {
                            delivered = true
                            stopListeningInternal()
                            onRecognizedNumber(number, candidates.first())
                        }
                    }
                }
            }

            recognition.onerror = { _: dynamic ->
                if (listenToken == myListenToken && !delivered) {
                    onErrorOrEmpty("未听清答案")
                }
            }

            recognition.start()
        } catch (_: Throwable) {
            onErrorOrEmpty("语音启动失败，请使用按键模式")
        }
    }

    private fun stopListeningInternal() {
        listenToken++
        val rec = activeRecognition
        activeRecognition = null
        if (rec != null) {
            try {
                rec.abort()
            } catch (_: Throwable) {
            }
        }
    }

    actual fun stopAll() {
        speakToken++
        val src = activeSourceNode
        activeSourceNode = null
        if (src != null) {
            try {
                src.stop()
            } catch (_: Throwable) {
            }
        }
        try {
            window.asDynamic().speechSynthesis?.cancel()
        } catch (_: Throwable) {
        }
        stopListeningInternal()
    }

    actual fun release() {
        stopAll()
    }
}
