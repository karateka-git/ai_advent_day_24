package ru.compadre.indexer.cli

import ru.compadre.indexer.model.ChunkingStrategy
import ru.compadre.indexer.workflow.command.AskCommand
import ru.compadre.indexer.workflow.command.CompareCommand
import ru.compadre.indexer.workflow.command.HelpCommand
import ru.compadre.indexer.workflow.command.IndexCommand
import ru.compadre.indexer.workflow.command.SearchCommand
import ru.compadre.indexer.workflow.command.WorkflowCommand

/**
 * Минималистичный парсер CLI-команд для учебного MVP.
 */
class DefaultCliCommandParser : CliCommandParser {
    override fun parse(args: Array<String>): WorkflowCommand {
        val command = args.firstOrNull()
            ?.trim()
            ?.trimStart('\uFEFF')
            ?.lowercase()
            ?: return HelpCommand

        return when (command) {
            "help" -> HelpCommand
            "index" -> parseIndexCommand(args)
            "compare" -> parseCompareCommand(args)
            "ask" -> parseAskCommand(args)
            "search" -> parseSearchCommand(args)
            else -> throw IllegalArgumentException(
                "Неизвестная команда `$command`. Поддерживаемые команды: help, index, compare, ask, search.",
            )
        }
    }

    private fun parseIndexCommand(args: Array<String>): WorkflowCommand {
        val input = findOption(args, "--input")
        val strategy = findOption(args, "--strategy")?.let { rawValue ->
            ChunkingStrategy.fromCli(rawValue)
                ?: throw IllegalArgumentException(
                    "Для `--strategy` поддерживаются только значения `fixed` и `structured`.",
                )
        }
        val allStrategies = args.any { it.equals("--all-strategies", ignoreCase = true) }

        if (strategy != null && allStrategies) {
            throw IllegalArgumentException("Нельзя одновременно указывать `--strategy` и `--all-strategies`.")
        }

        return IndexCommand(
            inputDir = input,
            strategy = strategy,
            allStrategies = allStrategies,
        )
    }

    private fun parseCompareCommand(args: Array<String>): WorkflowCommand =
        CompareCommand(
            inputDir = findOption(args, "--input"),
        )

    private fun parseAskCommand(args: Array<String>): WorkflowCommand {
        val query = findOption(args, "--query")
            ?: throw IllegalArgumentException("Для команды `ask` требуется опция `--query`.")
        val mode = findOption(args, "--mode") ?: DEFAULT_ASK_MODE

        if (mode.lowercase() != DEFAULT_ASK_MODE) {
            throw IllegalArgumentException("На текущем этапе для `ask --mode` поддерживается только значение `plain`.")
        }

        return AskCommand(
            query = query,
            mode = mode.lowercase(),
        )
    }

    private fun parseSearchCommand(args: Array<String>): WorkflowCommand {
        val query = findOption(args, "--query")
            ?: throw IllegalArgumentException("Для команды `search` требуется опция `--query`.")
        val strategy = findOption(args, "--strategy")?.let { rawValue ->
            ChunkingStrategy.fromCli(rawValue)
                ?: throw IllegalArgumentException(
                    "Для `search --strategy` поддерживаются только значения `fixed` и `structured`.",
                )
        }
        val topK = findOption(args, "--top")?.toIntOrNull()
            ?: findOption(args, "--top")?.let {
                throw IllegalArgumentException("Для `search --top` требуется целое число.")
            }

        return SearchCommand(
            query = query,
            strategy = strategy,
            topK = topK,
        )
    }

    private fun findOption(args: Array<String>, optionName: String): String? {
        val index = args.indexOfFirst { it.equals(optionName, ignoreCase = true) }
        if (index == -1) {
            return null
        }

        return args.getOrNull(index + 1)
            ?.takeUnless { it.startsWith("--") }
            ?: throw IllegalArgumentException("Для опции `$optionName` требуется значение.")
    }

    private companion object {
        private const val DEFAULT_ASK_MODE = "plain"
    }
}
