package com.glassfiles.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage(val code: String, val label: String, val flag: String) {
    RUSSIAN("ru", "Русский", "🇷🇺"),
    ENGLISH("en", "English", "🇺🇸")
}

object Strings {
    var lang by mutableStateOf(AppLanguage.RUSSIAN)

    // ═══════════════════════════════════
    // Common
    // ═══════════════════════════════════
    val cancel get() = s("Отмена", "Cancel")
    val ok get() = s("ОК", "OK")
    val delete get() = s("Удалить", "Delete")
    val rename get() = s("Переименовать", "Rename")
    val copy get() = s("Копировать", "Copy")
    val move get() = s("Перенести", "Move")
    val share get() = s("Поделиться", "Share")
    val create get() = s("Создать", "Create")
    val search get() = s("Поиск", "Search")
    val settings get() = s("Настройки", "Settings")
    val settingsSub get() = s("Язык, AI, файловый менеджер", "Language, AI, file manager")
    val done get() = s("Готово", "Done")
    val error get() = s("Ошибка", "Error")
    val close get() = s("Закрыть", "Close")
    val back get() = s("Назад", "Back")
    val copied get() = s("Скопировано", "Copied")
    val deleted get() = s("Удалено", "Deleted")
    val objects get() = s("объектов", "items")

    // ═══════════════════════════════════
    // Browse Screen
    // ═══════════════════════════════════
    val browse get() = s("Обзор", "Browse")
    val favorites get() = s("Избранное", "Favorites")
    val locations get() = s("Места", "Locations")
    val tools get() = s("Инструменты", "Tools")
    val tags get() = s("Теги", "Tags")
    val searchOnDevice get() = s("Поиск по устройству", "Search on device")
    val onMyDevice get() = s("На моём устройстве", "On my device")
    val downloads get() = s("Загрузки", "Downloads")
    val documents get() = s("Документы", "Documents")
    val photos get() = s("Фото", "Photos")
    val music get() = s("Музыка", "Music")
    val trash get() = s("Корзина", "Trash")
    val tapToSignIn get() = s("Нажмите для входа", "Tap to sign in")
    val lowStorage get() = s("Мало места", "Low storage")
    val freeSpace get() = s("свободно", "free")
    val noFavorites get() = s("Нет избранных папок", "No favorite folders")

    // Tools
    val storageAnalysis get() = s("Анализ хранилища", "Storage analysis")
    val whatTakesSpace get() = s("Что занимает место", "What takes space")
    val duplicates get() = s("Дубликаты", "Duplicates")
    val findDuplicates get() = s("Найти одинаковые", "Find duplicates")
    val qrScanner get() = s("QR Сканер", "QR Scanner")
    val scanQrCode get() = s("Сканировать QR-код", "Scan QR code")
    val recognizeText get() = s("Распознать текст", "Recognize text")
    val ocrSubtitle get() = s("OCR — текст с фото", "OCR — text from photo")

    // Tags
    val tagRed get() = s("Красный", "Red")
    val tagOrange get() = s("Оранжевый", "Orange")
    val tagYellow get() = s("Жёлтый", "Yellow")
    val tagGreen get() = s("Зелёный", "Green")
    val tagBlue get() = s("Синий", "Blue")
    val tagPurple get() = s("Фиолетовый", "Purple")
    val tagGray get() = s("Серый", "Gray")

    // ═══════════════════════════════════
    // Tabs
    // ═══════════════════════════════════
    val recents get() = s("Недавние", "Recents")
    val shared get() = s("Общие", "Shared")

    // ═══════════════════════════════════
    // Folder Detail
    // ═══════════════════════════════════
    val folderEmpty get() = s("Папка пуста", "Folder is empty")
    val nothingFound get() = s("Ничего не найдено", "Nothing found")
    val selected get() = s("выбрано", "selected")
    val selectAll get() = s("Выбрать все", "Select all")
    val deselect get() = s("Снять", "Deselect")
    val grid get() = s("Сетка", "Grid")
    val list get() = s("Список", "List")
    val toTrash get() = s("В корзину", "To trash")
    val confirmDeleteTitle get() = s("Удалить?", "Delete?")
    val willBeMovedToTrash get() = s("будет перемещён в корзину", "will be moved to trash")
    val pasteHere get() = s("Вставить", "Paste")
    val pathCopied get() = s("Путь скопирован", "Path copied")

    // Sort
    val sortName get() = s("Имя", "Name")
    val sortDate get() = s("Дата", "Date")
    val sortSize get() = s("Размер", "Size")
    val sortType get() = s("Тип", "Type")

    // ═══════════════════════════════════
    // Context Menu
    // ═══════════════════════════════════
    val openWith get() = s("Открыть в…", "Open with…")
    val compress get() = s("Сжать в ZIP", "Compress to ZIP")
    val compressFolder get() = s("Сжать папку", "Compress folder")
    val decompress get() = s("Распаковать", "Extract")
    val addToFavorites get() = s("В избранное", "Add to favorites")
    val removeFromFavorites get() = s("Убрать из избранного", "Remove from favorites")
    val installApk get() = s("Установить APK", "Install APK")
    val openInTerminal get() = s("Открыть в терминале", "Open in terminal")
    val copyPath get() = s("Копировать путь", "Copy path")
    val properties get() = s("Свойства", "Properties")
    val convertImage get() = s("Конвертировать фото", "Convert image")
    val describePhoto get() = s("Описать фото (AI)", "Describe photo (AI)")
    val summarizeFile get() = s("Резюме файла (AI)", "Summarize file (AI)")
    val encrypt get() = s("Зашифровать", "Encrypt")
    val decrypt get() = s("Расшифровать", "Decrypt")

    // ═══════════════════════════════════
    // Feature Dialogs
    // ═══════════════════════════════════
    val batchRename get() = s("Массовое переименование", "Batch rename")
    val findAndReplace get() = s("Найти и заменить", "Find and replace")
    val addPrefix get() = s("Добавить префикс", "Add prefix")
    val addSuffix get() = s("Добавить суффикс", "Add suffix")
    val sequence get() = s("Пронумеровать", "Number sequence")
    val toLowercase get() = s("В нижний регистр", "To lowercase")
    val toUppercase get() = s("В верхний регистр", "To uppercase")
    val preview get() = s("Превью", "Preview")
    val renamed get() = s("Переименовано", "Renamed")
    val errors get() = s("ошибок", "errors")

    val password get() = s("Пароль", "Password")
    val confirmPassword get() = s("Подтвердите пароль", "Confirm password")
    val minChars get() = s("Минимум 4 символа", "Minimum 4 characters")
    val passwordsMismatch get() = s("Пароли не совпадают", "Passwords don't match")
    val encrypted get() = s("Зашифровано", "Encrypted")
    val decrypted get() = s("Расшифровано", "Decrypted")

    val createFolder get() = s("Папка", "Folder")
    val createFile get() = s("Файл", "File")
    val folderName get() = s("Имя папки", "Folder name")
    val fileName get() = s("Имя файла (с расширением)", "File name (with extension)")
    val created get() = s("Создано", "Created")

    // ═══════════════════════════════════
    // Duplicates
    // ═══════════════════════════════════
    val scanning get() = s("Сканирование...", "Scanning...")
    val files get() = s("Файлов", "Files")
    val duplicatesFound get() = s("Дубликатов", "Duplicates")
    val noDuplicates get() = s("Дубликатов не найдено", "No duplicates found")
    val storageClean get() = s("Хранилище чистое", "Storage is clean")
    val duplicateGroups get() = s("групп дубликатов", "duplicate groups")
    val canFree get() = s("Можно освободить", "Can free up")

    // ═══════════════════════════════════
    // QR Scanner
    // ═══════════════════════════════════
    val needCameraAccess get() = s("Нужен доступ к камере", "Camera access needed")
    val allow get() = s("Разрешить", "Allow")
    val scanMore get() = s("Сканировать ещё", "Scan more")
    val pointCamera get() = s("Наведите камеру на QR-код", "Point camera at QR code")
    val open get() = s("Открыть", "Open")

    // ═══════════════════════════════════
    // OCR
    // ═══════════════════════════════════
    val recognizing get() = s("Распознавание...", "Recognizing...")
    val recognizeFromImage get() = s("Распознать текст с изображения", "Recognize text from image")
    val selectPhoto get() = s("Выберите фото для извлечения текста", "Select photo to extract text")
    val choosePhoto get() = s("Выбрать фото", "Choose photo")
    val anotherPhoto get() = s("Другое фото", "Another photo")
    val textCopied get() = s("Текст скопирован", "Text copied")
    val textNotFound get() = s("Текст не найден", "No text found")
    val foundText get() = s("Найден текст:", "Found text:")

    // ═══════════════════════════════════
    // Settings
    // ═══════════════════════════════════
    val appearance get() = s("Внешний вид", "Appearance")
    val theme get() = s("Тема", "Theme")
    val themeLight get() = s("Светлая", "Light")
    val themeDark get() = s("Тёмная", "Dark")
    val themeSystem get() = s("Система", "System")
    val themeAmoled get() = s("AMOLED", "AMOLED")
    val language get() = s("Язык", "Language")
    val fileManager get() = s("Файловый менеджер", "File Manager")
    val showHiddenFiles get() = s("Скрытые файлы", "Hidden files")
    val defaultSort get() = s("Сортировка по умолчанию", "Default sort")
    val defaultView get() = s("Вид по умолчанию", "Default view")
    val confirmDelete get() = s("Подтверждение удаления", "Confirm delete")
    val aiChat get() = s("AI Чаты", "AI Chats")
    val aiHub get() = s("AI", "AI")
    val aiHubSubtitle get() = s("Чат, код, картинки, видео, музыка", "Chat, code, images, video, music")
    val aiSectionWorkspaces get() = s("workspaces", "workspaces")
    val aiSectionGeneration get() = s("generation", "generation")
    val aiSectionConfig get() = s("config", "config")
    val aiCoding get() = s("Режим кодинга", "Coding mode")
    val aiCodingSubtitle get() = s("Готовый код, дифы, рефакторинг", "Ready code, diffs, refactoring")
    val aiImageGen get() = s("Генерация картинок", "Image generation")
    val aiImageGenSubtitle get() = s("DALL·E, Imagen, Wanx, Grok Imagine", "DALL·E, Imagen, Wanx, Grok Imagine")
    val aiVideoGen get() = s("Генерация видео", "Video generation")
    val aiVideoGenSubtitle get() = s("Veo, Wan-Video, Grok Video", "Veo, Wan-Video, Grok Video")
    val aiMusicGen get() = s("Генерация музыки", "Music generation")
    val aiMusicGenSubtitle get() = s("ACEMusic engine · form release_task", "ACEMusic engine · form release_task")
    val aiMusicGenPausedSubtitle get() = s("нужен рабочий ACEMusic endpoint", "waiting for a working ACEMusic endpoint")
    val aiPaused get() = s("пауза", "paused")
    val aiModels get() = s("Модели", "Models")
    val aiModelsSubtitle get() = s("Каталог по провайдерам", "Catalog by provider")
    val aiKeys get() = s("API-ключи", "API keys")
    val aiKeysSubtitle get() = s("OpenAI, Anthropic, Grok, Kimi, ACEMusic…", "OpenAI, Anthropic, Grok, Kimi, ACEMusic…")
    val aiKeyHint get() = s("Введите ключ", "Enter key")
    val aiKeyShow get() = s("Показать", "Show")
    val aiKeyHide get() = s("Скрыть", "Hide")
    val aiKeySave get() = s("Сохранить", "Save")
    val aiKeySaved get() = s("Сохранено", "Saved")
    val aiKeyClear get() = s("Очистить", "Clear")
    val aiKeyGetHere get() = s("Получить ключ", "Get a key")
    val aiAceMusicKeyHint get() = s(
        "ACEMusic: ключ или https://host/engine/api/engine|ключ; используется form release_task",
        "ACEMusic: key or https://host/engine/api/engine|key; uses form release_task",
    )
    val aiRefresh get() = s("Обновить", "Refresh")
    val aiRefreshing get() = s("Обновляем…", "Refreshing…")
    val aiNoModels get() = s("Нет моделей. Введите ключ и нажмите «Обновить».", "No models. Enter the API key and tap “Refresh”.")
    val aiNoKey get() = s("Ключ не задан", "Key not set")
    val aiSoon get() = s("Скоро", "Soon")
    val aiCodingPickProvider get() = s("Провайдер", "Provider")
    val aiCodingPickModel get() = s("Модель", "Model")
    val aiCodingNoCoding get() = s("Нет coding-моделей у этого провайдера", "No coding-friendly models for this provider")
    val aiCodingPlaceholder get() = s("Опишите задачу или вставьте код…", "Describe the task or paste code…")
    val aiCodingSend get() = s("Отправить", "Send")
    val aiCodingStop get() = s("Стоп", "Stop")
    val aiCodingClear get() = s("Очистить", "Clear")
    val aiCodingCopy get() = s("Копировать", "Copy")
    val aiCodingCopied get() = s("Скопировано", "Copied")
    val aiCodingNeedKey get() = s("Введите ключ в «AI → API-ключи»", "Enter the key in 'AI → API keys'")
    val aiCodingNoProvider get() = s("Не настроено ни одного провайдера. Введите ключ.", "No providers configured. Enter an API key.")
    val aiCodingHint get() = s("Ответы фокусируются на коде: дифы, готовые сниппеты, рефакторинг.", "Replies focus on code: diffs, runnable snippets, refactors.")
    val aiCodingAttachImage get() = s("Прикрепить скриншот", "Attach screenshot")
    val aiCodingRemoveAttachment get() = s("Убрать", "Remove")
    val aiCodingRegenerate get() = s("Перегенерировать", "Regenerate")
    val aiCodingNoVision get() = s("Эта модель не принимает картинки", "This model can't accept images")
    val aiCodingDropdownClose get() = s("Закрыть", "Close")
    val aiCodingScreenshotHint get() = s("Скриншот будет отправлен вместе с сообщением.", "The screenshot will be sent with the message.")
    val aiImagePrompt get() = s("Описание картинки", "Image prompt")
    val aiImagePromptHint get() = s("Например: «закат над Балтийским морем, реализм»", "e.g. \"sunset over the Baltic sea, photorealistic\"")
    val aiImageSize get() = s("Размер", "Size")
    val aiImageCount get() = s("Кол-во", "Count")
    val aiImageGenerate get() = s("Сгенерировать", "Generate")
    val aiImageGenerating get() = s("Генерируем…", "Generating…")
    val aiImageNoModels get() = s("Нет моделей для генерации картинок. Введите ключ OpenAI, Google или xAI.", "No image-generation models. Add an OpenAI, Google or xAI key.")
    val aiImageSaveToGallery get() = s("В галерею", "Save to gallery")
    val aiImageSaved get() = s("Сохранено в галерею", "Saved to gallery")
    val aiImageOpen get() = s("Открыть", "Open")
    val aiImageShare get() = s("Поделиться", "Share")
    val aiHistoryDelete get() = s("Удалить", "Delete")
    val aiHistoryEmpty get() = s("История пуста", "History is empty")
    val aiHistoryClearAll get() = s("Очистить историю", "Clear history")
    val aiHistoryTitle get() = s("История", "History")
    val aiHistoryImageTitle get() = s("История картинок", "Image history")
    val aiHistoryVideoTitle get() = s("История видео", "Video history")
    val aiHistoryCount get() = s("записей", "items")
    val aiAgentHistoryTitle get() = s("История чатов агента", "Agent chats")
    val aiAgentHistoryNew get() = s("Новый чат", "New chat")
    val aiAgentHistoryNoRepo get() = s("без репо", "no repo")
    val aiImageEmpty get() = s("Введите описание и нажмите «Сгенерировать».", "Enter a prompt and tap “Generate”.")
    val aiVideoPrompt get() = s("Описание видео", "Video prompt")
    val aiVideoPromptHint get() = s("Например: «волна разбивается о скалы, slow-motion, 4K»", "e.g. \"wave crashes against cliffs, slow-motion, 4K\"")
    val aiVideoAspect get() = s("Формат", "Aspect")
    val aiVideoDuration get() = s("Длит., сек", "Sec")
    val aiVideoGenerate get() = s("Сгенерировать", "Generate")
    val aiVideoGenerating get() = s("Генерируем видео…", "Generating video…")
    val aiVideoStatus get() = s("Статус", "Status")
    val aiVideoNoModels get() = s("Нет моделей для генерации видео. Введите ключ Google или Alibaba.", "No video-generation models. Add a Google or Alibaba key.")
    val aiVideoSaveToGallery get() = s("В галерею", "Save to gallery")
    val aiVideoSaved get() = s("Сохранено в галерею", "Saved to gallery")
    val aiVideoOpen get() = s("Открыть", "Open")
    val aiVideoShare get() = s("Поделиться", "Share")
    val aiVideoEmpty get() = s("Видео-генерация занимает несколько минут. Введите описание и нажмите «Сгенерировать».", "Video generation takes a few minutes. Enter a prompt and tap “Generate”.")
    val aiVideoCancel get() = s("Отмена", "Cancel")
    val aiMusicPrompt get() = s("Описание музыки", "Music prompt")
    val aiMusicPromptHint get() = s("Например: «cinematic synthwave, night drive, 120 bpm»", "e.g. \"cinematic synthwave, night drive, 120 bpm\"")
    val aiMusicLyrics get() = s("Текст песни", "Lyrics")
    val aiMusicLyricsHint get() = s("[Verse]\\n...\\n[Chorus]\\n...", "[Verse]\\n...\\n[Chorus]\\n...")
    val aiMusicGenerate get() = s("Сгенерировать", "Generate")
    val aiMusicGenerating get() = s("Генерируем музыку…", "Generating music…")
    val aiMusicNoModels get() = s("Нет music-моделей. Введите ключ ACEMusic.", "No music-generation models. Add an ACEMusic key.")
    val aiMusicEmpty get() = s("Введите описание или lyrics и нажмите «Сгенерировать».", "Enter a prompt or lyrics and tap “Generate”.")
    val aiMusicSaveToLibrary get() = s("В музыку", "Save to music")
    val aiMusicDownload get() = s("Скачать", "Download")
    val aiMusicSaved get() = s("Сохранено", "Saved")
    val aiMusicPlay get() = s("Слушать", "Play")
    val aiMusicPause get() = s("Пауза", "Pause")
    val aiMusicResume get() = s("Продолжить", "Resume")
    val aiMusicOpen get() = s("Открыть", "Open")
    val aiMusicShare get() = s("Поделиться", "Share")
    val aiMusicShareFile get() = s("Отправить файл", "Send file")
    val aiMusicStatus get() = s("Статус", "Status")
    val aiMusicCancel get() = s("Отмена", "Cancel")
    val aiMusicDuration get() = s("Длит., сек", "Sec")
    val aiMusicBpm get() = s("BPM", "BPM")
    val aiMusicKey get() = s("Тональность", "Key")
    val aiMusicTimeSignature get() = s("Размер", "Time")
    val aiMusicLanguage get() = s("Язык", "Lang")
    val aiMusicFormat get() = s("Формат", "Format")
    val aiMusicBatch get() = s("Кол-во", "Batch")
    val aiMusicAdvanced get() = s("advanced", "advanced")
    val aiMusicThinking get() = s("thinking", "thinking")
    val aiMusicFormatInput get() = s("format input", "format input")
    val aiMusicRandomSeed get() = s("random seed", "random seed")
    val aiMusicHistoryTitle get() = s("История музыки", "Music history")

    // ═══════════════════════════════════
    // AI · Settings
    // ═══════════════════════════════════
    val aiSettings get() = s("Настройки", "Settings")
    val aiSettingsSubtitle get() = s("Темы кода, шрифт, авто-сохранение", "Code themes, font, auto-save")
    val aiSettingsSyntaxTheme get() = s("Тема подсветки кода", "Syntax theme")
    val aiSettingsSyntaxThemeHint get() = s("Применяется только в блоках кода и не зависит от цветовой темы приложения.", "Applied to code blocks only — independent of the app theme.")
    val aiSettingsCodeFontSize get() = s("Размер шрифта в коде", "Code font size")
    val aiSettingsChatFontSize get() = s("Размер шрифта в чате", "Chat font size")
    val aiSettingsAutoSave get() = s("Авто-сохранение в галерею", "Auto-save to gallery")
    val aiSettingsAutoSaveHint get() = s("Сгенерированные картинки, видео и музыка сразу попадают в системную медиатеку.", "Generated images, videos, and music are saved to the system media library automatically.")
    val aiSettingsStreamScroll get() = s("Авто-прокрутка во время стрима", "Auto-scroll while streaming")
    val aiSettingsClearCache get() = s("Очистить кэш AI", "Clear AI cache")
    val aiSettingsClearCacheHint get() = s("Удаляет загруженные превью; история и ключи остаются.", "Removes cached previews. History and keys are kept.")
    val aiSettingsClearCacheDone get() = s("Кэш очищен", "Cache cleared")
    val aiSettingsExpandCode get() = s("На весь экран", "Expand")
    val aiSetupTitle get() = s("AI · настройка", "AI · setup")
    val aiSetupAddKey get() = s("Добавьте хотя бы один ключ провайдера, чтобы продолжить.", "Add at least one provider key to continue.")
    val aiSetupGoogleProxy get() = s("google proxy (опционально)", "google proxy (optional)")
    val aiSetupProxyOptional get() = s("proxy (опционально)", "proxy (optional)")
    val aiSetupQwenRegion get() = s("регион qwen", "qwen region")
    val aiSetupContinue get() = s("продолжить", "continue")
    val aiChatTitle get() = s("AI · чат", "AI · chat")
    val aiChatNew get() = s("новый", "new")
    val aiChatNewFull get() = s("Новый чат", "New chat")
    val aiChatSearchHint get() = s("поиск чатов…", "search chats…")
    val aiChatNoMatches get() = s("ничего не найдено", "no matches")
    val aiChatNoSessions get() = s("> пока нет чатов", "> no sessions yet")
    val aiChatStartHint get() = s("нажмите [ новый ], чтобы начать.", "tap [ new ] above to start.")
    val aiChatSessions get() = s("сессий", "sessions")
    val aiChatSession get() = s("сессия", "session")
    val aiChatMessagesShort get() = s("сообщ.", "msg")
    val aiChatMessagesShortPlural get() = s("сообщ.", "msgs")
    val aiChatExportTitle get() = s("AI Чат", "AI Chat")
    val aiChatYou get() = s("Вы", "You")
    val aiChatNoChatsYet get() = s("Чатов пока нет", "No chats yet")
    val aiChatEdit get() = s("править", "edit")
    val aiChatSpeak get() = s("озвучить", "speak")
    val aiChatTerm get() = s("терм", "term")
    val aiSelectApiModel get() = s("выбор api модели", "select api model")
    val aiEditMessage get() = s("правка сообщения", "edit message")
    val aiEditPlaceholder get() = s("правка…", "edit…")
    val aiKeysTitle get() = s("ai · ключи", "ai · keys")
    val aiProxyOptional get() = s("proxy (опционально)", "proxy (optional)")
    val aiDiffEdit get() = s("правка", "edit")
    val aiDiffWrite get() = s("запись", "write")
    val aiChatCleared get() = s("Чат очищен", "Chat cleared")
    val aiVoicePrompt get() = s("Говорите...", "Speak...")
    val aiVoiceUnavailable get() = s("Голосовой ввод недоступен", "Voice not available")
    val aiCameraUnavailable get() = s("Камера недоступна", "Camera not available")
    val aiLoading get() = s("загрузка", "loading")
    val aiModelErrorShort get() = s("ошибка модели", "model err")
    val aiModelShort get() = s("модель", "model")
    val aiReady get() = s("готов", "ready")
    val aiModelLoadFailed get() = s("загрузка моделей не удалась", "model load failed")
    val aiSelectChatModelHint get() = s("Выберите API-модель с поддержкой чата.", "Select a chat-capable API model.")
    val aiAddApiKeySelectModel get() = s("Добавьте API-ключ и выберите модель с поддержкой чата.", "Add an API key and select a chat-capable model.")
    val aiContextPrefix get() = s("контекст", "context")
    val aiSuggested get() = s("> подсказки", "> suggested")
    val aiExplainThisCode get() = s("Объясни этот код", "Explain this code")
    val aiAnalyzeZipArchive get() = s("Проанализируй ZIP-архив", "Analyze ZIP archive")
    val aiWhatsInThisImage get() = s("Что на этом изображении?", "What's in this image?")
    val aiWhatFilesInFolder get() = s("Какие файлы есть в этой папке?", "What files are in this folder?")
    val aiWhatTakesMostSpace get() = s("Что занимает больше всего места здесь?", "What takes the most space here?")
    val aiMessagePlaceholder get() = s("сообщение…", "message…")
    val aiActionShorter get() = s("короче", "shorter")
    val aiActionMoreDetail get() = s("детальнее", "detail")
    val aiActionExplain get() = s("объяснить", "explain")
    val aiActionFix get() = s("исправить", "fix")
    val aiActionScript get() = s("скрипт", "script")
    val aiLoadingModelCatalog get() = s("загрузка каталога моделей...", "loading model catalog...")
    val aiAddApiKeySettings get() = s("добавьте API-ключ в [ настройки ]", "add an API key in [ settings ]")
    val aiNoChatModelsFound get() = s("не найдены chat-модели для настроенных провайдеров", "no chat-capable models found for configured providers")
    val aiAnalyzeFile get() = s("Проанализируй файл", "Analyze file")
    val aiAnalyzeImage get() = s("Проанализируй изображение", "Analyze image")
    val aiAnalyzeArchive get() = s("Проанализируй архив", "Analyze archive")
    val aiAttachment get() = s("вложение", "attachment")
    val aiAttachmentError get() = s("ошибка вложения", "attachment error")
    val aiReadError get() = s("ошибка чтения", "read error")
    val aiAttachedFile get() = s("Прикреплённый файл", "Attached file")
    val aiSlashCommands get() = s("[slash-команды]", "[slash commands]")
    val aiSlashShowCommands get() = s("показать команды", "show commands")
    val aiSlashClearChat get() = s("очистить текущий чат", "clear current chat")
    val aiSlashClearCoding get() = s("очистить текущий coding-чат", "clear current coding chat")
    val aiSlashClearTask get() = s("очистить текущую задачу", "clear current task")
    val aiSlashCost get() = s("показать оценку контекста", "show context estimate")
    val aiSlashCompact get() = s("сжать transcript", "compact transcript")
    val aiSlashCompactVisible get() = s("локально сжать видимый transcript", "compact visible transcript locally")
    val aiSlashResume get() = s("открыть историю", "open history")
    val aiSlashExport get() = s("сохранить markdown в Downloads", "save markdown to Downloads")
    val aiSlashSettings get() = s("открыть настройки model/API", "open model/API settings")
    val aiSlashContext get() = s("проверить prompt/tool context", "inspect prompt/tool context")
    val aiSlashMemory get() = s("открыть working memory", "open working memory")
    val aiSlashMemoryFiles get() = s("открыть файлы памяти", "open memory files")
    val aiSlashSkills get() = s("открыть установленные skills", "open installed skills")
    val aiSlashPermissions get() = s("показать или изменить permission mode", "show or set permission mode")
    val aiSlashPlan get() = s("переключить plan-first mode", "toggle plan-first mode")
    val aiSlashSystem get() = s("править system prompt", "edit system prompt")
    val aiSlashDiff get() = s("открыть workspace diff", "open workspace diff")
    val aiCostEstimateTitle get() = s("[оценка стоимости]", "[cost estimate]")
    val aiCostModel get() = s("модель", "model")
    val aiCostInputChars get() = s("входных символов", "input chars")
    val aiCostOutputChars get() = s("выходных символов", "output chars")
    val aiCostTotalChars get() = s("всего символов", "total chars")
    val aiCostTokens get() = s("токены", "tokens")
    val aiCostCost get() = s("стоимость", "cost")
    val aiCostMessages get() = s("сообщения", "messages")
    val aiCostScope get() = s("scope", "scope")
    val aiCostToolCalls get() = s("tool calls", "tool calls")
    val aiReportedUsage get() = s("usage от провайдера", "reported usage")
    val aiReportedCost get() = s("стоимость от провайдера", "reported cost")
    val aiEstimatedCost get() = s("оценочная стоимость", "estimated cost")
    val aiCostTrackingAccuracy get() = s("точность учёта стоимости", "cost tracking accuracy")
    val aiSummaryEntries get() = s("entries", "entries")
    val aiSummaryTodos get() = s("todos", "todos")
    val aiSummaryGeneratedFiles get() = s("созданные файлы", "generated files")
    val aiNotSelected get() = s("не выбрано", "not selected")
    val aiWaitForResponse get() = s("[system] дождитесь завершения текущего ответа перед compact.", "[system] wait for the current response to finish before compacting.")
    val aiNothingToCompact get() = s("[system] пока нечего сжимать.", "[system] nothing to compact yet.")
    val aiUnknownSlashCommand get() = s("[system] неизвестная slash-команда", "[system] unknown slash command")
    val aiCompactSummary get() = s("[compact summary]", "[compact summary]")
    val aiRecentUserRequests get() = s("последние запросы пользователя", "recent user requests")

    // AI · Agent
    val aiAgent get() = s("AI агент", "AI Agent")
    val aiAgentSubtitle get() = s("Агент с доступом к GitHub", "Agent with GitHub access")
    val aiAgentSelectRepo get() = s("Выбери репозиторий", "Pick a repository")
    val aiAgentSelectBranch get() = s("Ветка", "Branch")
    val aiAgentSelectModel get() = s("Модель", "Model")
    val aiAgentNoModels get() = s("Нет моделей. Добавь ключ OpenAI или Anthropic.", "No models. Add an OpenAI or Anthropic key.")
    val aiAgentNoRepos get() = s("Войди в GitHub чтобы выбрать репо.", "Sign in to GitHub to pick a repo.")
    val aiAgentInputHint get() = s("Опиши задачу для агента…", "Describe the task for the agent…")
    val aiAgentAutoApprove get() = s("Авто-одобрение чтения", "Auto-approve reads")
    val aiAgentAutoApproveHint get() = s("Read-only инструменты выполняются без подтверждения. Запись и коммиты всегда требуют одобрения.", "Read-only tools run without confirmation. Writes and commits always require approval.")
    val aiAgentApprove get() = s("Одобрить", "Approve")
    val aiAgentReject get() = s("Отклонить", "Reject")
    val aiAgentRunning get() = s("Агент работает…", "Agent working…")
    val aiAgentStop get() = s("Остановить", "Stop")
    val aiAgentToolCallTitle get() = s("Вызов инструмента", "Tool call")
    val aiAgentToolResultTitle get() = s("Результат", "Result")
    val aiAgentToolError get() = s("Ошибка", "Error")
    val aiAgentRejected get() = s("Отклонено пользователем.", "Rejected by user.")
    val aiAgentEmptyChat get() = s("Опиши что нужно сделать в репозитории.", "Tell the agent what to do in the repo.")
    val aiAgentDiffLoading get() = s("Загружаю текущее содержимое…", "Loading current contents…")
    val aiAgentDiffEmpty get() = s("Нет изменений для предпросмотра.", "No changes to preview.")
    val aiAgentQuickExplain get() = s("Объясни", "Explain")
    val aiAgentQuickAddTests get() = s("Добавь тесты", "Add tests")
    val aiAgentQuickFixLint get() = s("Поправь стиль", "Fix lint")
    val aiAgentQuickRefactor get() = s("Рефакторинг", "Refactor")
    val aiAgentQuickGenerateDocs get() = s("Сгенерируй KDoc", "Generate KDoc")
    val aiAgentProtectedBranchTitle get() = s("Это защищённая ветка", "Protected branch")
    val aiAgentProtectedBranchSubtitle get() = s(
        "Эта операция изменит ветку «{branch}» по умолчанию. Обычно правки идут через feature-ветку и Pull Request.",
        "This action will change the default branch \"{branch}\". Most teams expect changes to go through a feature branch + PR.",
    )
    val aiAgentProtectedBranchConfirm get() = s(
        "Понимаю, что пишу прямо в основную ветку",
        "I understand I'm writing directly to the main branch",
    )
    val aiAgentOpenPrPreview get() = s("Pull request", "Pull request")
    val aiAgentPrivateRepoWarning get() = s(
        "Это приватный репозиторий. Содержимое файлов будет отправлено внешнему AI-провайдеру.",
        "This is a private repo. File contents will be sent to an external AI provider.",
    )
    val aiAgentPrivateRepoDismiss get() = s("Понял", "Got it")
    val aiAgentReadOnlyWarning get() = s(
        "Read-only доступ — write-инструменты отключены.",
        "Read-only access — write tools disabled.",
    )
    val aiAgentNoApiKeyTitle get() = s("Нужен API ключ", "API key required")
    val aiAgentNoApiKeySubtitle get() = s(
        "Добавь ключ OpenAI или Anthropic в настройках AI, чтобы агент работал.",
        "Add an OpenAI or Anthropic key in AI settings so the agent can run.",
    )
    val aiAgentPickRepoHint get() = s(
        "Выбери репозиторий и ветку, чтобы инструменты заработали.",
        "Pick a repo and branch to enable the tools.",
    )
    val aiAgentHistorySearchHint get() = s("Поиск по истории…", "Search history…")
    val aiHistorySearchEmpty get() = s("Ничего не найдено", "No matches")
    val aiAgentCostLabel get() = s("Стоимость", "Cost")
    val aiAgentTokensLabel get() = s("ток.", "tok")
    val aiAgentFallbackToast get() = s(
        "Провайдер недоступен — переключился на {model}.",
        "Provider unavailable — falling back to {model}.",
    )
    val aiSummary get() = s("AI Сводка", "AI Summary")
    val aiSummaryLoading get() = s("Генерируется…", "Generating…")
    val aiSummaryEmpty get() = s("Нет сводки.", "No summary.")
    val aiSummaryRegenerate get() = s("Сгенерировать заново", "Regenerate")
    val aiCommitMsgGenerate get() = s("Сгенерировать AI", "AI suggest")
    val aiCommitMsgGenerating get() = s("Генерируется…", "Generating…")
    val aiCommitMsgError get() = s("AI недоступен", "AI unavailable")
    val aiNotConfigured get() = s(
        "Сначала настрой провайдер AI в настройках.",
        "Configure an AI provider first.",
    )
    val aiAgentSettingsTitle get() = s("НАСТРОЙКИ АГЕНТА", "AGENT SETTINGS")
    val aiAgentSettingsChat get() = s("ЧАТ", "CHAT")
    val aiAgentSettingsDisplayMode get() = s("РЕЖИМ ОТОБРАЖЕНИЯ", "DISPLAY MODE")
    val aiAgentSettingsRepo get() = s("РЕПО", "REPO")
    val aiAgentSettingsModel get() = s("МОДЕЛЬ", "MODEL")
    val aiAgentSettingsMode get() = s("РЕЖИМ", "MODE")
    val aiAgentSettingsPermissions get() = s("РАЗРЕШЕНИЯ", "PERMISSIONS")
    val aiAgentSettingsBackground get() = s("ФОНОВАЯ РАБОТА", "BACKGROUND EXECUTION")
    val aiAgentSettingsWorkspace get() = s("РЕЖИМ WORKSPACE", "WORKSPACE MODE")
    val aiAgentSettingsMemory get() = s("ПАМЯТЬ", "MEMORY")
    val aiAgentSettingsWorkingMemory get() = s("РАБОЧАЯ ПАМЯТЬ", "WORKING MEMORY")
    val aiAgentSettingsSkills get() = s("СКИЛЛЫ", "SKILLS")
    val aiAgentSettingsMemoryFiles get() = s("ФАЙЛЫ ПАМЯТИ", "MEMORY FILES")
    val aiAgentSettingsHistory get() = s("[ история чата → ]", "[ chat history → ]")
    val aiAgentSettingsSystemPrompt get() = s("[ системный промпт → ]", "[ system prompt → ]")
    val aiAgentSettingsContextInspector get() = s("[ инспектор контекста → ]", "[ context inspector → ]")
    val aiAgentExpandToolsDefault get() = s("раскрывать tool calls по умолчанию", "expand tool calls by default")
    val aiAgentCollapseToolsDefault get() = s("свернуто (тап для раскрытия)", "collapsed (tap to expand)")
    val aiAgentSelectRepositoryTitle get() = s("Выбор репозитория", "Select repository")
    val aiAgentSelectBranchTitle get() = s("Выбор ветки", "Select branch")
    val aiAgentSelectModelTitle get() = s("Выбор модели", "Select model")
    val aiAgentYoloMode get() = s("YOLO режим (без подтверждений)", "YOLO mode (no confirmations)")
    val aiAgentYoloReadCarefully get() = s("YOLO MODE — ПРОЧИТАЙТЕ ВНИМАТЕЛЬНО", "YOLO MODE - READ CAREFULLY")
    val aiAgentAutoApproveReadsLabel get() = s("авто-одобрение чтения", "auto-approve reads")
    val aiAgentAutoApproveEditsLabel get() = s("авто-одобрение правок", "auto-approve edits")
    val aiAgentAutoApproveWritesLabel get() = s("авто-одобрение записи / новых файлов", "auto-approve writes / new files")
    val aiAgentAutoApproveCommitsLabel get() = s("авто-одобрение коммитов / PR", "auto-approve commits / PRs")
    val aiAgentDestructiveApprovalLabel get() = s("опасные действия (approval всегда обязателен)", "destructive option (approval always required)")
    val aiAgentSessionTrustLabel get() = s("доверять правкам/записи в этой сессии", "session trust for edits/writes")
    val aiAgentProtectedPaths get() = s("защищённые пути", "protected paths")
    val aiAgentBackgroundExecution get() = s("продолжать работу в фоне", "continue working when app is in background")
    val aiAgentKeepCpuAwake get() = s("держать CPU активным для долгих задач", "keep CPU awake during long tasks")
    val aiAgentUseWorkspaces get() = s("использовать workspaces для атомарных правок", "use workspaces for atomic changes")
    val aiAgentWorkspaceOnHint get() = s("правки копятся в SQLite до review", "edits accumulate in SQLite until review")
    val aiAgentWorkspaceOffHint get() = s("legacy: write tools сразу коммитят", "legacy: write tools commit immediately")
    val aiAgentMemoryProjectKnowledge get() = s("знания проекта (project.md)", "project knowledge (project.md)")
    val aiAgentMemoryUserPreferences get() = s("предпочтения пользователя (preferences.md)", "user preferences (preferences.md)")
    val aiAgentMemoryChatSummaries get() = s("сводки чатов", "chat summaries")
    val aiAgentMemorySemanticSearch get() = s("семантический поиск по чатам", "semantic search across chats")
    val aiAgentMaintainWorkingMemory get() = s("вести working memory во время задач", "maintain working memory during tasks")
    val aiAgentWorkingMemoryReminders get() = s("напоминать агенту обновлять память после правок", "auto-remind agent to update after edits")
    val aiAgentViewWorkingMemory get() = s("[ открыть рабочую память → ]", "[ view working memory → ]")
    val aiAgentEnableSkills get() = s("включить скиллы", "enable skills")
    val aiAgentAutoSuggestSkill get() = s("авто-предлагать подходящий скилл", "auto-suggest matching skill")
    val aiAgentAutoDetectSkill get() = s("авто-детект (when_to_use)", "auto-detection (when_to_use)")
    val aiAgentSelectorModel get() = s("МОДЕЛЬ СЕЛЕКТОРА", "SELECTOR MODEL")
    val aiAgentSelectorModelTitle get() = s("Модель селектора", "Selector model")
    val aiAgentMaxAutoSkills get() = s("МАКС. AUTO-SKILLS", "MAX AUTO-SKILLS")
    val aiAgentMaxAutoSkillsTitle get() = s("Максимум auto-detected skills", "Max auto-detected skills")
    val aiAgentAllowUntrustedDangerous get() = s("разрешить опасные tools для недоверенных skills", "allow untrusted dangerous tools")
    val aiAgentSelectedSkillPrefix get() = s("выбранный скилл", "selected skill")
    val aiAgentViewInstalledSkills get() = s("установленные скиллы", "view installed skills")
    val aiAgentImportSkillPack get() = s("[+ импорт .gskill]", "[+ import .gskill]")
    val aiAgentViewMemoryFiles get() = s("[ открыть файлы памяти → ]", "[ view memory files → ]")
    val aiAgentClearAllMemory get() = s("[ очистить всю память ]", "[ clear all memory ]")
    val aiAgentInstantRender get() = s("мгновенный рендер (без streaming-анимации)", "instant render (no streaming animation)")
    val aiAgentClearChat get() = s("[ очистить чат ]", "[ clear chat ]")
    val aiAgentExportChat get() = s("[ экспорт чата ]", "[ export chat ]")
    val aiAgentWriteLimitPerTask get() = s("ЛИМИТ ЗАПИСИ НА ЗАДАЧУ", "WRITE LIMIT PER TASK")
    val aiAgentProtectedPathsTitle get() = s("ЗАЩИЩЁННЫЕ ПУТИ", "PROTECTED PATHS")
    val aiAgentProtectedPathsHint get() = s("один glob pattern на строку", "one glob pattern per line")
    val aiAgentMemoryFilesTitle get() = s("ФАЙЛЫ ПАМЯТИ", "MEMORY FILES")
    val aiAgentWorkingMemoryTitle get() = s("РАБОЧАЯ ПАМЯТЬ", "WORKING MEMORY")
    val aiAgentRebuildIndex get() = s("пересобрать индекс", "rebuild index")
    val aiAgentModeEco get() = s("эко", "eco")
    val aiAgentModeBalanced get() = s("сбалансированный", "balanced")
    val aiAgentModeMaxQuality get() = s("макс. качество", "max quality")
    val aiAgentPermissionAsk get() = s("спрашивать", "ask")
    val aiAgentPermissionAskDesc get() = s("Спрашивать перед каждым tool-действием.", "Ask before every tool action.")
    val aiAgentPermissionAutoReads get() = s("авто чтение", "auto reads")
    val aiAgentPermissionAutoReadsDesc get() = s("Read-only tools выполняются автоматически; правки всё ещё требуют подтверждения.", "Read-only tools run automatically; edits still ask.")
    val aiAgentPermissionAcceptEdits get() = s("принимать правки", "accept edits")
    val aiAgentPermissionAcceptEditsDesc get() = s("Чтение, правки и запись файлов выполняются автоматически; коммиты всё ещё спрашивают.", "Reads, edits and file writes run automatically; commits still ask.")
    val aiAgentPermissionYolo get() = s("yolo", "yolo")
    val aiAgentPermissionYoloDesc get() = s("Большинство действий выполняется автоматически; destructive actions требуют approval.", "Most actions run automatically; destructive actions still require approval.")
    val aiAgentPermissionCustom get() = s("custom", "custom")
    val aiAgentPermissionCustomDesc get() = s("Активны ручные toggles approval.", "Manual approval toggles are active.")
    val aiAgentYoloRememberedLine1 get() = s("Вы уже подтверждали это. Агент пропустит", "You confirmed this before. Agent will skip")
    val aiAgentYoloRememberedLine2 get() = s("approval для большинства действий.", "approval for most actions.")
    val aiAgentYoloWillExecute get() = s("Агент выполнит БЕЗ запроса:", "Agent will execute actions WITHOUT asking:")
    val aiAgentYoloReadFiles get() = s("Читать файлы", "Read files")
    val aiAgentYoloEditFiles get() = s("Править существующие файлы", "Edit existing files")
    val aiAgentYoloCreateFiles get() = s("Создавать новые файлы", "Create new files")
    val aiAgentYoloFeatureCommits get() = s("Коммитить в feature-ветки", "Commit to feature branches")
    val aiAgentYoloStillAsk get() = s("Агент ВСЁ ЕЩЁ спросит для:", "Agent will STILL ask for:")
    val aiAgentYoloMainCommits get() = s("Коммиты в main/master", "Commits to main/master")
    val aiAgentYoloDestructiveOps get() = s("Destructive operations (delete, force-push, reset)", "Destructive operations (delete, force-push, reset)")
    val aiAgentYoloProtectedFiles get() = s("Изменения protected files", "Changes to protected files")
    val aiAgentYoloRisks get() = s("Риски:", "Risks:")
    val aiAgentYoloRiskUnexpected get() = s("Агент может изменить неожиданные файлы", "Agent may modify files you didn't expect")
    val aiAgentYoloRiskCost get() = s("Стоимость может быстро расти при auto-execution", "Cost can grow quickly with auto-execution")
    val aiAgentYoloRiskMistakes get() = s("Ошибки сложнее ловить в реальном времени", "Mistakes are harder to catch in real-time")
    val aiAgentYoloRiskReview get() = s("Нужно review всех коммитов перед push", "You should review all commits before pushing")
    val aiAgentYoloRecommended get() = s("Рекомендуется только если:", "Recommended only if:")
    val aiAgentYoloBackup get() = s("Есть backup / можно откатиться через git", "You have backups / can revert via git")
    val aiAgentYoloUnderstandTools get() = s("Вы понимаете, какие tools использует агент", "You understand what tools the agent uses")
    val aiAgentYoloReviewChanges get() = s("Вы сделаете review итоговых изменений", "You'll review the resulting changes")
    val aiAgentApprovalRequired get() = s("ТРЕБУЕТСЯ APPROVAL", "APPROVAL REQUIRED")
    val aiAgentDestructiveAction get() = s("ОПАСНОЕ ДЕЙСТВИЕ", "DESTRUCTIVE ACTION")
    val aiAgentToolArgs get() = s("аргументы", "args")
    val aiAgentToolResult get() = s("результат", "result")
    val aiAgentEstimatedCostTitle get() = s("оценка стоимости выше лимита", "estimated cost above threshold")
    val aiAgentSendAnyway get() = s("y · всё равно отправить", "y · send anyway")
    val aiAgentRequestExpectedCost get() = s("ожидаемая стоимость запроса", "this request is expected to cost")
    val aiAgentThresholdLabel get() = s("ваш лимит: ", "your threshold: ")
    val aiAgentThresholdSettingsNote get() = s("  ·  меняется в Settings → AI Module", "  ·  set in Settings → AI Module")
    val aiSkillImportedToast get() = s("Импортировано", "Imported")
    val aiSkillImportFailedToast get() = s("Ошибка импорта", "Import failed")
    val aiAgentUnknownPermissionMode get() = s("[system] неизвестный permission mode", "[system] unknown permission mode")
    val aiAgentKnownPermissionModes get() = s("известные: ask, reads, accept-edits, yolo", "known: ask, reads, accept-edits, yolo")
    val aiAgentPermissionModeLabel get() = s("[system] permission mode", "[system] permission mode")
    val aiAgentPlanFirstMode get() = s("[system] plan-first mode", "[system] plan-first mode")
    val aiAgentEnabled get() = s("включён", "enabled")
    val aiAgentDisabled get() = s("выключен", "disabled")
    val aiAgentNoPendingWorkspaceDiff get() = s("[system] нет pending workspace diff.", "[system] no pending workspace diff.")
    val aiAgentWaitForRun get() = s("[system] дождитесь завершения текущего запуска агента перед compact.", "[system] wait for the current agent run to finish before compacting.")
    val aiAgentChatOnly get() = s("только чат", "chat only")
    val aiAgentChatOnlySubtitle get() = s("без repository tools", "no repository tools")
    val aiAgentTopBarChat get() = s("чат", "chat")
    val aiAgentIndicatorWorkspace get() = s("workspace", "workspace")
    val aiAgentIndicatorYolo get() = s("auto: yolo", "auto: yolo")
    val aiAgentIndicatorWrites get() = s("auto: запись", "auto: writes")
    val aiAgentIndicatorEdits get() = s("auto: правки", "auto: edits")
    val aiAgentIndicatorReads get() = s("auto: чтение", "auto: reads")
    val aiAgentContextSession get() = s("сессия", "session")
    val aiAgentContextPromptInputs get() = s("prompt inputs", "prompt inputs")
    val aiAgentContextSkills get() = s("скиллы", "skills")
    val aiAgentContextTools get() = s("tools", "tools")
    val aiAgentContextPermissionLog get() = s("permission log", "permission log")
    val aiAgentContextTranscript get() = s("transcript", "transcript")
    val aiAgentContextUsageEstimate get() = s("usage estimate", "usage estimate")
    val aiAgentContextInspectorTitle get() = s("ИНСПЕКТОР КОНТЕКСТА", "CONTEXT INSPECTOR")
    val aiAgentFilePreview get() = s("ПРЕВЬЮ ФАЙЛА", "FILE PREVIEW")
    val aiAgentArchiveFile get() = s("АРХИВ", "ARCHIVE FILE")
    val aiAgentSaving get() = s("сохранение...", "saving...")
    val aiAgentDownload get() = s("скачать", "download")
    val aiAgentSaved get() = s("сохранено", "saved")
    val aiAgentArchiveContentHidden get() = s(
        "Содержимое архива скрыто в чате. Используйте [ скачать ], чтобы сохранить сам архив.",
        "Archive contents are hidden in chat. Use [ download ] to save the archive file itself.",
    )
    val aiAgentNameKey get() = s("имя", "name")
    val aiAgentTypeKey get() = s("тип", "type")
    val aiAgentSizeKey get() = s("размер", "size")
    val aiAgentArchiveType get() = s("архив", "archive")
    val aiAgentSkillsTitle get() = s("AI СКИЛЛЫ", "AI SKILLS")
    val aiAgentAutoSkill get() = s("авто скилл", "auto skill")
    val aiAgentSelected get() = s("выбрано", "selected")
    val aiAgentImportError get() = s("ОШИБКА ИМПОРТА", "IMPORT ERROR")
    val aiAgentInstalledPacks get() = s("УСТАНОВЛЕННЫЕ ПАКИ", "INSTALLED PACKS")
    val aiAgentNoInstalledSkills get() = s("(нет установленных скиллов)", "(no installed skills)")
    val aiAgentUnknown get() = s("неизвестно", "unknown")
    val aiAgentTrusted get() = s("доверенный", "trusted")
    val aiAgentUntrusted get() = s("недоверенный", "untrusted")
    val aiAgentSkillsCount get() = s("скиллов", "skills")
    val aiAgentDisable get() = s("выкл", "disable")
    val aiAgentEnable get() = s("вкл", "enable")
    val aiAgentDelete get() = s("удалить", "delete")
    val aiAgentRiskKey get() = s("риск", "risk")
    val aiAgentToolsKey get() = s("тулы", "tools")
    val aiAgentUse get() = s("использовать", "use")
    val aiAgentImportSkillPackTitle get() = s("ИМПОРТ SKILL PACK", "IMPORT SKILL PACK")
    val aiAgentVersionKey get() = s("версия", "version")
    val aiAgentAuthorKey get() = s("автор", "author")
    val aiAgentSourceKey get() = s("источник", "source")
    val aiAgentRequestedTools get() = s("ЗАПРОШЕННЫЕ TOOLS", "REQUESTED TOOLS")
    val aiAgentWarnings get() = s("ПРЕДУПРЕЖДЕНИЯ", "WARNINGS")
    val aiAgentImport get() = s("импорт", "import")
    val aiAgentCommitMessage get() = s("сообщение коммита:", "commit message:")
    val aiAgentCommitting get() = s("коммит...", "committing...")
    val aiAgentCommitAndPush get() = s("commit & push", "commit & push")
    val aiAgentReviewDiff get() = s("просмотр diff", "review diff")
    val aiAgentCommit get() = s("коммит", "commit")
    val aiAgentDiscard get() = s("отбросить", "discard")

    // Per-repo system-prompt override (mega-PR D-E pack).
    val aiAgentSystemPromptTitle get() = s(
        "Системный промпт для репозитория",
        "Repo system prompt",
    )
    val aiAgentSystemPromptHint get() = s(
        "Этот текст добавляется как system-сообщение в каждом запуске агента в этом репозитории. Оставь пустым чтобы убрать.",
        "Prepended as a system message on every agent run in this repo. Leave empty to clear.",
    )
    val aiAgentSystemPromptPlaceholder get() = s(
        "Например: «Используй conventional commits, английский язык, без эмодзи»",
        "e.g. \"Use conventional commits, English, no emojis\"",
    )
    val aiAgentSystemPromptSave get() = s("Сохранить", "Save")
    val aiAgentSystemPromptCancel get() = s("Отмена", "Cancel")

    // C3 — plan-then-execute toggle. Lives in the same dialog as the
    // system-prompt override, so the strings are right next door.
    val aiAgentPlanFirstLabel get() = s(
        "Сначала план, потом действия",
        "Plan first, then act",
    )
    val aiAgentPlanFirstHint get() = s(
        "Агент в первом ответе только распишет план, без вызова инструментов. Дальше — после твоего «ок».",
        "On the first turn the agent will only outline a plan — no tool calls — and wait for your go-ahead.",
    )

    // D2 — resume banner shown when the previous agent run for this
    // session was killed mid-flight. The pointer lives in
    // AiAgentResumeStore and is wiped after the user picks an action.
    val aiAgentResumeBannerText get() = s(
        "Прошлый запуск агента не завершился. Возобновить с тем же запросом?",
        "The previous agent run didn't finish. Resume with the same prompt?",
    )
    val aiAgentResumeBannerAction get() = s("Возобновить", "Resume")
    val aiAgentResumeBannerDiscard get() = s("Отбросить", "Discard")

    // B — Suggest fix on a failed CI run. Prefilled prompt that the
    // workflow-run page sends into the agent.
    val aiAgentSuggestFixPrompt get() = s(
        "Этот запуск GitHub Actions упал. Прочитай логи неудавшегося job через read_workflow_run и предложи исправление.",
        "This GitHub Actions run failed. Read the failing job's logs via read_workflow_run and suggest a fix.",
    )

    // C2 — "Send selection" chip in the code editor's quick-actions row.
    val aiAgentSendSelectionChip get() = s(
        "Отправить выделение",
        "Send selection",
    )
    val aiAgentSendSelectionPromptPrefix get() = s(
        "Посмотри этот фрагмент из",
        "Take a look at this snippet from",
    )

    // Cost-policy UI (PR-COST-B). Three modes + warning dialog copy.
    val aiCostMode get() = s("Режим", "Mode")
    val aiCostModeEco get() = s("Эко", "Eco")
    val aiCostModeBalanced get() = s("Сбалансированный", "Balanced")
    val aiCostModeMax get() = s("Макс. качество", "Max quality")
    val aiCostModeEcoHint get() = s(
        "Минимум контекста, дешёвые запросы. Лучшая экономия.",
        "Smallest context, cheapest runs. Best for saving credits.",
    )
    val aiCostModeBalancedHint get() = s(
        "По умолчанию. Подходит для повседневных вопросов по репозиторию.",
        "Default. Comfortable for everyday repo questions.",
    )
    val aiCostModeMaxHint get() = s(
        "Большой контекст и больше итераций. Используй для глубокого анализа.",
        "Largest context and more iterations. For deep analysis.",
    )
    val aiCostWarningTitle get() = s("Внимание: возможно дорогая операция", "Heads up: this run may be expensive")
    val aiCostWarningReasonPrivate get() = s(
        "Это приватный репозиторий. Содержимое будет отправлено внешнему AI-провайдеру.",
        "This is a private repo. Content will be sent to an external AI provider.",
    )
    val aiCostWarningReasonMaxMode get() = s(
        "Активен режим максимального качества — лимиты по контексту и итерациям увеличены.",
        "Max quality mode is on — context and iteration caps are higher.",
    )
    val aiCostWarningReasonLarge get() = s(
        "В контекст будет отправлено много текста, это может стоить значимое количество токенов.",
        "A lot of text will be sent in the context — this may cost a meaningful amount of tokens.",
    )
    val aiCostWarningRepo get() = s("Репозиторий", "Repository")
    val aiCostWarningBranch get() = s("Ветка", "Branch")
    val aiCostWarningProvider get() = s("Провайдер", "Provider")
    val aiCostWarningModel get() = s("Модель", "Model")
    val aiCostWarningFiles get() = s("Файлов", "Files")
    val aiCostWarningContext get() = s("Размер", "Size")
    val aiCostWarningChars get() = s("{n} символов", "{n} chars")
    val aiCostWarningPrivate get() = s("приватный", "private")
    val aiCostWarningTransmitNote get() = s(
        "Содержимое будет отправлено в API провайдера. Стоимость и токены — оценочные.",
        "Content will be sent to the provider API. Token and cost numbers are estimates.",
    )
    val aiCostWarningRememberLabel get() = s(
        "Запомнить для этого репо и провайдера",
        "Remember for this repo and provider",
    )
    val aiCostWarningContinueOnce get() = s("Продолжить один раз", "Continue once")
    val aiCostWarningContinueRemember get() = s("Продолжить и запомнить", "Continue and remember")

    // ─── Local AI usage tracking (PR-COST-C) ───────────────────────
    val aiUsageTitle get() = s("AI Usage", "AI Usage")
    val aiUsageLocal get() = s("локально", "local")
    val aiUsageSubtitle get() = s(
        "Локальная статистика по запросам, токенам и тулам.",
        "Local stats: requests, tokens, tool calls.",
    )
    val aiUsageWindowToday get() = s("Сегодня", "Today")
    val aiUsageWindowWeek get() = s("Неделя", "Week")
    val aiUsageWindowMonth get() = s("Месяц", "Month")
    val aiUsageEmpty get() = s(
        "Пока нет записей. Запусти задачу в AI Agent или сгенерируй картинку — здесь появится статистика.",
        "No records yet. Run a task in AI Agent or generate an image — stats will show up here.",
    )
    val aiUsageRecords get() = s("Запросов", "Requests")
    val aiUsageTokens get() = s("Токенов", "Tokens")
    val aiUsageCost get() = s("Стоимость", "Cost")
    val aiUsageTokensEstimateOnly get() = s("оценочно", "estimate only")
    val aiUsageChars get() = s("Символов", "Chars")
    val aiUsageToolCalls get() = s("Тул-коллов", "Tool calls")
    val aiUsageFilesRead get() = s("Файлов прочитано", "Files read")
    val aiUsageFilesWritten get() = s("Файлов записано", "Files written")
    val aiUsageEstimated get() = s("Оценочные", "Estimated")
    val aiUsageEstimatedFmt get() = s("{n} из {total}", "{n} of {total}")
    val aiUsageByProvider get() = s("ПО ПРОВАЙДЕРАМ", "BY PROVIDER")
    val aiUsageByModel get() = s("ПО МОДЕЛЯМ", "BY MODEL")
    val aiUsageByMode get() = s("ПО РЕЖИМАМ", "BY MODE")
    val aiUsageBucketSubtitle get() = s("{n} запросов · {chars} симв.", "{n} requests · {chars} chars")
    val aiUsageDisclaimer get() = s(
        "Локальная оценка. Реальный счёт у провайдера может отличаться.",
        "Local estimate only. Final billing may differ from the provider dashboard.",
    )
    val aiUsageClearTitle get() = s("Очистить локальную статистику?", "Clear local usage stats?")
    val aiUsageClearBody get() = s(
        "Будут удалены все записи об использовании AI с этого устройства. Действие нельзя отменить.",
        "All AI usage records on this device will be removed. This cannot be undone.",
    )
    val aiUsageClearConfirm get() = s("Очистить", "Clear")

    val about get() = s("О приложении", "About")
    val version get() = s("Версия", "Version")

    // ═══════════════════════════════════
    // Trash
    // ═══════════════════════════════════
    val trashEmpty get() = s("Корзина пуста", "Trash is empty")
    val emptyTrash get() = s("Очистить", "Empty trash")
    val restore get() = s("Восстановить", "Restore")
    val deletePermanently get() = s("Удалить навсегда", "Delete permanently")

    // ═══════════════════════════════════
    // Terminal
    // ═══════════════════════════════════
    val terminalSettings get() = s("Настройки", "Settings")
    val font get() = s("Шрифт", "Font")
    val commands get() = s("Справочник команд", "Command reference")
    val sshConnections get() = s("SSH Подключения", "SSH Connections")
    val gestures get() = s("Жесты", "Gestures")
    val linux get() = s("Linux", "Linux")
    val install get() = s("Установить", "Install")
    val reinstall get() = s("Переустановить", "Reinstall")
    val uninstall get() = s("Удалить", "Uninstall")
    val setup get() = s("Настройка...", "Setting up...")

    // ═══════════════════════════════════
    // Onboarding
    // ═══════════════════════════════════
    val welcomeTitle get() = s("Glass Files", "Glass Files")
    val welcomeSubtitle get() = s("Файловый менеджер нового поколения", "Next generation file manager")
    val chooseLanguage get() = s("Выберите язык", "Choose language")
    val storageAccess get() = s("Доступ к хранилищу", "Storage access")
    val storageAccessDesc get() = s("Для работы с файлами нужен доступ к хранилищу", "Storage access is needed to manage your files")
    val grantAccess get() = s("Разрешить доступ", "Grant access")
    val continueBtn get() = s("Продолжить", "Continue")
    val getStarted get() = s("Начать", "Get started")

    // ═══════════════════════════════════
    // Splash
    // ═══════════════════════════════════
    val splashSubtitle get() = s("Файловый менеджер", "File Manager")

    // ═══════════════════════════════════
    // Permission Screen
    // ═══════════════════════════════════
    val permissionNeeded get() = s("Для работы с файлами нужен\nдоступ к хранилищу", "Storage access is needed\nto manage files")

    private fun s(ru: String, en: String): String = when (lang) {
        AppLanguage.RUSSIAN -> ru
        AppLanguage.ENGLISH -> en
    }

// ═══════════════════════════════════
// Additional strings added for full localization
// ═══════════════════════════════════

// SharedAndFolderScreens extras
val cutFile get() = s("Вырезано", "Cut")
val pasted get() = s("Вставлено", "Pasted")
val selectMode get() = s("Выбрать", "Select")
val cancelSelect get() = s("Отменить выбор", "Cancel selection")
val convert get() = s("Конвертировать", "Convert")
val find get() = s("Найти", "Find")
val replaceWith get() = s("Заменить на", "Replace with")
val text get() = s("Текст", "Text")
val prefixOpt get() = s("Префикс (опц.)", "Prefix (opt.)")
val renameBtn get() = s("Переименовать", "Rename")
val filesCount get() = s("файлов", "files")
val messages get() = s("сообщ.", "msgs")
val modelsWithPhoto get() = s("модели с поддержкой фото", "models with photo support")

// DuplicatesScreen
val deletedFiles get() = s("Удалено %d файлов", "Deleted %d files")
val storage get() = s("Хранилище", "Storage")
val duplicateGroupsOf get() = s("файлов", "files")

// QR types
val qrUrl get() = s("URL", "URL")
val qrWifi get() = s("WiFi", "WiFi")
val qrEmail get() = s("Email", "Email")
val qrPhone get() = s("Телефон", "Phone")
val qrSms get() = s("SMS", "SMS")
val qrGeo get() = s("Геолокация", "Location")
val qrContact get() = s("Контакт", "Contact")
val qrText get() = s("Текст", "Text")

// FileViewer
val cannotLoad get() = s("Не удалось загрузить", "Failed to load")
val fileTooLarge get() = s("Файл слишком большой для просмотра (>2МБ)", "File too large to view (>2MB)")
val lines get() = s("строк", "lines")
val viewNotAvailable get() = s("Просмотр недоступен", "Preview not available")

// GlobalSearch
val searchFiles get() = s("Поиск файлов...", "Search files...")
val searching get() = s("Поиск...", "Searching...")

// StorageAnalyzer
val storageAnalyzer get() = s("Хранилище", "Storage")
val used get() = s("Использовано", "Used")
val free get() = s("Свободно", "Free")
val of get() = s("из", "of")

// Trash extras
val trashTitle get() = s("Корзина", "Trash")
val restored get() = s("Восстановлено", "Restored")

// SSH
val sshTitle get() = s("SSH Подключения", "SSH Connections")
val newServer get() = s("Новый сервер", "New server")
val edit get() = s("Редактировать", "Edit")
val connect get() = s("Подключиться", "Connect")
val save get() = s("Сохранить", "Save")
val serverName get() = s("Название", "Name")
val host get() = s("Хост", "Host")
val port get() = s("Порт", "Port")
val user get() = s("Пользователь", "User")

// AI Chat
val noChats get() = s("Нет чатов", "No chats")
val startNewChat get() = s("Начните новый разговор с AI", "Start a new conversation with AI")
val newChat get() = s("Новый чат", "New chat")
val apiSettings get() = s("Настройки API", "API Settings")
val message get() = s("Сообщение...", "Message...")
val photoAttached get() = s("Фото прикреплено", "Photo attached")
val photo get() = s("Фото", "Photo")
val settingsSaved get() = s("Настройки сохранены", "Settings saved")

// Terminal
val termSettings get() = s("Настройки", "Settings")
val fontLabel get() = s("Шрифт", "Font")
val themeLabel get() = s("Тема", "Theme")
val quickCommands get() = s("Быстрые команды", "Quick commands")
val commandRef get() = s("Справочник команд", "Command reference")
val commandsCount get() = s("150+ команд Ubuntu", "150+ Ubuntu commands")
val savedServers get() = s("Сохранённые серверы", "Saved servers")
val gesturesTitle get() = s("Жесты", "Gestures")
val processEnded get() = s("⟳ Процесс завершён — нажми чтобы перезапустить", "⟳ Process ended — tap to restart")
val installing get() = s("Настройка...", "Setting up...")
val installed get() = s("Установлено", "Installed")
val reinstallQ get() = s("Переустановить?", "Reinstall?")
val uninstallQ get() = s("Удалить Linux?", "Remove Linux?")

// Properties
val fileProperties get() = s("Свойства", "Properties")
val propName get() = s("Имя", "Name")
val propPath get() = s("Путь", "Path")
val propSize get() = s("Размер", "Size")
val propModified get() = s("Изменён", "Modified")
val propType get() = s("Тип", "Type")
val propFolder get() = s("Папка", "Folder")
val propFile get() = s("Файл", "File")
val propItems get() = s("Элементов", "Items")

// Context Menu
val openIn get() = s("Открыть в…", "Open with…")

// Settings extras  
val fileFontSize get() = s("Размер шрифта файлов", "File font size")
val resetAll get() = s("Сбросить настройки", "Reset settings")
val resetConfirm get() = s("Все настройки будут сброшены", "All settings will be reset")
val reset get() = s("Сбросить", "Reset")
val aboutApp get() = s("О приложении", "About")

// Tags extras
val noFilesWithTag get() = s("Нет файлов с тегом", "No files with this tag")
val filesWithTag get() = s("Файлы с тегом появятся здесь", "Files with this tag will appear here")

// Audio
val audioPlayer get() = s("Аудиоплеер", "Audio player")
val justNow get() = s("Только что", "Just now")
val today get() = s("Сегодня", "Today")
val yesterday get() = s("Вчера", "Yesterday")
val minAgo get() = s("мин. назад", "min ago")
val hourAgo get() = s("час назад", "1 hour ago")
val hoursAgo get() = s("ч. назад", "hours ago")
val daysAgo get() = s("дн. назад", "days ago")
val weekAgo get() = s("неделю назад", "1 week ago")
val weeksAgo get() = s("нед. назад", "weeks ago")
val monthAgo get() = s("месяц назад", "1 month ago")

// Device Info
val deviceInfo get() = s("Об устройстве", "Device Info")
val deviceInfoSubtitle get() = s("CPU, GPU, батарея, камера", "CPU, GPU, battery, camera")
val deviceSection get() = s("Устройство", "Device")
val cpuSection get() = s("Процессор", "CPU")
val ramSection get() = s("Оперативная память", "RAM")
val storageSection get() = s("Хранилище", "Storage")
val batterySection get() = s("Батарея", "Battery")
val displaySection get() = s("Дисплей", "Display")
val networkSection get() = s("Сеть", "Network")
val sensorsSection get() = s("Сенсоры", "Sensors")
val cameraSection get() = s("Камера", "Camera")
val model get() = s("Модель", "Model")
val manufacturer get() = s("Производитель", "Manufacturer")
val androidVersion get() = s("Android", "Android")
val apiLevel get() = s("API уровень", "API Level")
val buildNumber get() = s("Номер сборки", "Build Number")
val securityPatch get() = s("Патч безопасности", "Security Patch")
val bootloader get() = s("Загрузчик", "Bootloader")
val board get() = s("Плата", "Board")
val hardware get() = s("Железо", "Hardware")
val cpuModel get() = s("Чип", "Chip")
val cores get() = s("Ядра", "Cores")
val architecture get() = s("Архитектура", "Architecture")
val cpuFreqMin get() = s("Мин. частота", "Min Frequency")
val cpuFreqMax get() = s("Макс. частота", "Max Frequency")
val cpuGovernor get() = s("Регулятор", "Governor")
val totalRam get() = s("Всего", "Total")
val availableRam get() = s("Доступно", "Available")
val usedRam get() = s("Используется", "Used")
val internalStorage get() = s("Внутренняя память", "Internal Storage")
val totalStorage get() = s("Всего", "Total")
val usedStorage get() = s("Занято", "Used")
val freeStorage get() = s("Свободно", "Free")
val batteryLevel get() = s("Уровень", "Level")
val batteryStatus get() = s("Статус", "Status")
val batteryHealth get() = s("Здоровье", "Health")
val batteryTemp get() = s("Температура", "Temperature")
val batteryVoltage get() = s("Напряжение", "Voltage")
val batteryTech get() = s("Технология", "Technology")
val charging get() = s("Заряжается", "Charging")
val discharging get() = s("Разряжается", "Discharging")
val full get() = s("Заряжен", "Full")
val healthGood get() = s("Хорошее", "Good")
val healthOverheat get() = s("Перегрев", "Overheat")
val healthDead get() = s("Мёртвая", "Dead")
val unknown get() = s("Неизвестно", "Unknown")
val resolution get() = s("Разрешение", "Resolution")
val density get() = s("Плотность", "Density")
val refreshRate get() = s("Частота обновления", "Refresh Rate")
val screenSize get() = s("Размер экрана", "Screen Size")
val wifiNetwork get() = s("WiFi сеть", "WiFi Network")
val ipAddress get() = s("IP адрес", "IP Address")
val macAddress get() = s("MAC адрес", "MAC Address")
val operator_ get() = s("Оператор", "Carrier")
val networkType get() = s("Тип сети", "Network Type")
val connected get() = s("Подключён", "Connected")
val disconnected get() = s("Отключён", "Disconnected")
val noSensors get() = s("Сенсоры не найдены", "No sensors found")
val megapixels get() = s("Мегапиксели", "Megapixels")
val frontCamera get() = s("Фронтальная", "Front")
val backCamera get() = s("Основная", "Rear")
val copyAll get() = s("Копировать всё", "Copy all")

// ═══════════════════════════════════
// v2.0 — App Manager
// ═══════════════════════════════════
val appManager get() = s("Приложения", "App Manager")
val appManagerSub get() = s("Извлечь APK, кэш, информация", "Extract APK, cache, info")
val installedApps get() = s("Установленные", "Installed")
val systemApps get() = s("Системные", "System")
val userApps get() = s("Пользовательские", "User")
val extractApk get() = s("Извлечь APK", "Extract APK")
val apkExtracted get() = s("APK извлечён", "APK extracted")
val clearCache get() = s("Очистить кэш", "Clear cache")
val appInfo get() = s("Информация", "Info")
val openApp get() = s("Открыть", "Open")
val uninstallApp get() = s("Удалить", "Uninstall")
val appSize get() = s("Размер", "Size")
val cacheSize get() = s("Кэш", "Cache")
val dataSize get() = s("Данные", "Data")
val versionLabel get() = s("Версия", "Version")
val packageName get() = s("Пакет", "Package")
val installedDate get() = s("Установлено", "Installed")
val updatedDate get() = s("Обновлено", "Updated")
val targetSdk get() = s("Target SDK", "Target SDK")
val minSdkLabel get() = s("Min SDK", "Min SDK")
val permissions get() = s("Разрешения", "Permissions")
val activities get() = s("Активности", "Activities")
val services get() = s("Сервисы", "Services")
val receivers get() = s("Ресиверы", "Receivers")
val sortByName get() = s("По имени", "By name")
val sortBySize get() = s("По размеру", "By size")
val sortByDate get() = s("По дате", "By date")
val totalApps get() = s("Всего приложений", "Total apps")
val searchApps get() = s("Поиск приложений...", "Search apps...")

// ═══════════════════════════════════
// v2.0 — Bookmarks
// ═══════════════════════════════════
val bookmarks get() = s("Закладки", "Bookmarks")
val bookmarksSub get() = s("Быстрый доступ к папкам", "Quick access to folders")
val addBookmark get() = s("Добавить закладку", "Add bookmark")
val removeBookmark get() = s("Удалить закладку", "Remove bookmark")
val bookmarkAdded get() = s("Закладка добавлена", "Bookmark added")
val bookmarkRemoved get() = s("Закладка удалена", "Bookmark removed")
val noBookmarks get() = s("Нет закладок", "No bookmarks")
val bookmarkName get() = s("Название", "Name")
val bookmarkPath get() = s("Путь к папке", "Folder path")

// ═══════════════════════════════════
// v3.0 — Shizuku
// ═══════════════════════════════════
val shizuku get() = s("Shizuku", "Shizuku")
val shizukuTools get() = s("Shizuku инструменты", "Shizuku Tools")
val shizukuSub get() = s("Расширенное управление без root", "Advanced management without root")
val shizukuNotRunning get() = s("Shizuku не запущен", "Shizuku is not running")
val shizukuNotInstalled get() = s("Shizuku не установлен", "Shizuku is not installed")
val shizukuNoPermission get() = s("Нет разрешения Shizuku", "No Shizuku permission")
val shizukuRequestPerm get() = s("Запросить разрешение", "Request permission")
val shizukuConnected get() = s("Shizuku подключён", "Shizuku connected")
val freezeApp get() = s("Заморозить", "Freeze")
val unfreezeApp get() = s("Разморозить", "Unfreeze")
val forceStop get() = s("Остановить", "Force stop")
val forceStopped get() = s("Остановлено", "Force stopped")
val frozen get() = s("Заморожено", "Frozen")
val unfrozen get() = s("Разморожено", "Unfrozen")
val clearCacheDone get() = s("Кэш очищен", "Cache cleared")
val silentInstall get() = s("Тихая установка", "Silent install")
val silentInstallDone get() = s("APK установлен", "APK installed")
val androidData get() = s("Android/data", "Android/data")
val androidObb get() = s("Android/obb", "Android/obb")
val accessRestricted get() = s("Доступ через Shizuku", "Access via Shizuku")

// ═══════════════════════════════════
// v3.0 — File Diff
// ═══════════════════════════════════
val fileDiff get() = s("Сравнение файлов", "File Diff")
val fileDiffSub get() = s("Сравнить два текстовых файла", "Compare two text files")
val selectFile1 get() = s("Выберите первый файл", "Select first file")
val selectFile2 get() = s("Выберите второй файл", "Select second file")
val file1 get() = s("Файл 1", "File 1")
val file2 get() = s("Файл 2", "File 2")
val compareFiles get() = s("Сравнить", "Compare")
val identical get() = s("Файлы идентичны", "Files are identical")
val added get() = s("Добавлено", "Added")
val removed get() = s("Удалено", "Removed")
val changed get() = s("Изменено", "Changed")
val linesChanged get() = s("строк отличается", "lines differ")

// ═══════════════════════════════════
// v3.0 — Markdown Notes
// ═══════════════════════════════════
val quickNotes get() = s("Быстрые заметки", "Quick Notes")
val quickNotesSub get() = s("Markdown редактор заметок", "Markdown note editor")
val newNote get() = s("Новая заметка", "New note")
val editNote get() = s("Редактировать", "Edit")
val noteTitle get() = s("Заголовок", "Title")
val noteContent get() = s("Содержание...", "Content...")
val noteSaved get() = s("Заметка сохранена", "Note saved")
val noteDeleted get() = s("Заметка удалена", "Note deleted")
val noNotes get() = s("Нет заметок", "No notes")
val markdownPreview get() = s("Предпросмотр", "Preview")
val markdownEdit get() = s("Редактор", "Editor")
val untitled get() = s("Без названия", "Untitled")

// ═══════════════════════════════════
// v3.0 — FTP Client
// ═══════════════════════════════════
val ftpClient get() = s("FTP/SFTP", "FTP/SFTP")
val ftpClientSub get() = s("Подключение к удалённым серверам", "Connect to remote servers")
val ftpHost get() = s("Хост", "Host")
val ftpPort get() = s("Порт", "Port")
val ftpUser get() = s("Логин", "Username")
val ftpPassword get() = s("Пароль", "Password")
val ftpConnect get() = s("Подключиться", "Connect")
val ftpDisconnect get() = s("Отключиться", "Disconnect")
val ftpConnecting get() = s("Подключение...", "Connecting...")
val ftpConnected get() = s("Подключено", "Connected")
val ftpError get() = s("Ошибка подключения", "Connection error")
val ftpDownload get() = s("Скачать", "Download")
val ftpUpload get() = s("Загрузить", "Upload")
val ftpSaved get() = s("Сохранённые серверы", "Saved servers")
val ftpProtocol get() = s("Протокол", "Protocol")
val ftpNoFiles get() = s("Пусто", "Empty")
val ftpDownloaded get() = s("Файл скачан", "File downloaded")
val ftpUploaded get() = s("Файл загружен", "File uploaded")

// ═══════════════════════════════════
// v3.0 — Archive formats
// ═══════════════════════════════════
val compressTo get() = s("Сжать в", "Compress to")
val archiveFormat get() = s("Формат архива", "Archive format")
val extracting get() = s("Распаковка...", "Extracting...")
val compressing get() = s("Сжатие...", "Compressing...")

// ═══════════════════════════════════
// v3.0 — Trash size on main
// ═══════════════════════════════════
val trashSizeLabel get() = s("Корзина", "Trash")

// ═══════════════════════════════════
// v2.0 — Content Search
// ═══════════════════════════════════
val contentSearch get() = s("Поиск по содержимому", "Content search")
val contentSearchSub get() = s("Grep — поиск текста в файлах", "Grep — search text in files")
val searchInFiles get() = s("Текст для поиска", "Text to search")
val searchFolder get() = s("Папка для поиска", "Search folder")
val matchesFound get() = s("Совпадений найдено", "Matches found")
val noMatches get() = s("Совпадений нет", "No matches")
val lineNumber get() = s("Строка", "Line")

// ═══════════════════════════════════
// v3.0 — Shizuku extended
// ═══════════════════════════════════
val shSystem get() = s("Система", "System")
val shLogs get() = s("Логи", "Logs")
val shAuto get() = s("Авто", "Auto")
val shRestrictedDirs get() = s("Ограниченные директории", "Restricted directories")
val shSystemDirs get() = s("Системные директории", "System directories")
val shTempFiles get() = s("Временные файлы", "Temp files")
val shSystemApps get() = s("Системные приложения", "System apps")
val shAppData get() = s("Данные приложений", "App data")
val shFileTools get() = s("Файловые инструменты", "File tools")
val shChangePerms get() = s("Изменить права", "Change permissions")
val shChmodSub get() = s("chmod для файлов и папок", "chmod for files and folders")
val shSymlink get() = s("Символическая ссылка", "Symbolic link")
val shCreateSymlink get() = s("Создать symlink", "Create symlink")
val shMountPoints get() = s("Точки монтирования", "Mount points")
val shCopyMount get() = s("Скопировать mount в буфер", "Copy mount to clipboard")
val shCopied get() = s("Скопировано", "Copied")
val shClearAll get() = s("Очистить всё", "Clear all")
val shDataCleared get() = s("Данные очищены", "Data cleared")
val shRemoved get() = s("Удалено", "Removed")
val shSize get() = s("Размер", "Size")
val shRestrictBg get() = s("Огр. фон", "Restrict bg")
val shAllowBg get() = s("Разр. фон", "Allow bg")
val shBgRestricted get() = s("Фон ограничен", "Background restricted")
val shBgAllowed get() = s("Фон разрешён", "Background allowed")
val shBackup get() = s("Бэкап", "Backup")
val shBackupSaved get() = s("Бэкап сохранён", "Backup saved")
val shDisplay get() = s("Дисплей", "Display")
val shChangeDpi get() = s("Изменить плотность экрана", "Change screen density")
val shChangeRes get() = s("Изменить разрешение экрана", "Change screen resolution")
val shResetDisplay get() = s("Сбросить дисплей", "Reset display")
val shResetDisplaySub get() = s("Вернуть DPI и разрешение по умолчанию", "Reset DPI and resolution to default")
val shReset get() = s("Сброшено", "Reset")
val shScreenCapture get() = s("Захват экрана", "Screen capture")
val shScreenshot get() = s("Скриншот", "Screenshot")
val shSaveScreenshot get() = s("Сохранить снимок экрана", "Save screenshot")
val shScreenshotSaved get() = s("Скриншот сохранён", "Screenshot saved")
val shScreenRecord get() = s("Запись экрана", "Screen recording")
val shScreenRecordSub get() = s("30 секунд screenrecord", "30 seconds screenrecord")
val shStopRecord get() = s("Остановить запись", "Stop recording")
val shStopRecordSub get() = s("Завершить screenrecord", "Stop screenrecord")
val shRecordStarted get() = s("Запись начата (30с)", "Recording started (30s)")
val shRecordStopped get() = s("Запись остановлена", "Recording stopped")
val shConnection get() = s("Подключение", "Connection")
val shBattery get() = s("Батарея", "Battery")
val shProcesses get() = s("Процессы", "Processes")
val shLoadProcesses get() = s("Загрузить процессы", "Load processes")
val shProcess get() = s("Процесс", "Process")
val shReboot get() = s("Перезагрузка", "Reboot")
val shFilter get() = s("Фильтр...", "Filter...")
val shLogsCleared get() = s("Логи очищены", "Logs cleared")
val shPressRefresh get() = s("Нажмите обновить для загрузки", "Press refresh to load")
val shChangeDpiTitle get() = s("Изменить DPI", "Change DPI")
val shCurrent get() = s("Текущий", "Current")
val shResolution get() = s("Разрешение экрана", "Screen resolution")
val shFilePath get() = s("Путь к файлу", "File path")
val shPermissions get() = s("Права", "Permissions")
val shTarget get() = s("Цель", "Target")
val shLinkPath get() = s("Путь ссылки", "Link path")
val shCreated get() = s("Создано", "Created")
val shApply get() = s("Применить", "Apply")

// Automation tab
val shTouchSim get() = s("Симуляция касаний", "Touch simulation")
val shTap get() = s("Тап", "Tap")
val shLongTap get() = s("Долгий тап", "Long tap")
val shSwipeUp get() = s("Свайп вверх", "Swipe up")
val shSwipeDown get() = s("Свайп вниз", "Swipe down")
val shTextInput get() = s("Ввод текста", "Text input")
val shTextToType get() = s("Текст для ввода", "Text to type")
val shType get() = s("Ввести", "Type")
val shTyped get() = s("Введено", "Typed")
val shControlButtons get() = s("Кнопки управления", "Control buttons")
val shBack get() = s("Назад", "Back")
val shHome get() = s("Домой", "Home")
val shRecent get() = s("Недавние", "Recents")
val shVolumeUp get() = s("Громкость +", "Volume +")
val shVolumeDown get() = s("Громкость -", "Volume -")
val shMute get() = s("Без звука", "Mute")
val shForward get() = s("Вперёд", "Forward")
val shPower get() = s("Питание", "Power")
val shCamera get() = s("Камера", "Camera")
val shBrightness get() = s("Яркость", "Brightness")
val shMin get() = s("Мин", "Min")
val shMedium get() = s("Средняя", "Medium")
val shMax get() = s("Макс", "Max")
val shAutoBrightness get() = s("Авто", "Auto")
val shAutoB get() = s("Авто-яркость", "Auto brightness")
val shAnimations get() = s("Анимации", "Animations")
val shOff get() = s("Выкл", "Off")
val shScreenTimeout get() = s("Таймаут экрана", "Screen timeout")
val shDataOn get() = s("Данные вкл", "Data on")
val shDataOff get() = s("Данные выкл", "Data off")
val shAirplaneOn get() = s("Авиа вкл", "Airplane on")
val shAirplaneOff get() = s("Авиа выкл", "Airplane off")
val shGpsOn get() = s("GPS вкл", "GPS on")
val shGpsOff get() = s("GPS выкл", "GPS off")
val shSound get() = s("Звук", "Sound")
val shSilence get() = s("Тишина", "Silence")
val shAlarmsOnly get() = s("Только будильники", "Alarms only")
val shSoundOn get() = s("Звук ON", "Sound ON")
val shSysSoundsOff get() = s("Сист. звуки выкл", "System sounds off")
val shSysSoundsOn get() = s("Сист. звуки вкл", "System sounds on")
val shDisplayMode get() = s("Режим отображения", "Display mode")
val shDarkTheme get() = s("Тёмная тема", "Dark theme")
val shLightTheme get() = s("Светлая тема", "Light theme")
val shStayOnCharging get() = s("Не гаснуть (зарядка)", "Stay on (charging)")
val shNormalTimeout get() = s("Гаснуть обычно", "Normal timeout")
val shQuickLaunch get() = s("Быстрый запуск", "Quick launch")
val shUrlToOpen get() = s("URL для открытия", "URL to open")
val shOpenUrl get() = s("Открыть URL", "Open URL")
val shOpened get() = s("Открыто", "Opened")
val shWifiOn get() = s("Wi-Fi вкл", "Wi-Fi on")
val shWifiOff get() = s("Wi-Fi выкл", "Wi-Fi off")
val shBtOn get() = s("Bluetooth вкл", "Bluetooth on")
val shBtOff get() = s("Bluetooth выкл", "Bluetooth off")
val shEmptyOrNoAccess get() = s("Пусто или нет доступа", "Empty or no access")

// ═══════════════════════════════════
// Security
// ═══════════════════════════════════
val securityViolation get() = s(
    "Обнаружена модификация приложения. Использование модифицированной версии невозможно.",
    "Application modification detected. Modified version cannot be used."
)

// ═══════════════════════════════════
// v3.0 — Shizuku Privacy
// ═══════════════════════════════════
val shPrivacy get() = s("Приватность", "Privacy")
val shNetwork get() = s("Сеть", "Network")
val shBatteryTab get() = s("Батарея", "Battery")
val shFilesExtra get() = s("Файлы+", "Files+")
val shHideApp get() = s("Скрыть", "Hide")
val shUnhideApp get() = s("Показать", "Unhide")
val shAppHidden get() = s("Приложение скрыто", "App hidden")
val shAppUnhidden get() = s("Приложение видимо", "App unhidden")
val shBlockCamera get() = s("Блок камера", "Block camera")
val shAllowCamera get() = s("Разр. камера", "Allow camera")
val shBlockMic get() = s("Блок микрофон", "Block mic")
val shAllowMic get() = s("Разр. микрофон", "Allow mic")
val shBlockGps get() = s("Блок GPS", "Block GPS")
val shAllowGps get() = s("Разр. GPS", "Allow GPS")
val shCameraBlocked get() = s("Камера заблокирована", "Camera blocked")
val shCameraAllowed get() = s("Камера разрешена", "Camera allowed")
val shMicBlocked get() = s("Микрофон заблокирован", "Mic blocked")
val shMicAllowed get() = s("Микрофон разрешён", "Mic allowed")
val shGpsBlocked get() = s("GPS заблокирован", "GPS blocked")
val shGpsAllowed get() = s("GPS разрешён", "GPS allowed")
val shRevokeAll get() = s("Отозвать все разрешения", "Revoke all permissions")
val shRevoked get() = s("Отозвано", "Revoked")
val shRecentCameraUse get() = s("Недавний доступ к камере", "Recent camera access")
val shRecentMicUse get() = s("Недавний доступ к микрофону", "Recent mic access")
val shRecentGpsUse get() = s("Недавний доступ к GPS", "Recent GPS access")
val shPermsControl get() = s("Контроль разрешений", "Permission control")
val shSelectApp get() = s("Выберите приложение", "Select app")

// Network
val shNetstat get() = s("Активные соединения", "Active connections")
val shFirewall get() = s("Файрвол", "Firewall")
val shBlockInet get() = s("Блокировать интернет", "Block internet")
val shUnblockInet get() = s("Разблокировать интернет", "Unblock internet")
val shInetBlocked get() = s("Интернет заблокирован", "Internet blocked")
val shInetUnblocked get() = s("Интернет разблокирован", "Internet unblocked")
val shFlushRules get() = s("Сбросить правила", "Flush rules")
val shRulesFlushed get() = s("Правила сброшены", "Rules flushed")
val shDns get() = s("DNS серверы", "DNS servers")
val shSetDns get() = s("Установить DNS", "Set DNS")
val shDnsSet get() = s("DNS установлен", "DNS set")
val shNetworkInfo get() = s("Сетевые интерфейсы", "Network interfaces")
val shPing get() = s("Пинг", "Ping")
val shRoutes get() = s("Маршруты", "Routes")
val shCurrentDns get() = s("Текущий DNS", "Current DNS")
val shHost get() = s("Хост", "Host")

// Battery
val shDoze get() = s("Режим Doze", "Doze mode")
val shForceDoze get() = s("Принудительный Doze", "Force Doze")
val shDozeWhitelist get() = s("Белый список Doze", "Doze whitelist")
val shAddWhitelist get() = s("Добавить", "Add")
val shRemoveWhitelist get() = s("Убрать", "Remove")
val shKillAll get() = s("Убить все фоновые", "Kill all background")
val shKilled get() = s("Фоновые процессы убиты", "Background killed")
val shBatteryTemp get() = s("Температура", "Temperature")
val shBatteryHealth get() = s("Здоровье", "Health")
val shBatteryCycles get() = s("Циклы зарядки", "Charge cycles")
val shWakelocks get() = s("Вейклоки", "Wakelocks")
val shConsumers get() = s("Потребители батареи", "Battery consumers")

// Files extra
val shClearDalvik get() = s("Очистить Dalvik кэш", "Clear Dalvik cache")
val shDalvikCleared get() = s("Dalvik кэш очищен", "Dalvik cache cleared")
val shRescanMedia get() = s("Обновить медиа", "Rescan media")
val shMediaRescanned get() = s("Медиа обновлено", "Media rescanned")
val shWifiPasswords get() = s("Wi-Fi пароли", "Wi-Fi passwords")
val shStorageUsage get() = s("Использование хранилища", "Storage usage")
val shLargestFiles get() = s("Самые большие файлы", "Largest files")
val shClearAllCaches get() = s("Очистить все кэши", "Clear all caches")
val shAllCachesCleared get() = s("Все кэши очищены", "All caches cleared")
val shLoad get() = s("Загрузить", "Load")
val shCopiedToClipboard get() = s("Скопировано в буфер", "Copied to clipboard")

// ═══════════════════════════════════
// v3.0 — Theme customization
// ═══════════════════════════════════
val themeCustomize get() = s("Оформление", "Appearance")
val themeCustomizeSub get() = s("Темы, цвета, иконки", "Themes, colors, icons")
val themeMode get() = s("Тема", "Theme")
val accentColorLabel get() = s("Акцентный цвет", "Accent color")
val folderStyle get() = s("Стиль папок", "Folder style")
val defaultViewLabel get() = s("Вид по умолчанию", "Default view")
val fontSize get() = s("Размер шрифта", "Font size")
val previewTitle get() = s("Превью интерфейса", "Interface preview")
val previewSubtitle get() = s("Так будут выглядеть элементы", "This is how elements will look")
val previewButton get() = s("Кнопка", "Button")

// ═══════════════════════════════════
// v3.0 — Media player
// ═══════════════════════════════════
val mediaPlayer get() = s("Медиаплеер", "Media player")
val mediaPlayerSub get() = s("Встроенный видео/аудио плеер", "Built-in video/audio player")

// ═══════════════════════════════════
// v3.0 — Dual pane
// ═══════════════════════════════════
val dualPane get() = s("Двойное окно", "Dual pane")
val dualPaneSub get() = s("Два окна файлов рядом", "Two file panels side by side")

// ═══════════════════════════════════
// v3.0 — GitHub
// ═══════════════════════════════════
val github get() = s("GitHub", "GitHub")
val githubSub get() = s("Репозитории, коммиты, issues", "Repos, commits, issues")
val ghSignIn get() = s("Войти", "Sign in")
val ghLoginDesc get() = s("Войдите с помощью Personal Access Token для доступа к вашим репозиториям", "Sign in with Personal Access Token to access your repositories")
val ghTokenHint get() = s("Создайте токен: GitHub → Settings → Developer settings → Personal access tokens → Generate new token (classic)", "Create token: GitHub → Settings → Developer settings → Personal access tokens → Generate new token (classic)")
val ghRepos get() = s("Репозитории", "Repositories")
val ghFollowers get() = s("Подписчики", "Followers")
val ghFollowing get() = s("Подписки", "Following")
val ghSearchRepos get() = s("Поиск репозиториев...", "Search repositories...")
val ghNewRepo get() = s("Новый репозиторий", "New repository")
val ghRepoName get() = s("Название", "Name")
val ghRepoDesc get() = s("Описание", "Description")
val ghPrivate get() = s("Приватный", "Private")
val ghCommits get() = s("Коммиты", "Commits")
val ghBranches get() = s("Ветки", "Branches")
val ghMinimize get() = s("Свернуть", "Minimize")
val ghExpand get() = s("Развернуть", "Expand")
val ghClose get() = s("Закрыть GitHub", "Close GitHub")
val ghDropHint get() = s("Перенесите файлы сюда", "Drop files here")
// File operations
val ghUpload get() = s("Загрузить файл", "Upload file")
val ghUploadMulti get() = s("Загрузить файлы", "Upload files")
val ghEditFile get() = s("Редактировать", "Edit")
val ghDeleteFile get() = s("Удалить файл", "Delete file")
val ghDownloadFile get() = s("Скачать", "Download")
val ghCommitMsg get() = s("Сообщение коммита", "Commit message")
val ghFilePath get() = s("Путь в репозитории", "Path in repository")
val ghUploading get() = s("Загрузка...", "Uploading...")
val ghUploaded get() = s("Загружено", "Uploaded")
val ghCreateFile get() = s("Создать файл", "Create file")
val ghFileContent get() = s("Содержимое", "Content")
// Pull Requests
val ghPulls get() = s("PR", "PR")
val ghNewPR get() = s("Новый Pull Request", "New Pull Request")
val ghHead get() = s("Из ветки", "From branch")
val ghBase get() = s("В ветку", "Into branch")
val ghMerge get() = s("Merge", "Merge")
val ghMerged get() = s("Смержен", "Merged")
val ghOpen get() = s("Открыт", "Open")
val ghClosed get() = s("Закрыт", "Closed")
// Issues extended
val ghNewIssue get() = s("Новый issue", "New issue")
val ghCloseIssue get() = s("Закрыть", "Close")
val ghReopenIssue get() = s("Переоткрыть", "Reopen")
val ghAddComment get() = s("Комментарий...", "Comment...")
val ghSend get() = s("Отправить", "Send")
val ghLabels get() = s("Метки", "Labels")
val ghNoComments get() = s("Нет комментариев", "No comments")
// Star / Fork
val ghStar get() = s("Star", "Star")
val ghUnstar get() = s("Unstar", "Unstar")
val ghFork get() = s("Fork", "Fork")
val ghForked get() = s("Форкнуто", "Forked")
val ghStarred get() = s("В избранном", "Starred")
// Branches extended
val ghNewBranch get() = s("Новая ветка", "New branch")
val ghBranchName get() = s("Название ветки", "Branch name")
val ghFromBranch get() = s("От ветки", "From branch")
val ghDeleteBranch get() = s("Удалить ветку", "Delete branch")
// README & Stats
val ghReadme get() = s("README", "README")
val ghStats get() = s("Статистика", "Stats")
val ghLanguages get() = s("Языки", "Languages")
val ghContributors get() = s("Участники", "Contributors")
// Releases
val ghReleases get() = s("Релизы", "Releases")
val ghPrerelease get() = s("Пререлиз", "Prerelease")
val ghDownloads get() = s("Скачиваний", "Downloads")
// Gists
val ghGists get() = s("Gists", "Gists")
val ghNewGist get() = s("Новый Gist", "New Gist")
val ghPublic get() = s("Публичный", "Public")
val ghGistFiles get() = s("Файлы", "Files")
// Diff
val ghAdditions get() = s("Добавлено", "Additions")
val ghDeletions get() = s("Удалено", "Deletions")
val ghChangedFiles get() = s("Изменённые файлы", "Changed files")
// Misc
val ghConfirmDelete get() = s("Точно удалить?", "Confirm delete?")
val ghSearchPublic get() = s("Искать на GitHub...", "Search GitHub...")
val ghNoReadme get() = s("README не найден", "No README found")
val ghPickBranch get() = s("Ветка", "Branch")
// Actions (CI/CD)
val ghActions get() = s("Actions", "Actions")
val ghWorkflows get() = s("Рабочие процессы", "Workflows")
val ghRunning get() = s("Запущен", "Running")
val ghSuccess get() = s("Успешно", "Success")
val ghFailed get() = s("Ошибка", "Failed")
val ghCancelled get() = s("Отменён", "Cancelled")
val ghRerun get() = s("Перезапустить", "Rerun")
val ghRunWorkflow get() = s("Запустить сборку", "Run workflow")
val ghArtifacts get() = s("Артефакты", "Artifacts")
val ghExpired get() = s("Истёк", "Expired")
val ghDownloadArtifact get() = s("Скачать артефакт", "Download artifact")
val ghViewLogs get() = s("Логи", "Logs")
val ghTriggeredBy get() = s("Запустил", "Triggered by")
val ghDuration get() = s("Длительность", "Duration")
val ghNoWorkflows get() = s("Нет workflows", "No workflows")
// Notifications
val ghNotifications get() = s("Уведомления", "Notifications")
val ghMarkRead get() = s("Прочитано", "Mark read")
val ghMarkAllRead get() = s("Прочитать все", "Mark all read")
val ghNoNotifications get() = s("Нет уведомлений", "No notifications")
val ghUnread get() = s("Непрочитанные", "Unread")
val ghReason get() = s("Причина", "Reason")
// Upload from device
val ghUploadToGitHub get() = s("Загрузить в GitHub", "Upload to GitHub")
val ghSelectRepo get() = s("Выберите репозиторий", "Select repository")
val ghUploadingFile get() = s("Загрузка файла...", "Uploading file...")
val ghUploadSuccess get() = s("Файл загружен в GitHub", "File uploaded to GitHub")
// Watch
val ghWatch get() = s("Отслеживать", "Watch")
val ghUnwatch get() = s("Не отслеживать", "Unwatch")
// Code search
val ghSearchCode get() = s("Поиск по коду", "Search code")
val ghSearchCodeHint get() = s("Искать в коде...", "Search in code...")
val ghNoResults get() = s("Нет результатов", "No results")
// Profiles
val ghProfile get() = s("Профиль", "Profile")
val ghFollow get() = s("Подписаться", "Follow")
val ghUnfollow get() = s("Отписаться", "Unfollow")
val ghJoined get() = s("Присоединился", "Joined")
val ghCompany get() = s("Компания", "Company")
val ghLocation get() = s("Местоположение", "Location")
val ghBlog get() = s("Блог", "Blog")
// Starred & Orgs
val ghStarredRepos get() = s("Избранные", "Starred")
val ghOrganizations get() = s("Организации", "Organizations")
// Labels & Milestones
val ghManageLabels get() = s("Метки", "Labels")
val ghNewLabel get() = s("Новая метка", "New label")
val ghLabelColor get() = s("Цвет (hex)", "Color (hex)")
val ghMilestones get() = s("Вехи", "Milestones")
val ghNewMilestone get() = s("Новая веха", "New milestone")
val ghDueDate get() = s("Срок", "Due date")
// Batch
val ghBatchUpload get() = s("Загрузить папку", "Upload folder")
// GitHub Settings
val ghSettings get() = s("Настройки GitHub", "GitHub Settings")
val ghToken get() = s("Токен", "Token")
val ghChangeToken get() = s("Изменить токен", "Change token")
val ghClonePath get() = s("Путь загрузок", "Download path")
val ghClonePathSub get() = s("Downloads/GlassFiles_Git", "Downloads/GlassFiles_Git")
val ghClearCache get() = s("Очистить кэш", "Clear cache")
val ghCacheClearedMsg get() = s("Кэш очищен", "Cache cleared")
val ghDefaultBranch get() = s("Ветка по умолчанию", "Default branch")
val ghAccount get() = s("Аккаунт", "Account")
val ghTokenHidden get() = s("ghp_••••••••", "ghp_••••••••")
val ghAbout get() = s("О GitHub интеграции", "About GitHub integration")
val ghAboutDesc get() = s("Полноценный GitHub клиент внутри Glass Files", "Full GitHub client inside Glass Files")
val ghVersion get() = s("Версия", "Version")
// Commit
val ghCommitToGitHub get() = s("Коммит в GitHub", "Commit to GitHub")
val ghCommitFiles get() = s("Файлы для коммита", "Files to commit")
val ghCommitting get() = s("Коммит...", "Committing...")
val ghCommitSuccess get() = s("Коммит успешен", "Commit successful")
val ghCommitFailed get() = s("Ошибка коммита", "Commit failed")
val ghRepoPath get() = s("Путь в репозитории", "Path in repository")
}
