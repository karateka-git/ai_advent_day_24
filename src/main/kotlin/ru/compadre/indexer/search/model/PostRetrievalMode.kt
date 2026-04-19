package ru.compadre.indexer.search.model

/**
 * Р РµР¶РёРј РІС‚РѕСЂРѕРіРѕ СЌС‚Р°РїР° retrieval РїРѕСЃР»Рµ Р±Р°Р·РѕРІРѕРіРѕ vector search.
 */
enum class PostRetrievalMode(val configValue: String) {
    NONE("none"),
    THRESHOLD_FILTER("threshold-filter"),
    HEURISTIC_FILTER("heuristic-filter"),
    HEURISTIC_RERANK("heuristic-rerank"),
    MODEL_RERANK("model-rerank"),
    ;

    companion object {
        /**
         * РџСЂРµРѕР±СЂР°Р·СѓРµС‚ СЃС‚СЂРѕРєСѓ РёР· РєРѕРЅС„РёРіР° РёР»Рё CLI РІ РїРѕРґРґРµСЂР¶РёРІР°РµРјС‹Р№ СЂРµР¶РёРј.
         */
        fun fromValue(rawValue: String): PostRetrievalMode? =
            entries.firstOrNull { mode -> mode.configValue.equals(rawValue.trim(), ignoreCase = true) }
    }
}
