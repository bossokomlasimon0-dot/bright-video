package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

@Keep
data class AiConfig(
    @DocumentId
    val id: String = "ai_settings",
    val geminiApiKey: String = "",
    val defaultModel: String = "gemini-1.5-flash",
    val availableModels: List<String> = listOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.0-flash",
        "gemini-2.5-flash"
    ),
    val isAiEnabled: Boolean = true,
    val updatedAt: Timestamp? = null,
    val updatedBy: String = ""
) {
    fun hasValidApiKey(): Boolean = geminiApiKey.isNotBlank() && geminiApiKey.trim().length >= 10

    fun maskedApiKey(): String {
        val trimmed = geminiApiKey.trim()
        if (trimmed.length <= 8) return "••••••••"
        return "${trimmed.take(6)}••••••••${trimmed.takeLast(4)}"
    }
}
