package ru.compadre.indexer.trace

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class JsonlTraceSink(
    private val outputPath: Path,
    private val json: Json = Json {
        prettyPrint = false
        explicitNulls = false
    },
) : TraceSink {
    private val writeLock = ReentrantLock()

    override fun emit(record: TraceRecord) {
        runCatching {
            writeLock.withLock {
                Files.createDirectories(outputPath.parent)
                Files.writeString(
                    outputPath,
                    json.encodeToString(record) + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND,
                )
            }
        }
    }
}
