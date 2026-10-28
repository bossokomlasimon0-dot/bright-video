package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.AiConfig
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AiConfigRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val configDoc = firestore.collection("app_config").document("ai_settings")

    fun isUserAdmin(): Boolean {
        val email = auth.currentUser?.email
        return email == "bossokomlasimon0@gmail.com"
    }

    /**
     * Observes the global AI settings in real-time.
     * All authenticated users can observe this, allowing them to use AI features
     * seamlessly without managing an API key themselves.
     */
    fun observeAiConfig(): Flow<AiConfig> {
        return configDoc.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(AiConfig::class.java) ?: AiConfig()
                } else {
                    AiConfig()
                }
            }
            .catch { e ->
                Log.w(TAG, "Error observing AI config: ${e.message}")
                if (e is Exception) {
                    handleFirestoreError(e, OperationType.GET, "app_config/ai_settings")
                }
                emit(AiConfig())
            }
    }

    /**
     * Fetch current AI configuration once.
     */
    suspend fun getAiConfig(): AiConfig {
        return try {
            val snapshot = configDoc.get().await()
            if (snapshot.exists()) {
                snapshot.toObject(AiConfig::class.java) ?: AiConfig()
            } else {
                AiConfig()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get AI config: ${e.message}")
            AiConfig()
        }
    }

    /**
     * Admin-only: Save or update the global Gemini API Key and AI configurations.
     */
    suspend fun saveAiConfig(
        apiKey: String,
        defaultModel: String = "gemini-1.5-flash",
        isAiEnabled: Boolean = true
    ): Result<Unit> {
        if (!isUserAdmin()) {
            return Result.failure(IllegalAccessException("Seul l'administrateur peut modifier la clé API."))
        }

        return try {
            val trimmedKey = apiKey.trim()
            val data = hashMapOf<String, Any>(
                "geminiApiKey" to trimmedKey,
                "defaultModel" to defaultModel,
                "isAiEnabled" to isAiEnabled,
                "availableModels" to listOf(
                    "gemini-1.5-flash",
                    "gemini-1.5-pro",
                    "gemini-2.0-flash",
                    "gemini-2.5-flash"
                ),
                "updatedAt" to Timestamp.now(),
                "updatedBy" to (auth.currentUser?.email ?: "admin")
            )
            configDoc.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "app_config/ai_settings")
            Result.failure(e)
        }
    }

    /**
     * Admin-only: Remove the global Gemini API key.
     */
    suspend fun removeApiKey(): Result<Unit> {
        if (!isUserAdmin()) {
            return Result.failure(IllegalAccessException("Seul l'administrateur peut supprimer la clé API."))
        }

        return try {
            val data = hashMapOf<String, Any>(
                "geminiApiKey" to "",
                "updatedAt" to Timestamp.now(),
                "updatedBy" to (auth.currentUser?.email ?: "admin")
            )
            configDoc.set(data, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "app_config/ai_settings")
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "AiConfigRepository"
    }
}
