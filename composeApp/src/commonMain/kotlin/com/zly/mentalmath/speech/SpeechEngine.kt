package com.zly.mentalmath.speech

/**
 * 跨平台语音播报 (TTS) 与极速语音识别 (ASR) 引擎
 */
expect class SpeechEngine() {

    /**
     * 当前是否已授予麦克风/语音识别权限
     */
    fun hasRecordPermission(): Boolean

    /**
     * 请求麦克风与语音识别权限
     */
    fun requestRecordPermission(onResult: (Boolean) -> Unit)

    /**
     * 极速播报文本（如 "3加5"），播报完成（或模拟器无 TTS 时自动兜底）回调 [onDone]
     */
    fun speak(text: String, onDone: () -> Unit)

    /**
     * 开启单题流式语音抢答监听
     *
     * @param expectedAnswer 当前标准答案（用于同音字消歧）
     * @param onListeningReady 可以开始答题计时；Web 等待实际收音，其它平台保持原有启动时序
     * @param onSpeechDetected 当检测到孩子开口发声瞬间回调（立即锁表暂停超时倒计时）
     * @param onRecognizedNumber 当流式或最终结果解析出 0~10 数字时立即回调 (数字, 原始文本)
     * @param onErrorOrEmpty 当识别结束仍未解析出有效数字或引擎不可用时回调提示信息
     */
    fun startListening(
        expectedAnswer: Int,
        onListeningReady: () -> Unit,
        onSpeechDetected: () -> Unit,
        onRecognizedNumber: (number: Int, rawText: String) -> Unit,
        onErrorOrEmpty: (reason: String) -> Unit
    )

    /**
     * 停止当前播报与麦克风监听
     */
    fun stopAll()

    /**
     * 释放底层资源
     */
    fun release()
}
