package ru.compadre.indexer.extractor

import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import ru.compadre.indexer.model.SourceType
import java.nio.file.Path

/**
 * Извлекает текст из PDF-документов через Apache PDFBox.
 */
class PdfTextExtractor : TextExtractor {
    override fun supports(sourceType: SourceType): Boolean = sourceType == SourceType.PDF

    override fun extract(path: Path, sourceType: SourceType): String =
        Loader.loadPDF(path.toFile()).use { document ->
            PDFTextStripper().getText(document)
        }
}
