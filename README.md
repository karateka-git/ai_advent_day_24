# local-document-indexer

Учебный Kotlin-проект для локальной индексации документов.

Проект:
- загружает документы из файловой системы;
- извлекает из них текст;
- разбивает текст на чанки двумя стратегиями;
- получает embeddings через локальный `Ollama`;
- сохраняет индекс в `SQLite`;
- формирует отчёт по сравнению `fixed` и `structured` chunking.

## Что реализовано

- загрузка локальных файлов из `docs/`;
- поддержка текстовых документов `txt`;
- fixed-size chunking;
- structured chunking;
- metadata для каждого чанка;
- embeddings через `Ollama` и модель `nomic-embed-text`;
- локальное хранение индекса в `SQLite`;
- интерактивный CLI;
- `comparison.md` с результатами сравнения стратегий.

## Стек

- Kotlin
- Gradle
- Ktor Client
- kotlinx.serialization
- SQLite (`sqlite-jdbc`)
- Apache PDFBox
- Ollama

## Корпус документов

Для выполнения требования по объёму текста в проект добавлен русский текстовый корпус на основе книги Власа Дорошевича **«Легенды и сказки Востока»**.

Корпус лежит в:

- `docs/articles/doroshevich/`

Сейчас он состоит из 8 отдельных текстов:

- `aden.txt`
- `agasfer.txt`
- `bez-allaha.txt`
- `bosfor.txt`
- `reforma.txt`
- `rozhdestvo-hrista.txt`
- `videnie-moiseya.txt`
- `zheleznoe-serdtse.txt`

Суммарный объём корпуса:

- около `73 000+` символов чистого текста;
- примерно `40` страниц при грубой оценке `1800` символов на страницу.

Источники текстов:

- Викитека / public domain

## Требования перед запуском

Нужно установить и запустить `Ollama`, а также скачать embedding-модель:

```powershell
ollama pull nomic-embed-text
```

Проверить, что `Ollama` доступен:

```powershell
curl http://localhost:11434/api/tags
```

В конфиге проекта используется:

- `ollama.baseUrl = http://localhost:11434`
- `ollama.embeddingModel = nomic-embed-text`

## Сборка и запуск

Сборка проекта:

```powershell
.\gradlew.bat build
```

Запуск интерактивного CLI:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\start-manual-check.ps1
```

После запуска доступны команды:

```text
index --input ./docs --strategy fixed
index --input ./docs --strategy structured
index --input ./docs --all-strategies
compare --input ./docs
help
exit
```

## Основные сценарии

### 1. Индексация fixed

```text
index --input ./docs --strategy fixed
```

Результат:

- создаётся `data/index-fixed.db`;
- в CLI выводится краткая сводка по количеству документов, чанков и embeddings.

### 2. Индексация structured

```text
index --input ./docs --strategy structured
```

Результат:

- создаётся `data/index-structured.db`;
- structured-чанки также сохраняются в `SQLite`.

### 3. Сравнение стратегий

```text
compare --input ./docs
```

Результат:

- создаётся `data/comparison.md`;
- в CLI выводится краткая сводка по `fixed` и `structured`.

## Что хранится в индексе

`SQLite`-индекс хранит:

- `documents`
- `chunks`
- `embeddings`

Для каждого чанка сохраняются:

- `chunkId`
- `documentId`
- `sourceType`
- `filePath`
- `title`
- `section`
- `startOffset`
- `endOffset`
- `strategy`
- `text`

Для embeddings сохраняются:

- `model`
- `vector_json`
- `vector_size`

## Результаты сравнения chunking

Сравнение выполнялось на корпусе из 8 русских текстов Власа Дорошевича из сборника **«Легенды и сказки Востока»**.

Итоговые результаты:

- `fixed`:
  - количество чанков: `76`
  - средняя длина чанка: `1153`
  - минимальная длина: `303`
  - максимальная длина: `1200`

- `structured`:
  - количество чанков: `105`
  - средняя длина чанка: `709`
  - минимальная длина: `229`
  - максимальная длина: `1200`

Короткий вывод:

- `fixed` создаёт меньше чанков и держит длину почти около целевого лимита;
- `structured` создаёт больше чанков, но лучше сохраняет смысловые границы текста;
- после доработки `StructuredChunker` structured-чанки стали достаточно крупными для индексации и больше не разваливаются на сотни микрофрагментов;
- обе стратегии пригодны для индексации, но дают разный баланс между размером чанка и смысловой локализацией.

## Артефакты после запуска

После проверки проекта появляются:

- `data/index-fixed.db`
- `data/index-structured.db`
- `data/comparison.md`

## Что пока не входит в проект

Пока не реализованы:

- поиск по embeddings;
- web UI;
- интеграция с FAISS.

Архитектура при этом оставлена в формате `SQLite-first`, чтобы позже можно было добавить поиск и дальнейшее развитие в сторону `FAISS`.
