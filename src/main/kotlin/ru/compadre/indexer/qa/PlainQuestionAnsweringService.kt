package ru.compadre.indexer.qa

import ru.compadre.indexer.config.LlmSection
import ru.compadre.indexer.llm.ExternalLlmClient
import ru.compadre.indexer.llm.model.ChatMessage

/**
 * Сервис простого вопрос-ответа без retrieval-контекста.
 */
class PlainQuestionAnsweringService(
    private val llmClient: ExternalLlmClient = ExternalLlmClient(),
) {
    fun answer(question: String, config: LlmSection): String =
        llmClient.complete(
            config = config,
            messages = listOf(
                ChatMessage(
                    role = SYSTEM_ROLE,
                    content = SYSTEM_PROMPT,
                ),
                ChatMessage(
                    role = USER_ROLE,
                    content = question,
                ),
            ),
        )

    private companion object {
        private const val SYSTEM_ROLE = "system"
        private const val USER_ROLE = "user"
        private const val SYSTEM_PROMPT =
            "Ты полезный ассистент. Отвечай кратко, если пользователь не просит подробнее."
    }
}
