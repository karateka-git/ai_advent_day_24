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
        add("# Отчёт по сравнению chunking-стратегий")
        add("")
        add("- Входная директория: `${report.inputDir}`")
        add("- Количество документов: `${report.documentsCount}`")
        add("")
        add("## Стратегия fixed")
        addAll(renderMetrics(report.fixedMetrics))
        add("")
        add("## Стратегия structured")
        addAll(renderMetrics(report.structuredMetrics))
        add("")
        add("## Краткий вывод")
        add(
            if (report.fixedMetrics.chunksCount == report.structuredMetrics.chunksCount) {
                "Обе стратегии дали одинаковое количество чанков."
            } else if (report.fixedMetrics.chunksCount > report.structuredMetrics.chunksCount) {
                "Structured дала меньше чанков, чем fixed."
            } else {
                "Structured дала больше чанков, чем fixed."
            },
        )
        add(
            if (report.fixedMetrics.averageLength == report.structuredMetrics.averageLength) {
                "Средняя длина чанка совпадает."
            } else if (report.fixedMetrics.averageLength > report.structuredMetrics.averageLength) {
                "У fixed чанки в среднем длиннее."
            } else {
                "У structured чанки в среднем длиннее."
            },
        )
    }.joinToString(separator = System.lineSeparator())

    private fun renderMetrics(metrics: ChunkingStrategyMetrics): List<String> = buildList {
        add("- Количество чанков: `${metrics.chunksCount}`")
        add("- Средняя длина: `${metrics.averageLength.roundToInt()}`")
        add("- Минимальная длина: `${metrics.minLength}`")
        add("- Максимальная длина: `${metrics.maxLength}`")
        if (metrics.lengthBuckets.isEmpty()) {
            add("- Распределение: пусто")
        } else {
            add("- Распределение:")
            metrics.lengthBuckets.forEach { bucket ->
                add("  - `${bucket.rangeLabel}`: `${bucket.count}`")
            }
        }
    }
}
