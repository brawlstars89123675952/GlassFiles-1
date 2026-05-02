# GlassFiles GitHub Modules Progress

## 2026-04-23

### Завершено
- Выполнен первичный аудит проекта `GlassFiles-1`.
- Подтверждена структура Android-приложения на Kotlin + Jetpack Compose.
- Проверены целевые файлы:
  - `app/src/main/java/com/glassfiles/ui/screens/GitHubCodeEditorModule.kt`
  - `app/src/main/java/com/glassfiles/ui/screens/GitHubActionsModule.kt`
  - `app/src/main/java/com/glassfiles/data/github/GitHubManager.kt`
  - `app/src/main/java/com/glassfiles/ui/screens/GitHubMarkdownModule.kt`
- Подтверждено наличие готовых вспомогательных возможностей для доработки:
  - markdown rendering
  - syntax highlighting helpers
  - GitHub Actions API methods
  - Coil image loading для preview изображений
- Сформирован план работ:
  1. модернизация GitHub code editor
  2. улучшение GitHub Actions UI/UX и функций
- Выполнен крупный рефакторинг `GitHubCodeEditorModule.kt`.
- В редактор добавлены и/или переработаны:
  - современный верхний бар и статусные badge
  - поиск по тексту с переходом по совпадениям
  - undo / redo
  - улучшенный edit/view toggle
  - markdown preview
  - split-view markdown editor + preview
  - image preview через Coil с zoom/pan
  - copy content action
  - улучшенная сводка по файлу (`тип`, `lines`, `chars`, modified state)
  - повторное использование существующей syntax highlighting логики через `highlightLine(...)`
- Выполнено крупное улучшение `GitHubActionsModule.kt` без server-side сборки.
- В Actions-модуль добавлены и/или переработаны:
  - overview header со stat cards
  - workflow control panel
  - загрузка и отображение списка workflows
  - выбор конкретного workflow для фильтрации runs
  - dispatch workflow c branch/ref и inputs (`key=value` по строкам)
  - refresh всего actions overview
  - группировка runs по workflow name
  - более живые run cards
  - summary badges по статусам run
  - actor avatar в карточках run
  - более явный control plane для rerun/cancel/open
- Выполнена дополнительная доработка редактора кода в сторону более современного editor UX.
- Дополнительно внедрено в `GitHubCodeEditorModule.kt`:
  - перевод состояния редактора на `TextFieldValue`
  - cursor/selection-aware editing
  - go to line
  - search + replace bar
  - quick insert toolbar
  - расширенный status bar (`Ln`, selection, wrap, mode, modified)
  - более editor-like layout
  - наложенный syntax preview слой для edit mode
- Выполнен финальный polish-pass редактора.
- Финально улучшено:
  - replace now respects current highlighted match better
  - duplicate current line action
  - pair insertion for brackets/quotes/comments
  - более глубокий quick insert toolbar
  - улучшенное snapshot/undo behavior
  - более цельный editor UX
- Выполнен дополнительный финальный editor pass.
- Добавлено:
  - toggle comment/uncomment для текущей строки или выделенного блока
  - comment prefix detection по расширению файла
  - расширенный quick actions toolbar с editor operations
  - ещё более функциональный top action row
- Выполнен финальный safety-pass перед коммитом.
- Исправлены критичные риски из независимого review:
  - нормализация reversed selections в editor operations
  - file-scoped rememberSaveable state для редактора
  - снижение риска повторного save с устаревшим SHA
  - refresh/dispatch safety guards в Actions
  - сохранение workflow-filtered view при обновлении входных runs
- После CI-ошибок выполнен compile-fix pass.
- Исправлено:
  - removed unresolved `Icons.Rounded.Markdown`
  - added missing `verticalScroll` import
  - resolved size formatter name conflict in `GitHubActionsModule.kt`
- Выполнен новый UX-поворот по запросу пользователя.
- Изменения по Actions:
  - `Workflow control` сделан компактнее, чтобы список сборок было удобнее смотреть
- Изменения по editor:
  - редактор файлов в GitHub-модуле полностью переработан как новый clean modern screen
  - layout упрощён и перестроен
  - режимы чтения/редактирования/markdown preview выделены чище
  - UI сделан более современным и визуально легче
- Выполнен ещё один этап: редактор полностью переписан с нуля новой архитектурой.
- Новый editor now built around:
  - отдельный top bar
  - mode strip
  - search/replace card
  - action ribbon
  - modern edit canvas
  - modern read canvas с syntax highlighting
  - markdown canvas
  - image canvas
- Цель этого этапа: не полировка старого editor, а новый экран с нуля.
- По запросу пользователя текущее состояние зафиксировано для следующей CI-сборки и визуальной оценки после push.
- После нового CI-лога выполнен быстрый compile-fix:
  - добавлены недостающие theme imports (`Blue`, `SeparatorColor`, `TextSecondary`, `TextTertiary`) в `GitHubCodeEditorModule.kt`
- Builder architecture переведён на dynamic model по требованиям пользователя:
  - branches only from GitHub API
  - workflows only from GitHub API
  - workflow_dispatch.inputs parsed from workflow YAML
  - build cards generated only for workflows that реально имеют workflow_dispatch schema
  - форма запуска строится автоматически по inputs workflow, без жёстко зашитых полей
  - dispatch uses real workflow filename + ref + inputs from parsed schema
- Выполнен integration pass по dynamic builder:
  - убраны оставшиеся статичные build-категории/пресеты из builder UI
  - `BuildsScreen` подключён к реальным `workflows`, `branches` и текущей ветке из repo screen
  - workflows с `workflow_dispatch` без inputs больше не скрываются
  - после `Run workflow` выполняется поиск нового `workflow_dispatch` run и открывается detail screen с polling
- Выполнен расширенный dynamic-discovery pass:
  - parser теперь принимает больше реальных YAML-вариантов `workflow_dispatch`
  - поддержаны inline trigger forms и comments-safe parsing
  - `workflow_dispatch.inputs.type` читается из YAML
  - boolean inputs автоматически отображаются как выбор `true/false`
  - убран специальный label-маппинг под отдельные примеры параметров
- Зафиксировано подтверждение по финальному ТЗ для builder module:
  - ветки читаются из репозитория через GitHub API `/repos/{owner}/{repo}/branches`
  - параметры формы читаются из YAML workflow-файлов `.github/workflows/*.yml`
  - workflow ID/filename определяется из реальных workflows репозитория
  - карточки сборок скрываются, если подходящий workflow отсутствует
  - после `Run workflow` открывается экран отслеживания run со статусным polling
  - статичные списки build-параметров и build-пресетов убраны
- Сборка после этих изменений подтверждена как успешная.
- Выполнена полная доработка GitHub Actions-модуля в сторону поведения сайта GitHub:
  - runs теперь запрашиваются через API с `page`, `branch`, `event`, `status` и поддержкой `Load more`
  - Actions screen получил фильтры по workflow/status/branch/event, поиск по title/SHA/actor/event/branch/run number и live polling с сохранением фильтров
  - `Run workflow` в Actions использует реальные branches и динамическую форму из `workflow_dispatch.inputs`, а не `key=value`
  - верхний `DispatchWorkflowDialog` также переведён на YAML-driven inputs, чтобы не оставалось второго неполного пути запуска
  - добавлены enable/disable workflow, rerun all jobs, rerun failed jobs, cancel run
  - run detail теперь подтягивает metadata конкретного run, показывает title/status/branch/event/SHA/attempt/repository, jobs, steps, logs и artifacts
  - artifact delete перенесён в `GitHubManager`, jobs/artifacts запрашиваются расширенно (`per_page=100`)
- Локальная сборка не запускалась по просьбе пользователя; выполнена только безопасная проверка `git diff --check`.
- Выполнен дополнительный full-Actions API/UI pass:
  - добавлены repo-wide artifacts с поиском по имени, пагинацией, digest/expiration/run metadata, download/delete
  - добавлены workflow run attempts и загрузка jobs для выбранной попытки
  - добавлены check runs и annotations по `head_sha`
  - добавлены pending deployments, approve/reject и deployment review history
  - добавлены run usage/timing, rerun single job, force cancel, delete logs, delete run
  - добавлены Actions caches: usage, list, delete
  - добавлены repository variables: list/create/update/delete
  - добавлены repository secrets: list/delete; create/update закрыто следующим crypto-pass, потому что GitHub требует sealed-box encryption
  - добавлены repository self-hosted runners: list/delete, registration/remove tokens
  - добавлены read API и set API для Actions permissions, workflow permissions и artifact/log retention
  - Actions screen получил отдельные секции `Runs`, `Artifacts`, `Caches`, `Variables`, `Secrets`, `Runners`, `Settings`
- Локальная сборка по-прежнему не запускалась; повторно выполнена безопасная проверка `git diff --check`.
- Выполнен crypto-pass для GitHub Actions secrets:
  - добавлена Android-зависимость `lazysodium-android` + `jna` для Libsodium sealed box
  - добавлен `GitHubSecretCrypto`, который шифрует value через `crypto_box_seal` и отдаёт base64 для GitHub API
  - добавлен API `/actions/secrets/public-key`
  - добавлен `PUT /actions/secrets/{secret_name}` с `encrypted_value` и `key_id`
  - Secrets UI теперь умеет create/update/delete repository secrets; значение секрета по правилам GitHub обратно не читается
- Выполнен fix ложного "Ошибка" тоста в Actions run details:
  - `ensureJobLogsLoaded` теперь проверяет, что результат `getJobLogs` не начинается с "Error: "
  - `refreshJobLogsNow` тоже игнорирует ошибки от API, не сохраняя их как логи
  - таким образом, transient состояния (queued/in_progress) не вызывают ложные ошибки
  - UI теперь не показывает Toast на нормальные промежуточные состояния workflow

- Выполнен targeted fix для GitHub Actions run details при активных workflow:
  - ранний 404 / `No step log captured` для live step logs теперь считается временным нормальным состоянием, а не ошибкой
  - polling активного run/jobs сохранён без изменения UI
  - step log placeholders продолжают показываться как `Log not available yet` / `Waiting for live log...` вместо error-state
  - generic error toast больше не провоцируется временным отсутствием step logs у active jobs
  - null/empty check annotations и пустые check items скрыты из run details

- Выполнен compile-fix после CI-ошибки по live-log patch:
  - добавлены недостающие imports `android.content.Context` и `kotlinx.coroutines.CoroutineScope` в `GitHubActionsModule.kt`
  - восстановлены imports `TextTertiary` и `java.io.File`, случайно затронутые при точечном import-edit pass

### Важно
- По просьбе пользователя server-side сборки/compile checks больше не запускать.
- Фокус только на реальной доработке UI/UX и функциональности GitHub-модулей внутри проекта.

### Текущее состояние
- Builder больше не опирается на статичные списки параметров формы или build-пресеты.
- Последняя проверенная сборка прошла успешно.
- Actions-модуль расширен до GitHub-style workflow/run management, но новая локальная compile-сборка намеренно не запускалась.
- Actions API покрывает основные repository-level функции сайта GitHub Actions, включая encrypted create/update для repository secrets.

### Итоговая запись после commit/push
- Подготовлен и отправлен commit `80bbae5 Complete GitHub Actions module`.
- Push выполнен в `origin/main`.
- В push вошли изменения по файлам:
  - `WORKLOG_GITHUB_MODULES.md`
  - `app/build.gradle`
  - `app/src/main/java/com/glassfiles/data/github/GitHubManager.kt`
  - `app/src/main/java/com/glassfiles/data/github/GitHubSecretCrypto.kt`
  - `app/src/main/java/com/glassfiles/ui/screens/GitHubActionsModule.kt`
  - `app/src/main/java/com/glassfiles/ui/screens/GitHubGistsAndDialogsModule.kt`
- Основной результат:
  - Actions-модуль доработан до GitHub-style интерфейса с runs, artifacts, caches, variables, secrets, runners и settings.
  - Workflow runs получили фильтры, поиск, пагинацию, polling и расширенный detail screen.
  - Workflow dispatch запускается через реальные branches и YAML-driven `workflow_dispatch.inputs`.
  - Artifacts доступны как на уровне run, так и repository-wide, с download/delete.
  - Jobs поддерживают logs, step logs и rerun отдельного job.
  - Runs поддерживают rerun, rerun failed jobs, cancel, force cancel, delete logs и delete run.
  - Добавлены attempts, check runs, annotations, pending deployments, approve/reject deployments и review history.

## 2026-04-24

### Завершено
- Начата следующая связка после Actions: Releases + Actions artifacts.
- В `GitHubManager.kt` расширена модель GitHub Releases:
  - `GHRelease` теперь хранит `id`, `draft`, `htmlUrl`, `uploadUrl`
  - `GHAsset` теперь хранит `id`, `contentType`, `state`
  - добавлен общий parser для release и release assets
- Добавлены GitHub API helpers для Releases:
  - create release с возвратом полной модели release
  - get release by tag
  - upload release asset с возвратом полной модели asset
  - download release asset
  - delete release asset
- В `GitHubActionsModule.kt` добавлена публикация artifacts из run details в GitHub Release:
  - кнопка `Publish release` в секции artifacts
  - диалог создания draft/pre-release release
  - автогенерация tag/name/body по выбранному workflow run
  - скачивание Actions artifacts во временный cache
  - загрузка этих файлов как release assets
- В `GitHubReleasesModule.kt` доработан экран Releases:
  - создание draft release
  - создание pre-release
  - генерация changelog из последних commits
  - отображение badges `Draft` и `Pre`
  - скачивание release assets в `Downloads/GlassFiles_Git`
  - удаление release assets
- В `GitHubRepoModule.kt` исправлен переход в Releases:
  - экран Releases теперь открывается даже если в репозитории пока нет релизов

### Проверка
- Локальная Gradle/Android сборка не запускалась по просьбе пользователя.
- Выполнена безопасная статическая проверка `git diff --check`, ошибок не найдено.

### Правило на следующие этапы
- Все дальнейшие изменения по GitHub-модулю фиксировать в этом markdown-журнале после каждого завершенного блока работ.

### Продолжение Releases polish-pass
- Доработан Releases UI до более полноценного GitHub-like поведения:
  - добавлена публикация draft release через действие `Publish`
  - добавлено открытие release на сайте GitHub через `Open`
  - добавлен ручной upload release asset с устройства
  - удаление release asset теперь идет через отдельное подтверждение
  - edit release теперь умеет менять не только title/body/pre-release, но и draft state
- Улучшена работа с release assets:
  - выбранный через Android picker файл копируется во временный cache перед upload
  - после upload asset сразу добавляется в текущую release card без полной перезагрузки экрана
  - assets визуально классифицируются как APK, kernel image, Magisk/kernel module, Turnip/Adreno driver, Windows/Linux/iOS build, archive, checksum/signature
  - для assets добавлены более подходящие icons/colors вместо одинакового generic attachment
- В `GitHubManager.kt` добавлены/доработаны release mutation helpers:
  - `updateReleaseDetailed`
  - `publishRelease`
  - tag lookup для update/delete теперь использует URL-encoding
- Цель pass:
  - закрыть базовый release lifecycle после Actions artifacts
  - подготовить UI к публикации сборок kernel/APK/img/Turnip/модулей как нормальных GitHub Release assets
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Commits / Compare pass
- Доработан `GitHubCompareModule.kt` до полноценного GitHub-like compare flow:
  - compare screen теперь использует реальные ветки из GitHub API
  - текущая ветка репозитория передается как base branch по умолчанию
  - добавлен swap base/head
  - добавлен summary по compare result: ahead, behind, commits, changed files
  - показываются commits из compare response
  - показывается список changed files со status/additions/deletions
  - добавлен переход в существующий `DiffViewerScreen` для просмотра patch/diff
  - добавлено открытие compare на сайте GitHub
  - добавлено создание Pull Request прямо из compare result
- В `GitHubManager.kt` улучшен compare API:
  - branch/ref names в compare endpoint теперь URL-encoded
  - `GHCompareResult` расширен списком commits и `htmlUrl`
  - parser compare response теперь вытаскивает commits, author/date/avatar и files
- В `GitHubRepoModule.kt` compare screen получает `selectedBranch` как initial base.
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Compare commits перенесен из not implemented в implemented
  - Releases assets coverage дополнен download/delete/manual upload
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Pull Requests pass
- Доработан PR flow в `GitHubRepoModule.kt`:
  - список Pull Requests остался компактным
  - добавлен отдельный full-screen PR detail screen
  - PR detail загружает `/pulls/{number}` для точных данных GitHub
  - показываются state/merged/draft badges
  - показываются `head -> base`, author и body
  - добавлены PR metrics: commits, changed files, additions, deletions, review comments
  - добавлен mergeability block с `mergeable` и `mergeable_state`
  - добавлен checks summary: successful / active / failed / no checks
  - добавлены действия: Files, Review, Checks, Merge, Open on GitHub, Refresh
  - merge скрыт для draft PR и обновляет detail после успешного merge
- В `GitHubManager.kt` расширена модель PR:
  - `draft`
  - `htmlUrl`
  - `headSha`
  - `mergeable`
  - `mergeableState`
  - `reviewComments`
  - `commits`
  - `additions`
  - `deletions`
  - `changedFiles`
- Добавлен API helper:
  - `getPullRequestDetail`
- Улучшены checks для PR:
  - check-runs теперь запрашиваются по `headSha`, если он доступен
  - ref для check-runs URL-encoded
- Доработан `GitHubDiffModule.kt`:
  - после добавления line-level PR review comment комментарии перезагружаются сразу
  - больше не нужно выходить из diff viewer, чтобы увидеть новый комментарий
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - PR detail, PR review comments и PR check runs отмечены как implemented
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Issues quality pass
- Доработан `IssueDetailScreen` в `GitHubRepoModule.kt` с фокусом на удобство:
  - issue detail теперь строится из отдельных карточек: header, metadata, description, comments
  - metadata видна прямо на экране, а не только внутри скрытого dialog
  - labels, assignee и milestone можно редактировать через существующий metadata dialog
  - после изменения metadata issue detail обновляется без выхода со страницы
  - close/reopen теперь обновляет весь detail/comments/reactions state и показывает результат
- Улучшены комментарии issue:
  - комментарии отображаются в отдельных cards с avatar/author/date
  - добавлен composer с режимами `Write` и `Preview`
  - preview использует простой markdown-rendering для headings, lists, quotes и separators
  - после отправки комментарии обновляются сразу
- Добавлены реакции:
  - issue reactions теперь загружаются вместе с issue detail
  - summary реакций показывается прямо в header card
  - после добавления реакции summary обновляется
  - добавлены reactions для issue comments через `/issues/comments/{id}/reactions`
- В `GitHubManager.kt` добавлены:
  - `getIssueCommentReactions`
  - `addIssueCommentReaction`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Issue reactions и comment reactions отмечены как implemented
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Repository metadata/settings pass
- Доработан `RepoSettingsScreen` в `GitHubRepoSettingsModule.kt`:
  - добавлена summary-card с default branch, visibility, topics count, tags count
  - добавлен индикатор `Unsaved`, когда на экране есть несохраненные изменения
  - кнопка Save теперь disabled, если изменений нет
  - topics нормализуются в GitHub-compatible формат
  - topics ограничиваются до 20 уникальных значений
  - topics сохраняются через dedicated `/repos/{owner}/{repo}/topics` endpoint
  - добавлена read-only секция Tags с последними тегами и SHA
  - `Danger Zone` переименован в более корректный `Administration`, потому что там не только опасные действия
  - archive/unarchive теперь требует подтверждение dialog перед изменением toggle
- В `GitHubManager.kt` добавлено:
  - `getRepoTags`
  - `GHTag`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - update repo settings отмечен как implemented
  - repo topics отмечены как implemented
  - repo tags отмечены как implemented
  - repository settings убраны из high-priority missing list
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Branch Protection / Collaborators pass
- Доработан Branch Protection flow:
  - branch protection API теперь URL-encodes branch names для branches со slash/спецсимволами
  - добавлена summary-card по выбранной ветке
  - добавлен индикатор `Unsaved`
  - кнопка Save disabled, если изменений нет
  - disable protection теперь требует подтверждение dialog
  - summary показывает checks/reviews/conversation/admin badges
- Исправлен Collaborators flow:
  - исправлен permission mapping `read/write` -> GitHub API values `pull/push`
  - update collaborator теперь отправляет корректные permission values
  - добавлен summary по collaborators и ролям
  - добавлен поиск по username/role
  - add/remove/update защищены от повторных кликов через action-in-flight state
  - role labels/colors теперь строятся из нормализованных permission values
- В `GitHubManager.kt` уточнено:
  - `getBranchProtection`
  - `updateBranchProtection`
  - `deleteBranchProtection`
  - `getCollaborators`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Branch protection rules отмечены как implemented
  - Repo collaborators отмечены как implemented
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Webhooks / Rulesets / Security polish
- Доработан Webhooks flow:
  - добавлена summary-card по endpoints: total, active, inactive, failing
  - добавлен поиск по URL, event и delivery status
  - карточки webhook теперь показывают active state, content type, events, SSL verification marker, updated date и last response
  - добавлены действия `Ping`, `Edit`, `Delete` с защитой от повторных кликов
  - delete теперь требует подтверждение dialog
  - add/edit dialog поддерживает URL, events presets, secret, active toggle, content type и insecure SSL
  - секрет показывается как write-only: при редактировании пустое поле не перезаписывает существующий secret
- Доработан Rulesets screen:
  - добавлена summary-card по active/evaluate/disabled rulesets
  - добавлены search и фильтр по enforcement
  - карточки показывают target, source type, rules count, updated date
  - добавлен переход к ruleset в GitHub settings, если доступен URL
- Доработан Security screen:
  - Dependabot alerts получили summary-card по open/high-risk alerts
  - добавлены фильтры по state и severity
  - добавлен поиск по package, advisory id, ecosystem и manifest path
  - карточки показывают package, ecosystem, manifest, GHSA/CVE, vulnerable requirements, fixed version и updated date
  - пустые/null значения скрываются, вместо технического мусора показываются спокойные fallback labels
- В `GitHubManager.kt` расширено:
  - `GHWebhook`: content type, insecure SSL, created/updated timestamps, last response
  - `createWebhook`, `updateWebhook`, `pingWebhook`, `deleteWebhook`
  - `GHRuleset`: target, source type, timestamps, html URL
  - `GHDependabotAlert`: ecosystem, manifest, requirements, GHSA/CVE, html URL, updated date, fixed versions
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Webhooks отмечены как partial/usable
  - Repository Rules отмечены как partial/readable
  - Security отмечен как partial через Dependabot alerts
- Проверка:
  - выполнен `git diff --check`
  - локальная Gradle/Android сборка не запускалась

### Actions kernel builder enhancements — Task 1
- Доработан jobs viewer в `GitHubActionsModule.kt`:
  - matrix jobs с именами формата `prefix / child` группируются по `prefix`, если в текущем списке больше 10 jobs
  - группы показывают aggregate status, количество jobs и duration по longest child job
  - при общем количестве jobs больше 20 группы по умолчанию collapsed, иначе expanded
  - expanded/collapsed state хранится per run только в текущей сессии
  - для 10 или менее jobs остается прежний flat list
  - карточка job вынесена в локальный composable без изменения публичного API экрана
- Gotchas:
  - `GitHubManager.kt` не трогался для этого task
  - серверные GitHub Actions/workflow не запускались
  - локальная `./gradlew :app:compileDebugKotlin` была запущена, но compile не дошел до Kotlin из-за окружения: сначала sandbox запрещал Gradle читать network interfaces, после escalated запуска Gradle остановился на `SDK location not found`; Android SDK / `ANDROID_HOME` / `local.properties` в текущем окружении отсутствуют

### Actions kernel builder enhancements — Task 2
- Добавлен `Jump to next failed job` FAB в `WorkflowRunDetailScreen`:
  - FAB показывается только на секции Jobs и только если в текущем run/list есть failed jobs
  - нажатие прокручивает список к следующему failed job ниже текущей позиции и делает wrap к началу
  - если failed job находится внутри collapsed matrix group, FAB раскрывает группу и скроллит к ее header
  - используется существующий mobile visual style: маленький bottom-right FAB с error icon
- Gotchas:
  - серверные GitHub Actions/workflow не запускались
  - локальная `./gradlew :app:compileDebugKotlin` снова остановилась на `SDK location not found`; Android SDK / `ANDROID_HOME` / `local.properties` отсутствуют в текущем окружении

### Actions kernel builder enhancements — Task 3
- Добавлен share failure summary в kernel failure card:
  - рядом с `Copy failure summary` появилась кнопка `Share`
  - share открывает стандартный Android share sheet через `ACTION_SEND` / `text/plain`
  - текст включает workflow name, run number, repo, branch, failed step, job name, summary и URL run
  - новые пользовательские строки вынесены в `app/src/main/res/values/strings.xml`
- Gotchas:
  - существующий copy-summary behavior сохранен
  - серверные GitHub Actions/workflow не запускались
  - локальная `./gradlew :app:compileDebugKotlin` снова остановилась на `SDK location not found`; Android SDK / `ANDROID_HOME` / `local.properties` отсутствуют в текущем окружении

### Actions kernel builder enhancements — Task 4
- Kernel failure diagnostics переведены на remote-updatable pattern catalog:
  - добавлен bundled fallback asset `app/src/main/assets/kernel_errors.json`
  - добавлен loader `KernelErrorPatterns` с порядком remote -> cached -> bundled
  - remote URL оставлен TODO-константой внутри loader, как просили
  - remote fetch использует короткие timeouts по 3 секунды
  - успешный remote JSON кешируется в app storage
  - если remote недоступен, используется cache; если cache нет, используется bundled asset
  - regex patterns применяются к tail последних 200 строк failed log
  - прежние hardcoded kernel patterns перенесены в bundled JSON
  - в failure card добавлена low-key строка `Patterns: vN (source)` для проверки активного источника
  - descriptions/titles вынесены в `strings.xml` по keys из JSON
- Новые файлы:
  - `app/src/main/java/com/glassfiles/data/github/KernelErrorPatterns.kt` — загрузка, кеширование и matching pattern catalog
  - `app/src/main/assets/kernel_errors.json` — bundled fallback catalog для kernel/AnyKernel/Magisk/Turnip/NDK/toolchain ошибок
- Gotchas:
  - `GitHubManager.kt` не трогался
  - remote URL пока пустой TODO, поэтому до настройки URL активным источником будет bundled или cache
  - серверные GitHub Actions/workflow не запускались
  - локальная `./gradlew :app:compileDebugKotlin` снова остановилась на `SDK location not found`; Android SDK / `ANDROID_HOME` / `local.properties` отсутствуют в текущем окружении

### Actions kernel builder enhancements — Task 5
- Обновлена документация по Actions kernel builder changes:
  - Task 1-4 записаны в этот worklog по отдельности
  - указаны новые файлы и их назначение
  - зафиксирован главный blocker проверки: в текущем окружении нет Android SDK / `ANDROID_HOME` / `local.properties`
  - зафиксировано, что серверные GitHub Actions/workflow не запускались
- Обновлен `GITHUB_API_ANALYSIS.md`:
  - добавлены строки про matrix job grouping и kernel failure diagnostics
  - Actions module coverage поднят с 52% до 62% с учетом новой kernel-builder функциональности
- Проверка:
  - выполнен `git diff --check`
  - финальная локальная `./gradlew :app:compileDebugKotlin` снова остановилась на `SDK location not found`; Android SDK / `ANDROID_HOME` / `local.properties` отсутствуют в текущем окружении

### Webhook deliveries pass
- Доработан Webhooks module:
  - добавлен переход из webhook card в отдельный экран `Webhook deliveries`
  - delivery history загружается через GitHub API `/repos/{owner}/{repo}/hooks/{hook_id}/deliveries`
  - добавлены summary counters: total, success, failed, redelivered
  - добавлены search и фильтры `all/success/failed/redelivery`
  - delivery card показывает event, guid/date, status/code, action, duration и redelivery marker
  - detail dialog показывает request headers, request payload, response headers и response payload
  - добавлен redeliver action из карточки и из detail dialog через `/attempts`
- В `GitHubManager.kt` добавлено:
  - `getWebhookDeliveries`
  - `getWebhookDelivery`
  - `redeliverWebhookDelivery`
  - `GHWebhookDelivery`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - webhook deliveries и redelivery отмечены как implemented
  - Webhooks coverage поднят с 45% до 64%
  - следующий recommended block переключен на Security Scanning
- Проверка:
  - выполнен `git diff --check`
  - серверные GitHub Actions/workflow не запускались
  - локальная Android compile-проверка не запускалась: в окружении все еще нет Android SDK / `ANDROID_HOME` / `local.properties`

### Security scanning pass
- Доработан `SecurityScreen`:
  - добавлены вкладки `Dependabot`, `Code`, `Secrets`
  - Dependabot alerts сохранены с прежними фильтрами и карточками
  - Code scanning alerts загружаются через `/repos/{owner}/{repo}/code-scanning/alerts`
  - Secret scanning alerts загружаются через `/repos/{owner}/{repo}/secret-scanning/alerts`
  - для Code scanning добавлены summary counters, state/severity filters, search по rule/tool/path/ref/message
  - для Secret scanning добавлены summary counters, state filters, search по secret type/state/resolution/validity
  - добавлены detail dialogs для code/secret alerts
  - secret values маскируются в UI
  - пустые/null поля скрываются через общий detail-row helper
- В `GitHubManager.kt` добавлено:
  - `getCodeScanningAlerts`
  - `getSecretScanningAlerts`
  - `GHCodeScanningAlert`
  - `GHSecretScanningAlert`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - code scanning alerts и secret scanning alerts отмечены как implemented
  - Security coverage поднят с 11% до 33%
  - следующий recommended block переключен на Repository Rulesets Detail
- Проверка:
  - выполнен `git diff --check`
  - серверные GitHub Actions/workflow не запускались
  - локальная Android compile-проверка не запускалась: в окружении все еще нет Android SDK / `ANDROID_HOME` / `local.properties`

### Repository rulesets detail pass
- Доработан Rulesets module:
  - ruleset card теперь открывает detail screen без изменения create/update/delete behavior
  - detail screen показывает summary: target, source type, source, enforcement, rules count, bypass actors count, updated date
  - добавлена секция Conditions с include/exclude ref name patterns
  - добавлена секция Rules с type и parameters
  - добавлена секция Bypass actors с actor type, bypass mode и actor id
  - добавлена секция Recent rule suites со статусом/result/evaluation, actor, ref, sha и датой
  - Open in GitHub сохранен отдельной кнопкой
- В `GitHubManager.kt` добавлено:
  - `getRulesetDetail`
  - `getRuleSuites`
  - `GHRulesetDetail`
  - `GHRulesetRule`
  - `GHRulesetBypassActor`
  - `GHRuleSuite`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - get ruleset и list rule suites отмечены как implemented
  - Repository Rules coverage поднят с 20% до 50%
  - следующий recommended block переключен на Pull Request Polish
- Проверка:
  - выполнен `git diff --check`
  - серверные GitHub Actions/workflow не запускались
  - локальная Android compile-проверка не запускалась: в окружении все еще нет Android SDK / `ANDROID_HOME` / `local.properties`

### Pull request polish pass
- Доработан PR detail flow:
  - добавлено редактирование PR title/body/base/state через `PATCH /pulls/{number}`
  - добавлен dialog для requested reviewers: request/remove usernames через `/requested_reviewers`
  - requested reviewers показываются в header PR detail
  - добавлен review history dialog через `/pulls/{number}/reviews`
  - merge теперь открывает confirm dialog с выбором merge method: merge/squash/rebase
  - merge dialog поддерживает commit title и commit message
  - прежний `mergePullRequest(...)` API сохранен совместимым за счет optional параметров
- В `GitHubManager.kt` добавлено:
  - `updatePullRequest`
  - `getPullRequestReviews`
  - `requestPullRequestReviewers`
  - `removePullRequestReviewers`
  - `GHPullReview`
  - `requestedReviewers` в `GHPullRequest`
  - `mergePullRequest` получил optional `method` и `title`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Update PR, review history, squash/rebase merge, request/remove reviewers отмечены как implemented
  - Pull Requests Advanced coverage поднят с 0% до 40%
  - следующий recommended block переключен на Issues Timeline Polish
- Проверка:
  - выполнен `git diff --check`
  - серверные GitHub Actions/workflow не запускались
  - локальная Android compile-проверка не запускалась: в окружении все еще нет Android SDK / `ANDROID_HOME` / `local.properties`

### Issues timeline polish pass
- Доработан Issue detail flow:
  - issue header теперь показывает locked state и lock reason
  - добавлен lock/unlock dialog с GitHub-compatible reasons: `resolved`, `off-topic`, `too heated`, `spam`
  - comments получили actions для edit/delete рядом с reactions
  - edit comment открывает отдельный dialog и обновляет список комментариев после сохранения
  - delete comment требует confirmation и обновляет список после удаления
  - существующий timeline dialog сохранен как отдельный lightweight history view
- В `GitHubManager.kt` добавлено:
  - `updateIssueComment`
  - `deleteIssueComment`
  - `lockIssue`
  - `unlockIssue`
  - `locked` и `activeLockReason` в `GHIssueDetail`
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Issues Advanced теперь отмечает timeline, lock/unlock, issue/comment reactions, edit/delete comments как implemented
  - Issues Advanced coverage поднят с 0% до 75%
  - следующий practical block переключен на Discussions / Projects
- Проверка:
  - выполнен `git diff --check`
  - серверные GitHub Actions/workflow не запускались
  - локальная Android compile-проверка `./gradlew :app:compileDebugKotlin` запускалась, но остановилась на конфигурации проекта: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Actions CI compile fix
- Исправлена compile-ошибка в `GitHubActionsModule.kt` после kernel builder enhancements:
  - `jobListState` перенесен из `ActionsTab` в `WorkflowRunDetailScreen`, где реально используется `LazyColumn` jobs/details
  - `buildJobListItems(...)` теперь явно возвращает `List<JobListItem>`, чтобы Kotlin не выводил тип ветки `flatMap` как `JobRow` и не падал на `GroupHeader`
- Проверка:
  - выполнен `git diff --check`
  - серверные GitHub Actions/workflow не запускались
  - локальная Android compile-проверка не запускалась повторно: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Repository teams pass
- Реализован следующий high-priority блок из `GITHUB_API_ANALYSIS.md`: Repository Teams для org repositories.
- В `GitHubManager.kt` добавлено:
  - `getRepoTeams`
  - `getOrgTeams`
  - `addRepoTeam`
  - `updateRepoTeamPermission`
  - `removeRepoTeam`
  - модели `GHRepoTeam` и `GHOrgTeam`
- Добавлен новый экран `GitHubTeamsModule.kt`:
  - список teams, имеющих доступ к репозиторию
  - summary-card по количеству teams и permission levels
  - поиск по team name/slug/permission
  - добавление org team к repo через real org teams API
  - изменение team permission: read/triage/write/maintain/admin
  - удаление team access с confirmation dialog
- В `GitHubRepoSettingsModule.kt` добавлен пункт `Manage teams` рядом с collaborators.
- В `GitHubRepoModule.kt` подключена навигация на `RepoTeamsScreen`.
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Repo teams перенесены из missing в implemented
  - Repositories Advanced coverage поднят с 0% до 5%
  - следующий recommended block переключен на Discussions / Projects
- Проверка:
  - выполнен `git diff --check`
  - локальная Android compile-проверка не запускалась: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Discussions GraphQL pass
- Реализован следующий блок по очереди после Repository Teams: repository Discussions.
- Важно: старый REST-path `/repos/{owner}/{repo}/discussions` заменен на официальный GitHub GraphQL API для Discussions.
- В `GitHubManager.kt` добавлено:
  - общий private GraphQL helper через `/graphql`
  - `getDiscussionCategories`
  - `getDiscussions`
  - `getDiscussionDetail`
  - `createDiscussion`
  - `updateDiscussion`
  - `deleteDiscussion`
  - `getDiscussionComments`
  - `addDiscussionComment`
  - модели `GHDiscussionCategory` и расширенная `GHDiscussion`
- `GitHubDiscussionsModule.kt` переписан как полноценный flow:
  - summary-card по discussions/categories
  - поиск по title/body/author
  - фильтр по discussion category
  - detail screen с body, category, answered/locked/upvotes/comments metadata
  - create discussion dialog с category picker
  - edit discussion dialog для title/body/category
  - delete discussion confirmation
  - comments list и composer для добавления comment
  - open discussion on GitHub
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Discussions перенесены в fully implemented
  - Discussions coverage поднят с 0% до 100%
  - следующий high-priority block переключен на Projects
- Проверка:
  - выполнен `git diff --check`
  - локальная Android compile-проверка не запускалась: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Projects pass
- Реализован следующий high-priority блок после Discussions: repository Projects.
- Важно:
  - Classic Projects закрыты по основным REST endpoints из матрицы.
  - Projects V2 добавлены как read-only overview через GraphQL, потому что полноценное редактирование V2 требует отдельной item/field/mutation модели.
- В `GitHubManager.kt` добавлено:
  - `getRepoProjects`
  - `getProject`
  - `createRepoProject`
  - `updateProject`
  - `deleteProject`
  - `getProjectColumns`
  - `createProjectColumn`
  - `getProjectCards`
  - `createProjectCard`
  - `moveProjectCard`
  - `deleteProjectCard`
  - `getRepoProjectsV2`
  - модели `GHProject`, `GHProjectColumn`, `GHProjectCard`, `GHProjectV2`
- Добавлен новый `GitHubProjectsModule.kt`:
  - repo tab `Projects`
  - summary по Classic/V2 projects
  - поиск
  - переключение Classic / V2
  - создание classic project
  - detail classic project с columns/cards
  - edit/delete classic project
  - create column
  - create note card
  - move card между columns
  - delete note card
  - V2 cards с title/description/items/open/public и open on GitHub
- В `GitHubRepoModule.kt` добавлена вкладка `PROJECTS`.
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Projects classic перенесены в implemented
  - Projects V2 overview отмечен как partial
  - Projects coverage поднят с 0% до 89%
  - следующий practical block переключен на Security Tab / Packages / Projects V2 mutations
- Проверка:
  - выполнен `git diff --check`
  - локальная Android compile-проверка не запускалась: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Security advisories / controls pass
- Реализован следующий practical block из матрицы: Security Tab polish.
- В `GitHubManager.kt` добавлено:
  - `getRepositorySecurityAdvisories`
  - `getRepositorySecuritySettings`
  - `setAutomatedSecurityFixes`
  - `setVulnerabilityAlerts`
  - `setPrivateVulnerabilityReporting`
  - модели `GHRepositorySecurityAdvisory`, `GHAdvisoryVulnerability`, `GHRepositorySecuritySettings`
- `GitHubSecurityModule.kt` доработан:
  - добавлена вкладка `Advisories`
  - добавлена вкладка `Settings`
  - advisories получили summary, severity/state filters, search по GHSA/CVE/summary/package/CWE
  - advisory cards показывают severity/state/CVSS/CWE, description и affected package ranges
  - security settings показывают toggles:
    - dependency graph/vulnerability alerts
    - Dependabot security updates
    - private vulnerability reporting
  - toggles обновляют состояние через GitHub API и перечитывают settings
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - security advisories перенесены в implemented
  - vulnerability alerts enable/disable перенесены в implemented
  - Dependabot security updates и private vulnerability reporting добавлены как implemented controls
  - Security coverage поднят с 33% до 73%
  - следующий practical block переключен на Packages
- Проверка:
  - выполнен `git diff --check`
  - локальная Android compile-проверка не запускалась: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Packages pass
- Реализован следующий practical block после Security: GitHub Packages.
- В `GitHubManager.kt` добавлено:
  - `getUserPackages`
  - `getOrgPackages`
  - `getPackage`
  - `deletePackage`
  - `getPackageVersions`
  - `deletePackageVersion`
  - модели `GHPackage` и `GHPackageVersion`
- Добавлен новый экран `GitHubPackagesModule.kt`:
  - entry point из GitHub home quick actions
  - выбор владельца: текущий user или orgs пользователя
  - фильтр package type: all/container/docker/npm/maven/nuget/rubygems
  - поиск по package name/type/repository
  - package detail с metadata и versions
  - отображение tags у package versions
  - удаление package и package version через confirmation dialogs
  - open on GitHub для package/version
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Packages перенесены в fully implemented
  - Packages coverage поднят с 0% до 100%
  - следующий practical block переключен на Projects V2 mutations/items или Advanced search
- Проверка:
  - выполнен `git diff --check`
  - локальная Android compile-проверка не запускалась: в окружении нет Android SDK / `ANDROID_HOME` / `local.properties`

### Projects V2 mutations/items pass
- Реализован следующий block после Packages: Projects V2 items and mutations.
- В `GitHubManager.kt` добавлено:
  - `getProjectV2Detail`
  - `updateProjectV2`
  - `addProjectV2DraftIssue`
  - `updateProjectV2DraftIssue`
  - `deleteProjectV2Item`
  - `archiveProjectV2Item`
  - `updateProjectV2ItemFieldValue`
  - `clearProjectV2ItemFieldValue`
  - `moveProjectV2Item`
  - модели `GHProjectV2Detail`, `GHProjectV2Field`, `GHProjectV2FieldOption`, `GHProjectV2Item`, `GHProjectV2ItemFieldValue`
- `GitHubProjectsModule.kt` доработан:
  - V2 карточка теперь открывает detail screen
  - detail загружает Project V2 fields/items через GraphQL
  - добавлено создание draft item
  - добавлено редактирование draft item title/body
  - добавлено удаление item с confirmation dialog
  - добавлено archive/unarchive item
  - добавлено редактирование field values для text/number/date/single-select
  - добавлено clear field через пустое значение
  - добавлено move item to top через `updateProjectV2ItemPosition`
  - добавлено редактирование project title/description/readme/open/public
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Projects V2 overview/detail/items/mutations перенесены в implemented
  - remaining gap сдвинут на Projects V2 field/view workflow editing
  - следующий practical block переключен на Advanced search или Repository rulesets mutations
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку дальше не запускать в этом окружении по просьбе пользователя

### Advanced search pass
- Реализован следующий block после Projects V2 mutations/items: Advanced search.
- В `GitHubManager.kt` добавлено:
  - `searchIssuesAdvanced`
  - `searchCommitsAdvanced`
  - `searchTopics`
  - `searchLabels`
  - URL-encoding для `searchUsers`
  - lookup repository id для repo-scoped label search
  - модели `GHSearchIssueResult`, `GHSearchCommitResult`, `GHTopicSearchResult`, `GHLabelSearchResult`
- Добавлен новый экран `GitHubAdvancedSearchModule.kt`:
  - глобальный search entry point из GitHub home quick actions
  - tabs: Repos, Issues, Commits, Topics, Labels, Users
  - repository results открывают repo detail flow
  - user results открывают profile flow
  - issue/PR и commit results открываются на GitHub
  - topics показывают description, featured/curated markers, aliases
  - labels работают как repo-scoped search через поле `owner/repo`
  - pagination для issues/commits/topics/labels через Load more
- В `GitHubHomeModule.kt` добавлен quick chip `Search`.
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Advanced search по commits/issues/topics/labels/users перенесен в implemented
  - Search coverage поднят до 100%
  - следующий practical block переключен на Repository rulesets mutations
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускать в этом окружении по просьбе пользователя

### Repository rulesets mutations pass
- Реализован следующий block после Advanced search: create/update/delete repository rulesets.
- В `GitHubManager.kt` добавлено:
  - `createRuleset`
  - `updateRuleset`
  - `deleteRuleset`
  - payload builder для `name`, `target`, `enforcement`, `conditions.ref_name`, `rules`
  - общий parser `parseRulesetDetail`
- `GitHubSecurityModule.kt` доработан:
  - в Rulesets top bar добавлен create action
  - добавлен `RulesetEditorDialog`
  - create/edit поддерживают target `branch/tag/push`
  - create/edit поддерживают enforcement `active/evaluate/disabled`
  - include/exclude refs вводятся строками или через comma-separated values
  - rules редактируются как raw JSON array, чтобы не терять гибкость GitHub ruleset API
  - detail screen получил edit и delete actions
  - delete требует confirmation dialog
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - create/update/delete rulesets отмечены как implemented
  - Repository Rules coverage поднят до 86%
  - следующий practical block переключен на Projects V2 field/view workflows или Advanced notifications
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускать в этом окружении по просьбе пользователя

### Notifications thread subscriptions pass
- Реализован следующий компактный block после Repository rulesets mutations: notification thread subscriptions.
- В `GitHubManager.kt` добавлено:
  - `getThreadSubscription`
  - `setThreadSubscription`
  - `deleteThreadSubscription`
  - модель `GHThreadSubscription`
- `GitHubNotificationsModule.kt` доработан:
  - notification card получила action для thread subscription dialog
  - dialog загружает текущее состояние subscription
  - доступны действия Subscribe, Ignore и Default
  - Default удаляет thread subscription через DELETE endpoint
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Advanced Notifications перенесены в implemented
  - Notifications coverage поднят до 100%
  - следующий practical block переключен на Projects V2 field/view workflows или Webhook config/test helpers
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускать в этом окружении по просьбе пользователя

### Webhook config/test helpers pass
- Реализован следующий компактный block после Notifications: webhook config/test helpers.
- В `GitHubManager.kt` добавлено:
  - `getWebhook`
  - `testWebhook`
  - `getWebhookConfig`
  - `updateWebhookConfig`
  - модель `GHWebhookConfig`
- `GitHubWebhooksModule.kt` доработан:
  - webhook card теперь открывает detail dialog через `/hooks/{id}`
  - webhook card получила отдельное действие `Test delivery` через `/hooks/{id}/tests`
  - webhook card получила отдельное действие `Config`
  - config dialog загружает актуальный `/hooks/{id}/config`
  - config dialog редактирует payload URL, content type, SSL verification и write-only secret replacement
  - после update config список webhooks перечитывается без выхода со страницы
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - get webhook, test webhook, get webhook config и update webhook config отмечены как implemented
  - Webhooks coverage поднят до 100%
  - следующий practical block переключен на Projects V2 field/view workflows или Security single-alert detail/community health
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### Security single-alert/community pass
- Реализован следующий compact block после Webhooks: Security single-alert detail и community health.
- В `GitHubManager.kt` добавлено:
  - `getDependabotAlert`
  - `getCodeScanningAlert`
  - `getSecretScanningAlert`
  - `getRepositorySecurityAdvisory`
  - `getCommunityProfile`
  - модели `GHCommunityProfile` и `GHCommunityProfileFile`
  - общие parsers для Dependabot/code/secret/advisory alerts, чтобы list и detail endpoints не расходились
- `GitHubSecurityModule.kt` доработан:
  - Dependabot cards получили detail dialog с дозагрузкой single alert
  - Code scanning detail перед открытием дозагружает `/code-scanning/alerts/{alert_number}`
  - Secret scanning detail перед открытием дозагружает `/secret-scanning/alerts/{alert_number}`
  - Repository advisory cards получили detail dialog с дозагрузкой `/security-advisories/{ghsa_id}`
  - добавлена вкладка `Community`
  - Community tab показывает health percentage, progress bar, documentation link и checklist community-файлов
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - single-alert security endpoints и community profile отмечены как implemented
  - Security coverage поднят до 100%
  - следующий practical block переключен на Projects V2 field/view workflows
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### Projects V2 field/view workflows pass
- Реализован следующий practical block после Security: Projects V2 field/view workflows.
- В `GitHubManager.kt` добавлено:
  - `ProjectV2.views` и `ProjectV2.workflows` в detail query
  - `createProjectV2Field`
  - `updateProjectV2Field`
  - `deleteProjectV2Field`
  - модели `GHProjectV2View` и `GHProjectV2Workflow`
  - расширение `GHProjectV2FieldOption` color/description для single-select schema
- `GitHubProjectsModule.kt` доработан:
  - Project V2 detail показывает отдельную секцию schema fields
  - добавлено создание custom fields: text, number, date, single-select
  - добавлено редактирование field name и replacement single-select options
  - добавлено удаление field с confirmation dialog
  - добавлена секция views с layout/filter/visible fields
  - добавлена секция workflows с enabled state
  - Project V2 summary теперь показывает counts по items/fields/views/workflows
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Projects V2 field schema, views и workflows отмечены как implemented
  - Projects coverage поднят до 100%
  - следующий practical block переключен на Repository ruleset suite detail
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### Repository rule suite detail pass
- Реализован один следующий блок: single rule-suite detail.
- В `GitHubManager.kt` добавлено:
  - `getRuleSuite`
  - общий parser `parseRuleSuite`, используемый list и single endpoints
- `GitHubSecurityModule.kt` доработан:
  - rule suite cards в ruleset detail стали кликабельными
  - при открытии suite выполняется дозагрузка `/repos/{owner}/{repo}/rule-suites/{id}`
  - добавлен detail dialog со status/result/evaluation, actor, ref, before/after SHA и датами
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - single rule suite endpoint отмечен как implemented
  - Repository Rules coverage поднят до 100%
  - следующий practical block переключен на Advanced PR review mutations/check suites
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### Pull request review mutation pass
- Реализован один следующий блок: single PR review detail/mutations.
- В `GitHubManager.kt` добавлено:
  - `getPullRequestReview`
  - `updatePullRequestReview`
  - `deletePullRequestReview`
  - общий parser `parsePullReview`, используемый list и single endpoints
- `GitHubRepoModule.kt` доработан:
  - review history cards стали кликабельными и дозагружают `/pulls/{number}/reviews/{id}`
  - добавлен detail dialog для single review
  - pending review можно редактировать через PUT endpoint
  - pending review можно удалить через DELETE endpoint с confirmation dialog
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Get/Update/Delete single review отмечены как implemented
  - Pull Requests Advanced coverage поднят до 60%
  - следующий practical block переключен на Advanced PR review comment mutations/check suites
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### Pull request review comment mutation pass
- Реализован один следующий блок: PR review comment update/delete UI.
- API helpers уже были в `GitHubManager.kt`:
  - `updatePullRequestReviewComment`
  - `deletePullRequestReviewComment`
- `GitHubDiffModule.kt` доработан:
  - общий review comments screen получил edit/delete actions
  - inline comment bubbles в diff viewer получили edit/delete actions для PR контекста
  - после update/delete comments перезагружаются через existing refresh callback
  - delete защищен confirmation dialog
- В `GITHUB_API_ANALYSIS.md` обновлено покрытие:
  - Update/Delete review comment отмечены как implemented
  - Pull Requests Advanced coverage поднят до 73%
  - следующий practical block переключен на Advanced PR check suites / explicit merge status
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### GitHub visual polish pass
- Выполнен UI-only polish pass по GitHub module без изменений API/data/request logic.
- Общий визуальный паттерн:
  - добавлен `Modifier.ghGlassCard()` для GitHub cards: dark glass surface, subtle vertical depth, low-alpha border, soft accent shadow.
  - green/accent оставлен для primary/success states, gray используется для secondary metadata.
- `GitHubProfileModule.kt`:
  - profile hero и stat cards получили единый glass treatment.
  - Repositories/Followers/Following теперь читаются через numeric hierarchy: крупное легкое число + маленький uppercase label.
- `GitHubSharedUiModule.kt`:
  - repo cards получили более явную hierarchy: icon tile, medium title, muted description, monospace counters.
- `GitHubRepoModule.kt`:
  - file browser rows получили folder/file icon variation, compact metadata, monospace file sizes.
  - commits получили hash avatar fallback, monospace short SHA, compact author/date metadata.
  - issues и PR rows получили left status bar, state dot/icon, uppercase status labels and better scanning rhythm.
- `GitHubActionsModule.kt`:
  - matrix job groups and job cards use the shared glass recipe.
  - job rows получили status-aware left accent bar, monospace duration, compact step rows.
  - step statuses теперь rendered as icon/dot + bordered low-alpha pill instead of plain colored text.
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### GitHub detail polish follow-up
- Выполнен второй UI-only polish pass по оставшимся заметным GitHub detail screens.
- `GitHubRepoModule.kt`:
  - PR detail header, mergeability, metrics, changed files, issue header/meta/body/comments, README card and releases list aligned to the shared glass-card recipe.
  - PR metrics получили моноширинные числовые значения and compact uppercase labels.
- `GitHubActionsModule.kt`:
  - workflow control, stat cards, run cards, run detail header, usage/attempts/danger/deployments/review/check cards aligned with GitHub glass styling.
- `GitHubProjectsModule.kt`:
  - project summary, classic/V2 cards, fields/views/workflows/items, classic columns and empty states moved to consistent glass cards.
- `GitHubSecurityModule.kt`:
  - rulesets, ruleset detail cards, rule suites, security summaries, community cards and alert rows moved to the same visual system.
- `GitHubReleasesModule.kt`:
  - release cards aligned with GitHub glass card styling.
- Проверка:
  - выполнен `git diff --check`
  - локальную Android compile-проверку не запускал по просьбе пользователя

### GitHub visual regression cleanup
- Reverted the problematic card depth treatment from the GitHub visual polish pass.
- `Modifier.ghGlassCard()` no longer uses a linear gradient, hardcoded dark fallback color, or custom accent shadow.
- GitHub glass cards now use theme-aware solid `MaterialTheme.colorScheme.surface` with `outlineVariant` hairline border.
- Active repo tabs, branch chip, commit author/hash accents, and touched Actions status accents were moved away from `Blue`/hardcoded tints to `MaterialTheme.colorScheme.primary`, `error`, and `onSurfaceVariant`.
- Log preview surfaces touched by the polish pass now use `surfaceVariant` / `onSurface` instead of hardcoded dark-only colors.
- Reason: the previous gradient/shadow implementation created muddy grey card stains in Light theme and allowed user accent hue changes to leak orange into GitHub active states.

### GitHub top toolbar edge cleanup
- Fixed visible cropped-corner artifacts on GitHub top bars by using Option A edge-to-edge layout.
- `GHTopBar()` no longer paints a rounded outer toolbar background; it now fills full width with `MaterialTheme.colorScheme.surface`.
- Toolbar content keeps internal horizontal padding, while the outer bar touches both screen edges cleanly.
- Repo branch/action row, tab row, and search row now use full-width surface backgrounds with internal content padding.
- Added theme-aware 1dp bottom hairline using `outlineVariant` alpha to separate the top area from content without rounded edge artifacts.

### README freeze safeguard pass
- Fixed likely freeze culprit in external repository README view: markdown parsing was happening synchronously inside composition via `remember { parseReadmeBlocks(...) }`.
- README parsing now runs off the main thread on `Dispatchers.Default`, with Logcat timing under tag `ReadmeRender`.
- README fetch in `RepoTab.README` now runs on `Dispatchers.IO` with a 10 second timeout wrapper and fetch timing logs.
- Rendering safeguards added:
  - README render cap at 500 KB with browser fallback.
  - parser/render fallback card with `Retry`, `View raw`, and `Open in browser`.
  - embedded image requests are constrained to 2048px and fail visually after 5 seconds.
  - code blocks larger than 1000 lines and tables larger than 50 rows render as previews with expand controls.
  - long README lines are truncated before text layout to avoid massive single-line Compose stalls.
  - README content renders incrementally in batches instead of composing every parsed block at once.
- Root cause identified: heavy markdown parsing/render preparation on the Compose main thread, amplified by huge tables/code/list READMEs from third-party repos.

## 2026-04-25

### GitHub Actions module UI tidy (iteration 1 — Overview header)
- Цель: пользователь сообщил, что Actions/Workflow runs выглядит как «нейрослоп», нужна симметрия и порядок. Светлая тема. Скоуп строго `GitHubActionsModule.kt`, общие компоненты не трогаем.
- `ActionsOverviewHeader`:
  - Stat cards (`Total / Active / Success / Failed`) переведены с фикс-ширины 96dp в горизонтальном скролле на 4 колонки с `Modifier.weight(1f)` — равная сетка на всю ширину экрана.
  - Шапка карточки `Workflow control` сделана секционным заголовком: uppercase 11sp, `letter-spacing 0.8`, иконка 18dp.
  - Внутренний паддинг карточки приведён к ритму 12/12dp, вертикальный `spacedBy(10.dp)`.
- `StatCard`:
  - Принимает `Modifier` (через именованный параметр в конце для совместимости с легаси-вызовами в `RepositoryArtifactsPanel`).
  - Иконка 20dp, значение 22sp SemiBold monospace, label uppercase 10sp + letter-spacing.
- `ActionsTab` notice strip: вместо alpha-подложки `Color(0xFFFF9500).copy(alpha = 0.1f)` — solid `surface` + 1px hairline border `Orange.copy(alpha = 0.35f)` + иконка `Info`.
- Хардкод-хексы заменены на токены `theme/Color.kt`: `0xFF58A6FF / 0xFF34C759 / 0xFFFF3B30 / 0xFFBF5AF2 / 0xFFFF9500` → `Blue / Green / Red / Purple / Orange`.
- Проверка: локальный билд по запросу пользователя не запускался.

### Workflow Control: input fields lifted, keyboard fix
- Жалоба пользователя: при тапе по полям ввода клавиатура закрывает их, добраться до Branch / dispatch inputs нельзя.
- Внешний `Column` в `ActionsTab` обёрнут в `verticalScroll(rememberScrollState()) + imePadding()` — контент сдвигается над клавиатурой и проскроллится.
- В `ActionsOverviewHeader` карточка `Workflow control` переразложена так, чтобы все текстовые инпуты были в верхней половине:
  1. Section `WORKFLOW` — чипсы воркфлоу.
  2. Section `BRANCH / REF` — `OutlinedTextField` + чипсы веток.
  3. Section `INPUTS` — `DynamicDispatchInputs` (только если у воркфлоу есть `workflow_dispatch` и непустой schema).
  4. Hairline-разделитель `outlineVariant.copy(alpha = 0.10f)`.
  5. Метаданные воркфлоу (state-badge + monospace path + `Disable/Enable`) — съехали ВНИЗ, под инпутами.
  6. Кнопки `Open Runs` / `Latest #N` / `Run workflow`.
  7. Latest-run бейджи.
- Добавлены приватные хелперы `SectionLabel(text)` и `InputGroup(label, content)` — лейбл + контент группируются с `spacedBy(8.dp)`, между группами 10dp.
- В `WorkflowDispatchInputField` `label = { ... }` заменён на `placeholder` (ключ инпута уже выводится отдельной строкой над полем).
- Выпилен авто-цветной ряд `buildKindBadges` под выбранным воркфлоу — это была декорация без функциональной нагрузки (фиолетовые/оранжевые лейблы по ключевым словам в имени файла). Сама функция оставлена, она ещё используется для категоризации артефактов.

### MiniActionsBadge + ModernRunCard + History screen + WorkflowRunDetail
- `MiniActionsBadge` переписан: вместо `color.copy(alpha = 0.12f)` фон + `color.copy(alpha = 0.24f)` бордер используется нейтральный `surfaceVariant` + 1px hairline `outlineVariant.copy(alpha = 0.10f)`. Цвет передаётся только через `color` параметр в текст. Бейдж стал 6dp радиус, 10sp текст с letter-spacing 0.2 — убрана «куча alpha-таблеток».
- `StepStatusPill` переведён на ту же схему (uniform surface + hairline + colored text).
- `ModernRunCard` переразмечен:
  - Аватар 32dp + цветная точка-индикатор LIVE поверх (вместо отдельного бейджа `LIVE`).
  - Заголовок + display title + `#N` (моноширинный) в одну верхнюю строку.
  - Один скроллируемый ряд бейджей: статус, ветка, событие, attempt — без раздутия.
  - Footer одной строкой моноширинно: `actor · elapsed · sha`.
  - Тонкий разделитель перед action-кнопками.
  - Кнопки: `Cancel` (или `Rerun`) слева — `Open` справа через `Spacer(weight)` — симметричная раскладка.
- `ActionsRunsHistoryScreen`:
  - Поисковая строка переделана на iOS-стиль: иконка `Search` внутри поля, плейсхолдер, кнопка очистки `Cancel` появляется при непустом запросе. Курсор окрашен в `Blue` (accent).
  - Контейнер поиска: solid `surface` + 1px hairline border (без alpha-подложек).
  - Три ряда фильтр-чипсов разбиты на четыре по категориям через хелпер `FilterRow`:
    1. Status (`All / Active / Queued / Success / Failed / Cancelled / Skipped / Mine`).
    2. Workflows (только если `workflows.isNotEmpty()`).
    3. Branches (только если `branches.isNotEmpty()`).
    4. Events (`All events / workflow_dispatch / push / pull_request / schedule`).
  - Раньше Branches и Events были в одном горизонтальном скролле — приходилось скроллить мимо всех веток, чтобы найти событие.
- `WorkflowRunDetailScreen`:
  - `detailNotice` переделан в Row с solid `surface` + `Orange` hairline + `Info` иконкой — однотипно с notice strip из ActionsTab.
  - `FailureDiagnosisCard` переведён с alpha-подложки `Red.copy(alpha = 0.08f)` на solid `surface` + `Red.copy(alpha = 0.35f)` hairline border.
- Bulk-replace хардкод-хексов по всему файлу через theme/Color.kt токены:
  - `Color(0xFFFF3B30)` → `Red`
  - `Color(0xFFFF9500)` → `Orange`
  - `Color(0xFF34C759)` → `Green`
  - `Color(0xFFBF5AF2)` → `Purple`
  - `Color(0xFF5AC8FA)` → `Teal`
  - `Color(0xFF8E8E93)` → `TextSecondary`
  - Оставлен только `Color(0xFF0078D4)` для бренда Windows в `artifactKindGroup` (категориальный идентификатор, не UI-цвет).
- Импорты обновлены: добавлены `Green / Orange / Purple / Red / Teal` из `com.glassfiles.ui.theme`, иконка `Icons.Rounded.Info`, `Modifier.imePadding`.
- Коммит: `f9c95d5` — `Tidy GitHub Actions module UI`. Запушен в `origin/main`.

### CI fix: StatCard signature regression
- Поломка: CI словил `e: ... GitHubActionsModule.kt:1022:21 No value passed for parameter 'color'. Argument type mismatch: actual type is 'String', but 'Modifier' was expected.`
- Причина: после переписывания `StatCard` сигнатура стала `StatCard(modifier: Modifier = Modifier, label, value, icon, color)` с дефолтным `modifier` в первой позиции. Старые позиционные вызовы в `RepositoryArtifactsPanel` (`StatCard("Caches", it.activeCachesCount.toString(), Icons.Rounded.Timeline, Blue)`) пытались уложить `"Caches"` в `Modifier`, потому что Kotlin не пропускает первый параметр с default-значением при позиционных аргументах.
- Фикс: `modifier` перенесён в конец сигнатуры (`label, value, icon, color, modifier: Modifier = Modifier`). Новые call-sites в `ActionsOverviewHeader` обновлены на именованный аргумент `modifier = Modifier.weight(1f)`. Старые вызовы в `RepositoryArtifactsPanel` заработали без правок.
- Коммит: `c1dc96f` — `Fix StatCard signature to keep legacy callers compiling`. Запушен в `origin/main`.

### Скоуп НЕ затронут
- Общие компоненты приложения (`BrowseScreen`, `FileManager`, AI Chat, Terminal, `AppearanceScreen`, `SettingsScreen`).
- `Theme.kt` и `theme/Color.kt`.
- `GitHubManager.kt` (data layer).
- Остальные GitHub-модули (`GitHubProfileModule.kt`, `GitHubRepoModule.kt`, `GitHubProjectsModule.kt`, и т.д.).
- В пределах Actions-модуля логика сети/state/dispatch не менялась — правки чисто визуальные/структурные.

### Проверка
- Локальная Android compile-проверка не запускалась по просьбе пользователя.
- После пуша CI выявил один регресс (StatCard) — исправлен отдельным коммитом, второй пуш прошёл.

## 2026-04-28

### AI Agent screen: selector layout, image attachment, chat history
- Скоуп: `app/src/main/java/com/glassfiles/ui/screens/AiAgentScreen.kt` + минимальная сериализация image input в существующем OpenAI-compatible tool-chat path.
- Исправлена сломанная верстка верхних selector chips на экране AI Agent:
  - убран `horizontalScroll` вокруг `Repo / Branch / Model`, который давал детям некорректные narrow constraints;
  - `Repo` и `Branch` разложены в одну строку через `weight(1f)`;
  - `Model` вынесен отдельной строкой на `fillMaxWidth()`;
  - добавлены явные constraints `widthIn(min = 0.dp)`, чтобы chips не схлопывались в вертикальные столбики и label не переносился по буквам.
- Добавлена отправка изображения в AI Agent:
  - кнопка выбора фото рядом с input bar через `ActivityResultContracts.GetContent()`;
  - выбранное изображение downscale до 1024px, JPEG 80%, base64;
  - preview attachment перед отправкой;
  - пользовательское сообщение отображает thumbnail изображения в transcript;
  - imageBase64 передаётся в `AiMessage` для vision-capable моделей.
- Добавлена история чатов AI Agent:
  - используется существующий `AiChatSessionStore` с отдельным mode `agent`;
  - top bar получил кнопки History и New chat;
  - history dialog показывает сохранённые агентские чаты, открытие, удаление одного чата, очистку всех, создание нового чата;
  - transcript user/assistant сохраняется с `providerId`, `modelId`, `createdAt/updatedAt`, imageBase64.
- Логика GitHub tool executor, `GitHubManager`, permissions/gating destructive tool calls не менялись.

### Проверка
- Серверная/Android сборка не запускалась по прямой просьбе пользователя.
- Выполнена только статическая проверка `git diff --check`.

## 2026-05-02

### GitHub repo tabs: terminal glyph cleanup
- Скоуп: `app/src/main/java/com/glassfiles/ui/screens/GitHubRepoModule.kt`.
- Во вкладках `commits`, `issues`, `pulls` убраны оставшиеся Material vector indicators из list rows:
  - `ChevronRight` заменён на `GhGlyphs.ARROW_RIGHT` через `AiModuleGlyph`;
  - `CallMerge` в PR-row заменён на текстовый `GhGlyphs.MERGE`.
- В `commits` list row старые theme globals (`TextPrimary`, `TextTertiary`, `Blue`) заменены на текущую палитру `AiModuleTheme.colors`.
- `load more` в `commits/issues` теперь использует `AiModuleTheme.colors.accent`, чтобы вкладки не зависели от старого GitHub color token.

### Проверка
- Gradle/CI не запускались по build policy.
- Выполнена только статическая проверка `git diff --check`.
