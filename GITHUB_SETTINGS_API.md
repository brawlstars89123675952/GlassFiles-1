# GitHub Settings API — что можно интегрировать в GlassFiles

Справочник по тому, какие пункты из GitHub `Settings` можно интегрировать
в мобильный клиент через публичный REST API, что урезано, а что
полностью недоступно.

## Легенда

- ✅ **Доступно** — есть полноценный публичный API, можно реализовать
- ⚠️ **Частично** — есть API, но с ограничениями (только чтение, либо
  не все поля, либо требует особых scope)
- ❌ **Недоступно** — нет публичного API, реализовать нельзя
- 🎯 **Приоритет** — приоритет для GlassFiles (HIGH / MED / LOW)

---

## 1. Public profile

**Статус:** ✅ Доступно
**Приоритет:** 🎯 HIGH

Endpoints:
- `GET /user` — получить свой профиль
- `PATCH /user` — обновить поля профиля

Редактируемые поля:
- `name` — имя
- `bio` — биография
- `company` — компания
- `location` — локация
- `blog` — URL сайта/блога
- `twitter_username` — Twitter/X username
- `email` — публичный email (только из тех, что добавлены в аккаунт)
- `hireable` — открыт к предложениям

**Ограничения:**
- Аватарку через API изменить **нельзя** — только через web-интерфейс
- Pronouns изменить через API **нельзя**
- ProfileREADME (приколотый репозиторий-визитка) изменяется через
  обычные repo content endpoints

**Польза для GlassFiles:**
- Пользователь может обновить bio/локацию прямо из приложения
- Простой экран с формой редактирования
- Решает баг с "null" в имени и описании на текущем профиле

---

## 2. Account

**Статус:** ⚠️ Частично

Что можно через API:
- ✅ Прочитать basic info (через `GET /user`)
- ✅ Изменить username — `PATCH /user` с полем `login`
  ⚠️ Но это рискованная операция, перенаправит все ссылки

Что **нельзя** через API:
- ❌ Удалить аккаунт
- ❌ Деактивировать
- ❌ Сменить тип (Personal → Organization)
- ❌ Экспортировать данные

**Приоритет:** 🎯 LOW — основные операции опасны или невозможны

---

## 3. Appearance

**Статус:** ❌ Недоступно

Тема (light/dark/auto) и tab size GitHub-сайта — **исключительно
client-side настройки браузера**. Не сохраняются на сервере, нет API.

**Польза для GlassFiles:** нулевая. У тебя своя тема в приложении,
к GitHub Settings не привязана.

---

## 4. Accessibility

**Статус:** ❌ Недоступно

Те же self-only client-side настройки. Нет API.

---

## 5. Notifications

**Статус:** ✅ Доступно
**Приоритет:** 🎯 HIGH (must have)

Endpoints:
- `GET /notifications` — список unread notifications
- `GET /notifications?all=true` — все, включая прочитанные
- `PATCH /notifications` — пометить все прочитанными
- `GET /notifications/threads/{thread_id}` — детали одного уведомления
- `PATCH /notifications/threads/{thread_id}` — пометить одно прочитанным
- `DELETE /notifications/threads/{thread_id}/subscription` — отписаться
- `PUT /notifications/threads/{thread_id}/subscription` — настроить
  (subscribed / ignored)
- `GET /repos/{owner}/{repo}/notifications` — для конкретного репо
- `PUT /repos/{owner}/{repo}/notifications` — пометить прочитанными в репо

Параметры фильтра:
- `participating` — только где ты участник
- `since` — с какого момента
- `before` — до какого момента

**Что нельзя:**
- ❌ Управлять настройками email-уведомлений (web-only)
- ❌ Push-notifications настраиваются на уровне Web/Mobile приложения
  GitHub, не через API
- ❌ Custom routing (рабочий email vs личный) — web-only

**Польза для GlassFiles:**
- Это **сердце** мобильного клиента
- На текущем профиле уже есть синяя точка на иконке inbox — фичу
  стоит развивать
- Можно сделать вкладку Notifications с группировкой по репо/типу
- Тапнул уведомление → перешёл к соответствующему PR/Issue/Discussion

---

## 6. Billing and licensing

**Статус:** ⚠️ Частично

Endpoints:
- `GET /user/settings/billing/actions` — usage Actions minutes
- `GET /user/settings/billing/packages` — usage Packages
- `GET /user/settings/billing/shared-storage` — usage Storage

Что нельзя:
- ❌ Подписку Pro / Team / Enterprise через API не покупаешь
- ❌ Carbon billing details (детальная стоимость) — недоступны
- ❌ Способы оплаты (карты) — web-only
- ❌ Скачивать invoices через API

**Польза для GlassFiles:** низкая. Юзеру обычно достаточно увидеть
сколько Actions-минут он сжёг, остальное мобильным клиентам не нужно.

**Приоритет:** 🎯 LOW (опционально, виджет "осталось X минут")

---

## 7. Emails

**Статус:** ✅ Доступно
**Приоритет:** 🎯 MED

Endpoints:
- `GET /user/emails` — список всех email'ов аккаунта
- `POST /user/emails` — добавить email (требует подтверждения через
  верификационное письмо)
- `DELETE /user/emails` — удалить email
- `PATCH /user/email/visibility` — выбрать публичный email

Что нельзя:
- ❌ Подтвердить email через API (только через клик в письме)
- ❌ Изменить primary email через API (можно только через web)

**Польза для GlassFiles:**
- Удобно если у юзера несколько email'ов (личный + рабочий)
- Простой экран список/добавить/удалить

---

## 8. Password and authentication

**Статус:** ❌ Почти полностью недоступно

Что нельзя через API:
- ❌ Сменить пароль
- ❌ Включить/отключить 2FA
- ❌ Управлять recovery codes
- ❌ Управлять passkeys
- ❌ Управлять authenticator apps

Что можно:
- ⚠️ Видеть статус 2FA через `GET /user` поле `two_factor_authentication`

**Польза для GlassFiles:** околонулевая. Безопасность аутентификации —
строго через web.

---

## 9. Sessions

**Статус:** ❌ Недоступно через публичный API

Управление активными SSH-сессиями и web-сессиями есть только в
web-интерфейсе. API нет.

---

## 10. SSH and GPG keys

**Статус:** ✅ Доступно
**Приоритет:** 🎯 HIGH (уникальная фича для мобильного клиента)

### SSH keys

Endpoints:
- `GET /user/keys` — список SSH-ключей аккаунта
- `GET /user/keys/{id}` — конкретный ключ
- `POST /user/keys` — добавить SSH-ключ (поля: `title`, `key`)
- `DELETE /user/keys/{id}` — удалить ключ
- `GET /users/{username}/keys` — публичные ключи любого юзера

### GPG keys

Endpoints:
- `GET /user/gpg_keys` — список
- `GET /user/gpg_keys/{id}` — детали
- `POST /user/gpg_keys` — добавить
- `DELETE /user/gpg_keys/{id}` — удалить

### Signing keys (новый тип)

Endpoints:
- `GET /user/ssh_signing_keys`
- `POST /user/ssh_signing_keys`
- `DELETE /user/ssh_signing_keys/{id}`

**Польза для GlassFiles:** **очень высокая**.

Сценарий: пользователь генерирует SSH-ключ в Termux на телефоне,
открывает GlassFiles, тапает "Add SSH key", вставляет pub-key из
буфера, готово. Без открытия github.com.

Это **уникальная фича** для мобильного клиента — официальный GitHub
Mobile этого не делает удобно.

---

## 11. Organizations

**Статус:** ⚠️ Частично

Endpoints:
- `GET /user/orgs` — список твоих организаций
- `GET /orgs/{org}` — данные организации
- `GET /user/memberships/orgs` — твои membership'ы
- `PATCH /user/memberships/orgs/{org}` — принять/отклонить приглашение
- `DELETE /orgs/{org}/memberships/{username}` — выйти из организации

Что нельзя:
- ❌ Создать новую организацию через API (только через web)
- ❌ Удалить организацию через API
- ❌ Изменять billing организации

**Польза для GlassFiles:** есть смысл показывать список организаций
(у тебя уже есть в профиле). Полное управление — нет.

**Приоритет:** 🎯 LOW (read-only достаточно)

---

## 12. Enterprises

**Статус:** ⚠️ Частично, для Enterprise-плана

Endpoints (только для Enterprise):
- `GET /enterprises/{enterprise}` — данные
- Audit log endpoints
- License management endpoints

**Польза для GlassFiles:** для индивидуальных разработчиков нулевая.
Целевая аудитория твоего приложения — solo devs и small teams.

**Приоритет:** 🎯 SKIP (не делать)

---

## 13. Moderation

**Статус:** ⚠️ Частично

### Blocked users

- `GET /user/blocks` — список заблокированных
- `GET /user/blocks/{username}` — проверить заблокирован ли
- `PUT /user/blocks/{username}` — заблокировать
- `DELETE /user/blocks/{username}` — разблокировать

### Interaction limits (для своих репо)

- `GET /repos/{owner}/{repo}/interaction-limits`
- `PUT /repos/{owner}/{repo}/interaction-limits`
- `DELETE /repos/{owner}/{repo}/interaction-limits`

**Польза для GlassFiles:** низкая, но дёшево добавить.

**Приоритет:** 🎯 LOW

---

## 14. Repositories

**Статус:** ✅ Доступно
**Приоритет:** 🎯 HIGH (создание репо), 🎯 MED (settings репо)

Endpoints:
- `GET /user/repos` — твои репозитории
- `POST /user/repos` — создать новый репо (поля: `name`, `description`,
  `private`, `auto_init`, `gitignore_template`, `license_template`,
  `homepage`, и др.)
- `PATCH /repos/{owner}/{repo}` — изменить настройки репо
- `DELETE /repos/{owner}/{repo}` — удалить (опасно, требует scope `delete_repo`)
- `POST /repos/{owner}/{repo}/forks` — форкнуть
- `POST /repos/{owner}/{repo}/transfer` — передать владельцу

**Польза для GlassFiles:**
- "Новый репо" — кнопка прямо из профиля. Заполнил форму, тапнул
  Create, репо создан
- Settings репо: name, description, default branch, topics, visibility
  — у тебя **уже есть** этот экран

---

## 15. Codespaces

**Статус:** ⚠️ Частично

Endpoints:
- `GET /user/codespaces` — список твоих codespaces
- `POST /user/codespaces` — создать новый
- `GET /user/codespaces/{name}` — детали
- `DELETE /user/codespaces/{name}` — удалить
- `POST /user/codespaces/{name}/start` — запустить
- `POST /user/codespaces/{name}/stop` — остановить

Что нельзя:
- ❌ Использовать сам редактор VS Code Server через API
  (он работает по WebSocket, не REST). Только через WebView.

**Польза для GlassFiles:** низкая. Можно показать список codespaces
и управлять ими, но **редактировать в них нельзя без WebView**.
Сложность интеграции высокая, целевая ниша узкая.

**Приоритет:** 🎯 SKIP (отложено, см. отдельное обсуждение)

---

## 16. Models (Preview)

**Статус:** ⚠️ Preview-фича, API нестабилен

GitHub Models — это AI-модели от Microsoft Azure доступные через
GitHub. Preview-функция, API может меняться.

**Польза для GlassFiles:** мобильному клиенту не нужно. У тебя уже
есть свой AI-чат с Qwen.

**Приоритет:** 🎯 SKIP

---

## 17. Packages

**Статус:** ✅ Доступно
**Приоритет:** 🎯 LOW

Endpoints:
- `GET /user/packages` — твои пакеты (npm, maven, docker, и т.д.)
- `GET /users/{username}/packages` — пакеты юзера
- `GET /user/packages/{package_type}/{package_name}` — детали
- `DELETE /user/packages/{package_type}/{package_name}` — удалить
- `POST /user/packages/{package_type}/{package_name}/restore` — восстановить
- `GET /user/packages/{package_type}/{package_name}/versions` — версии

**Польза для GlassFiles:** для разработчиков пакетов — да. Узкая
аудитория, можно отложить.

---

## 18. Copilot

**Статус:** ⚠️ Частично

Endpoints:
- `GET /user/copilot/billing` — статус подписки
- `GET /orgs/{org}/copilot/billing/seats` — для организаций

Что нельзя:
- ❌ Использовать Copilot completions через GitHub API (они доступны
  только через VS Code/JetBrains-плагины и Copilot Chat)
- ❌ Включать/выключать подписку через API

**Польза для GlassFiles:** околонулевая. Copilot интегрирован в
IDE, не в API.

**Приоритет:** 🎯 SKIP

---

## 19. Pages

**Статус:** ✅ Доступно (для отдельного репо)

Endpoints (на уровне репо):
- `GET /repos/{owner}/{repo}/pages` — данные Pages
- `POST /repos/{owner}/{repo}/pages` — включить Pages
- `PUT /repos/{owner}/{repo}/pages` — обновить настройки
- `DELETE /repos/{owner}/{repo}/pages` — отключить
- `POST /repos/{owner}/{repo}/pages/builds` — запустить build

**Польза для GlassFiles:** узкая ниша (те кто хостит сайты на Pages).
Можно интегрировать в экран settings репозитория.

**Приоритет:** 🎯 LOW

---

## 20. Saved replies

**Статус:** ❌ Нет публичного API

Saved replies (заготовки ответов на issues/PR) сохраняются только
в web-интерфейсе. API не предоставлен.

**Приоритет:** 🎯 SKIP

---

## 21. Code security

**Статус:** ⚠️ Частично

Много sub-endpoints для:
- Dependabot alerts/updates
- Code scanning alerts
- Secret scanning alerts
- Security advisories

Endpoints (на уровне репо):
- `GET /repos/{owner}/{repo}/dependabot/alerts`
- `GET /repos/{owner}/{repo}/code-scanning/alerts`
- `GET /repos/{owner}/{repo}/secret-scanning/alerts`
- И многое другое

**Польза для GlassFiles:** **есть** (у тебя уже `GitHubSecurityModule.kt`
на 1521 строку). Это полезная фича для отслеживания уязвимостей в
проектах. Уже реализовано.

**Приоритет:** 🎯 (уже сделано) — поддерживать

---

## 22. Applications

**Статус:** ⚠️ Частично

Endpoints:
- `GET /user/installations` — установленные GitHub Apps
- `DELETE /user/installations/{installation_id}` — удалить
- `GET /user/installations/{installation_id}/repositories` — репо доступа

Что нельзя:
- ❌ Авторизовать новые OAuth-приложения через API (только web)

**Польза для GlassFiles:** возможность отозвать доступ у скомпрометированного
приложения. Дёшево добавить.

**Приоритет:** 🎯 LOW

---

## 23. Scheduled reminders

**Статус:** ❌ Нет публичного API

Slack/Microsoft Teams интеграция, web-only настройки.

**Приоритет:** 🎯 SKIP

---

## 24. Security log

**Статус:** ⚠️ Только для организаций / Enterprise

Endpoints:
- `GET /orgs/{org}/audit-log` — для Org admin
- `GET /enterprises/{enterprise}/audit-log` — для Enterprise

Для personal-аккаунтов **нет** audit-log API.

**Приоритет:** 🎯 SKIP (только если делаешь Enterprise-features)

---

## 25. Sponsorship log

**Статус:** ⚠️ Частично через GraphQL

GraphQL API позволяет получить sponsors data, но это узкая ниша.

**Приоритет:** 🎯 SKIP

---

## 26. Developer settings

**Статус:** ⚠️ Частично

### Personal Access Tokens (PAT)

Что можно:
- `GET /user/tokens` — **только перечислить** существующие PAT
- `DELETE /user/tokens/{id}` — отозвать

Что **нельзя**:
- ❌ Создать новый PAT через API — намеренно, для безопасности.
  Только через web github.com/settings/tokens

### OAuth Apps

Что можно:
- `GET /user/installations` — список installations
- Управление scope существующих OAuth Apps — частично

### GitHub Apps

- `GET /user/marketplace_purchases` — твои купленные Apps
- Управление приложениями — для разработчиков Apps

**Польза для GlassFiles:**
- Список существующих PAT с возможностью отозвать — полезно для безопасности
- Создание нового PAT не сделать никак

**Приоритет:** 🎯 LOW (только просмотр и отзыв PAT)

---

## Дополнительные API не из Settings, но полезные

### Followers / Following

**Статус:** ✅ Доступно
**Приоритет:** 🎯 MED

- `GET /user/followers` — твои подписчики
- `GET /user/following` — на кого ты подписан
- `GET /users/{username}/followers`
- `GET /users/{username}/following`
- `PUT /user/following/{username}` — подписаться
- `DELETE /user/following/{username}` — отписаться
- `GET /user/following/{username}` — проверить подписку

### Starred repositories

**Статус:** ✅ Доступно
**Приоритет:** 🎯 MED (улучшение существующего)

- `GET /user/starred` — твои starred repos
- `PUT /user/starred/{owner}/{repo}` — добавить в starred
- `DELETE /user/starred/{owner}/{repo}` — убрать
- `GET /user/starred/{owner}/{repo}` — проверить

У тебя уже есть Starred. Можно сделать кнопку Star/Unstar **прямо
в карточке репозитория**, а не только через специальный экран.

---

## Резюме по приоритетам для GlassFiles

### 🎯 HIGH — реализовать в первую очередь

1. **Notifications** (#5) — must have для мобильного клиента
2. **SSH and GPG keys** (#10) — уникальная фича для мобильного
3. **Public profile editing** (#1) — простой экран, решает проблему "null"
4. **Repositories: создать новый** (#14) — кнопка из профиля

### 🎯 MED — желательно

5. **Followers / Following** (доп) — социальные функции
6. **Star/Unstar в карточках** — улучшение UX
7. **Emails management** (#7) — для тех у кого несколько email'ов
8. **Repositories: settings** (#14) — у тебя уже есть, продолжать

### 🎯 LOW — если будет время

9. **Blocked users** (#13) — дёшево добавить
10. **Personal Access Tokens: список и отзыв** (#26) — безопасность
11. **Billing: usage stats** (#6) — виджет "сколько Actions-минут осталось"
12. **Applications: список и отзыв** (#22) — безопасность
13. **Pages settings для репо** (#19) — узкая ниша
14. **Packages: list / view** (#17) — узкая ниша

### 🎯 SKIP — не делать

- Account management (#2) — операции опасны или web-only
- Appearance / Accessibility (#3, #4) — нет API, у тебя своя тема
- Password / 2FA (#8) — нет API
- Sessions (#9) — нет публичного API
- Enterprises (#12) — не для целевой аудитории
- Codespaces (#15) — сложно интегрировать редактор
- Models (#16) — preview, нестабильно
- Copilot (#18) — нет API completions
- Saved replies (#20) — нет API
- Scheduled reminders (#23) — нет API
- Security log (#24) — только для Org/Enterprise
- Sponsorship log (#25) — узкая ниша
- Developer settings: создание PAT (#26) — намеренно нет API

---

## Принципы при добавлении новых API в GitHubManager

Когда дойдёт до реализации этих фич — следуй существующим паттернам:

1. **Каждая новая публичная функция** в `GitHubManager.kt` должна
   использовать общий `request()` хелпер, не дублировать auth/error
   handling

2. **Data classes** именуй с префиксом `GH` для consistency:
   `GHNotification`, `GHSshKey`, `GHGpgKey`, `GHBlock`, и т.д.

3. **ViewModels** для каждой новой фичи — отдельный файл, не пихай в
   существующие модули

4. **Compose-экраны** — отдельный файл на экран, не добавляй в
   `GitHubManager.kt` ни одной строчки UI

5. **Триггеры рефакторинга**: если добавление новых API раздуло
   `GitHubManager.kt` до 7000+ строк — пора резать на отдельные
   API-классы по доменам (см. `GITHUB_MANAGER_MAP.md`)

---

## Источники для проверки актуальности

- Официальная документация: https://docs.github.com/en/rest
- Список изменений: https://docs.github.com/en/rest/overview/api-versions
- GraphQL API: https://docs.github.com/en/graphql

API GitHub эволюционирует — некоторые endpoint'ы deprecated, новые
появляются. Перед реализацией фичи **сверяйся с текущей документацией**.

---

*Файл создан как справочник на этапе планирования расширения GlassFiles.
Обновляй по мере реализации фич — отмечай реализованные ✅ и
актуальные приоритеты.*