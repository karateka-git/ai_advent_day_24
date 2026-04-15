package ru.compadre.indexer.storage

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.compadre.indexer.model.EmbeddedChunk
import ru.compadre.indexer.model.RawDocument
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

/**
 * SQLite-реализация локального хранилища индекса.
 *
 * На текущем этапе база рассматривается как локальный артефакт одной стратегии,
 * поэтому перед новой записью мы очищаем таблицы и пересохраняем актуальный срез индекса.
 */
class SqliteIndexStore : IndexStore {
    override fun save(
        databasePath: Path,
        documents: List<RawDocument>,
        embeddedChunks: List<EmbeddedChunk>,
    ): StoredIndexSummary {
        ensureParentDirectoryExists(databasePath)

        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.autoCommit = false
            createSchema(connection)
            clearExistingIndex(connection)
            insertDocuments(connection, documents)
            insertChunks(connection, embeddedChunks)
            insertEmbeddings(connection, embeddedChunks)
            connection.commit()

            return readSummary(connection)
        }
    }

    private fun ensureParentDirectoryExists(databasePath: Path) {
        databasePath.parent?.let(Files::createDirectories)
    }

    private fun createSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS documents (
                    document_id TEXT PRIMARY KEY,
                    file_path TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    extension TEXT NOT NULL,
                    text TEXT NOT NULL
                )
                """.trimIndent(),
            )

            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS chunks (
                    chunk_id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    strategy TEXT NOT NULL,
                    source_type TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    title TEXT NOT NULL,
                    section TEXT NOT NULL,
                    text TEXT NOT NULL,
                    start_offset INTEGER NOT NULL,
                    end_offset INTEGER NOT NULL,
                    FOREIGN KEY(document_id) REFERENCES documents(document_id)
                )
                """.trimIndent(),
            )

            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS embeddings (
                    chunk_id TEXT PRIMARY KEY,
                    model TEXT NOT NULL,
                    vector_json TEXT NOT NULL,
                    vector_size INTEGER NOT NULL,
                    FOREIGN KEY(chunk_id) REFERENCES chunks(chunk_id)
                )
                """.trimIndent(),
            )
        }
    }

    private fun clearExistingIndex(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM embeddings")
            statement.executeUpdate("DELETE FROM chunks")
            statement.executeUpdate("DELETE FROM documents")
        }
    }

    private fun insertDocuments(connection: Connection, documents: List<RawDocument>) {
        connection.prepareStatement(
            """
            INSERT INTO documents (
                document_id, file_path, file_name, source_type, title, extension, text
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            documents.forEach { document ->
                statement.setString(1, document.documentId)
                statement.setString(2, document.filePath)
                statement.setString(3, document.fileName)
                statement.setString(4, document.sourceType.name)
                statement.setString(5, document.title)
                statement.setString(6, document.extension)
                statement.setString(7, document.text)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    private fun insertChunks(connection: Connection, embeddedChunks: List<EmbeddedChunk>) {
        connection.prepareStatement(
            """
            INSERT INTO chunks (
                chunk_id, document_id, strategy, source_type, file_path, title, section, text, start_offset, end_offset
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            embeddedChunks.forEach { embeddedChunk ->
                val chunk = embeddedChunk.chunk
                val metadata = chunk.metadata

                statement.setString(1, metadata.chunkId)
                statement.setString(2, metadata.documentId)
                statement.setString(3, chunk.strategy.name)
                statement.setString(4, metadata.sourceType.name)
                statement.setString(5, metadata.filePath)
                statement.setString(6, metadata.title)
                statement.setString(7, metadata.section)
                statement.setString(8, chunk.text)
                statement.setInt(9, metadata.startOffset)
                statement.setInt(10, metadata.endOffset)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    private fun insertEmbeddings(connection: Connection, embeddedChunks: List<EmbeddedChunk>) {
        connection.prepareStatement(
            """
            INSERT INTO embeddings (
                chunk_id, model, vector_json, vector_size
            ) VALUES (?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            embeddedChunks.forEach { embeddedChunk ->
                statement.setString(1, embeddedChunk.chunk.metadata.chunkId)
                statement.setString(2, embeddedChunk.embedding.model)
                statement.setString(3, json.encodeToString(embeddedChunk.embedding.vector))
                statement.setInt(4, embeddedChunk.embedding.vector.size)
                statement.addBatch()
            }

            statement.executeBatch()
        }
    }

    private fun readSummary(connection: Connection): StoredIndexSummary {
        val documentsCount = queryCount(connection, "SELECT COUNT(*) FROM documents")
        val chunksCount = queryCount(connection, "SELECT COUNT(*) FROM chunks")
        val embeddingsCount = queryCount(connection, "SELECT COUNT(*) FROM embeddings")
        val strategies = mutableListOf<String>()

        connection.prepareStatement(
            "SELECT DISTINCT strategy FROM chunks ORDER BY strategy",
        ).use { statement ->
            statement.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    strategies += resultSet.getString("strategy").lowercase()
                }
            }
        }

        return StoredIndexSummary(
            documentsCount = documentsCount,
            chunksCount = chunksCount,
            embeddingsCount = embeddingsCount,
            strategies = strategies,
        )
    }

    private fun queryCount(connection: Connection, sql: String): Int =
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    resultSet.getInt(1)
                } else {
                    0
                }
            }
        }

    private companion object {
        private val json = Json {
            prettyPrint = false
        }
    }
}
