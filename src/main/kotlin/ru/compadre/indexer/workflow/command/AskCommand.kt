package ru.compadre.indexer.workflow.command

/**
 * Команда запроса ответа у модели.
 */
data class AskCommand(
    val query: String,
    val mode: String,
) : WorkflowCommand
