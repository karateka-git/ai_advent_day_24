package ru.compadre.indexer.search.postprocess

import ru.compadre.indexer.config.AppConfig
import ru.compadre.indexer.search.model.PostRetrievalRequest
import ru.compadre.indexer.search.model.RetrievalCandidate
import ru.compadre.indexer.search.model.RetrievalPipelineResult
import ru.compadre.indexer.search.model.SearchMatch

/**
 * Фильтрует retrieval-кандидатов по минимальному similarity score.
 */
class ThresholdPostRetrievalProcessor : PostRetrievalProcessor {
    override suspend fun process(
        request: PostRetrievalRequest,
        matches: List<SearchMatch>,
        config: AppConfig,
    ): RetrievalPipelineResult {
        val minimumSimilarity = config.search.minSimilarity
        val filteredMatches = matches.filter { match -> match.score >= minimumSimilarity }
        val selectedMatches = filteredMatches.take(request.finalTopK)

        val candidates = matches.map { match ->
            val filterReason = when {
                match.score < minimumSimilarity -> "below_min_similarity"
                match !in selectedMatches -> "trimmed_by_final_top_k"
                else -> null
            }

            RetrievalCandidate(
                match = match,
                cosineScore = match.score,
                finalScore = match.score,
                filterReason = filterReason,
                selected = match in selectedMatches,
            )
        }

        return RetrievalPipelineResult(
            mode = request.mode,
            initialTopK = request.initialTopK,
            finalTopK = request.finalTopK,
            candidates = candidates,
        )
    }
}

