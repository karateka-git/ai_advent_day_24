package ru.compadre.indexer.report

import ru.compadre.indexer.model.ChunkingStrategy
import ru.compadre.indexer.model.DocumentChunk

/**
 * Считает итоговые метрики сравнения fixed и structured chunking.
 */
class ChunkingComparisonService {
    fun buildReport(
        inputDir: String,
        documentsCount: Int,
        fixedChunks: List<DocumentChunk>,
        structuredChunks: List<DocumentChunk>,
    ): ChunkingComparisonReport =
        ChunkingComparisonReport(
            inputDir = inputDir,
            documentsCount = documentsCount,
            fixedMetrics = toMetrics(ChunkingStrategy.FIXED, fixedChunks),
            structuredMetrics = toMetrics(ChunkingStrategy.STRUCTURED, structuredChunks),
        )

    private fun toMetrics(
        strategy: ChunkingStrategy,
        chunks: List<DocumentChunk>,
    ): ChunkingStrategyMetrics {
        if (chunks.isEmpty()) {
            return ChunkingStrategyMetrics(
                strategy = strategy,
                chunksCount = 0,
                averageLength = 0.0,
                minLength = 0,
                maxLength = 0,
                lengthBuckets = emptyList(),
            )
        }

        val lengths = chunks.map { it.text.length }
        return ChunkingStrategyMetrics(
            strategy = strategy,
            chunksCount = lengths.size,
            averageLength = lengths.average(),
            minLength = lengths.min(),
            maxLength = lengths.max(),
            lengthBuckets = buildBuckets(lengths),
        )
    }

    private fun buildBuckets(lengths: List<Int>): List<ChunkLengthBucket> =
        lengths
            .groupingBy(::bucketStart)
            .eachCount()
            .toSortedMap()
            .map { (start, count) ->
                ChunkLengthBucket(
                    rangeLabel = "$start-${start + BUCKET_SIZE - 1}",
                    count = count,
                )
            }

    private fun bucketStart(length: Int): Int = (length / BUCKET_SIZE) * BUCKET_SIZE

    private companion object {
        private const val BUCKET_SIZE = 100
    }
}
