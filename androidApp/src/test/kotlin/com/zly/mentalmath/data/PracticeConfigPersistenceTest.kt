package com.zly.mentalmath.data

import com.zly.mentalmath.model.InputMode
import com.zly.mentalmath.model.PracticeConfig
import com.zly.mentalmath.model.QuestionMode
import com.zly.mentalmath.model.TimeLimitOption
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PracticeConfigPersistenceTest {

    @BeforeTest
    fun setup() {
        PlatformStorage.clearForTesting()
    }

    @Test
    fun defaultPracticeConfigIsReturnedWhenEmpty() {
        val repository = MathRepository()
        val config = repository.loadPracticeConfig()
        assertEquals(PracticeConfig(), config)
    }

    @Test
    fun savedConfigIsPersistedAndRestoredAcrossInstances() {
        val repo1 = MathRepository()
        val customConfig = PracticeConfig(
            questionMode = QuestionMode.SUBTRACTION,
            timeLimit = TimeLimitOption.FAST_1S,
            inputMode = InputMode.KEYPAD
        )
        repo1.savePracticeConfig(customConfig)

        // 模拟重新进入应用（全新实例）
        val repo2 = MathRepository()
        val restored = repo2.loadPracticeConfig()
        assertEquals(QuestionMode.SUBTRACTION, restored.questionMode)
        assertEquals(TimeLimitOption.FAST_1S, restored.timeLimit)
        assertEquals(InputMode.KEYPAD, restored.inputMode)
    }

    @Test
    fun malformedOrCorruptedStorageGracefullyFallbacks() {
        PlatformStorage.putString("practice_config_v1", "INVALID_MODE|NOT_A_TIME|INVALID_INPUT")
        val repository = MathRepository()
        val fallback = repository.loadPracticeConfig()
        assertEquals(QuestionMode.MIXED, fallback.questionMode)
        assertEquals(TimeLimitOption.NORMAL_2S, fallback.timeLimit)
    }
}
