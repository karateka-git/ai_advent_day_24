package ru.compadre.indexer.chunking

import ru.compadre.indexer.model.ChunkMetadata
import ru.compadre.indexer.model.ChunkingStrategy
import ru.compadre.indexer.model.DocumentChunk
import ru.compadre.indexer.model.RawDocument
import kotlin.math.max
import kotlin.math.min

/**
 * Режет документ на чанки фиксированного размера с overlap.
 */
class FixedSizeChunker(
    val chunkSize: Int,
    val overlap: Int,
) : Chunker {
    init {
        require(chunkSize > 0) { "chunkSize должен быть больше 0." }
        require(overlap >= 0) { "overlap не может быть отрицательным." }
        require(overlap < chunkSize) { "overlap должен быть меньше chunkSize." }
    }

    override fun chunk(document: RawDocument): List<DocumentChunk> {
        if (document.text.isBlank()) {
            return emptyList()
        }

        val chunks = mutableListOf<DocumentChunk>()
        var startOffset = 0
        var chunkIndex = 0

        while (startOffset < document.text.length) {
            val endOffset = min(startOffset + chunkSize, document.text.length)
            val chunkText = document.text.substring(startOffset, endOffset).trim()

            if (chunkText.isNotEmpty()) {
                chunks += DocumentChunk(
                    metadata = ChunkMetadata(
                        chunkId = "${document.documentId}#fixed-$chunkIndex",
                        documentId = document.documentId,
                        sourceType = document.sourceType,
                        filePath = document.filePath,
                        title = document.title,
                        section = document.title,
                        startOffset = startOffset,
                        endOffset = endOffset,
                    ),
                    strategy = ChunkingStrategy.FIXED,
                    text = chunkText,
                )
                chunkIndex++
            }

            if (endOffset >= document.text.length) {
                break
            }

            startOffset = max(endOffset - overlap, startOffset + 1)
        }

        return chunks
    }
}
