package ru.compadre.indexer.search.postprocess

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ru.compadre.indexer.config.LlmSection
import ru.compadre.indexer.llm.ExternalLlmClient
import ru.compadre.indexer.llm.model.ChatMessage
import ru.compadre.indexer.model.DocumentChunk

/**
 * Performs model-based relevance scoring for the `query + chunk` pair.
 */
class ModelRerankJudge(
    private val llmClient: ExternalLlmClient = ExternalLlmClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun buildPrompt(
        query: String,
        chunk: DocumentChunk,
        config: LlmSection,
    ): ModelRerankPrompt =
        ModelRerankPrompt(
            config = config.copy(
                temperature = MODEL_RERANK_TEMPERATURE,
                maxTokens = minOf(config.maxTokens, MODEL_RERANK_MAX_TOKENS),
            ),
            messages = listOf(
                ChatMessage(role = SYSTEM_ROLE, content = SYSTEM_PROMPT),
                ChatMessage(
                    role = USER_ROLE,
                    content = buildUserPrompt(query = query, chunk = chunk),
                ),
            ),
        )

    /**
     * Requests a model-based relevance score in the `0..100` range.
     */
    fun score(
        prompt: ModelRerankPrompt,
        fallbackCosineScore: Double,
    ): ModelRerankEvaluation {
        val responseText = llmClient.complete(
            config = prompt.config,
            messages = prompt.messages,
        )

        val parsedScore = parseScore(responseText)
        return if (parsedScore != null) {
            ModelRerankEvaluation(
                score = parsedScore,
                usedFallback = false,
                rawResponse = responseText,
            )
        } else {
            ModelRerankEvaluation(
                score = normalizeFallbackScore(fallbackCosineScore),
                usedFallback = true,
                rawResponse = responseText,
            )
        }
    }

    private fun buildUserPrompt(
        query: String,
        chunk: DocumentChunk,
    ): String = buildString {
        appendLine("Оцени релевантность чанка пользовательскому вопросу.")
        appendLine()
        appendLine("Вопрос:")
        appendLine(query)
        appendLine()
        appendLine("Title:")
        appendLine(chunk.metadata.title)
        appendLine()
        appendLine("Section:")
        appendLine(chunk.metadata.section)
        appendLine()
        appendLine("Chunk text:")
        appendLine(chunk.text)
    }.trimEnd()

    private fun parseScore(responseText: String): Double? =
        runCatching {
            val payload = json.decodeFromString<ModelRerankScorePayload>(responseText.trim())
            payload.score.coerceIn(MIN_MODEL_SCORE, MAX_MODEL_SCORE)
        }.getOrNull()

    private fun normalizeFallbackScore(cosineScore: Double): Double =
        (cosineScore * MAX_MODEL_SCORE).coerceIn(MIN_MODEL_SCORE, MAX_MODEL_SCORE)

    private companion object {
        private const val SYSTEM_ROLE = "system"
        private const val USER_ROLE = "user"
        private const val MODEL_RERANK_TEMPERATURE = 0.0
        private const val MODEL_RERANK_MAX_TOKENS = 32
        private const val MIN_MODEL_SCORE = 0.0
        private const val MAX_MODEL_SCORE = 100.0
        private const val SYSTEM_PROMPT =
            "Ты оцениваешь релевантность фрагмента текста вопросу пользователя. " +
                "Верни только JSON объекта вида {\"score\": число_от_0_до_100}. " +
                "Никакого дополнительного текста, markdown и комментариев."
    }
}

data class ModelRerankPrompt(
    val config: LlmSection,
    val messages: List<ChatMessage>,
)

/**
 * Result of model-based relevance evaluation.
 */
data class ModelRerankEvaluation(
    val score: Double,
    val usedFallback: Boolean,
    val rawResponse: String,
)

/**
 * JSON contract returned by the model-based reranker.
 */
@Serializable
data class ModelRerankScorePayload(
    val score: Double,
)
