package com.zly.mentalmath.domain

/**
 * 0 ~ 10 语音识别结果极速解析器
 *
 * 针对一年级儿童发音特点及中文 ASR 常见同音字/近音字做本地零延迟容错映射。
 */
object NumberVoiceParser {

    /**
     * 精确词表与常见同音字映射（从 10 到 0 匹配，优先识别 "10"）
     */
    private val synonymTokens: List<Pair<Int, List<String>>> = listOf(
        10 to listOf("10", "十", "拾", "石", "时", "实", "食", "识", "什", "直"),
        9 to listOf("9", "九", "玖", "久", "酒", "旧", "救", "舅", "就", "揪"),
        8 to listOf("8", "八", "捌", "吧", "巴", "发", "爸", "拔", "把", "罢", "霸"),
        7 to listOf("7", "七", "柒", "期", "欺", "漆", "戚", "妻", "栖", "气", "起", "骑"),
        6 to listOf("6", "六", "陆", "溜", "刘", "流", "留", "柳", "牛", "绿"),
        5 to listOf("5", "五", "伍", "午", "舞", "武", "捂", "雾", "无", "吴", "我", "呜"),
        4 to listOf("4", "四", "肆", "寺", "似", "死", "丝", "撕", "私", "司", "试", "世", "事", "市", "室", "是"),
        3 to listOf("3", "三", "叁", "山", "衫", "删", "珊", "闪", "伞", "散"),
        2 to listOf("2", "二", "贰", "两", "俩", "儿", "耳", "尔", "饿", "鳄", "爱", "啊"),
        1 to listOf("1", "一", "壹", "幺", "衣", "医", "依", "伊", "揖", "椅", "以", "已", "亿", "义", "艺"),
        0 to listOf("0", "零", "〇", "灵", "铃", "玲", "凌", "陵", "伶", "林", "淋", "邻", "洞")
    )

    /**
     * 从语音识别文本列表（candidates / partialResults）中提取 0~10 的答案。
     *
     * @param candidates 语音识别返回的多候选文本
     * @param expectedAnswer 当前题目的标准答案（用于处理 "是/十/四" 等边界同音歧义时的合理消歧）
     */
    fun parseSpokenNumber(candidates: List<String>, expectedAnswer: Int? = null): Int? {
        if (candidates.isEmpty()) return null

        val parsedCandidates = mutableListOf<Int>()

        for (raw in candidates) {
            val text = raw.trim()
            if (text.isEmpty()) continue

            // 1. 先检查阿拉伯数字（优先检查 "10"，再检查 "0".."9"）
            if (text.contains("10")) {
                parsedCandidates.add(10)
                continue
            }
            val singleDigitMatch = Regex("[0-9]").find(text)
            if (singleDigitMatch != null) {
                parsedCandidates.add(singleDigitMatch.value.toInt())
                continue
            }

            // 2. 处理 "等于八" / "答案是七" 等带有前缀口头禅的情况，优先取末尾有效字
            val cleaned = text
                .replace("等于", "")
                .replace("答案是", "")
                .replace("答案", "")
                .replace("得", "")
                .trim()

            val targetText = cleaned.ifEmpty { text }

            // 3. 特殊处理南方/儿童平翘舌不分：如 "十(shi)" 与 "四(si)" 与 "是(shi)"
            // 如果识别出 "是" 且期望答案正好是 10 或 4，优先匹配
            if (targetText.contains("是") && (expectedAnswer == 10 || expectedAnswer == 4)) {
                parsedCandidates.add(expectedAnswer)
                continue
            }

            // 4. 从后往前扫描字符匹配同音词表（因为孩子有时会先念题目再念答案，如 "三加五八"）
            var matchedNumber: Int? = null
            for (i in targetText.length - 1 downTo 0) {
                val charStr = targetText[i].toString()
                val hit = synonymTokens.firstOrNull { (_, words) -> charStr in words }?.first
                if (hit != null) {
                    matchedNumber = hit
                    break
                }
            }
            if (matchedNumber != null) {
                parsedCandidates.add(matchedNumber)
            }
        }

        if (parsedCandidates.isEmpty()) return null

        // 若多候选中包含与期望答案一致的同音候选，则优先采信，避免 ASR 多音字误伤
        if (expectedAnswer != null && expectedAnswer in parsedCandidates) {
            return expectedAnswer
        }
        return parsedCandidates.first()
    }
}
