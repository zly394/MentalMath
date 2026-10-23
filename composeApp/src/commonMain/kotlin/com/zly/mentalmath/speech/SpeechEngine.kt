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
     * Web 在整轮语音训练中保持录放音会话，避免逐题切换；原生平台保持现有音频配置。
     * 不会打开麦克风；退出、结算或切换按键时恢复默认会话。
     */
    fun setVoiceMode(enabled: Boolean)

    /**
     * 极速播报文本（如 "3加5"），播报完成（或模拟器无 TTS 时自动兜底）回调 [onDone]。
     * Web 等待上一轮识别结束后播报，音频准备失败时通过 [onError] 提示并回退按键。
     */
    fun speak(text: String, onError: (String) -> Unit, onDone: () -> Unit)

    /**
     * 开启单题流式语音抢答监听
     *
     * @param expectedAnswer 当前标准答案（用于同音字消歧）
     * @param onListeningReady 麦克风实际开始收音后触发，用于开始答题计时
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
