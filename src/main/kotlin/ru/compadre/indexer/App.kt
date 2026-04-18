package ru.compadre.indexer

import kotlinx.coroutines.runBlocking
import ru.compadre.indexer.cli.CliCommandParser
import ru.compadre.indexer.cli.CliOutputFormatter
import ru.compadre.indexer.cli.DefaultCliCommandParser
import ru.compadre.indexer.cli.DefaultCliOutputFormatter
import ru.compadre.indexer.config.AppConfig
import ru.compadre.indexer.config.AppConfigLoader
import ru.compadre.indexer.workflow.command.HelpCommand
import ru.compadre.indexer.workflow.command.AskCommand
import ru.compadre.indexer.workflow.service.DefaultWorkflowCommandHandler
import ru.compadre.indexer.workflow.service.WorkflowCommandHandler
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Главная точка входа учебного индексатора документов.
 */
fun main(args: Array<String>) = runBlocking {
    configureUtf8Console()
    configureLogging()

    val config = AppConfigLoader.load()
    val parser: CliCommandParser = DefaultCliCommandParser()
    val formatter: CliOutputFormatter = DefaultCliOutputFormatter()
    val commandHandler: WorkflowCommandHandler = DefaultWorkflowCommandHandler()

    if (args.isEmpty()) {
        runInteractiveShell(
            parser = parser,
            formatter = formatter,
            config = config,
            commandHandler = commandHandler,
        )
        return@runBlocking
    }

    val command = try {
        parser.parse(args)
    } catch (error: IllegalArgumentException) {
        println(error.message ?: "Не удалось разобрать CLI-команду.")
        return@runBlocking
    }

    println(formatter.format(executeCommandWithFeedback(command, config, commandHandler)))
}

private fun configureLogging() {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "warn")
    System.setProperty("org.slf4j.simpleLogger.log.org.apache.pdfbox", "error")
}

private fun configureUtf8Console() {
    System.setOut(
        PrintStream(
            FileOutputStream(FileDescriptor.out),
            true,
            StandardCharsets.UTF_8,
        ),
    )
    System.setErr(
        PrintStream(
            FileOutputStream(FileDescriptor.err),
            true,
            StandardCharsets.UTF_8,
        ),
    )
}

private suspend fun runInteractiveShell(
    parser: CliCommandParser,
    formatter: CliOutputFormatter,
    config: AppConfig,
    commandHandler: WorkflowCommandHandler,
) {
    println("Local Document Indexer")
    println("Интерактивный режим. Введите `help`, чтобы увидеть доступные команды, или `exit`, чтобы завершить сессию.")

    while (true) {
        print("> ")
        val rawInput = readlnOrNull()
            ?.trim()
            ?.trimStart('\uFEFF')
            ?: run {
                println("CLI-сессия завершена.")
                return
            }

        if (rawInput.isBlank()) {
            continue
        }

        when (rawInput.lowercase()) {
            "exit", "quit" -> {
                println("CLI-сессия завершена.")
                return
            }

            "help" -> {
                println(formatter.format(commandHandler.handle(HelpCommand, config)))
                continue
            }
        }

        executeInteractiveCommand(
            rawInput = rawInput,
            parser = parser,
            formatter = formatter,
            config = config,
            commandHandler = commandHandler,
        )
    }
}

private suspend fun executeInteractiveCommand(
    rawInput: String,
    parser: CliCommandParser,
    formatter: CliOutputFormatter,
    config: AppConfig,
    commandHandler: WorkflowCommandHandler,
) {
    val command = try {
        parser.parse(tokenizeCliInput(rawInput))
    } catch (error: IllegalArgumentException) {
        println(error.message ?: "Не удалось разобрать CLI-команду.")
        return
    }

    println(formatter.format(executeCommandWithFeedback(command, config, commandHandler)))
}

private suspend fun executeCommandWithFeedback(
    command: ru.compadre.indexer.workflow.command.WorkflowCommand,
    config: AppConfig,
    commandHandler: WorkflowCommandHandler,
) = if (command is AskCommand) {
    val loadingIndicator = LoadingIndicator()
    try {
        loadingIndicator.start()
        commandHandler.handle(command, config)
    } finally {
        loadingIndicator.stop()
    }
} else {
    commandHandler.handle(command, config)
}

private fun tokenizeCliInput(rawInput: String): Array<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var quoteChar: Char? = null

    rawInput.forEach { char ->
        when {
            quoteChar == null && char.isWhitespace() -> {
                if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }

            char == '"' || char == '\'' -> {
                if (quoteChar == null) {
                    quoteChar = char
                } else if (quoteChar == char) {
                    quoteChar = null
                } else {
                    current.append(char)
                }
            }

            else -> current.append(char)
        }
    }

    if (current.isNotEmpty()) {
        tokens += current.toString()
    }

    return tokens.toTypedArray()
}

private class LoadingIndicator {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        running.set(true)
        thread = Thread {
            var step = 0
            while (running.get()) {
                val dots = ".".repeat(step % 4)
                val padding = " ".repeat(3 - dots.length)
                print("\rОтвет модели$dots$padding")
                Thread.sleep(350)
                step++
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        thread?.join(500)
        print("\r${" ".repeat(40)}\r")
    }
}
