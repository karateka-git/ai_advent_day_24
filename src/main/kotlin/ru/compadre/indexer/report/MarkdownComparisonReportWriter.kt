package ru.compadre.indexer.report

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/**
 * Сохраняет comparison report в markdown-файл.
 */
class MarkdownComparisonReportWriter {
    fun write(outputPath: Path, report: ChunkingComparisonReport) {
        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(outputPath, render(report))
    }

    fun render(report: ChunkingComparisonReport): String = buildList {
        add("# Chunking Comparison Report")
        add("")
        add("- inputDir: `${report.inputDir}`")
        add("- documents: `${report.documentsCount}`")
        add("")
        add("## Fixed")
        addAll(renderMetrics(report.fixedMetrics))
        add("")
        add("## Structured")
        addAll(renderMetrics(report.structuredMetrics))
        add("")
        add("## Summary")
        add(
            if (report.fixedMetrics.chunksCount == report.structuredMetrics.chunksCount) {
                "Both strategies produced the same number of chunks."
            } else if (report.fixedMetrics.chunksCount > report.structuredMetrics.chunksCount) {
                "Structured produced fewer chunks than fixed."
            } else {
                "Structured produced more chunks than fixed."
            },
        )
        add(
            if (report.fixedMetrics.averageLength == report.structuredMetrics.averageLength) {
                "Average chunk length is identical."
            } else if (report.fixedMetrics.averageLength > report.structuredMetrics.averageLength) {
                "Fixed chunks are longer on average."
            } else {
                "Structured chunks are longer on average."
            },
        )
    }.joinToString(separator = System.lineSeparator())

    private fun renderMetrics(metrics: ChunkingStrategyMetrics): List<String> = buildList {
        add("- chunksCount: `${metrics.chunksCount}`")
        add("- averageLength: `${metrics.averageLength.roundToInt()}`")
        add("- minLength: `${metrics.minLength}`")
        add("- maxLength: `${metrics.maxLength}`")
        if (metrics.lengthBuckets.isEmpty()) {
            add("- distribution: empty")
        } else {
            add("- distribution:")
            metrics.lengthBuckets.forEach { bucket ->
                add("  - `${bucket.rangeLabel}`: `${bucket.count}`")
            }
        }
    }
}
