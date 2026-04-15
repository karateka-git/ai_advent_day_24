package ru.compadre.indexer.loader

import java.nio.file.Files
import java.nio.file.Path

/**
 * Проверяет корректность входной директории для загрузки корпуса.
 */
object InputDirectoryValidator {
    fun validate(inputDir: Path) {
        require(Files.exists(inputDir)) {
            "Входная директория не найдена: `${inputDir.toAbsolutePath()}`."
        }
        require(Files.isDirectory(inputDir)) {
            "Указанный путь не является директорией: `${inputDir.toAbsolutePath()}`."
        }
    }
}
