package com.zly.mentalmath.speech

import com.zly.mentalmath.domain.VoiceAnswerConfirmation
import com.zly.mentalmath.domain.NumberVoiceParser
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.AudioToolbox.AudioServicesPlaySystemSound
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionModeMeasurement
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSLocale
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSTimer
import platform.Foundation.NSProcessInfo
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfFile
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.posix.memcpy
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
    private var inputTapInstalled = false
    private var speakToken: Long = 0L
    private var listenToken: Long = 0L
    private var finalizationTimer: NSTimer? = null

    init {
        ensureVoicePackLoaded()
    }

    actual fun hasRecordPermission(): Boolean {
        return SFSpeechRecognizer.authorizationStatus() ==
            SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized &&
            AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
    }

    actual fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        SFSpeechRecognizer.requestAuthorization { status ->
            val granted = status ==
                SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
            AVAudioSession.sharedInstance().requestRecordPermission { micGranted ->
                dispatch_async(dispatch_get_main_queue()) {
                    onResult(granted && micGranted)
                }
            }
        }
    }

        actual fun playFeedbackSound(isCombo: Boolean) {
        val soundId = if (isCombo) 1025u else 1057u
        AudioServicesPlaySystemSound(soundId)
    }

    actual fun setVoiceMode(enabled: Boolean) = Unit

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
        var slice = phraseIndex[text]
        if (slice == null) {
            val mathKey = Regex("""[零一二三四五六七八九十]+[加减][零一二三四五六七八九十]+""").find(text)?.value
            if (mathKey != null) slice = phraseIndex[mathKey]
        }
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

        val recognizer = speechRecognizer
        if (!hasRecordPermission()) {
            onErrorOrEmpty("未授予麦克风或语音识别权限，请切换按键模式")
            return
        }
        if (recognizer == null || !recognizer.supportsOnDeviceRecognition) {
            onErrorOrEmpty("当前设备不支持中文端侧识别，不会使用联网识别，请切换按键模式")
            return
        }
        if (!recognizer.isAvailable()) {
            onErrorOrEmpty("中文端侧识别暂不可用，请检查系统语音资源或切换按键模式")
            return
        }

        var hasDeliveredRecognition = false
        var hasTriggeredSpeechDetected = false
        var hasDeliveredReady = false
        var audioInputEnded = false
        val confirmation = VoiceAnswerConfirmation()

        val audioSession = AVAudioSession.sharedInstance()
        val sessionError = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            if (!audioSession.setCategory(
                    AVAudioSessionCategoryPlayAndRecord,
                    mode = AVAudioSessionModeMeasurement,
                    options = AVAudioSessionCategoryOptionDefaultToSpeaker,
                    error = error.ptr
                ) || !audioSession.setActive(true, error = error.ptr)) {
                error.value?.localizedDescription ?: "音频会话启动失败"
            } else null
        }
        if (sessionError != null) {
            NSLog("Offline speech audio session failed: %@", sessionError)
            onErrorOrEmpty("离线收音启动失败：$sessionError")
            return
        }

        val request = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
            requiresOnDeviceRecognition = true
            contextualStrings = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "两")
        }
        recognitionRequest = request

        val inputNode = audioEngine.inputNode
        val recordingFormat = inputNode.outputFormatForBus(0u)
        if (recordingFormat.sampleRate <= 0.0 || recordingFormat.channelCount == 0u) {
            recognitionRequest = null
            onErrorOrEmpty("麦克风音频格式不可用，请切换按键模式")
            return
        }
        inputNode.installTapOnBus(
            bus = 0u,
            bufferSize = 512u,
            format = recordingFormat
        ) { buffer, _ ->
            if (buffer != null && listenToken == myListenToken) {
                dispatch_async(dispatch_get_main_queue()) {
                    if (listenToken == myListenToken && !hasDeliveredReady) {
                        hasDeliveredReady = true
                        onListeningReady()
                    }
                }
                request.appendAudioPCMBuffer(buffer)
            }
        }
        inputTapInstalled = true

        recognitionTask = recognizer.recognitionTaskWithRequest(request) { result, error ->
            dispatch_async(dispatch_get_main_queue()) {
                if (hasDeliveredRecognition || listenToken != myListenToken) return@dispatch_async
                if (result != null) {
                    finalizationTimer?.invalidate()
                    finalizationTimer = null
                    val rawString = result.bestTranscription.formattedString
                    if (rawString.isNotBlank()) {
                        if (!hasTriggeredSpeechDetected) {
                            hasTriggeredSpeechDetected = true
                            onSpeechDetected()
                            if (listenToken != myListenToken) return@dispatch_async
                        }
                        val parsed = confirmation.observe(
                            listOf(rawString),
                            isFinal = result.isFinal(),
                            nowMs = (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
                        )
                        if (parsed != null) {
                            hasDeliveredRecognition = true
                            stopListeningInternal()
                            onRecognizedNumber(parsed, rawString)
                            return@dispatch_async
                        }
                        if (!audioInputEnded && !result.isFinal() &&
                            NumberVoiceParser.parseSpokenNumber(listOf(rawString)) != null) {
                            // Apple may report a short word only once until endAudio().
                            finalizationTimer = NSTimer.scheduledTimerWithTimeInterval(
                                interval = VoiceAnswerConfirmation.STABLE_DURATION_MS / 1000.0,
                                repeats = false
                            ) {
                                if (listenToken != myListenToken || hasDeliveredRecognition) {
                                    return@scheduledTimerWithTimeInterval
                                }
                                finalizationTimer = null
                                audioInputEnded = true
                                if (audioEngine.isRunning()) audioEngine.stop()
                                if (inputTapInstalled) {
                                    audioEngine.inputNode.removeTapOnBus(0u)
                                    inputTapInstalled = false
                                }
                                request.endAudio()
                            }
                        }
                    } else {
                        confirmation.observe(
                            emptyList(),
                            result.isFinal(),
                            (NSProcessInfo.processInfo.systemUptime * 1000).toLong()
                        )
                    }
                    if (result.isFinal() && !hasDeliveredRecognition) {
                        stopListeningInternal()
                        onErrorOrEmpty("未匹配到 0~10 数字")
                    }
                } else if (error != null && !hasDeliveredRecognition) {
                    NSLog("On-device speech recognition failed: %@", error.localizedDescription)
                    stopListeningInternal()
                    onErrorOrEmpty("端侧识别失败，请检查系统语音资源或使用按键作答")
                }
            }
        }

        audioEngine.prepare()
        val captureError = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            error.value = null
            if (!audioEngine.startAndReturnError(error.ptr)) {
                error.value?.localizedDescription ?: "麦克风启动失败"
            } else null
        }
        if (captureError != null) {
            NSLog("Offline speech capture failed: %@", captureError)
            stopListeningInternal()
            onErrorOrEmpty("麦克风启动失败：$captureError")
        }
    }

    private fun stopListeningInternal() {
        listenToken++
        finalizationTimer?.invalidate()
        finalizationTimer = null
        if (audioEngine.isRunning()) {
            audioEngine.stop()
        }
        if (inputTapInstalled) {
            audioEngine.inputNode.removeTapOnBus(0u)
            inputTapInstalled = false
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
