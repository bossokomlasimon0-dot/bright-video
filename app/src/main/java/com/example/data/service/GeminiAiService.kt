package com.example.data.service

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiAiService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    /**
     * General content generation via Gemini REST API.
     */
    suspend fun generateContent(
        apiKey: String,
        model: String,
        prompt: String,
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Aucune clé API configurée. Veuillez contacter l'administrateur.")
            )
        }

        try {
            val cleanModel = model.trim().ifEmpty { "gemini-1.5-flash" }
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=${apiKey.trim()}"

            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val textPart = JSONObject().apply {
                                val fullText = if (!systemInstruction.isNullOrBlank()) {
                                    "Instructions: $systemInstruction\n\nContenu/Demande: $prompt"
                                } else {
                                    prompt
                                }
                                put("text", fullText)
                            }
                            put(textPart)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)

                val generationConfig = JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 2048)
                }
                put("generationConfig", generationConfig)
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(responseBody)
                Log.e(TAG, "Gemini API error (${response.code}): $errorMsg")
                return@withContext Result.failure(Exception("Erreur Gemini (${response.code}) : $errorMsg"))
            }

            val resultText = parseResponseText(responseBody)
            Result.success(resultText)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to call Gemini API: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Summarize a publication using the user-selected Gemini model.
     */
    suspend fun summarizePublication(
        apiKey: String,
        model: String,
        title: String,
        category: String,
        type: String,
        description: String,
        content: String
    ): Result<String> {
        val prompt = buildString {
            append("Analyse et synthétise ce contenu pour les abonnés de Bright Video.\n\n")
            append("Titre : $title\n")
            append("Type : $type | Catégorie : $category\n")
            if (description.isNotBlank()) append("Description : $description\n")
            if (content.isNotBlank()) append("Contenu/Détails : $content\n\n")
            append("Consignes : Fournis un résumé captivant en français, structure avec 3 à 5 points clés essentiels, et conclus par une courte recommandation.")
        }
        return generateContent(apiKey, model, prompt)
    }

    /**
     * Generate revision quiz or comprehension questions for study/tutorials.
     */
    suspend fun generateQuiz(
        apiKey: String,
        model: String,
        title: String,
        content: String
    ): Result<String> {
        val prompt = buildString {
            append("Génère un mini-quiz interactif de 3 questions de compréhension à choix multiples (QCM) sur le sujet suivant :\n\n")
            append("Sujet : $title\n")
            if (content.isNotBlank()) append("Détails : $content\n\n")
            append("Pour chaque question, propose 3 options (A, B, C) et indique la bonne réponse avec une brève explication pédagogique.")
        }
        return generateContent(apiKey, model, prompt)
    }

    /**
     * Suggest description and tags for creators in PublishScreen.
     */
    suspend fun generateDescriptionAndTags(
        apiKey: String,
        model: String,
        title: String,
        category: String,
        type: String
    ): Result<Pair<String, List<String>>> {
        val prompt = buildString {
            append("Tu es le conseiller éditorial de Bright Video. Un créateur souhaite publier le contenu suivant :\n")
            append("Titre : $title\n")
            append("Catégorie : $category\n")
            append("Type : $type\n\n")
            append("Rédige en français :\n")
            append("1. Une description attrayante, claire et professionnelle (3 à 5 phrases).\n")
            append("2. Une liste de 5 tags pertinents séparés par des virgules (ex: #Tech, #Android, #Video).\n\n")
            append("Formatte ta réponse exactement ainsi :\n")
            append("DESCRIPTION:\n<la description ici>\nTAGS:\n<les tags séparés par des virgules>")
        }

        val result = generateContent(apiKey, model, prompt)
        return result.map { rawText ->
            val descIndex = rawText.indexOf("DESCRIPTION:")
            val tagsIndex = rawText.indexOf("TAGS:")

            if (descIndex != -1 && tagsIndex != -1 && tagsIndex > descIndex) {
                val desc = rawText.substring(descIndex + "DESCRIPTION:".length, tagsIndex).trim()
                val tagsRaw = rawText.substring(tagsIndex + "TAGS:".length).trim()
                val tagsList = tagsRaw.split(",", "\n")
                    .map { it.trim().removePrefix("#") }
                    .filter { it.isNotBlank() }
                Pair(desc, tagsList)
            } else {
                Pair(rawText.trim(), listOf(category.lowercase(), type))
            }
        }
    }

    /**
     * Admin tool: Test the API key connection with a simple prompt.
     */
    suspend fun testApiKey(apiKey: String, model: String): Result<String> {
        val testPrompt = "Réponds brièvement en une seule phrase : Bonjour, l'API Gemini est-elle opérationnelle ?"
        return generateContent(apiKey, model, testPrompt)
    }

    private fun parseResponseText(responseBody: String): String {
        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            if (content != null) {
                val parts = content.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        sb.append(part.optString("text", ""))
                    }
                    val text = sb.toString().trim()
                    if (text.isNotEmpty()) return text
                }
            }
        }
        return "Aucune réponse générée par l'IA."
    }

    private fun parseErrorMessage(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val error = json.optJSONObject("error")
            error?.optString("message", "Erreur inconnue") ?: "Réponse invalide"
        } catch (_: Exception) {
            responseBody.take(120)
        }
    }

    companion object {
        private const val TAG = "GeminiAiService"
    }
}
