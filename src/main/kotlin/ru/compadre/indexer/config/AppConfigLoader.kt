package ru.compadre.indexer.config

import com.typesafe.config.ConfigFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * Загружает настройки приложения из `application.conf`
 * и, при наличии, накладывает локальный `config/app_secrets.conf`.
 */
object AppConfigLoader {
    fun load(): AppConfig {
        val config = loadMergedConfig()

        return AppConfig(
            app = AppSection(
                inputDir = config.getString("app.inputDir"),
                outputDir = config.getString("app.outputDir"),
            ),
            ollama = OllamaSection(
                baseUrl = config.getString("ollama.baseUrl"),
                embeddingModel = config.getString("ollama.embeddingModel"),
            ),
            llm = LlmSection(
                agentId = config.getString("llm.agentId"),
                userToken = config.getString("llm.userToken"),
                model = config.getString("llm.model"),
                temperature = config.getDouble("llm.temperature"),
                maxTokens = config.getInt("llm.maxTokens"),
            ),
            chunking = ChunkingSection(
                fixedSize = config.getInt("chunking.fixedSize"),
                overlap = config.getInt("chunking.overlap"),
            ),
            search = SearchSection(
                topK = config.getInt("search.topK"),
                initialTopK = config.getInt("search.initialTopK"),
                finalTopK = config.getInt("search.finalTopK"),
                minSimilarity = config.getDouble("search.minSimilarity"),
                postProcessingMode = config.getString("search.postProcessingMode"),
                heuristic = SearchHeuristicSection(
                    minKeywordOverlap = config.getInt("search.heuristic.minKeywordOverlap"),
                    cosineWeight = config.getDouble("search.heuristic.cosineWeight"),
                    keywordOverlapWeight = config.getDouble("search.heuristic.keywordOverlapWeight"),
                    exactMatchBonus = config.getDouble("search.heuristic.exactMatchBonus"),
                    titleMatchBonus = config.getDouble("search.heuristic.titleMatchBonus"),
                    sectionMatchBonus = config.getDouble("search.heuristic.sectionMatchBonus"),
                    duplicatePenalty = config.getDouble("search.heuristic.duplicatePenalty"),
                ),
                modelRerank = SearchModelRerankSection(
                    enabled = config.getBoolean("search.modelRerank.enabled"),
                    maxCandidates = config.getInt("search.modelRerank.maxCandidates"),
                ),
            ),
        )
    }

    private fun loadMergedConfig() =
        if (Files.exists(SECRET_CONFIG_PATH)) {
            ConfigFactory.parseFile(SECRET_CONFIG_PATH.toFile())
                .withFallback(ConfigFactory.load())
                .resolve()
        } else {
            ConfigFactory.load()
        }

    private val SECRET_CONFIG_PATH: Path = Path.of("config", "app_secrets.conf")
}

