package com.zly.mentalmath.speech

import com.zly.mentalmath.domain.NumberVoiceParser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSLocale
import platform.Foundation.NSTimer
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
actual class SpeechEngine actual constructor() {

    private class PcmSlice(val offset: Int, val length: Int)

    companion object {
        private const val SAMPLE_RATE = 16000
        private var phrasePcmBlob: ByteArray? = null
        private var phraseIndex: Map<String, PcmSlice> = emptyMap()

        private fun ensureVoicePackLoaded() {
            if (phrasePcmBlob != null) return
            val path = NSBundle.mainBundle.pathForResource("phrases", ofType = "pak") ?: return
            val nsData = NSData.dataWithContentsOfFile(path) ?: return
            val totalLen = nsData.length.toInt()
            if (totalLen <= 8) return

            val allBytes = ByteArray(totalLen)
            allBytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), nsData.bytes, nsData.length)
            }

            if (allBytes[0] != 'M'.code.toByte() ||
                allBytes[1] != 'P'.code.toByte() ||
                allBytes[2] != 'A'.code.toByte() ||
                allBytes[3] != 'K'.code.toByte()
            ) {
                return
            }

            var pos = 4
            fun readUInt16(): Int {
                val v = ((allBytes[pos].toInt() and 0xFF) shl 8) or
                    (allBytes[pos + 1].toInt() and 0xFF)
                pos += 2
                return v
            }

            fun readInt32(): Int {
                val v = ((allBytes[pos].toInt() and 0xFF) shl 24) or
                    ((allBytes[pos + 1].toInt() and 0xFF) shl 16) or
                    ((allBytes[pos + 2].toInt() and 0xFF) shl 8) or
                    (allBytes[pos + 3].toInt() and 0xFF)
                pos += 4
                return v
            }

            val count = readInt32()
            val map = HashMap<String, PcmSlice>(count * 2)
            for (i in 0 until count) {
                val keyLen = readUInt16()
                val key = allBytes.decodeToString(pos, pos + keyLen)
                pos += keyLen
                val offset = readInt32()
                val length = readInt32()
                map[key] = PcmSlice(offset, length)
            }

            val pcmSection = allBytes.copyOfRange(pos, totalLen)
            phraseIndex = map
            phrasePcmBlob = pcmSection
        }

        private fun buildWavBytes(pcmBlob: ByteArray, offset: Int, length: Int): ByteArray {
            val wav = ByteArray(44 + length)
            val totalDataLen = 36 + length
            val byteRate = SAMPLE_RATE * 2

            fun writeAscii(idx: Int, s: String) {
                for (i in s.indices) wav[idx + i] = s[i].code.toByte()
            }

            fun writeLE32(idx: Int, v: Int) {
                wav[idx] = (v and 0xFF).toByte()
                wav[idx + 1] = ((v ushr 8) and 0xFF).toByte()
                wav[idx + 2] = ((v ushr 16) and 0xFF).toByte()
                wav[idx + 3] = ((v ushr 24) and 0xFF).toByte()
            }

            fun writeLE16(idx: Int, v: Int) {
                wav[idx] = (v and 0xFF).toByte()
                wav[idx + 1] = ((v ushr 8) and 0xFF).toByte()
            }

            writeAscii(0, "RIFF")
            writeLE32(4, totalDataLen)
            writeAscii(8, "WAVE")
            writeAscii(12, "fmt ")
            writeLE32(16, 16)
            writeLE16(20, 1) // PCM
            writeLE16(22, 1) // Mono
            writeLE32(24, SAMPLE_RATE)
            writeLE32(28, byteRate)
            writeLE16(32, 2) // BlockAlign
            writeLE16(34, 16) // BitsPerSample
            writeAscii(36, "data")
            writeLE32(40, length)
            pcmBlob.copyInto(wav, destinationOffset = 44, startIndex = offset, endIndex = offset + length)
            return wav
        }
    }

    private val synthesizer = AVSpeechSynthesizer()
    private val speechRecognizer = SFSpeechRecognizer(locale = NSLocale(localeIdentifier = "zh-CN"))
    private val audioEngine = AVAudioEngine()
    private var activePlayer: AVAudioPlayer? = null
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest? = null
    private var recognitionTask: SFSpeechRecognitionTask? = null
    private var speakToken: Long = 0L
    private var listenToken: Long = 0L

    init {
        ensureVoicePackLoaded()
    }

    actual fun hasRecordPermission(): Boolean {
        return SFSpeechRecognizer.authorizationStatus() ==
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
    }

    actual fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        SFSpeechRecognizer.requestAuthorization { status ->
            val granted = status ==
                SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
            AVAudioSession.sharedInstance().requestRecordPermission { micGranted ->
                onResult(granted && micGranted)
            }
        }
    }

    actual fun speak(text: String, onError: (String) -> Unit, onDone: () -> Unit) {
        stopAll()
        val myToken = ++speakToken
        ensureVoicePackLoaded()

        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null
        )
        audioSession.setActive(true, error = null)

        val blob = phrasePcmBlob
        val slice = phraseIndex[text]
        if (blob != null && slice != null && slice.length > 0) {
            val wavBytes = buildWavBytes(blob, slice.offset, slice.length)
            val nsData = wavBytes.usePinned { pinned ->
                NSData.dataWithBytes(pinned.addressOf(0), wavBytes.size.toULong())
            }
            val player = AVAudioPlayer(data = nsData, error = null)
            activePlayer = player
            player.prepareToPlay()
            player.play()

            val durationSec = (slice.length.toDouble() / 32000.0).coerceIn(0.2, 1.5)
            NSTimer.scheduledTimerWithTimeInterval(
                interval = durationSec,
                repeats = false
            ) {
                if (speakToken == myToken) {
                    activePlayer?.stop()
                    activePlayer = null
                    onDone()
                }
            }
            return
        }

        // Fallback to offline AVSpeechSynthesizer if phrases.pak is missing
        val utterance = AVSpeechUtterance(string = text).apply {
            voice = AVSpeechSynthesisVoice.voiceWithLanguage("zh-CN")
            rate = 0.54f
        }
        synthesizer.speakUtterance(utterance)
        NSTimer.scheduledTimerWithTimeInterval(
            interval = 0.65,
            repeats = false
        ) {
            if (speakToken == myToken) {
                onDone()
            }
        }
    }

    actual fun startListening(
        expectedAnswer: Int,
        onListeningReady: () -> Unit,
        onSpeechDetected: () -> Unit,
        onRecognizedNumber: (number: Int, rawText: String) -> Unit,
        onErrorOrEmpty: (reason: String) -> Unit
    ) {
        stopListeningInternal()
        val myListenToken = ++listenToken
        onListeningReady()

        val recognizer = speechRecognizer
        if (recognizer == null || !recognizer.isAvailable()) {
            onErrorOrEmpty("当前设备离线语音识别不可用，请切换『听音按键』模式")
            return
        }

        var hasDeliveredRecognition = false
        var hasTriggeredSpeechDetected = false

        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeMeasurement,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null
        )
        audioSession.setActive(true, error = null)

        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            requiresOnDeviceRecognition = true
            contextualStrings = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "两")
        }
        recognitionRequest = request

        val inputNode = audioEngine.inputNode
        val recordingFormat = inputNode.outputFormatForBus(0u)
        inputNode.removeTapOnBus(0u)
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 512u,
            format = recordingFormat
        ) { buffer, _ ->
            if (buffer != null && listenToken == myListenToken) {
                request.appendAudioPCMBuffer(buffer)
            }
        }

        audioEngine.prepare()
        audioEngine.startAndReturnError(null)

        recognitionTask = recognizer.recognitionTaskWithRequest(request) { result, error ->
            if (hasDeliveredRecognition || listenToken != myListenToken) return@recognitionTaskWithRequest
            if (result != null) {
                val rawString = result.bestTranscription.formattedString
                if (rawString.isNotBlank()) {
                    if (!hasTriggeredSpeechDetected) {
                        hasTriggeredSpeechDetected = true
                        onSpeechDetected()
                    }
                    val parsed = NumberVoiceParser.parseSpokenNumber(
                        listOf(rawString),
                        expectedAnswer
                    )
                    if (parsed != null) {
                        hasDeliveredRecognition = true
                        stopListeningInternal()
                        onRecognizedNumber(parsed, rawString)
                        return@recognitionTaskWithRequest
                    }
                }
                if (result.isFinal() && !hasDeliveredRecognition) {
                    stopListeningInternal()
                    onErrorOrEmpty("未匹配到 0~10 数字")
                }
            } else if (error != null && !hasDeliveredRecognition) {
                stopListeningInternal()
                onErrorOrEmpty("未听清答案")
            }
        }
    }

    private fun stopListeningInternal() {
        listenToken++
        if (audioEngine.isRunning()) {
            audioEngine.stop()
            audioEngine.inputNode.removeTapOnBus(0u)
        }
        recognitionRequest?.endAudio()
        recognitionRequest = null
        recognitionTask?.cancel()
        recognitionTask = null
    }

    actual fun stopAll() {
        speakToken++
        activePlayer?.stop()
        activePlayer = null
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        stopListeningInternal()
    }

    actual fun release() {
        stopAll()
    }
}
