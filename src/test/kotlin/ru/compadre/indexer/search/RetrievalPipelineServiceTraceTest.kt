package ru.compadre.indexer.search

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import ru.compadre.indexer.config.AnswerGuardSection
import ru.compadre.indexer.config.AppConfig
import ru.compadre.indexer.config.AppSection
import ru.compadre.indexer.config.ChunkingSection
import ru.compadre.indexer.config.LlmSection
import ru.compadre.indexer.config.OllamaSection
import ru.compadre.indexer.config.SearchHeuristicSection
import ru.compadre.indexer.config.SearchModelRerankSection
import ru.compadre.indexer.config.SearchSection
import ru.compadre.indexer.embedding.model.ChunkEmbedding
import ru.compadre.indexer.model.ChunkMetadata
import ru.compadre.indexer.model.ChunkingStrategy
import ru.compadre.indexer.model.DocumentChunk
import ru.compadre.indexer.model.EmbeddedChunk
import ru.compadre.indexer.model.SourceType
import ru.compadre.indexer.search.model.SearchMatch
import ru.compadre.indexer.trace.TraceRecord
import ru.compadre.indexer.trace.TraceSink
import java.nio.file.Path

class RetrievalPipelineServiceTraceTest {
    @Test
    fun `retrieve uses the provided request id for all trace events`() = runBlocking {
        val traceSink = RecordingTraceSink()
        val service = RetrievalPipelineService(
            searchEngine = FixedSearchEngine(
                matches = listOf(sampleMatch()),
            ),
            traceSink = traceSink,
        )

        service.retrieve(
            requestId = "req-123",
            query = "test query",
            databasePath = Path.of("data/index-fixed.db"),
            strategy = ChunkingStrategy.FIXED,
            initialTopK = 1,
            finalTopK = 1,
            config = testConfig(),
        )

        assertEquals(listOf("embedding_candidates_built", "selected_matches_built"), traceSink.records.map(TraceRecord::kind))
        assertEquals(setOf("req-123"), traceSink.records.map(TraceRecord::requestId).toSet())
    }

    private fun sampleMatch(): SearchMatch {
        val chunk = DocumentChunk(
            metadata = ChunkMetadata(
                chunkId = "doc-1#chunk-1",
                documentId = "doc-1",
                sourceType = SourceType.TEXT,
                filePath = "docs/doc-1.txt",
                title = "Doc 1",
                section = "section-1",
                startOffset = 0,
                endOffset = 42,
            ),
            strategy = ChunkingStrategy.FIXED,
            text = "sample chunk text",
        )
        return SearchMatch(
            embeddedChunk = EmbeddedChunk(
                chunk = chunk,
                embedding = ChunkEmbedding(
                    model = "test-model",
                    vector = listOf(0.1f, 0.2f),
                ),
            ),
            score = 0.88,
        )
    }

    private fun testConfig() = AppConfig(
        app = AppSection(
            inputDir = "./docs",
            outputDir = "./data",
        ),
        ollama = OllamaSection(
            baseUrl = "http://localhost:11434",
            embeddingModel = "test-embed",
        ),
        llm = LlmSection(
            agentId = "agent",
            userToken = "token",
            model = "test-model",
            temperature = 0.0,
            maxTokens = 32,
        ),
        chunking = ChunkingSection(
            fixedSize = 100,
            overlap = 10,
        ),
        search = SearchSection(
            topK = 5,
            initialTopK = 1,
            finalTopK = 1,
            minSimilarity = 0.0,
            postProcessingMode = "none",
            heuristic = SearchHeuristicSection(
                minKeywordOverlap = 1,
                cosineWeight = 0.7,
                keywordOverlapWeight = 0.3,
                exactMatchBonus = 0.1,
                titleMatchBonus = 0.1,
                sectionMatchBonus = 0.1,
                duplicatePenalty = 0.1,
            ),
            modelRerank = SearchModelRerankSection(
                enabled = true,
                maxCandidates = 1,
            ),
        ),
        answerGuard = AnswerGuardSection(
            enabled = true,
            minTopScore = 0.2,
            minSelectedChunks = 1,
        ),
    )

    private class FixedSearchEngine(
        private val matches: List<SearchMatch>,
    ) : SearchEngine {
        override suspend fun search(
            query: String,
            databasePath: Path,
            strategy: ChunkingStrategy?,
            topK: Int,
            config: AppConfig,
        ): List<SearchMatch> = matches
    }

    private class RecordingTraceSink : TraceSink {
        val records = mutableListOf<TraceRecord>()

        override fun emit(record: TraceRecord) {
            records += record
        }
    }
}
