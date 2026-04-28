# BUGS

### Бага: Кнопки редактирования показываются на чужих репо

Симптомы:
- Открываешь чужой репозиторий (например d2phap/ImageGlass)
- Видишь кнопки: Edit file, Delete file, Create release, New PR, и т.д.
- Тапаешь — получаешь ошибку 403 Forbidden от GitHub API
- Без объяснения юзеру что у него нет прав

Что должно быть:
- Кнопки редактирования / создания скрыты ИЛИ disabled на чужих репо
- Показываются только read-only действия: Star, Fork, Watch, Clone, View
- Если у юзера есть push-доступ (через permissions.push) — кнопки активны

Реализация:
- В GHRepo data class добавить поле permissions: GHPermissions?
  data class GHPermissions(val admin: Boolean, val push: Boolean,
                           val pull: Boolean, val maintain: Boolean,
                           val triage: Boolean)
- При парсинге репо извлекать поле "permissions" из API response
- В UI каждого экрана: проверять repo.permissions?.push == true
  перед показом кнопок редактирования
- Глобальный helper:
  fun GHRepo.canWrite(): Boolean = permissions?.push == true
  fun GHRepo.canAdmin(): Boolean = permissions?.admin == true

Где конкретно прятать кнопки:
- Files tab: кнопка "Edit", "Delete", "Create new file"
- Releases tab: кнопка "+" (create release), Edit/Delete на existing
- Issues: кнопка "+" (create issue) — это можно оставить, любой
  может создать issue в публичном репо если не отключено
- PRs: кнопка "+" (create PR) — оставить, fork-and-PR workflow
- Settings tab — скрыть полностью если canAdmin() == false
- Actions: кнопка "Run workflow" → скрыть если !canWrite()
- Сборщик: тоже скрыть если !canWrite()
- Branches: кнопка delete branch → скрыть если !canWrite()
- Webhooks: скрыть полностью если !canAdmin()

Дополнительно:
- При первом открытии репо показать badge "Read-only" если нет
  write доступа — пользователь сразу понимает контекст
