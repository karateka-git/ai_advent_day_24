package ru.compadre.indexer.config

/**
 * Корневая конфигурация приложения.
 */
data class AppConfig(
    val app: AppSection,
    val ollama: OllamaSection,
    val llm: LlmSection,
    val chunking: ChunkingSection,
    val search: SearchSection,
)

/**
 * Настройки путей приложения.
 */
data class AppSection(
    val inputDir: String,
    val outputDir: String,
)

/**
 * Настройки подключения к Ollama.
 */
data class OllamaSection(
    val baseUrl: String,
    val embeddingModel: String,
)

/**
 * Настройки внешнего LLM API для генерации ответа.
 */
data class LlmSection(
    val agentId: String,
    val userToken: String,
    val model: String,
    val temperature: Double,
    val maxTokens: Int,
)

/**
 * Настройки chunking.
 */
data class ChunkingSection(
    val fixedSize: Int,
    val overlap: Int,
)

/**
 * Настройки будущего поиска.
 */
data class SearchSection(
    val topK: Int,
    val initialTopK: Int,
    val finalTopK: Int,
    val minSimilarity: Double,
    val postProcessingMode: String,
    val heuristic: SearchHeuristicSection,
    val modelRerank: SearchModelRerankSection,
)

/**
 * РќР°СЃС‚СЂРѕР№РєРё rule-based РѕС†РµРЅРєРё РґР»СЏ post-retrieval СЌС‚Р°РїР°.
 */
data class SearchHeuristicSection(
    val minKeywordOverlap: Int,
    val cosineWeight: Double,
    val keywordOverlapWeight: Double,
    val exactMatchBonus: Double,
    val titleMatchBonus: Double,
    val sectionMatchBonus: Double,
    val duplicatePenalty: Double,
)

/**
 * РќР°СЃС‚СЂРѕР№РєРё model-based reranking.
 */
data class SearchModelRerankSection(
    val enabled: Boolean,
    val maxCandidates: Int,
)

