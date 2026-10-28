package com.example

import com.example.data.model.AiConfig
import com.example.data.model.AiModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {

    @Test
    fun testAiConfigValidationAndMasking() {
        val emptyConfig = AiConfig()
        assertFalse(emptyConfig.hasValidApiKey())

        val validConfig = AiConfig(
            geminiApiKey = "AIzaSySecretApiKey123456",
            defaultModel = "gemini-1.5-flash",
            isAiEnabled = true
        )
        assertTrue(validConfig.hasValidApiKey())
        assertEquals("AIzaSy••••••••3456", validConfig.maskedApiKey())
    }

    @Test
    fun testAiModelsListAndLookup() {
        assertEquals(4, AiModels.ALL.size)
        val flashModel = AiModels.getById("gemini-1.5-flash")
        assertEquals("Gemini 1.5 Flash", flashModel.displayName)

        val proModel = AiModels.getById("gemini-1.5-pro")
        assertEquals("Gemini 1.5 Pro", proModel.displayName)

        // Default fallback for unknown model ID
        val fallback = AiModels.getById("unknown-model-xyz")
        assertEquals("gemini-1.5-flash", fallback.id)
    }
}
