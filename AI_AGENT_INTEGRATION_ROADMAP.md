# Полная интеграция AI-агента с GlassFiles

Этот документ описывает, что можно добавить в GlassFiles, чтобы AI-агент стал не просто чатом внутри приложения, а полноценным управляющим слоем файлового менеджера, GitHub-клиента, терминала, OCR, архиватора и Android-инструментов.

## Текущее состояние проекта

В проекте уже есть хорошая база для AI-интеграции:

- отдельный AI-модуль: `app/src/main/java/com/glassfiles/data/ai`;
- агентские сущности: `AiTool`, `AiToolCall`, `GitHubToolExecutor`, `LineDiff`, `ReadFileDiskCache`;
- экраны AI: `AiHubScreen`, `AiAgentScreen`, `AiChatScreen`, `AiCodingScreen`, `AiUsageScreen`, `AiSettingsScreen`, `AiModelsScreen`, `AiKeysScreen`, генерация изображений и видео;
- несколько AI-провайдеров и реестр моделей;
- учёт стоимости и токенов через `ModelPricing` и usage-модули;
- GitHub-инструменты для агента: чтение файлов, поиск, diff, workflow logs, issues, PR, commits, branches;
- файловые модули приложения: `FileManager`, `FileOperations`, `ArchiveHelper`, `DuplicateFinder`, `BatchRenamer`, `TrashManager`, `TagManager`, `FileConverter`, `ShizukuManager`;
- терминал: `TerminalService`, `TerminalScreen`, `data/terminal`;
- OCR/QR/сканер, GitHub, FTP, Drive и другие экраны.

Главное направление развития: расширить агента с GitHub-only режима до полноценного App Agent, который видит текущий контекст приложения и умеет безопасно выполнять действия через существующие функции GlassFiles.

---

## 1. App Agent Context

Агенту нужно передавать не только текст пользователя, но и контекст приложения.

Пример структуры:

```kotlin
data class AppAgentContext(
    val currentScreen: String,
    val currentPath: String?,
    val selectedFiles: List<String>,
    val clipboardFiles: List<String>,
    val activeRepo: String?,
    val activeBranch: String?,
    val activeProvider: String?,
    val sortMode: String?,
    val searchQuery: String?,
    val permissions: List<String>,
    val language: String,
    val isDualPane: Boolean,
    val leftPanePath: String?,
    val rightPanePath: String?
)
```

Это позволит писать команды естественно:

- «Распакуй выбранный архив сюда»;
- «Переименуй выбранные фото по дате»;
- «Создай README для этой папки»;
- «Скопируй выбранные файлы во вторую панель»;
- «Проанализируй текущий репозиторий и найди ошибку сборки».

Без такого контекста агент вынужден спрашивать путь и детали вручную.

---

## 2. Единый реестр инструментов агента

Сейчас инструменты завязаны в основном на GitHub. Лучше сделать модульную систему tool-executor'ов.

```kotlin
interface AiToolExecutor {
    val namespace: String
    val tools: List<AiTool>
    suspend fun execute(context: AppAgentContext, call: AiToolCall): AiToolResult
}
```

Потом зарегистрировать исполнители:

- `GitHubToolExecutor` — GitHub, PR, issues, workflows;
- `LocalFileToolExecutor` — локальные файлы;
- `ArchiveToolExecutor` — архивы;
- `TerminalToolExecutor` — команды терминала;
- `OcrToolExecutor` — OCR/QR/извлечение текста;
- `DriveToolExecutor` — Google Drive;
- `FtpToolExecutor` — FTP/SFTP;
- `ShizukuToolExecutor` — расширенные Android-действия;
- `UiToolExecutor` — действия в интерфейсе приложения;
- `MediaToolExecutor` — изображения, видео, аудио;
- `SecurityToolExecutor` — поиск секретов, шифрование, хеши.

Так агент сможет работать не с одним GitHub-слоем, а со всем приложением.

---

## 3. Local File Tools

Это самая важная часть для GlassFiles как файлового менеджера.

Минимальный набор read-only tools:

- `local_list_dir(path)` — список файлов и папок;
- `local_stat(path)` — размер, дата, MIME, права, URI;
- `local_read_file(path, maxBytes?)` — чтение текстового файла;
- `local_read_file_range(path, startLine, endLine)` — чтение части файла;
- `local_search_files(root, query, filters?)` — поиск файлов;
- `local_search_text(root, query, extensions?)` — поиск текста внутри файлов;
- `local_hash(path, algorithm)` — MD5/SHA-1/SHA-256;
- `local_analyze_storage(root)` — анализ размера папок;
- `local_find_duplicates(root)` — поиск дублей.

Write/action tools:

- `local_mkdir(path)`;
- `local_write_file(path, content, mode)`;
- `local_edit_file(path, oldString, newString)`;
- `local_rename(path, newName)`;
- `local_copy(source, destination)`;
- `local_move(source, destination)`;
- `local_delete_to_trash(path)`;
- `local_restore_from_trash(path)`;
- `local_batch_rename(files, pattern)`;
- `local_tag_file(path, tags)`;
- `local_add_to_favorites(path)`.

Опасные операции вроде удаления, перезаписи, перемещения системных файлов должны требовать подтверждение.

---

## 4. Безопасность и подтверждения действий

Агенту нельзя давать возможность молча удалять или перезаписывать файлы.

Нужны уровни риска:

### Read-only

Можно выполнять автоматически:

- list;
- stat;
- read;
- search;
- analyze;
- hash.

### Safe write

Нужно подтверждение пачкой:

- создание нового файла;
- создание архива;
- копирование;
- добавление тегов;
- добавление в избранное.

### Dangerous

Всегда отдельное подтверждение:

- удаление;
- перезапись;
- перемещение;
- запуск терминальных команд;
- Shizuku/root-действия;
- загрузка файлов наружу;
- чтение приватных директорий;
- операции с секретами/API-ключами.

UI подтверждения должен показывать:

- что агент хочет сделать;
- какие файлы будут затронуты;
- примерный риск;
- можно ли отменить действие;
- кнопки `Approve`, `Reject`, `Edit`, `Approve all read-only`.

Удаление лучше делать только через `TrashManager`, а не прямым удалением.

---

## 5. AI-панель в BrowseScreen

Сейчас AI может быть отдельным экраном. Для полной интеграции стоит добавить AI-панель прямо в файловый браузер.

Варианты UI:

- нижняя панель ассистента;
- боковой drawer;
- floating bubble;
- split view: файлы слева, агент справа;
- mini-command bar сверху.

Панель должна видеть:

- текущую папку;
- выбранные файлы;
- активную сортировку;
- режим отображения;
- вторую панель в dual-pane режиме;
- текущие права доступа.

Примеры команд:

- «Наведи порядок в этой папке»;
- «Найди самые большие файлы»;
- «Создай архив из выбранных файлов»;
- «Переименуй фото по дате съёмки»;
- «Сравни эти два файла»;
- «Найди похожие документы».

---

## 6. Контекстное меню AI для файлов

В меню файла/папки можно добавить раздел `AI`.

Команды для любого файла:

- «Спросить AI об этом файле»;
- «Объяснить содержимое»;
- «Сделать краткое резюме»;
- «Переименовать понятнее»;
- «Найти похожие файлы»;
- «Проверить на секреты»;
- «Посчитать хеш»;
- «Создать описание».

Для кода:

- «Объяснить код»;
- «Найти баги»;
- «Сгенерировать документацию»;
- «Предложить refactor»;
- «Создать тесты».

Для изображений:

- «Извлечь текст OCR»;
- «Описать изображение»;
- «Сканировать QR»;
- «Найти похожие»;
- «Сжать/оптимизировать».

Для архивов:

- «Показать содержимое»;
- «Проверить архив»;
- «Распаковать сюда»;
- «Найти инструкцию внутри»;
- «Суммаризировать содержимое».

---

## 7. Diff preview и rollback

В проекте уже есть `LineDiff` и `DiffScreen`. Их стоит использовать для локальных изменений агента.

Перед записью файла агент должен показывать:

- старую версию;
- новую версию;
- подсветку diff;
- список изменённых строк;
- кнопку `Apply`;
- кнопку `Reject`;
- кнопку `Edit manually`;
- кнопку `Apply all`.

Для каждой write-операции желательно сохранять undo-запись:

```kotlin
data class AgentUndoAction(
    val id: String,
    val toolName: String,
    val affectedPaths: List<String>,
    val beforeState: String?,
    val afterState: String?,
    val createdAt: Long
)
```

Это даст пользователю доверие к агенту.

---

## 8. Archive Tools

Можно подключить существующий `ArchiveHelper`.

Tools:

- `archive_list(path)`;
- `archive_extract(path, destination)`;
- `archive_create(files, outputPath, format)`;
- `archive_test(path)`;
- `archive_find_file(path, query)`;
- `archive_summarize(path)`.

Сценарии:

- «Распакуй выбранный архив сюда»;
- «Найди README внутри архива»;
- «Создай zip из выбранных папок»;
- «Проверь, не повреждён ли архив»;
- «Распакуй только APK и README».

---

## 9. OCR, QR и Vision Tools

Так как в приложении есть OCR и QR-экран, их можно сделать инструментами агента.

Tools:

- `ocr_image(path)`;
- `ocr_pdf(path)`;
- `scan_qr(path)`;
- `describe_image(path)`;
- `extract_tables_from_image(path)`;
- `summarize_scanned_document(path)`.

Сценарии:

- «Прочитай текст со скриншота»;
- «Сохрани текст из фото в .txt рядом»;
- «Отсканируй QR-код на этом изображении»;
- «Найди все сканы документов и сделай по ним резюме».

---

## 10. Terminal Tools

В проекте уже есть терминал, поэтому можно добавить агенту управляемый запуск команд.

Tools:

- `terminal_run(command, cwd, timeout)`;
- `terminal_explain_error(output)`;
- `terminal_fix_command(command)`;
- `terminal_run_gradle(task, projectPath)`;
- `terminal_run_tests(projectPath)`.

Обязательные ограничения:

- подтверждение перед запуском;
- timeout;
- denylist опасных команд;
- предупреждение для `rm`, `chmod`, `su`, `sh`, `curl | sh`;
- запрет прямого доступа к приватным директориям без подтверждения;
- логирование всех запусков;
- dry-run режим.

Сценарии:

- «Собери проект и объясни ошибку»;
- «Запусти тесты»;
- «Проверь, почему Gradle падает»;
- «Сгенерируй команду, но не запускай».

---

## 11. GitHub Agent upgrade

GitHub-агент уже есть, но его можно расширить.

Добавить tools:

- `rerun_workflow(runId)`;
- `cancel_workflow(runId)`;
- `dispatch_workflow(workflowId, ref, inputs)`;
- `list_artifacts(runId)`;
- `download_artifact(artifactId)`;
- `create_release(tag, title, body, assets)`;
- `upload_release_asset(releaseId, file)`;
- `review_pr(number)`;
- `request_pr_changes(number, body)`;
- `merge_pr(number, method)`;
- `read_failed_job_logs(runId)`;
- `suggest_ci_fix(runId)`.

Особенно полезный сценарий:

1. агент читает failed workflow;
2. находит ошибку;
3. читает соответствующие файлы;
4. предлагает fix;
5. создаёт branch;
6. применяет изменения;
7. открывает PR.

---

## 12. Локальный coding-agent

Сейчас coding-agent в основном GitHub-ориентирован. Нужен локальный режим для проектов на устройстве.

Tools:

- `local_project_scan(root)`;
- `local_project_tree(root, maxDepth)`;
- `local_grep(root, query)`;
- `local_read_code(path)`;
- `local_apply_patch(patch)`;
- `local_build(root, command)`;
- `local_test(root, command)`;
- `local_diff(path)`;
- `local_revert(path)`.

Сценарии:

- «Найди ошибку сборки этого Android-проекта»;
- «Добавь экран настроек»;
- «Обнови README»;
- «Сделай refactor этого класса»;
- «Создай unit-тесты».

---

## 13. Unified Virtual File System

Чтобы агент одинаково работал с локальными файлами, GitHub, Drive, FTP и архивами, стоит сделать общий VFS-слой.

```kotlin
interface VirtualFileSystem {
    suspend fun list(path: String): List<VfsItem>
    suspend fun stat(path: String): VfsStat
    suspend fun read(path: String): ByteArray
    suspend fun write(path: String, data: ByteArray)
    suspend fun delete(path: String)
    suspend fun move(source: String, destination: String)
    suspend fun copy(source: String, destination: String)
}
```

Реализации:

- `LocalVfs`;
- `SafVfs`;
- `MediaStoreVfs`;
- `GitHubVfs`;
- `DriveVfs`;
- `FtpVfs`;
- `ArchiveVfs`.

Тогда пользователь сможет сказать:

- «Скопируй последние APK на FTP»;
- «Перенеси фото из Drive в локальную папку»;
- «Открой архив как папку и найди файл»;
- «Сделай backup текущего проекта в GitHub».

---

## 14. Android Storage Access Framework

Для Android 11+ нужно учитывать разные источники доступа:

- обычный `File`;
- `Uri`;
- `DocumentFile`;
- `MediaStore`;
- `MANAGE_EXTERNAL_STORAGE`;
- Shizuku/root-доступ.

AI-tools должны уметь возвращать не только path, но и URI.

Пример результата:

```kotlin
data class AgentFileRef(
    val displayName: String,
    val path: String?,
    val uri: String?,
    val mimeType: String?,
    val size: Long?,
    val source: String
)
```

Иначе агент будет хорошо работать только с обычными путями, но хуже с файлами, открытыми через SAF.

---

## 15. AI Usage Dashboard

У тебя уже показывается стоимость и токены, например `$0.085` и `18.3k tok`. Это стоит развить в полноценный usage dashboard.

Показывать:

- токены input/output;
- стоимость input/output;
- суммарную стоимость сессии;
- стоимость за день/месяц;
- расход по режимам: Chat, Agent, Coding, Vision, GitHub, Local Files;
- расход по моделям;
- расход по проектам/папкам;
- прогноз стоимости следующего запроса;
- заполнение context window.

Пример UI:

```text
Session: $0.085 · 18.3k tok
Input: 14.2k
Output: 4.1k
Context: 18.3k / 128k
Today: $0.31 / $2.00
```

---

## 16. Budget Guard

Нужны лимиты:

- максимум токенов на один запрос;
- максимум стоимости на один запрос;
- дневной лимит;
- месячный лимит;
- лимит для agent mode;
- лимит для coding mode;
- лимит для vision/image/video.

Поведение:

- предупреждать на 50%, 80%, 100%;
- блокировать дорогие запросы без подтверждения;
- предлагать более дешёвую модель;
- предлагать сжатие контекста;
- ограничивать tool output.

---

## 17. Token breakdown

Для отладки агент-режима полезно видеть, что именно тратит токены.

Breakdown:

- system prompt;
- developer prompt;
- история диалога;
- сообщение пользователя;
- результаты tools;
- прикреплённые файлы;
- изображения;
- ответ ассистента.

Пример:

```text
System prompt: 1.2k
History: 6.4k
Tool results: 7.9k
Attached files: 2.1k
User message: 0.1k
Assistant output: 0.6k
```

Это особенно важно, потому что в agent mode токены часто уходят на большие логи, файлы и результаты tools.

---

## 18. Сжатие истории и памяти

Когда история приближается к лимиту модели, нужно автоматически сжимать старый контекст.

Механизм:

1. считать токены истории;
2. если занято больше 70% context window — создать summary;
3. сохранить summary в session store;
4. убрать старые сообщения из активного контекста;
5. продолжить диалог.

Также можно добавить долговременную память:

- предпочитаемый язык ответов;
- любимая модель;
- стиль переименования файлов;
- запрещённые папки;
- auto-approve для read-only tools;
- лимиты стоимости;
- часто используемые проекты.

---

## 19. Индекс файлов и RAG

Для умного поиска нужен индекс.

Минимально:

- SQLite/Room;
- FTS по именам файлов;
- FTS по тексту документов;
- MIME, размер, дата, расширение;
- теги;
- OCR-текст;
- путь/URI.

Продвинутый вариант:

- embeddings;
- локальный vector index;
- semantic search;
- summary для больших документов;
- folder-level summaries.

Сценарии:

- «Где документы по аренде квартиры?»;
- «Найди старые APK, которые можно удалить»;
- «Покажи похожие фотографии»;
- «Найди файлы, где упоминается этот API key».

---

## 20. Background AI Tasks

Через WorkManager можно добавить фоновые AI-задачи.

Примеры задач:

- ночной анализ хранилища;
- поиск больших файлов;
- поиск дублей;
- OCR новых скриншотов;
- summary новых документов;
- проверка GitHub workflow failures;
- генерация отчёта по папке;
- авто-сортировка Downloads после подтверждения.

Нужен экран `AI Tasks`:

- queued;
- running;
- waiting approval;
- completed;
- failed;
- cancelled.

---

## 21. AI Notifications

Уведомления:

- «AI нашёл 15 дубликатов»;
- «AI закончил анализ Downloads»;
- «AI ждёт подтверждения удаления»;
- «CI сборка упала, найден возможный fix»;
- «В файлах найдены секреты»;
- «Архив повреждён»;
- «Токен-бюджет почти исчерпан».

Уведомления должны открывать конкретный экран результата.

---

## 22. Share Sheet integration

Добавить intent для отправки файлов/текста в AI.

Сценарии:

- из галереи отправить изображение в GlassFiles AI для OCR;
- из браузера отправить текст на summary;
- из мессенджера отправить файл на анализ;
- из любого приложения отправить zip на проверку;
- отправить лог ошибки и попросить объяснить.

Варианты действий:

- summarize;
- translate;
- OCR;
- explain;
- save note;
- extract links;
- analyze archive.

---

## 23. Голосовой режим

Голос хорошо подходит для файлового менеджера.

Функции:

- speech-to-text;
- text-to-speech;
- голосовое подтверждение;
- hands-free режим.

Примеры:

- «Найди последние скриншоты»;
- «Создай архив из выбранных файлов»;
- «Переименуй эти фото по дате»;
- «Очисти корзину, но сначала покажи список».

---

## 24. Шаблоны AI-команд

Можно добавить готовые команды:

- «Навести порядок в папке»;
- «Найти большие файлы»;
- «Найти дубликаты»;
- «Создать архив»;
- «Распаковать архив»;
- «Переименовать по шаблону»;
- «Сделать README»;
- «Проверить проект»;
- «Объяснить ошибку сборки»;
- «Проверить GitHub Actions»;
- «Суммаризировать документ».

Это снизит порог входа для пользователя.

---

## 25. AI для безопасности

Tools и сценарии:

- `security_scan_secrets(root)` — поиск API keys, tokens, private keys;
- `security_scan_permissions(apk)` — анализ APK permissions;
- `security_hash_file(path)` — хеши;
- `security_encrypt_file(path)`;
- `security_decrypt_file(path)`;
- `security_check_suspicious_files(root)`;
- `security_redact_file(path)` — скрыть секреты в файле.

Сценарии:

- «Проверь папку проекта на секреты перед публикацией»;
- «Найди приватные ключи»;
- «Зашифруй выбранный архив»;
- «Проверь APK на подозрительные permissions».

---

## 26. AI для тегов, избранного и организации файлов

Подключить `TagManager`, `FavoritesManager`, `BatchRenamer`.

Функции:

- авто-теги по содержимому;
- теги по OCR;
- теги по расширению;
- рекомендации избранного;
- умные папки;
- авто-переименование.

Сценарии:

- «Поставь тег invoice всем счетам»;
- «Пометь документы по работе»;
- «Переименуй фото в формате YYYY-MM-DD_location»;
- «Создай умную подборку APK за последний месяц».

---

## 27. AI для медиа

Для фото/видео/аудио:

- определить дубликаты и похожие фото;
- описать изображение;
- извлечь EXIF;
- переименовать по EXIF-дате;
- найти большие видео;
- создать отчёт по медиа;
- предложить сжатие;
- найти скриншоты с текстом через OCR.

Tools:

- `media_read_exif(path)`;
- `media_find_similar(root)`;
- `media_compress(path, quality)`;
- `media_extract_audio(path)`;
- `media_generate_thumbnail(path)`.

---

## 28. AI для APK и приложений

Так как есть `AppManagerScreen`, можно добавить AI-анализ приложений.

Функции:

- объяснить permissions APK;
- сравнить версии APK;
- найти большие приложения;
- найти редко используемые приложения;
- проверить подпись APK;
- извлечь manifest;
- суммаризировать компоненты APK.

Tools:

- `apk_read_manifest(path)`;
- `apk_list_permissions(path)`;
- `apk_compare(a, b)`;
- `apk_verify_signature(path)`;
- `apk_extract_icon(path)`.

---

## 29. AI UI actions

Иногда агенту нужно не менять файлы, а управлять интерфейсом.

Tools:

- `ui_navigate(screen, args)`;
- `ui_open_file(path)`;
- `ui_select_files(paths)`;
- `ui_show_diff(before, after)`;
- `ui_show_approval(actions)`;
- `ui_show_search_results(results)`;
- `ui_focus_path(path)`.

Сценарии:

- «Открой экран корзины»;
- «Покажи найденные дубликаты»;
- «Открой diff этого изменения»;
- «Перейди к настройкам AI».

---

## 30. Архитектурный roadmap

### Этап 1 — App Context + Local read-only tools

- добавить `AppAgentContext`;
- передавать currentPath и selectedFiles из `BrowseScreen` в AI;
- добавить `LocalFileToolExecutor`;
- реализовать `local_list_dir`, `local_stat`, `local_read_file`, `local_search_files`;
- разрешить auto-run только read-only tools.

### Этап 2 — Approval system для локальных действий

- risk levels;
- UI подтверждения;
- журнал agent actions;
- удаление только через корзину;
- настройка auto-approve для read-only.

### Этап 3 — Write tools + Diff preview

- `local_write_file`;
- `local_edit_file`;
- `local_rename`;
- `local_copy`;
- `local_move`;
- diff preview;
- rollback/undo.

### Этап 4 — Архивы, OCR, дубли, теги

- `ArchiveToolExecutor`;
- `OcrToolExecutor`;
- `DuplicateToolExecutor`;
- `TagToolExecutor`;
- контекстные AI-команды для файлов.

### Этап 5 — Terminal и локальный coding-agent

- безопасный `TerminalToolExecutor`;
- build/test tools;
- локальное редактирование проектов;
- анализ ошибок сборки.

### Этап 6 — Unified VFS

- общий интерфейс для Local/SAF/GitHub/Drive/FTP/Archive;
- единые tools поверх VFS;
- перенос файлов между источниками.

### Этап 7 — RAG, background tasks, notifications

- индекс файлов;
- semantic search;
- фоновые AI-задачи;
- уведомления;
- AI reports.

---

## 31. Самая главная фича

Если выбрать один главный шаг, то это:

> AI Agent в `BrowseScreen`, который видит текущую папку и выбранные файлы, умеет безопасно читать, искать, переименовывать, копировать, архивировать и анализировать файлы через tools с подтверждением действий.

Это превратит GlassFiles из файлового менеджера с AI-чатом в настоящий AI file manager.

---

## 32. Минимальный MVP

Минимальный набор для первой версии полной интеграции:

1. `AppAgentContext` с `currentPath` и `selectedFiles`.
2. `LocalFileToolExecutor`.
3. Tools:
   - `local_list_dir`;
   - `local_stat`;
   - `local_read_file`;
   - `local_search_files`;
   - `local_rename`;
   - `local_copy`;
   - `local_move`;
   - `local_delete_to_trash`.
4. Approval dialog для write/delete/move.
5. AI-кнопка в `BrowseScreen`.
6. Контекстное меню «Спросить AI».
7. Usage meter: токены, стоимость, context usage.
8. Action log: что агент сделал и когда.

После этого агент уже будет практически полезен в реальном файловом менеджере.
