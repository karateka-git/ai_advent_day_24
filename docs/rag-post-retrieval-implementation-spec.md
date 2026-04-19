# ТЗ: поэтапное внедрение post-retrieval фильтрации и reranking

## Цель

Расширить текущий RAG-пайплайн так, чтобы после базового vector search можно было применять второй этап post-retrieval обработки и сравнивать качество режимов:

- `none`
- `threshold-filter`
- `heuristic-filter`
- `heuristic-rerank`
- `model-rerank`

Итог проекта должен показывать:

- качество без второго этапа;
- качество с разными вариантами фильтрации и реранкинга;
- влияние `top-K до` и `top-K после`;
- сравнение режимов в CLI и evaluation-отчёте.

## Текущее состояние

Сейчас пайплайн устроен так:

- `query`;
- embedding вопроса;
- cosine search по всем чанкам;
- `topK`;
- передача результатов в prompt;
- ответ LLM.

Сейчас отсутствуют:

- отдельный post-retrieval stage;
- configurable `topK before` и `topK after`;
- threshold/filter/rerank режимы;
- model-based reranker;
- сравнение режимов второго этапа в evaluation.

## Общие требования

Новая реализация должна:

- сохранить текущую архитектуру проекта простой и прозрачной;
- не ломать существующий `plain` и базовый `rag`;
- быть конфигурируемой через `application.conf`;
- поддерживать единый post-retrieval pipeline;
- выводить в CLI не только итоговые чанки, но и краткую сводку второго этапа;
- позволять расширять evaluation без дублирования логики.

## Целевой пайплайн

Для `rag`-режима целевой сценарий такой:

1. Пользователь задаёт вопрос.
2. Строится embedding вопроса.
3. Выполняется базовый vector search.
4. Берётся `initialTopK = 8` кандидатов.
5. К кандидатам применяется post-retrieval режим:
   - `none`
   - `threshold-filter`
   - `heuristic-filter`
   - `heuristic-rerank`
   - `model-rerank`
6. После обработки остаётся `finalTopK = 3`.
7. Эти чанки попадают в prompt.
8. LLM генерирует ответ.
9. CLI и evaluation получают:
   - исходные кандидаты;
   - финальные кандидаты;
   - сведения о применённом режиме.

## Функциональные режимы

Нужно реализовать 5 режимов.

### 1. `none`

Поведение как сейчас:

- search возвращает кандидатов;
- в prompt попадают первые `finalTopK` без второго этапа.

### 2. `threshold-filter`

После retrieval:

- кандидаты ниже `minSimilarity` отбрасываются;
- из оставшихся берутся первые `finalTopK`.

### 3. `heuristic-filter`

После retrieval:

- кандидаты оцениваются простыми rule-based правилами;
- нерелевантные кандидаты исключаются;
- в prompt попадают первые `finalTopK`.

### 4. `heuristic-rerank`

После retrieval:

- кандидатам присваивается новый `finalScore` на основе эвристик;
- список пересортировывается;
- в prompt попадают первые `finalTopK`.

### 5. `model-rerank`

После retrieval:

- для каждого кандидата из `initialTopK` вызывается внешний LLM;
- LLM оценивает релевантность пары `(query, chunk)` и возвращает JSON с числом;
- кандидаты сортируются по `modelScore`;
- при равенстве используется `cosineScore` как tie-breaker;
- в prompt попадают первые `finalTopK`.

## Параметры конфигурации

Добавить в конфиг параметры второго этапа.

Минимально нужны:

- `search.initialTopK = 8`
- `search.finalTopK = 3`
- `search.minSimilarity = ...`
- `search.postProcessingMode = "none"`
- настройки heuristic-режимов
- настройки model-rerank

Примерно по смыслу:

- `search.heuristic.minKeywordOverlap`
- `search.heuristic.exactMatchBonus`
- `search.heuristic.keywordOverlapWeight`
- `search.heuristic.cosineWeight`
- `search.heuristic.duplicatePenalty`
- `search.modelRerank.enabled`
- `search.modelRerank.maxCandidates = 8`

Значения должны быть вынесены в `application.conf`, а не зашиты в код.

## Требования к model-based reranker

Зафиксированные решения:

- используется тот же внешний LLM API и та же модель, что уже применяются для генерации ответов;
- rerank выполняется только по `initialTopK = 8` кандидатам;
- на выходе остаётся `finalTopK = 3`;
- LLM получает:
  - `query`
  - `title`
  - `section`
  - `chunk text`
- LLM возвращает строго JSON с числом.

Формат ответа:

```json
{"score": 83}
```

Требования:

- шкала `0..100`;
- вне JSON не должно быть текста;
- при ошибке парсинга должна быть предусмотрена безопасная деградация:
  - либо кандидат получает минимальный score;
  - либо используется fallback на исходный cosine score.

## Эвристики

Для `heuristic-filter` и `heuristic-rerank` нужно использовать простые и понятные правила, без тяжёлой NLP-обработки.

Допустимые сигналы:

- cosine similarity;
- пересечение слов вопроса и чанка;
- наличие точных вхождений ключевых слов;
- бонус за совпадение слов из `title` и `section`;
- штраф за почти дублирующиеся чанки;
- штраф за слишком слабое lexical overlap.

Эвристики должны быть:

- объяснимыми;
- небольшими по объёму;
- пригодными для логирования в CLI и отчёте.

## Архитектурные требования

Нужно ввести отдельный post-retrieval слой.

Рекомендуемая декомпозиция:

- базовый `SearchEngine` остаётся retrieval-компонентом первого этапа;
- новый слой отвечает за post-processing найденных кандидатов;
- `RagQuestionAnsweringService` использует уже не просто список `SearchMatch`, а результат полного retrieval pipeline.

Новая логика не должна размывать ответственность:

- search отвечает за получение кандидатов;
- post-processor отвечает за filter/rerank;
- QA-сервис отвечает за сбор контекста и prompt.

## Требования к данным и моделям

Нужно расширить retrieval-модели так, чтобы можно было хранить:

- исходный cosine score;
- heuristic score;
- model score;
- причину фильтрации;
- факт попадания в финальную выдачу;
- режим обработки.

Нужен объект результата, который позволит:

- показать `topK before`;
- показать `topK after`;
- использовать финальные матчи в prompt;
- выводить подробности в CLI и evaluation.

## Требования к CLI

Текущий `ask --mode rag` нужно расширить поддержкой post-processing режима.

Минимально должны поддерживаться:

- выбор режима второго этапа;
- вывод `initialTopK` и `finalTopK`;
- retrieval summary до и после обработки.

CLI должен показывать:

- исходный query;
- режим post-processing;
- сколько кандидатов взято до обработки;
- сколько осталось после;
- финальные чанки и их score;
- для model-rerank дополнительно `modelScore`.

Также желательно поддержать такие сценарии:

- дефолтный режим из конфига;
- override через CLI.

## Требования к search-команде

Команда `search` должна уметь показывать не только сырые cosine-результаты, но и результат post-processing, если режим включён.

Нужно иметь возможность:

- смотреть baseline retrieval;
- смотреть retrieval с конкретным post-processing режимом;
- использовать `search` как отладочный инструмент для анализа качества.

## Требования к evaluation

Скрипт `run-rag-evaluation.ps1` нужно расширить так, чтобы он сравнивал не только `plain/fixed/structured`, но и post-processing режимы.

Минимальный набор сравнения:

- baseline RAG
- threshold-filter
- heuristic-filter
- heuristic-rerank
- model-rerank

В отчёте должны быть видны:

- ответ модели;
- retrieval summary;
- режим обработки;
- итоговые наблюдения по качеству.

Отдельно в итоговом markdown-отчёте нужно зафиксировать:

- где фильтр помогает убрать мусор;
- где reranker поднимает правильный чанк выше;
- где model-based reranker даёт лучший эффект;
- где improvement отсутствует или спорен.

## Требования к документации

Нужно обновить:

- `README.md`
- `docs/rag-evaluation.md`

README должен описывать:

- новые режимы;
- разницу между filter и rerank;
- параметры `initialTopK` и `finalTopK`;
- использование model-based reranker.

Встроенную документацию новых публичных классов и функций нужно писать KDoc на русском, согласно локальным правилам проекта.

## Поэтапный план внедрения

### 1. Этап 1. Конфиг и каркас post-retrieval слоя

Сделать:

- новые конфиг-поля;
- enum/режимы post-processing;
- новые data class для расширенного retrieval result;
- каркас post-processing интерфейса.

Результат:

- проект компилируется;
- baseline работает через новый общий pipeline без изменения поведения.

### 2. Этап 2. `threshold-filter`

Сделать:

- реализацию отсечения по `minSimilarity`;
- поддержку `initialTopK` и `finalTopK`;
- вывод summary в CLI.

Результат:

- можно сравнить baseline и threshold-filter.

### 3. Этап 3. `heuristic-filter`

Сделать:

- простой набор explainable heuristic-правил;
- логирование причин исключения кандидатов;
- поддержку в `search` и `ask`.

Результат:

- можно сравнить baseline, threshold-filter, heuristic-filter.

### 4. Этап 4. `heuristic-rerank`

Сделать:

- формулу `finalScore` на базе cosine и lexical/metadata signals;
- сортировку по новому score;
- отображение `cosineScore` и `heuristicScore`.

Результат:

- можно оценить, улучшает ли rerank порядок выдачи.

### 5. Этап 5. `model-rerank`

Сделать:

- prompt-контракт relevance judge;
- JSON parsing ответа;
- обработку ошибок;
- вызов того же внешнего LLM API;
- сортировку по `modelScore` с tie-breaker по cosine.

Результат:

- готов полный набор режимов, включая model-based reranker.

### 6. Этап 6. Evaluation и итоговый отчёт

Сделать:

- расширение evaluation-скрипта;
- прогон контрольных вопросов;
- markdown-отчёт по режимам;
- обновление README и `docs/rag-evaluation.md`.

Результат:

- в проекте есть итоговое сравнение режимов и выводы по качеству.

## Критерии готовности

Задача считается завершённой, когда:

- `rag` работает через новый post-retrieval pipeline;
- реализованы все 5 режимов;
- `initialTopK = 8` и `finalTopK = 3` поддерживаются конфигом;
- `model-rerank` использует текущий внешний LLM API;
- model reranker возвращает и парсит JSON с числом;
- CLI показывает retrieval до и после обработки;
- evaluation сравнивает режимы;
- документация обновлена.
