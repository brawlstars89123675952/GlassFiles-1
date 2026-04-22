# План: Полная реализация GitHub API модуля для GlassFiles

## Цель
Полностью реализовать модуль интеграции с GitHub API v3 (REST) во всех аспектах: backend (API клиент), модели данных, UI экраны.

## Текущий контекст
Проект GlassFiles — Android файловый менеджер на Kotlin + Jetpack Compose.
Уже есть базовый GitHub модуль с частичной реализацией:
- GitHubManager.kt — API клиент (~500 строк, часть методов)
- 10+ UI файлов с частичной реализацией
- Нет полного покрытия API

## Что уже есть
### Backend (GitHubManager.kt)
- [x] Авторизация (токен)
- [x] Профиль пользователя
- [x] Список репозиториев
- [x] Поиск репозиториев
- [x] Создание/удаление репо
- [x] Просмотр содержимого (файлы/папки)
- [x] Чтение файлов (base64 decode)
- [x] Коммиты (список)
- [x] Issues (CRUD + комментарии)
- [x] Pull Requests (список, создание, merge)
- [x] Branches (список, create/delete)
- [x] Звёзды (star/unstar/check)
- [x] Fork
- [x] Watch
- [x] Releases (список)
- [x] Actions/Workflows (список, запуск)
- [x] Upload файлов (single + multiple)
- [x] Delete файлов
- [x] Download файлов
- [x] Clone (zipball)
- [x] README
- [x] Languages
- [x] Contributors

### UI экраны
- [x] GitHubScreen.kt — навигация между экранами
- [x] GitHubHomeModule.kt — логин, профиль, список репов, поиск
- [x] GitHubRepoModule.kt — детали репо (файлы, коммиты, issues, PRs, релизы, actions)
- [x] GitHubSettingsModule.kt — настройки, logout
- [x] GitHubGistsAndDialogsModule.kt — гисты, диалоги
- [x] GitHubMarkdownModule.kt — markdown viewer
- [x] GitHubExploreModule.kt — explore поиск
- [x] GitHubActionsModule.kt — CI/CD actions
- [x] GitHubSharedUiModule.kt — общие компоненты
- [x] GitHubRepoSettingsScreen.kt — настройки репо

## Чего НЕ хватает

### 🔴 Критично — Backend API
1. **Repositories**
   - [ ] Update repository (описание, настройки)
   - [ ] Transfer repository
   - [ ] Archive/Unarchive
   - [ ] Enable/disable features (wiki, issues, projects)
   - [ ] Collaborators (список, добавить, удалить, изменить права)
   - [ ] Topics (список, обновить)
   - [ ] Traffic (views, clones)

2. **Contents**
   - [ ] Rename/move файлов
   - [ ] Сравнение коммитов (compare)
   - [ ] Symlinks/submodules handling

3. **Commits**
   - [ ] Детали коммита (полный diff)
   - [ ] Статус коммита (check runs)

4. **Branches**
   - [ ] Branch protection rules
   - [ ] Required status checks
   - [ ] Merge methods (squash, rebase)

5. **Pull Requests**
   - [ ] PR files changed (список файлов)
   - [ ] PR diff
   - [ ] PR reviews (создать, список)
   - [ ] PR review comments
   - [ ] Update PR (title, body, state)
   - [ ] Close/reopen PR

6. **Issues**
   - [ ] Labels (список, создать, назначить)
   - [ ] Assignees (назначить)
   - [ ] Milestones
   - [ ] Issue templates
   - [ ] Lock/unlock issue

7. **Releases**
   - [ ] Create release
   - [ ] Update release
   - [ ] Delete release
   - [ ] Upload release assets

8. **Actions**
   - [ ] Workflow logs (download)
   - [ ] Re-run workflow
   - [ ] Cancel workflow
   - [ ] Workflow dispatch with inputs

9. **Notifications**
   - [ ] Список notifications
   - [ ] Mark as read
   - [ ] Mark all as read

10. **Search**
    - [ ] Code search
    - [ ] Commits search
    - [ ] Issues search
    - [ ] Users search
    - [ ] Topics search

11. **Users/Orgs**
    - [ ] Follow/unfollow
    - [ ] User repositories
    - [ ] Organization repositories
    - [ ] Team repositories

12. **Gists**
    - [ ] Создать gist
    - [ ] Update gist
    - [ ] Delete gist
    - [ ] Star/unstar gist
    - [ ] Fork gist
    - [ ] Comments on gists

13. **Git Data (low-level)**
    - [ ] Trees (get/create)
    - [ ] Blobs (create)
    - [ ] References (update)

### 🟡 Средний приоритет — UI
1. **Редактор файлов**
   - [ ] In-app редактор кода
   - [ ] Сохранение изменений (commit)
   - [ ] Syntax highlighting

2. **Diff viewer**
   - [ ] Просмотр изменений
   - [ ] Side-by-side diff
   - [ ] Inline diff

3. **Image viewer**
   - [ ] Просмотр изображений из репо
   - [ ] Gallery mode

4. **Markdown editor**
   - [ ] WYSIWYG редактор
   - [ ] Preview mode

5. **Notifications center**
   - [ ] Список уведомлений
   - [ ] Фильтры (repo, type, read/unread)

6. **Profile viewer**
   - [ ] Профиль любого пользователя
   - [ ] Activity graph
   - [ ] Repositories list

### 🟢 Низкий приоритет
- [ ] GitHub Pages status
- [ ] Repository vulnerability alerts
- [ ] Dependency graph
- [ ] Code scanning alerts
- [ ] Secret scanning alerts
- [ ] Webhooks (список)
- [ ] Deploy keys
- [ ] GitHub Apps

## Подход к реализации

### Фаза 1: Backend (GitHubManager.kt)
1. Добавить недостающие data classes в `data/github/`
2. Расширить GitHubManager.kt всеми методами
3. Группировать методы по секциям (Repos, Issues, PRs, etc.)

### Фаза 2: UI Модели
1. Создать недостающие экраны:
   - CodeEditorScreen.kt
   - DiffViewerScreen.kt
   - NotificationsScreen.kt
   - ProfileScreen.kt (любой пользователь)
   - ReleaseCreateScreen.kt
   - CollaboratorsScreen.kt
   - Settings/BranchesProtectionScreen.kt

### Фаза 3: Интеграция
1. Связать все экраны с навигацией
2. Добавить deep links
3. Обработка ошибок и edge cases

## Файлы для изменения

### Существующие (расширить)
- `app/src/main/java/com/glassfiles/data/github/GitHubManager.kt`
- `app/src/main/java/com/glassfiles/data/github/GitHubModels.kt` (или создать)
- `app/src/main/java/com/glassfiles/ui/screens/GitHubScreen.kt`
- `app/src/main/java/com/glassfiles/ui/screens/GitHubRepoModule.kt`

### Новые файлы (создать)
- `app/src/main/java/com/glassfiles/data/github/GitHubModels.kt` — все data classes
- `app/src/main/java/com/glassfiles/ui/screens/GitHubCodeEditorModule.kt`
- `app/src/main/java/com/glassfiles/ui/screens/GitHubDiffModule.kt`
- `app/src/main/java/com/glassfiles/ui/screens/GitHubNotificationsModule.kt`
- `app/src/main/java/com/glassfiles/ui/screens/GitHubProfileModule.kt`
- `app/src/main/java/com/glassfiles/ui/screens/GitHubReleasesModule.kt` (create/edit)
- `app/src/main/java/com/glassfiles/ui/screens/GitHubCollaboratorsModule.kt`

## Тестирование
1. Unit tests для GitHubManager (mock API)
2. UI tests для основных flow
3. Manual testing на реальном репозитории

## Риски
- Размер файлов может превысить лимиты IDE
- Rate limiting GitHub API (60/5000 requests)
- Сложность UI с Compose
- Время реализации (оценка: 40-60 часов)

## Открытые вопросы
1. Нужна ли поддержка GitHub Enterprise?
2. Нужен ли offline mode (кэширование)?
3. Какие фичи приоритетнее?
