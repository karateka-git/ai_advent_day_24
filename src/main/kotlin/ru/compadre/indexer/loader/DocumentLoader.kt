package ru.compadre.indexer.loader

import ru.compadre.indexer.model.RawDocument
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

/**
 * Преобразует поддерживаемые файлы в унифицированную модель `RawDocument`.
 */
class DocumentLoader(
    private val fileScanner: FileScanner = FileScanner(),
) {
    fun load(inputDir: Path): List<RawDocument> =
        fileScanner.scan(inputDir).map { file ->
            toRawDocument(file)
        }

    private fun toRawDocument(file: Path): RawDocument {
        val sourceType = requireNotNull(SourceTypeDetector.detect(file)) {
            "Файл `${file.toAbsolutePath()}` не поддерживается загрузчиком."
        }

        return RawDocument(
            documentId = file.toAbsolutePath().normalize().invariantSeparatorsPathString,
            filePath = file.toAbsolutePath().normalize().invariantSeparatorsPathString,
            fileName = file.name,
            sourceType = sourceType,
            title = file.nameWithoutExtension.ifBlank { file.name },
            extension = file.extension.lowercase(),
        )
    }
}
