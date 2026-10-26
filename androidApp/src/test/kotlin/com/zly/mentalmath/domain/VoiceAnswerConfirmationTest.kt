package com.zly.mentalmath.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceAnswerConfirmationTest {
    @Test
    fun acceptsOnlyExplicitNumbersAndOptionalAnswerPrefix() {
        val spoken = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        for (number in 0..10) {
            assertEquals(number, NumberVoiceParser.parseSpokenNumber(listOf(spoken[number])))
            assertEquals(number, NumberVoiceParser.parseSpokenNumber(listOf(number.toString())))
        }
        assertEquals(2, NumberVoiceParser.parseSpokenNumber(listOf("两")))
        assertEquals(0, NumberVoiceParser.parseSpokenNumber(listOf("〇")))
        assertEquals(8, NumberVoiceParser.parseSpokenNumber(listOf(" 答案是八。 ")))
        assertEquals(10, NumberVoiceParser.parseSpokenNumber(listOf("等于 十！")))
    }

    @Test
    fun rejectsNoiseHomophonesIncidentalDigitsAndOutOfRangeNumbers() {
        for (text in listOf(
            "", "[unk]", "啊", "我", "是", "吧", "石", "八[unk]", "三加五八",
            "十一", "18", "100", "-1", "01", "8.5", "八九", "今天八点", "第8题"
        )) {
            assertNull(NumberVoiceParser.parseSpokenNumber(listOf(text)), text)
        }
        assertNull(NumberVoiceParser.parseSpokenNumber(emptyList()))
        assertNull(NumberVoiceParser.parseSpokenNumber(listOf("杂音", "八")))
        assertEquals(4, NumberVoiceParser.parseSpokenNumber(listOf("四", "十")))
    }

    @Test
    fun transientPartialDoesNotCommitAndRepeatedCandidateNeeds180Ms() {
        val confirmation = VoiceAnswerConfirmation()
        assertNull(confirmation.observe(listOf("八"), false, 0))
        assertNull(confirmation.observe(listOf("八"), false, 90))
        assertNull(confirmation.observe(listOf("八"), false, 179))
        assertEquals(8, confirmation.observe(listOf("八"), false, 180))
        assertNull(confirmation.observe(listOf("八"), true, 200))
    }

    @Test
    fun changedAndInvalidHypothesesResetStability() {
        val confirmation = VoiceAnswerConfirmation()
        assertNull(confirmation.observe(listOf("四"), false, 0))
        assertNull(confirmation.observe(listOf("十"), false, 170))
        assertNull(confirmation.observe(listOf("十"), false, 300))
        assertNull(confirmation.observe(emptyList(), false, 310))
        assertNull(confirmation.observe(listOf("十"), false, 350))
        assertNull(confirmation.observe(listOf("啊"), false, 400))
        assertNull(confirmation.observe(listOf("十"), false, 500))
        assertEquals(10, confirmation.observe(listOf("十"), false, 680))
    }

    @Test
    fun staleOrBackwardsObservationsCannotConfirmAnOldCandidate() {
        val confirmation = VoiceAnswerConfirmation()
        assertNull(confirmation.observe(listOf("八"), false, 1000))
        assertNull(confirmation.observe(listOf("八"), false, 1400))
        assertNull(confirmation.observe(listOf("八"), false, 1390))
        assertNull(confirmation.observe(listOf("八"), false, 1500))
        assertEquals(8, confirmation.observe(listOf("八"), false, 1570))
    }

    @Test
    fun explicitFinalCommitsImmediatelyEvenWhenItCorrectsPartial() {
        val confirmation = VoiceAnswerConfirmation()
        assertNull(confirmation.observe(listOf("四"), false, 0))
        assertEquals(10, confirmation.observe(listOf("十"), true, 20))
        assertNull(confirmation.observe(listOf("四"), true, 30))
    }

    @Test
    fun rejectedFinalDoesNotPreventRetryAndQuestionsAreIndependent() {
        val first = VoiceAnswerConfirmation()
        assertNull(first.observe(listOf("[unk]"), true, 0))
        assertEquals(8, first.observe(listOf("八"), true, 30))
        val second = VoiceAnswerConfirmation()
        assertNull(second.observe(listOf("八"), false, 1000))
        assertEquals(8, second.observe(listOf("八"), true, 1020))
    }
}
