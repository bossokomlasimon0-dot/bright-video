package com.example.data.model

data class AiModelInfo(
    val id: String,
    val displayName: String,
    val badge: String,
    val description: String,
    val iconEmoji: String
)

object AiModels {
    val ALL = listOf(
        AiModelInfo(
            id = "gemini-1.5-flash",
            displayName = "Gemini 1.5 Flash",
            badge = "Recommandé • Ultra-Rapide",
            description = "Idéal pour les résumés rapides, les explications concises et l'aide instantanée.",
            iconEmoji = "⚡"
        ),
        AiModelInfo(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            badge = "Raisonnement Approfondi",
            description = "Analyse poussée, compréhension complexe et synthèse détaillée des longs documents.",
            iconEmoji = "🧠"
        ),
        AiModelInfo(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            badge = "Vitesse Nouvelle Génération",
            description = "Architecture nouvelle génération ultra-réactive de Google pour tous contenus.",
            iconEmoji = "🚀"
        ),
        AiModelInfo(
            id = "gemini-2.5-flash",
            displayName = "Gemini 2.5 Flash",
            badge = "Haute Précision",
            description = "Dernière version polyvalente alliant créativité, précision et vitesse.",
            iconEmoji = "✨"
        )
    )

    fun getById(id: String): AiModelInfo {
        return ALL.find { it.id == id } ?: ALL.first()
    }
}
