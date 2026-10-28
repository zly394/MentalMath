package com.zly.mentalmath.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.zly.mentalmath.domain.VoiceAnswerConfirmation
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Android 端 100% 纯离线极速语音引擎（遵循第一性原理与 KISS 原则）：
 * 1. 全局常驻内存：晓晓（XiaoxiaoNeural）132 句整句离线语音包 + Vosk 声学模型 Model
 * 2. 播报期异步预建全新 Recognizer：在播报题目的 ~670ms 期间预先构建好本题专属的全新 Recognizer，
 *    播报结束瞬间 0ms 延迟直接取用，单题结束立即 close() 销毁，兼顾「0 构建延迟」与「100% 物理跨题隔离」。
 * 3. 100% 全帧连续送入解码器：绝不跳过低能量帧，完整保留中文数字清辅音声母（s/sh/q/l 等）与 Kaldi TDNN 上下文。
 */
actual class SpeechEngine actual constructor() {

    private class PcmSlice(val offset: Int, val length: Int)

    companion object {
        private const val TAG = "MentalMathSpeech"
        private const val SAMPLE_RATE = 16000
        private const val SPEECH_RMS_THRESHOLD = 380.0

        private const val NUMBER_GRAMMAR_JSON =
            """["零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "两", "[unk]"]"""

        private var appContext: Context? = null
        private var permissionRequester: ((onResult: (Boolean) -> Unit) -> Unit)? = null

        @Volatile
        private var sharedVoskModel: Model? = null

        @Volatile
        private var modelLoading = false

        @Volatile
        private var phrasePcmBlob: ByteArray? = null

        @Volatile
        private var phraseIndex: Map<String, PcmSlice> = emptyMap()

        private val initExecutor = Executors.newSingleThreadExecutor()

        private val singleFeedbackPcm: ByteArray by lazy {
            generateTonePcm(durationMs = 120, startFreq = 880f, endFreq = 1320f)
        }
        private val comboFeedbackPcm: ByteArray by lazy {
            generateComboTonePcm()
        }
        private val wrongFeedbackPcm: ByteArray by lazy {
            generateTonePcm(durationMs = 80, startFreq = 340f, endFreq = 180f)
        }

        private fun generateTonePcm(durationMs: Int, startFreq: Float, endFreq: Float): ByteArray {
            val totalSamples = (SAMPLE_RATE * durationMs) / 1000
            val bytes = ByteArray(totalSamples * 2)
            var phase = 0.0
            for (i in 0 until totalSamples) {
                val progress = i.toFloat() / totalSamples
                val currentFreq = startFreq + (endFreq - startFreq) * progress
                phase += 2.0 * Math.PI * currentFreq / SAMPLE_RATE
                val envelope = kotlin.math.sin(progress * Math.PI).toFloat().coerceIn(0f, 1f)
                val sample = (kotlin.math.sin(phase) * envelope * 7500f).toInt().coerceIn(-32767, 32767)
                bytes[i * 2] = (sample and 0xFF).toByte()
                bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        private fun generateComboTonePcm(): ByteArray {
            val durationMs = 200
            val totalSamples = (SAMPLE_RATE * durationMs) / 1000
            val bytes = ByteArray(totalSamples * 2)
            val halfSamples = totalSamples / 2
            var phase1 = 0.0
            var phase2 = 0.0
            for (i in 0 until totalSamples) {
                val sampleVal = if (i < halfSamples) {
                    val progress = i.toFloat() / halfSamples
                    phase1 += 2.0 * Math.PI * 1046.0 / SAMPLE_RATE
                    val envelope = kotlin.math.sin(progress * Math.PI).toFloat()
                    (kotlin.math.sin(phase1) * envelope * 7500f).toInt()
                } else {
                    val progress = (i - halfSamples).toFloat() / halfSamples
                    val freq = 1318.0 + (1568.0 - 1318.0) * progress
                    phase2 += 2.0 * Math.PI * freq / SAMPLE_RATE
                    val envelope = kotlin.math.sin(progress * Math.PI).toFloat()
                    (kotlin.math.sin(phase2) * envelope * 8000f).toInt()
                }
                val clamped = sampleVal.coerceIn(-32767, 32767)
                bytes[i * 2] = (clamped and 0xFF).toByte()
                bytes[i * 2 + 1] = ((clamped shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        fun bindContext(
            context: Context,
            requestPermission: (onResult: (Boolean) -> Unit) -> Unit
        ) {
            val appCtx = context.applicationContext
            appContext = appCtx
            permissionRequester = requestPermission
            preloadOfflineResources(appCtx)
        }

        private fun preloadOfflineResources(context: Context) {
            initExecutor.execute {
                if (phrasePcmBlob == null) {
                    try {
                        val t0 = System.currentTimeMillis()
                        context.assets.open("tts/phrases.pak").use { raw ->
                            DataInputStream(BufferedInputStream(raw)).use { dis ->
                                val magic = ByteArray(4)
                                dis.readFully(magic)
                                if (String(magic, Charsets.US_ASCII) == "MPAK") {
                                    val count = dis.readInt()
                                    val map = HashMap<String, PcmSlice>(count * 2)
                                    for (i in 0 until count) {
                                        val keyLen = dis.readUnsignedShort()
                                        val keyBytes = ByteArray(keyLen)
                                        dis.readFully(keyBytes)
                                        val key = String(keyBytes, Charsets.UTF_8)
                                        val offset = dis.readInt()
                                        val length = dis.readInt()
                                        map[key] = PcmSlice(offset, length)
                                    }
                                    val blob = dis.readBytes()
                                    phraseIndex = map
                                    phrasePcmBlob = blob
                                    val elapsed = System.currentTimeMillis() - t0
                                    Log.i(
                                        TAG,
                                        "Offline Xiaoxiao Neural pack loaded: ${map.size} phrases (${blob.size / 1024} KB) in ${elapsed}ms"
                                    )
                                } else {
                                    Log.e(TAG, "Invalid phrases.pak magic header")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to preload tts/phrases.pak", e)
                    }
                }

                if (sharedVoskModel == null && !modelLoading) {
                    modelLoading = true
                    try {
                        LibVosk.setLogLevel(LogLevel.WARNINGS)
                        val targetDir = ensureModelCopied(context, "model-cn")
                        sharedVoskModel = Model(targetDir.absolutePath)
                        Log.i(TAG, "Offline Vosk acoustic model ready in RAM")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load offline Vosk model", e)
                    } finally {
                        modelLoading = false
                    }
                }
            }
        }

        private fun ensureModelCopied(context: Context, assetDirName: String): File {
            val destDir = File(context.filesDir, assetDirName)
            val assetUuid = try {
                context.assets.open("$assetDirName/uuid").bufferedReader().use { it.readText().trim() }
            } catch (_: Exception) {
                "v1"
            }
            val destUuidFile = File(destDir, "uuid")
            if (destDir.exists() && destUuidFile.exists()) {
                val existingUuid = destUuidFile.readText().trim()
                if (existingUuid == assetUuid) {
                    return destDir
                }
            }
            destDir.deleteRecursively()
            copyAssetFolder(context, assetDirName, destDir)
            return destDir
        }

        private fun copyAssetFolder(context: Context, assetPath: String, destDir: File) {
            val children = context.assets.list(assetPath) ?: return
            if (children.isEmpty()) {
                destDir.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    destDir.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                destDir.mkdirs()
                for (child in children) {
                    copyAssetFolder(context, "$assetPath/$child", File(destDir, child))
                }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioExecutor = Executors.newSingleThreadExecutor()
    private val isListeningActive = AtomicBoolean(false)
    private val isSpeakingActive = AtomicBoolean(false)
    private val speakTokenGen = AtomicLong(0L)
    private val listenTokenGen = AtomicLong(0L)

    private val prebuiltLock = Any()
    private var prebuiltRecognizer: Recognizer? = null

    @Volatile
    private var activeAudioTrack: AudioTrack? = null

    init {
        appContext?.let { preloadOfflineResources(it) }
    }

    actual fun hasRecordPermission(): Boolean {
        val ctx = appContext ?: return false
        return ctx.checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual fun requestRecordPermission(onResult: (Boolean) -> Unit) {
        if (hasRecordPermission()) {
            onResult(true)
            return
        }
        val requester = permissionRequester
        if (requester != null) {
            requester(onResult)
        } else {
            onResult(false)
        }
    }

    /**
     * 在播报题目语音的 ~670ms 窗口内，后台提前预建好本题专属的全新 Recognizer，
     * 使得播报结束进入 startListening 时 0ms 即可直接开始收音。
     */
    private fun prebuildFreshRecognizerAsync() {
        audioExecutor.execute {
            val model = sharedVoskModel ?: return@execute
            synchronized(prebuiltLock) {
                if (prebuiltRecognizer == null) {
                    try {
                        prebuiltRecognizer = Recognizer(
                            model,
                            SAMPLE_RATE.toFloat(),
                            NUMBER_GRAMMAR_JSON
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to prebuild Recognizer", e)
                    }
                }
            }
        }
    }

    private fun takeOrCreateFreshRecognizer(model: Model): Recognizer {
        synchronized(prebuiltLock) {
            val existing = prebuiltRecognizer
            if (existing != null) {
                prebuiltRecognizer = null
                return existing
            }
        }
        return Recognizer(
            model,
            SAMPLE_RATE.toFloat(),
            NUMBER_GRAMMAR_JSON
        )
    }

    actual fun playWrongSound() {
        audioExecutor.execute {
            try {
                val data = wrongFeedbackPcm
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(data.size.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                val sleepMs = (data.size / 32L) + 20L
                Thread.sleep(sleepMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.w(TAG, "Wrong sound play error", e)
            }
        }
    }

    actual fun playFeedbackSound(isCombo: Boolean) {
        audioExecutor.execute {
            try {
                val data = if (isCombo) comboFeedbackPcm else singleFeedbackPcm
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(data.size.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.play()
                val sleepMs = (data.size / 32L) + 30L
                Thread.sleep(sleepMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.w(TAG, "Feedback sound play error", e)
            }
        }
    }

    actual fun setVoiceMode(enabled: Boolean) = Unit

    /**
     * 100% 离线整句神经网络女声（晓晓）零拷贝播报
     * 播报同时在后台预构建本题全新的 Recognizer，播报完毕即刻 0 等待开启麦克风
     */
    actual fun speak(text: String, onError: (String) -> Unit, onDone: () -> Unit) {
        stopAll()
        val mySpeakToken = speakTokenGen.incrementAndGet()
        isSpeakingActive.set(true)

        // 利用播报题目的 ~670ms 时间窗口，提前在后台准备好全新无残留的 Recognizer
        prebuildFreshRecognizerAsync()

        Thread {
            try {
                var waitedMs = 0
                while (phrasePcmBlob == null && waitedMs < 600 && isSpeakingActive.get() && speakTokenGen.get() == mySpeakToken) {
                    Thread.sleep(15L)
                    waitedMs += 15
                }

                val blob = phrasePcmBlob
                var slice = phraseIndex[text]
                if (slice == null) {
                    val mathKey = Regex("""[零一二三四五六七八九十]+[加减][零一二三四五六七八九十]+""").find(text)?.value
                    if (mathKey != null) slice = phraseIndex[mathKey]
                }
                if (blob == null || slice == null || slice.length <= 0 || !isSpeakingActive.get() || speakTokenGen.get() != mySpeakToken) {
                    finishSpeak(mySpeakToken, onDone)
                    return@Thread
                }

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(slice.length.coerceAtLeast(4096))
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                activeAudioTrack = track
                track.write(blob, slice.offset, slice.length)
                track.play()

                val durationMs = (slice.length / 32L).coerceIn(150L, 1500L)
                var slept = 0L
                while (slept < durationMs && isSpeakingActive.get() && speakTokenGen.get() == mySpeakToken) {
                    Thread.sleep(12L)
                    slept += 12L
                }

                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {
                }
                if (activeAudioTrack === track) {
                    activeAudioTrack = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Offline phrase speak error", e)
            } finally {
                finishSpeak(mySpeakToken, onDone)
            }
        }.start()
    }

    private fun finishSpeak(token: Long, onDone: () -> Unit) {
        if (speakTokenGen.get() == token && isSpeakingActive.compareAndSet(true, false)) {
            mainHandler.post {
                if (speakTokenGen.get() == token) {
                    onDone()
                }
            }
        }
    }

    /**
     * 0 等待取用预建的全新 Recognizer，100% 连续全帧送入解码器（完整保留声母与韵母）
     */
    actual fun startListening(
        expectedAnswer: Int,
        onListeningReady: () -> Unit,
        onSpeechDetected: () -> Unit,
        onRecognizedNumber: (number: Int, rawText: String) -> Unit,
        onErrorOrEmpty: (reason: String) -> Unit
    ) {
        isListeningActive.set(false)
        val myListenToken = listenTokenGen.incrementAndGet()

        audioExecutor.execute {
            if (listenTokenGen.get() != myListenToken) return@execute

            var waitCount = 0
            while (sharedVoskModel == null && modelLoading && waitCount < 30 && listenTokenGen.get() == myListenToken) {
                Thread.sleep(50L)
                waitCount++
            }
            val model = sharedVoskModel
            if (model == null) {
                mainHandler.post {
                    if (listenTokenGen.get() == myListenToken) {
                        onErrorOrEmpty("离线语音模型加载中，请稍候")
                    }
                }
                return@execute
            }

            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(3200)

            var recognizer: Recognizer? = null
            var audioRecord: AudioRecord? = null

            try {
                // 直接取用播报期预建好的全新 Recognizer（0ms 延迟，且 100% 物理隔离上一题）
                recognizer = takeOrCreateFreshRecognizer(model)

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    mainHandler.post {
                        if (listenTokenGen.get() == myListenToken) {
                            onErrorOrEmpty("麦克风初始化失败")
                        }
                    }
                    return@execute
                }

                if (listenTokenGen.get() != myListenToken) return@execute

                isListeningActive.set(true)
                audioRecord.startRecording()
                check(audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "麦克风未进入收音状态"
                }
                mainHandler.post {
                    if (listenTokenGen.get() == myListenToken) {
                        onListeningReady()
                    }
                }

                // 480 采样点 = 30ms 低延迟帧
                val frameShorts = ShortArray(480)
                var speechDetectedTriggered = false
                var delivered = false
                val confirmation = VoiceAnswerConfirmation()

                while (isListeningActive.get() && !delivered && listenTokenGen.get() == myListenToken) {
                    val readCount = audioRecord.read(frameShorts, 0, frameShorts.size)
                    if (readCount <= 0) continue

                    // 1. 实时计算 30ms 帧的 RMS 能量（灵敏阈值 380，捕捉开口瞬间并锁表）
                    if (!speechDetectedTriggered) {
                        var sumSq = 0.0
                        for (idx in 0 until readCount) {
                            val s = frameShorts[idx].toDouble()
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / readCount)
                        if (rms > SPEECH_RMS_THRESHOLD) {
                            speechDetectedTriggered = true
                            mainHandler.post {
                                if (listenTokenGen.get() == myListenToken) {
                                    onSpeechDetected()
                                }
                            }
                        }
                    }

                    // 2. 100% 连续全帧送入本题专属 Recognizer，完整保留清辅音声母与 TDNN 上下文
                    val isFinal = recognizer.acceptWaveForm(frameShorts, readCount)
                    val jsonStr = if (isFinal) recognizer.result else recognizer.partialResult
                    val extractedText = extractTextFromVoskJson(jsonStr)

                    // 3. Recognizer text is stronger evidence of speech than the RMS threshold alone.
                    if (extractedText.isNotBlank()) {
                        if (!speechDetectedTriggered) {
                            speechDetectedTriggered = true
                            mainHandler.post {
                                if (listenTokenGen.get() == myListenToken) {
                                    onSpeechDetected()
                                }
                            }
                        }
                        val number = confirmation.observe(
                            listOf(extractedText),
                            isFinal = isFinal,
                            nowMs = SystemClock.elapsedRealtime()
                        )
                        if (number != null) {
                            delivered = true
                            isListeningActive.set(false)
                            Log.i(TAG, "Recognized: '$extractedText' -> $number (expected=$expectedAnswer)")
                            mainHandler.post {
                                if (listenTokenGen.get() == myListenToken) {
                                    onRecognizedNumber(number, extractedText)
                                }
                            }
                            break
                        }
                    } else {
                        confirmation.observe(emptyList(), isFinal, SystemClock.elapsedRealtime())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Offline Vosk recognition error", e)
                mainHandler.post {
                    if (listenTokenGen.get() == myListenToken) {
                        onErrorOrEmpty("离线收音失败：${e.message ?: "未知错误"}")
                    }
                }
            } finally {
                if (listenTokenGen.get() == myListenToken) {
                    isListeningActive.set(false)
                }
                try {
                    audioRecord?.stop()
                } catch (_: Exception) {
                }
                try {
                    audioRecord?.release()
                } catch (_: Exception) {
                }
                try {
                    recognizer?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun extractTextFromVoskJson(json: String?): String {
        if (json.isNullOrBlank()) return ""
        val colonIdx = json.indexOf(':')
        if (colonIdx < 0) return ""
        val firstQuote = json.indexOf('"', colonIdx + 1)
        if (firstQuote < 0) return ""
        val secondQuote = json.indexOf('"', firstQuote + 1)
        if (secondQuote <= firstQuote) return ""
        return json.substring(firstQuote + 1, secondQuote).replace(" ", "").trim()
    }

    actual fun stopAll() {
        speakTokenGen.incrementAndGet()
        listenTokenGen.incrementAndGet()
        isSpeakingActive.set(false)
        isListeningActive.set(false)
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
        } catch (_: Exception) {
        }
        activeAudioTrack = null
    }

    actual fun release() {
        stopAll()
        audioExecutor.execute {
            synchronized(prebuiltLock) {
                try {
                    prebuiltRecognizer?.close()
                } catch (_: Exception) {
                }
                prebuiltRecognizer = null
            }
        }
    }
}
