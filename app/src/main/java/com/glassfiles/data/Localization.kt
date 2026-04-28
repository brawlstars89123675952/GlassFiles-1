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
    val aiHubSubtitle get() = s("Чат, код, картинки, видео", "Chat, code, images, video")
    val aiCoding get() = s("Режим кодинга", "Coding mode")
    val aiCodingSubtitle get() = s("Готовый код, дифы, рефакторинг", "Ready code, diffs, refactoring")
    val aiImageGen get() = s("Генерация картинок", "Image generation")
    val aiImageGenSubtitle get() = s("DALL·E, Imagen, Wanx, Grok Imagine", "DALL·E, Imagen, Wanx, Grok Imagine")
    val aiVideoGen get() = s("Генерация видео", "Video generation")
    val aiVideoGenSubtitle get() = s("Veo, Wan-Video, Grok Video", "Veo, Wan-Video, Grok Video")
    val aiModels get() = s("Модели", "Models")
    val aiModelsSubtitle get() = s("Каталог по провайдерам", "Catalog by provider")
    val aiKeys get() = s("API-ключи", "API keys")
    val aiKeysSubtitle get() = s("OpenAI, Anthropic, Grok, Kimi…", "OpenAI, Anthropic, Grok, Kimi…")
    val aiKeyHint get() = s("Введите ключ", "Enter key")
    val aiKeyShow get() = s("Показать", "Show")
    val aiKeyHide get() = s("Скрыть", "Hide")
    val aiKeySave get() = s("Сохранить", "Save")
    val aiKeySaved get() = s("Сохранено", "Saved")
    val aiKeyClear get() = s("Очистить", "Clear")
    val aiKeyGetHere get() = s("Получить ключ", "Get a key")
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
