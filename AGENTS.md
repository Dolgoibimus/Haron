# AGENTS.md — Проводник

Живой файл. Обновляется в процессе разработки.
Выполненные задачи → из TODO в Журнал решений.
Новые фичи → в Changelog.

---

## Статус проекта

**Текущая версия:** 0.23 (Phase 2, Batch 23)
**Текущая фаза:** Phase 2 — поиск и передача

---

## В работе (текущая задача)

> Сюда записывается задача которая выполняется прямо сейчас.
> При /compact — сохранить прогресс здесь перед сжатием.

Bugfix: защищённая папка (пустая при входе) + поиск (не сбрасывался при навигации). Завершено.

---

## TODO

### Phase 1 — MVP (Недели 1-8)

- [x] Создать проект, подключить ecosystem-core
- [x] Настроить Hilt DI, Navigation Component
- [x] Запрос разрешений по максимуму для полноценной работы: `MANAGE_EXTERNAL_STORAGE`, `READ/WRITE_EXTERNAL_STORAGE` (старый Android), `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `ACCESS_FINE_LOCATION` (нужен для Wi-Fi Direct), `BLUETOOTH` + `BLUETOOTH_ADMIN` + `BLUETOOTH_CONNECT` + `BLUETOOTH_SCAN`, `NEARBY_WIFI_DEVICES` (Android 13+), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `CAMERA` (для QR), `READ_MEDIA_IMAGES` + `READ_MEDIA_VIDEO` + `READ_MEDIA_AUDIO` (Android 13+), `USB_PERMISSION`
- [x] SAF (Storage Access Framework): поддержка доступа к SD-карте и защищённым папкам через системный пикер Android — там где MANAGE_EXTERNAL_STORAGE недостаточно; пользователь один раз выдаёт доступ к папке, приложение запоминает URI; явная поддержка microSD — монтируется как обычная панель, все операции работают полноценно ← Batch 8+10
- [x] Вертикальный двухпанельный интерфейс (верх/низ) ← Batch 2
- [x] Разделитель между панелями (drag для изменения соотношения, двойной тап → 50/50) ← Batch 2
- [x] Базовая навигация по папкам (верхняя панель)
- [x] Избранное и закреплённые папки: быстрый доступ к часто используемым местам; история недавних папок и файлов ← Batch 3
- [x] Счётчик файлов и общий размер выделенного в реальном времени — показывается в панели выделения ← Batch 3
- [x] Отображение файлов: pinch/spread плавно меняет размер сетки — от одноколоночного списка до 6 колонок в ряд, как в галерее iOS; крайние положения фиксируются с haptic-откликом ← Batch 5+9
- [x] Сортировка: по имени, дате, размеру, расширению
- [x] Базовые операции: копирование, перемещение, удаление, переименование ← Batch 2
- [x] Быстрый поиск по текущей папке: фильтрует видимые файлы в реальном времени без индексации ← Batch 3
- [x] Создание файлов и папок с шаблонами: новый TXT, MD, папка с датой и т.д. ← Batch 3
- [x] Групповое выделение файлов: долгий тап активирует режим выделения, затем свайп вниз выделяет диапазон ← Batch 3
- [x] Drag-and-Drop одиночный (верхняя → нижняя панель) ← Batch 4
- [x] Drag-and-Drop групповой (стопка с цифрой) ← Batch 4
- [x] Визуальный фидбек при drag (подсветка целевой панели, haptics) ← Batch 4
- [x] Диалог конфликтов при копировании/перемещении (заменить/переименовать/пропустить) ← Batch 6
- [x] Сохранение открытых папок панелей между запусками ← Batch 6
- [x] Контроль версий при конфликтах (карточка сравнения) ← Batch 8
- [x] Корзина (30 дней, автоочистка) ← Batch 4
- [x] Foreground Service для фоновых операций (dataSync): при крупных операциях (>10 файлов или >50MB) запускается foreground service с прогрессом ← Batch 5
- [x] Интерактивное уведомление с прогресс-баром ← Batch 5

### Phase 2 — Поиск и передача (Недели 9-16)

- [x] Room БД (haron_db): таблица file_index + FTS5 виртуальная таблица с триггерами ← Batch 21
- [x] Content Observer — автообновление индекса при изменениях MediaStore (debounce 5 сек) ← Batch 21
- [x] ScreenOnReceiver — автоиндексация при включении экрана (throttle 30 мин) ← Batch 21
- [x] Фоновая индексация (WorkManager + HiltWorker) ← Batch 21
- [x] Глобальный поиск по имени и расширению с фильтрами (категория, размер, дата) ← Batch 21
- [x] Глубокий поиск по содержимому (TXT, MD, DOCX, ODT, DOC, RTF, PDF, аудио ID3, видео метаданные) ← Batch 22
- [x] Полнотекстовый поиск (FTS4 на отдельной таблице file_content) ← Batch 22.5
- [x] Три режима индексации: базовая (текст+EXIF), медиа (аудио/видео), визуальная (ML Kit) ← Batch 22.5
- [x] ML Kit Image Labeling: распознавание объектов на фото + словарь EN→RU (~400 категорий) ← Batch 22.5
- [x] Поиск по содержимому в панели (кнопки Тт/FindInPage в локальном поиске) ← Batch 22.5
- [x] Подсветка найденных слов в PDF ридере (Canvas overlay, навигация стрелками, автоскролл) ← Batch 23
- [ ] Поиск внутри архивов (без распаковки)
- [ ] OCR-поиск по фотографиям (ML Kit офлайн)
- [ ] Передача файлов — умный выбор протокола: при инициации передачи приложение автоматически определяет что поддерживает принимающая сторона и выбирает лучший доступный способ; пользователь видит только кнопку "Отправить", а не список протоколов
  - **Приоритет 1 — Wi-Fi Direct P2P:** Проводник на обоих устройствах, нет нужды в роутере, максимальная скорость
  - **Приоритет 2 — WebRTC P2P:** Проводник на обоих устройствах, соединение через QR-код, работает через интернет
  - **Приоритет 3 — SMB / WebDAV / FTP / SFTP сервер:** для iPhone без Проводника — пользователь открывает стандартный Files.app и подключается к серверу, никакого браузера; SFTP для подключения к серверам и NAS через SSH; NAS монтируется как обычная папка в панели
  - **Приоритет 4 — Bluetooth RFCOMM:** медленно, но работает без Wi-Fi и без роутера
  - **Приоритет 5 — HTTP через браузер:** универсальный fallback, работает с любым устройством у которого есть браузер
- [ ] USB OTG: автообнаружение при подключении с определением типа устройства (флешка, кард-ридер, внешний диск) и мгновенным открытием содержимого в панели; поддержка USB-клавиатуры/мыши; безопасное извлечение срабатывает автоматически при отключении кабеля — приложение сбрасывает буферы до того как Android размонтирует накопитель
- [ ] Обработка обрывов сетевых соединений: перед началом долгой передачи — предупреждение если включён battery saver или сигнал нестабилен; при обрыве — пауза с автоповтором, не падаем в середине операции; WAKE_LOCK удерживает соединение активным

### Phase 3 — Инструменты (Недели 17-24)

- [x] Быстрое превью по тапу на иконку: фото — миниатюра с EXIF-ориентацией; видео — кадр + длительность; аудио — обложка + ID3 теги; текст/код — первые 50 строк; PDF — первая страница; ZIP/7z/RAR — список файлов; APK — иконка + версия; DOC/DOCX/ODT/RTF — текст ← Batch 9
- [x] Встроенный медиаплеер: аудио и видео без выхода из приложения ← Batch 10+11
- [x] Плейлист папки: последовательное воспроизведение всех медиафайлов из папки, авто-переход, повтор ← Batch 11
- [x] Фоновое воспроизведение: аудио продолжает играть при свёрнутом приложении ← Batch 11
- [x] Управление на экране блокировки: play/pause/next/prev через уведомление MediaSession ← Batch 11
- [x] Встроенный текстовый редактор: TXT и MD, редактирование прямо в приложении, undo/redo, номера строк ← Batch 10
- [x] Тема оформления: светлая / тёмная / системная; настройка запоминает последние открытые пути в каждой панели ← Batch 7
- [x] Breadcrumbs: путь сверху кликабельный как в Windows Explorer — тап на любой части пути переходит туда сразу ← Batch 7
- [x] История навигации: кнопки назад/вперёд в каждой панели независимо — как в браузере ← Batch 7
- [x] Быстрый переход: кнопка "открыть эту папку в другой панели" — одним тапом ← Batch 7
- [x] Блокировка приложения по биометрии или пину — защищает всё приложение целиком, не только защищённую папку ← Batch 20
- [x] Читалка PDF + документов (DOC/DOCX/ODT/RTF) ← Batch 12+15
- [x] Внутренняя галерея (фото без системной галереи) ← Batch 12
- [x] EXIF-просмотр и удаление метаданных ← Batch 13
- [x] Работа с архивами (просмотр ZIP/RAR/7z, создание ZIP, извлечение) ← Batch 12
- [x] Временная полка (Clipboard Shelf) — свайп от края экрана ← Batch 14
- [x] Система меток (цветовые + текстовые теги) ← Batch 19
- [x] Защищённая папка (AES-256, Android Keystore, биометрия) ← Batch 20
- [x] Анализатор памяти: диаграмма по типам файлов + список крупных файлов + удаление ← Batch 13
- [x] Детектор дубликатов по хешу ← Batch 14

### Phase 4 — Продвинутые функции (Недели 25-32)

- [ ] Сравнение файлов и папок (java-diff-utils): текстовые файлы — две панели синхронный скролл, различия подсвечены построчно, можно принять изменения с любой стороны; бинарные файлы — сравнение по размеру, дате, хешу; папки — список что есть только в одной, только в другой, есть в обоих но различаются; всё вписано в вертикальные панели
- [ ] Интеграция с облаком: Google Drive, Dropbox, OneDrive — монтируются как обычные папки в панели, операции те же что и с локальными файлами
- [ ] Открывалка по умолчанию: регистрация Проводника как обработчика типов файлов — система предлагает его при открытии файлов из других приложений
- [ ] Машина времени (версии файлов для текстовых документов)
- [ ] Переименование с выбором способа: при нажатии "переименовать" — варианты: вручную, или "предложить по содержимому" для фото (ML Kit, офлайн); AI предлагает — пользователь принимает или редактирует, никакого автоматического переименования без подтверждения
- [x] Пакетное переименование: шаблон с нумерацией (отпуск_001, отпуск_002...), замена части имени во всех выделенных файлах, добавление префикса/суффикса, изменение регистра; предпросмотр результата до применения; история последних 10 использованных шаблонов ← Batch 19
- [ ] Стеганография (Tail-Append метод, AES-256)
- [ ] Мониторинг мусора в реальном времени: фоновое сканирование через WorkManager находит кэш, дубликаты, мусор мессенджеров и временные файлы; если найдено много ненужного — ненавязчивое уведомление "найдено X ГБ которые можно освободить"; порог срабатывания настраивается пользователем
- [ ] Монтажный стол (FFmpeg): обрезка клипов, склейка нескольких видео, простые переходы между клипами, извлечение аудиодорожки, замена звука, изменение скорости; таймлайн с превью; экспорт в выбранном качестве
- [ ] Чистильщик мессенджеров (Telegram/WhatsApp): находит медиафайлы и кэш из общих папок мессенджеров; перед удалением показывает список файлов с размером каждого — можно снять галочку с того что хочется оставить; сами чаты и переписка не затрагиваются; с учётом ограничений Android 11+ чистит только то что доступно без root
- [x] Боковое меню (drawer): единая точка входа — избранные папки, закладки, недавние файлы, подключённые устройства (OTG, NAS, облако); инструменты одним тапом (анализатор памяти, детектор дубликатов, чистильщик, терминал, лог операций); быстрый доступ к настройкам (тема, жесты, голос, подсказки) ← Batch 14
- [x] Установка APK из Проводника: тап на APK файл запускает установщик без лишних шагов; предупреждение если подпись не совпадает с уже установленной версией или источник неизвестен ← Batch 16
- [x] Менеджер приложений: раздел в боковом меню со списком всех установленных приложений — версия, размер, дата установки; для каждого приложения: извлечь APK и сохранить как файл, удалить, открыть в системных настройках ← Batch 16
- [ ] Система жестов: каждая функция приложения которую можно вызвать жестом регистрирует внутренний action-код; в настройках — отдельный экран со списком всех доступных действий, поиск по названию, для каждого действия можно назначить жест из списка доступных (свайп влево/вправо по файлу, длинный свайп, свайп от края экрана, двойной тап и др.); одно действие — один жест, конфликты подсвечиваются
- [ ] Система голосовых команд: те же action-коды что и у жестов; в настройках — список всех доступных действий с поиском, для каждого можно записать или напечатать свою голосовую фразу; распознавание офлайн через Android Speech Recognition; активация по кнопке или опционально по wake-word
- [ ] Шаринг на телевизор: Cast фото/видео из галереи и медиаплеера через Chromecast/Google Cast; Miracast для беспроводного дублирования экрана
- [ ] Телефон как пульт управления: нижняя панель превращается в тачпад (палец по экрану = курсор мыши) + виртуальная клавиатура по кнопке; работает при подключении к телевизору или монитору; также поддержка физической мыши через USB OTG и Bluetooth без настроек
- [x] Хаптик-язык: осмысленные паттерны вибрации для разных событий — успех, предупреждение, ошибка, завершение фоновой операции; не просто "вибрация есть/нет" ← Batch 17
- [x] Пустые папки: выделяются цветной рамкой; опционально предлагают удалить себя ← Batch 17
- [x] Размер папки в реальном времени: при выделении сразу видно сколько весит включая вложенные ← Batch 17
- [x] ~~Лог операций~~ (удалён, заменён принудительным удалением) ← Batch 17 → Post-17
- [x] Быстрый переход: кнопка "открыть эту папку в другой панели" — одним тапом ← Batch 7
- [ ] Счётчик на иконке приложения: показывает сколько фоновых операций идёт прямо сейчас
- [x] История навигации: кнопки назад/вперёд в каждой панели независимо — как в браузере ← Batch 7
- [x] Открыть во внешнем приложении: "Открыть в..." из панели действий + авто-открытие для файлов без встроенного обработчика ← Batch 13
- [ ] Создание файлов и папок с шаблонами: новый TXT, MD, папка с датой и т.д.
- [x] Хеш файла: MD5 / SHA-256 из диалога свойств ← Batch 13
- [ ] Сетевое обнаружение: автоматически видит другие устройства в локальной сети у которых открыт Проводник или доступен SMB
- [x] Ночной режим по расписанию: независимо от системной темы, например с 22:00 до 7:00 ← Batch 17
- [x] Размер шрифта и иконок: независимо от системных настроек ← Batch 17
- [x] Виджет на рабочий стол: быстрый доступ к избранным папкам без открытия приложения ← Batch 17
- [x] Закладки с быстрым доступом: пай-меню (полукруг) при тапе на левую зону разделителя; короткий тап = навигация, долгий = сохранить ← Batch 17 → Post-17
- [ ] Встроенный терминал: базовые команды (ls, cp, mv, grep, curl, cat) работают из коробки без установки чего-либо; если установлен Termux — автоматически подключается к нему и получает полную мощь; пользователь ничего не настраивает
- [ ] Root-режим (опциональный): если устройство рутовано — открываются дополнительные возможности: chmod, доступ к /data, системные папки, полная очистка кэша приложений; приложение работает полноценно и без root; тестирование через Nox с включённым root
- [ ] Система подсказок: при первом появлении неочевидной функции — мягкая мигающая рамка; тап показывает короткое пояснение с чекбоксом "больше не показывать"; в настройках раздел "Инструкции" — все подсказки списком, можно просмотреть в любой момент или сбросить; глобальный переключатель показывать/не показывать
- [ ] Полировка UI/UX, анимации, Physics-based Drag

---

## Опасные места (найденные в процессе)

> Сюда добавлять баги и ловушки обнаруженные при разработке.
> Формат: **Проблема** → Решение / Что сделали.

- **MANAGE_EXTERNAL_STORAGE** → на Android 11+ требует `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` Intent, обычные READ/WRITE не дают полного доступа

---

## Известные проблемы (нерешённые)

_(нет)_

---

## Журнал решений

> Выполненные задачи с описанием как решили.

### Bugfix — Защищённая папка + поиск при навигации
- **Защищённая папка пуста при входе через долгий тап Shield**: при навигации в `VIRTUAL_SECURE_PATH` (и любую другую папку) поиск из предыдущей папки не сбрасывался. `searchQuery` оставался, фильтр `name.contains()` отсеивал все защищённые файлы → панель показывала "ничего не найдено". **Fix**: `navigateTo()` и `navigateToFileLocation()` теперь сбрасывают `searchQuery`, `isSearchActive`, `searchInContent`, `contentSearchSnippets` при переходе в другую папку (`isNewFolder`).
- **Поиск по содержимому скрывал папки**: при включении "поиск внутри" (content search) фильтр `it.path in snippets` убирал все папки (у папок нет текстового содержимого для FTS-индекса). **Fix**: папки, имя которых совпадает с запросом, сохраняются в выдаче даже при активном поиске по содержимому.

### Batch 23 — Подсветка найденных слов в PDF ридере
- **PdfTextPositionExtractor** — новый класс в `common/util/`. Загружает PDF через `PDDocument` (pdfbox-android), обходит страницы, наследуется от `PDFTextStripper`, переопределяет `writeString()` для получения `TextPosition` каждого символа. Группировка символов в слова по gap (> 0.3 × ширина предыдущего символа). Конкатенация слов страницы в строку с маппингом `charIndex → wordIndex`, поиск query (case-insensitive), определение задетых слов → `List<PdfMatch>` с `RectF` координатами в PDF points.
- **PdfReaderScreen — PdfReaderContent** — новые state: `highlightQuery`, `allMatches`, `currentMatchIndex`, `isExtractingText`, `pageScaleMap`, `matchesByPage`. LaunchedEffect для извлечения совпадений (Dispatchers.IO, temp file для content://). LaunchedEffect для авто-скролла к первому совпадению и навигации при изменении `currentMatchIndex`. TopAppBar actions: спиннер при извлечении, счётчик `1/N`, стрелки ↑↓.
- **PdfReaderScreen — PdfPageItem** — сохраняет `scaleF` в `pageScaleMap` при рендере. `Image` обёрнут в `Box { Image + Canvas }`. Canvas рисует полупрозрачные прямоугольники: жёлтый (`#FFEB3B` 40%) для обычных совпадений, оранжевый (`#FF9800` 60%) для текущего. Координаты: `rect * scaleF * displayScale`.
- **SearchViewModel — onNameClick** — устанавливает `SearchNavigationHolder.highlightQuery` при `searchInContent && query.isNotBlank()`.
- **Fix: recycled bitmap crash** — убран `recycle()` при eviction из кеша страниц (composable мог ещё рисовать bitmap). Добавлена проверка `!bmp.isRecycled` перед использованием.
- **Y координаты** — `yDirAdj` = baseline от верха страницы. `heightDir` часто underestimates высоту глифа. Fix: `charY = yDirAdj - heightDir * 1.15`, `charH = heightDir * 1.3` — покрывает весь глиф с padding.

### Batch 22.5 — Полнотекстовый FTS + ML Kit Image Labeling + три режима индексации
- **FileContentEntity** — отдельная таблица `file_content(path PK, full_text, indexed_at)` для хранения полного текста (до 100K символов). `content_snippet` в `file_index` остаётся для UI-отображения (500 символов).
- **file_content_fts** — FTS4 виртуальная таблица `USING fts4(full_text, content='file_content')` + 3 триггера (insert/delete/update).
- **ContentExtractor рефакторинг** — 3 новых публичных метода: `extractFullText(File)` (полный текст для FTS), `extractImageMeta(File)` (ExifInterface: Make, Model, DateTime, ImageDescription, GPS и т.д.), `extractMediaMeta(File)` (расширенный: +genre, +year, +composer). Старый `extractSnippet()` делегирует к snippet-версиям.
- **ImageLabeler** — `@Singleton`, обёртка над ML Kit `ImageLabeling.getClient()`. Bitmap downscale до 640px через `inSampleSize`. `suspendCancellableCoroutine` для async ML Kit. Confidence ≥ 0.7.
- **ImageLabelDictionary** — объект с ~400 маппингов EN→RU. `translate(label)` — case-insensitive lookup. `buildSearchableText(labels)` — "Dog Собака Cat Кошка" для билингвального поиска.
- **IndexMode enum** — `BASIC`, `MEDIA`, `VISUAL`. Добавлен в `IndexProgress.mode` для отображения текста прогресса.
- **SearchRepositoryImpl.indexByMode()** — `collectFiles(root, mode)` рекурсивный обход с фильтрацией по типу файлов. Батчи по 100 в `fileContentDao` + синхронизация snippet в `fileIndexDao`.
- **Поиск через FTS** — `searchFiles()` при `searchInContent=true` использует `INNER JOIN file_content_fts` с `MATCH ?` (prefix search `query*`). Fallback на `name LIKE` если FTS не даёт результат.
- **SearchScreen** — 3 IconButton в toolbar (Refresh/MusicNote/PhotoCamera), каждая показывает свой `CircularProgressIndicator` когда активна, остальные disabled.

### Fix: Чёрный экран VLC в превью + повторное открытие плеера
- **Проблема 1 — VLC в превью**: `attachViews()` внутри `AlertDialog` не может рендерить видео — Dialog создаёт отдельную субкомпозицию (`DialogWrapper` + своя `ComposeView`), VLC-поверхность не получает рендер.
- **Решение**: убрали VLC из превью целиком. Видео в превью показывает только thumbnail + кнопка Play → сразу открывает полноэкранный плеер (MediaPlayerScreen). Аудио продолжает играть inline (не использует VLC-поверхность).
- **Удалено**: `activeVlcLayout` глобальная переменная, VLC overlay в PreviewPagerContent, VLCVideoLayout/AndroidView в InlineVideoContent, `videoOverlayActive` + `autoStartPage` состояние, 5 неиспользуемых импортов.
- **Seekbar**: показывается только для аудио (видео не играет inline).
- **Проблема 2 — чёрный экран при повторном открытии**: `attachViews()` вызывался в `AndroidView.factory` (выполняется один раз при создании View), но `startService()` — асинхронный. На повторном входе `PlaybackService.instance` мог быть `null` в момент factory → поверхность никогда не подключалась к VLC.
- **Решение**: `attachViews()` вынесен из factory в `LaunchedEffect(videoLayoutRef)` — ждёт готовности сервиса (опрос каждые 50мс), затем подключает. Добавлен `detachViews()` в BackHandler перед `stopService()` для чистого отключения.

### Batch 18 — Пай-меню закладок/инструментов + BackHandler поиска
- **BookmarkPopup переписан** — 6 слотов вместо 9, плашки `RoundedCornerShape(12.dp)` 80×36dp вместо кружков. Только имя папки (без цифр). Пустой слот — тусклое "—". Фон: `primaryContainer.copy(0.9f)` / `surfaceContainerHighest.copy(0.9f)`. `focusable=false` — тап за popup закрывает его И проходит к элементу под ним (1 тап вместо 2).
- **ToolsPopup создан** — зеркальная версия BookmarkPopup. `Popup(CenterEnd)`, полукруг влево (углы 90°..270°). 6 инструментов: Корзина, Анализатор, Дупликатор, APK, Плеер, Читалка. Плашки `primaryContainer.copy(0.9f)`, focusable=false. Тап на правую зону разделителя.
- **HaronPreferences** — `lastMediaFile`, `lastDocumentFile` для быстрого доступа из пай-меню. Сохраняются при открытии медиа/документа через onFileClick.
- **ExplorerViewModel** — `showToolsPopup()`, `dismissToolsPopup()`, `onToolSelected(index)`, `openLastMedia()`, `openLastDocument()`. Проверка File.exists(), toast "Нет недавних".
- **BackHandler поиска** — `isSearchActive` поднят из локального state FilePanel в PanelUiState. BackHandler закрывает поиск кнопкой «Назад» (приоритет: drawer → поиск → rename → selection → history → navigate up).
- **Nudge** — вторая сверху и вторая снизу плашки сдвинуты на 10dp к центру чтобы не наезжали друг на друга при угле 31°.

### Post-Batch 17 — Принудительное удаление + Прогресс + Пай-меню + Корзина
- **Удалён лог операций** — OperationLogScreen, OperationLogRepository, OperationLogEntry, все ссылки из ViewModel/Screen/Navigation/DrawerMenu. Заменён на принудительное удаление.
- **ForceDeleteUseCase** — итеративный bottom-up DFS с лимитом глубины 256. `onProgress` callback для пофайлового прогресса. Для "матрёшечных" папок (бесконечная вложенность от бага копирования).
- **ForceDeleteConfirmDialog** — AlertDialog с красным предупреждением, список имён файлов, кнопка "Удалить навсегда".
- **Пофайловый прогресс** — все 5 операций (delete, copy, move, drag-move, force-delete) показывают текущий файл в прогресс-баре: `"Копирование: 3/10 — photo.jpg"`.
- **Предрасчёт корзины** — `confirmDelete()` суммирует размер всех входящих файлов, вызывает `trashRepository.evictToFitSize()` один раз, затем итерирует по файлам.
- **Лимит корзины** — секция "Корзина" в SettingsScreen: слайдер 0–5000 МБ, 0 = без лимита. `trashMaxSizeMb` в HaronPreferences/SettingsViewModel.
- **Обновление корзины в drawer** — `toggleDrawer()` вызывает `updateTrashSizeInfo()` при открытии.
- **BookmarkPopup → Пай-меню** — `Popup(CenterStart)` с custom `Layout`, полукруг от -90° до 90° (9 элементов). Ширина `radius + itemSize`. Без анимации (мгновенное появление).
- **PanelDivider → 3 зоны** — Row с 3 weighted Box: левая (тап → закладки, instant), центральная (тап → 50/50), правая (TBD). Drag gesture на parent Box.
- **DrawerMenu** — "Лог операций" заменён на "Принудительное удаление" (красная иконка DeleteOutline, subtitle "Без корзины, навсегда").

### Batch 17 — Настройки + Хаптик + Инструменты + Виджет + Закладки
- **HapticManager** — `@Singleton`, 5 паттернов: `success()` (короткий двойной), `warning()` (тройной пульс), `error()` (длинный), `completion()` (восходящий), `tick()` (одиночный). `VibrationEffect.createWaveform()` (SDK 26+). Проверка `hapticEnabled` из preferences. Inject в ExplorerViewModel, вызов после copy/move/rename/delete/zip + при ошибках.
- **FindEmptyFoldersUseCase** — `Flow<List<String>>`, поддержка recursive (`walkBottomUp`) и non-recursive режимов. Сортировка результатов.
- **EmptyFolderCleanupDialog** — AlertDialog с чекбоксами, "Выбрать все", toggle "Включая вложенные". Удаление через `MoveToTrashUseCase`. Пункт "Пустые папки" (FolderOff) в DrawerMenu.
- **Размер папки** — `folderSizeCache: Map<String, Long>` + `folderSizeJobs: Map<String, Job>` в ViewModel. Async `File.walkTopDown()` на `Dispatchers.IO` при выделении. `CircularProgressIndicator(12.dp)` в SelectionActionBar пока считается. Отмена stale jobs при снятии выделения.
- **OperationLogRepository** — `@Singleton`, файл `.haron_operation_log.json` в `getExternalFilesDir(null)`. Mutex для потокобезопасности. Лимит 1000 записей. `addEntry()`, `readAll()`, `clear()`.
- **OperationLogScreen** — Scaffold + TopAppBar + search + LazyColumn. Фильтрация по типу операции. Иконка + время + пути + бейдж результата. Overflow "Очистить лог". Роут `operation_log`, пункт "Лог операций" (Assignment) в DrawerMenu.
- **SettingsScreen** — 3 секции: "Ночной режим" (toggle + TimePickerDialog start/end), "Шрифт и иконки" (слайдеры 0.8–1.4 + текстовый превью), "Хаптик" (toggle). Роут `settings`, пункт "Настройки" (Settings) в DrawerMenu.
- **Night mode schedule** — `LaunchedEffect` в MainActivity проверяет расписание каждые 60 сек. `Calendar` для текущего времени, поддержка перехода через полночь. `nightModeForced` state → force darkTheme.
- **HaronScaling** — `data class(fontScale, iconScale)`, `LocalHaronScaling` compositionLocalOf. `CompositionLocalProvider` в MainActivity. `scaledTypography` — ребилд всех 15 стилей Typography при изменении fontScale.
- **BookmarkPopup** — `Popup` с сеткой 3×3 (слоты 1-9). `combinedClickable`: короткий тап → навигация, длинный тап → сохранить текущую папку. Вызов через long press на PanelDivider. JSON Map в HaronPreferences.
- **FavoriteFoldersWidget** — `GlanceAppWidget`, читает favorites из SharedPreferences. `LazyColumn` с кликабельными строками → `actionStartActivity(Intent)` с `navigate_to` extra. Widget metadata 180×110dp. `GlanceAppWidgetReceiver`. Зависимости: `glance-appwidget:1.1.1`, `glance-material3:1.1.1`.
- **HaronPreferences** — 10 новых свойств (haptic, nightMode×5, fontScale, iconScale) + 3 метода bookmarks (getBookmarks, setBookmark, removeBookmark).

### Batch 15 — Читалка документов + UX-улучшения
- **PdfReaderScreen** — расширен для документов. Определение типа по расширению: PDF → существующая логика (PdfRenderer), DOC/DOCX/ODT/RTF → извлечение текста + scrollable+zoomable отображение. Выделен `DocumentReaderContent` (новый composable) и `PdfReaderContent` (прежняя PDF-логика).
- **Извлечение текста** — 4 inline-функции: `extractDocx()` (ZipFile → word/document.xml → regex `<w:t>`), `extractOdt()` (ZipFile → content.xml → strip XML tags), `extractDoc()` (HWPFDocument + WordExtractor из Apache POI), `extractRtf()` (regex strip control words). `resolveFile()` для content:// URI (copy to temp cache).
- **DocumentReaderContent** — LaunchedEffect на `Dispatchers.IO`, state: `documentText`, `lineCount`, `isLoading`, `error`. UI: `Text` внутри `verticalScroll` + `transformable` + `graphicsLayer` для zoom (1x–5x). Double-tap: toggle 1x ↔ 2x. Bottom bar: "Строк: N".
- **ExplorerViewModel** — ветка `"document"` в `onFileClick()` → `NavigationEvent.OpenPdfReader`.
- **QuickPreviewDialog** — `isDocument = isText && entry.iconRes() == "document"`. Кнопка "Открыть в читалке" для документов (реюзает `onOpenPdf` callback). Порядок: media → image → pdf → archive → document → editableText.
- **FileListItem** — зона тапа по иконке увеличена с 40dp до 48dp (Material Design minimum). Ripple-отклик добавлен (убран `indication = null`). Grid: убран `indication = null` для ripple.
- **MainActivity** — `SideEffect` + `WindowInsetsControllerCompat.isAppearanceLightStatusBars` синхронизирует цвет статус-бара с текущей темой.

### Batch 14 — Детектор дубликатов + Полка + Боковое меню
- **ShelfItem** — `data class(name, path, isDirectory, size)`. Хранение в `HaronPreferences` через JSON (add/remove/clear/getAll).
- **HaronPreferences** — 5 новых методов для полки: `getShelfItems()`, `saveShelfItems()`, `addShelfItems()` (без дубликатов по path), `removeShelfItem()`, `clearShelf()`.
- **ExplorerUiState** — `showFavoritesPanel` заменён на `showDrawer` + `showShelf` + `shelfItems: List<ShelfItem>`.
- **ExplorerViewModel** — Drawer: `toggleDrawer()`, `dismissDrawer()`, `navigateFromDrawer()`. Shelf: `addToShelf()` (из выделения), `removeFromShelf()`, `clearShelf()`, `toggleShelf()`, `dismissShelf()`, `pasteFromShelf(isMove)`. Init: загрузка полки с фильтрацией несуществующих файлов. `openDuplicateDetector()` → emit NavigationEvent.
- **DrawerMenu** — Surface 280dp, скруглённый правый край, LazyColumn. 5 секций: Хранилища (внутренняя + SAF), Избранное, Недавние, Инструменты (корзина, анализатор, дубликаты), Оформление (3 кнопки темы). Заменил `FavoritesPanel` (ModalBottomSheet).
- **ShelfPanel** — Surface 280dp, скруглённый правый край. Header с badge. LazyColumn с элементами. Bottom: "Вставить сюда" (copy), "Переместить сюда" (move), "Очистить". Пустое состояние с подсказкой.
- **SelectionActionBar** — новая кнопка "На полку" (Inventory2) между ZIP и Info. Итого 8 кнопок.
- **FilePanel** — Star → Menu (гамбургер), `onShowFavorites` → `onShowDrawer`. Из overflow убраны: Корзина, Анализ памяти, Тема (теперь в drawer).
- **ExplorerScreen** — FavoritesPanel удалён. AnimatedVisibility для DrawerMenu и ShelfPanel (slideInHorizontally). Scrim overlay. Tab-индикаторы на левом краю (полка с badge + меню). Свайп-зона 24dp на левом краю: верхняя половина → shelf, нижняя → drawer. BackHandler для drawer/shelf.
- **FavoritesPanel.kt** — удалён.
- **FindDuplicatesUseCase** — двухфазный алгоритм: (1) рекурсивное сканирование, группировка по размеру, (2) MD5 только для групп с одинаковым размером. `Flow<DuplicateScanProgress>`. Пропуск скрытых и нулевых файлов. Сортировка по wasted space.
- **DuplicateDetectorViewModel** — `@HiltViewModel`. State: progress + groups + expandedGroup + selectedPaths. Methods: `startScan()`, `toggleGroup()`, `toggleFileSelection()`, `keepOldestInAllGroups()`, `keepOldestInGroup()`, `deleteSelected()` (+ rescan).
- **DuplicateDetectorScreen** — Scaffold + TopAppBar. Summary card (группы, файлы, потраченное место). LinearProgressIndicator с фазой. LazyColumn с раскрываемыми группами. Checkbox на каждом файле. Badge "Оригинал" у старейшего. Кнопки "Оставить старейшие" (глобально и per-group). FAB "Удалить" при наличии выделения.
- **NavigationEvent.OpenDuplicateDetector** — data object. Route `duplicate_detector` в HaronNavigation.

### Batch 14.1 — Постоянные маркеры оригиналов + пометка папок + превью файла
- **HaronPreferences** — 4 новых метода: `saveOriginalOverrides(Map)`, `getOriginalOverrides()`, `saveOriginalFolders(Set)`, `getOriginalFolders()`. JSON-сериализация в SharedPreferences.
- **DuplicateDetectorState** — 3 новых поля: `originalFolders: Set<String>`, `isOriginalFolderMode: Boolean`, `allFolderPaths: List<String>`.
- **DuplicateDetectorViewModel** — инжектирует `HaronPreferences` + `@ApplicationContext`. Init: загрузка overrides/folders из preferences. `startScan()` НЕ сбрасывает overrides/folders. `getOriginalForGroup()` — трёхуровневый приоритет (override > папка > oldest). `reassignOriginal()` + `deleteSelected()` → persist overrides. `enterOriginalFolderMode()`, `exitOriginalFolderMode()`, `toggleOriginalFolder()`. `previewFile()` → FileProvider + ACTION_VIEW + chooser.
- **DuplicateDetectorScreen** — overflow menu (MoreVert) → "Папки-оригиналы" / "Готово". Режим пометки папок: LazyColumn с Checkbox+Folder+путь. FAB "Готово" в режиме папок. Разделение зон тапа в DuplicateGroupCard: левая/центр (combinedClickable: tap=toggle, longClick=preview), правая 76dp (combinedClickable: longClick=reassignOriginal). BackHandler: в режиме папок → exit mode.

### Batch 13 — Свойства файла + Открыть во внешнем + Анализатор памяти
- **GetFilePropertiesUseCase** — `Flow<FileProperties>` с прогрессивной загрузкой: сначала базовые свойства (имя, путь, размер, дата, MIME, разрешения), затем EXIF для фото, затем рекурсивный размер для папок. `removeExif()` через `ExifInterface.setAttribute(tag, null)` + `saveAttributes()`.
- **CalculateHashUseCase** — `Flow<HashResult>` с прогрессом. Параллельно MD5 + SHA-256 за один проход по файлу (8KB буфер). `invokeFromUri()` для SAF content://.
- **FilePropertiesDialog** — AlertDialog с LazyColumn. Секции: "Общее" (путь, размер, дата, MIME, разрешения), "EXIF-данные" (key-value, кнопка "Удалить EXIF"), "Хеш-суммы" (on-demand, прогресс-бар, кнопка копирования). MonoSpace для хешей.
- **DialogState.FilePropertiesState** — entry + properties + hashResult + isHashCalculating.
- **ExplorerViewModel** — методы: `showFileProperties()`, `showSelectedFileProperties()`, `calculateHash()`, `removeExif()`, `openWithExternalApp()`, `openSelectedWithExternalApp()`, `openStorageAnalysis()`.
- **onFileClick** — добавлен `else` ветка: файлы без встроенного обработчика (document, spreadsheet, presentation, file) → `openWithExternalApp()` через `Intent.ACTION_VIEW`.
- **SelectionActionBar** — 2 новые кнопки: "i" (Info, свойства файла, enabled при 1 выделенном), "Открыть в..." (OpenInNew, enabled при 1 файле не-папке).
- **FileProvider** — `androidx.core.content.FileProvider` в AndroidManifest + `file_paths.xml` (`external-path`, `external-files-path`, `cache-path`). `openWithExternalApp()` использует `FileProvider.getUriForFile()` + `Intent.createChooser()` для корректной передачи URI другим приложениям (без `FileUriExposedException` на Android 7+).
- **AnalyzeStorageUseCase** — `Flow<StorageAnalysis>` с прогрессом. Рекурсивный обход `Environment.getExternalStorageDirectory()`. Группировка по `iconRes()` → 7 категорий. Top-50 крупных файлов (>10 МБ) через min-heap. `StatFs` для общего/свободного. Эмит каждые 500ms.
- **StorageAnalysisViewModel** — `@HiltViewModel`. State: analysis + expandedCategory + selectedFiles. Methods: `startScan()`, `toggleCategory()`, `toggleFileSelection()`, `deleteSelectedFiles()`.
- **StorageAnalysisScreen** — Scaffold + LazyColumn. Круговая диаграмма (Canvas, ring-style). Строка "Занято X из Y, свободно Z". Прогресс сканирования (LinearProgressIndicator + счётчик). Категории с цветными иконками + progress bar + счётчик файлов. Раскрытие категории → крупные файлы. Секция "Крупные файлы (>10 МБ)" с чекбоксами. FAB "Удалить" при выделении.
- **NavigationEvent.OpenStorageAnalysis** — data object. Роут `storage_analysis` в HaronNavigation.
- **FilePanel overflow menu** — пункт "Анализ памяти" с иконкой PieChart.

### Batch 12 — Работа с архивами + Внутренняя галерея + Читалка PDF
- **GalleryScreen** — полноэкранная галерея. `HorizontalPager` по всем изображениям папки из `GalleryHolder`. Pinch-to-zoom (1x–5x) через `transformable` + `graphicsLayer`. Double-tap: toggle 1x ↔ 2x. Pan при zoom > 1. `BitmapFactory.decodeFile/Stream` + EXIF-ротация. `inSampleSize` для >4096px. SAF: `contentResolver.openInputStream` для content://. Top bar: назад + имя файла + кнопка "Поделиться" (Intent.ACTION_SEND). Bottom bar: счётчик "3 / 25" + размер файла. Auto-hide контролов через 3 сек. `beyondViewportPageCount = 1`.
- **PdfReaderScreen** — полноэкранная читалка PDF. `PdfRenderer` (Android built-in). `LazyColumn` — каждая страница как `Image(bitmap)` на всю ширину. Рендер по требованию: видимые + ±1 страница, кеш до 5 bitmap. Pinch-to-zoom (1x–5x). Double-tap: toggle 1x ↔ 2x. Bottom bar: "Страница 3 / 15", тап → диалог перехода к странице (GoToPageDialog). `DisposableEffect` для `PdfRenderer.close()` + `bitmap.recycle()`. Evict bitmap при выходе за окно ±2 страницы.
- **ArchiveViewerScreen + ArchiveViewerViewModel** — просмотр архива с навигацией внутри. Scaffold + TopAppBar. Breadcrumbs виртуального пути. `LazyColumn` с записями (папки сверху, файлы снизу). Иконки по типу. Тап на папку → `navigateInto`. Режим выделения для выборочного извлечения. Bottom bar: кол-во записей. Кнопки: "Извлечь всё" / "Извлечь выделенные" → Downloads. Обнаружение пароля через exception.
- **BrowseArchiveUseCase** — чтение содержимого по виртуальному пути. ZIP (`ZipFile`), 7z (`SevenZFile`), RAR (`junrar`). Фильтрация прямых потомков + синтез виртуальных директорий. Сортировка: папки сверху, alphabetical.
- **ExtractArchiveUseCase** — извлечение файлов с прогрессом через `Flow<ExtractProgress>`. Поддержка ZIP/7z/RAR. Выборочное извлечение (`selectedEntries`). SAF: copy to temp cache first.
- **CreateZipUseCase** — создание ZIP из выделенных файлов. `ZipOutputStream` + рекурсивный обход директорий.
- **GalleryHolder** — статический in-process holder (по образцу PlaylistHolder). `GalleryItem(filePath, fileName, fileSize)`.
- **NavigationEvent** — 3 новых события: `OpenGallery`, `OpenPdfReader`, `OpenArchiveViewer`.
- **HaronNavigation** — 3 новых роута: `gallery`, `pdf_reader`, `archive_viewer`.
- **ExplorerViewModel** — расширен `onFileClick()`: image → GalleryHolder → OpenGallery, pdf → OpenPdfReader, archive → OpenArchiveViewer. `buildGalleryFromPreview()`. `requestCreateArchive()` + `confirmCreateArchive()`.
- **QuickPreviewDialog** — 3 новых callback-а: `onOpenGallery`, `onOpenPdf`, `onOpenArchive`. Кнопки "Открыть в галерее/читалке/архиве" по типу превью.
- **SelectionActionBar** — кнопка "ZIP" (иконка Archive) для создания архива из выделенных файлов.
- **DialogState.CreateArchive** — диалог ввода имени архива. `CreateArchiveDialog` с TextField + суффикс .zip.

### Batch 11 — Плейлист папки + фоновое воспроизведение + экран блокировки
- **PlaybackService** — `MediaSessionService` (Media3) хостит VLC-плеер. Foreground service с типом `mediaPlayback`. Автоматическое уведомление с play/pause/next/prev + управление на экране блокировки. `companion object { var instance }` для доступа из UI (один процесс).
- **VlcPlayerAdapter** — мост VLC → Media3 `SimpleBasePlayer`. Управляет плейлистом, авто-переходом (EndReached → next), повтором (`REPEAT_MODE_ALL`). `_isTransitioning` флаг игнорирует stale VLC-события при смене трека. Optimistic UI update — UI обновляется мгновенно до начала воспроизведения VLC. Прямые getter-методы (`getCurrentIndex()`, `isCurrentlyPlaying()`, `getCurrentPositionMs()`, `getCurrentDurationMs()`) для обхода кеша SimpleBasePlayer и задержки IPC MediaController.
- **PlaylistHolder** — статический in-process объект для передачи плейлиста между ViewModel/Service/Screen. `PlaylistItem(filePath, fileName, fileType)`.
- **VideoPositionStore** — извлечён из MediaPlayerScreen в `data/datastore/`. Сохранение/восстановление позиции воспроизведения через SharedPreferences+JSON. Используется и в Screen, и в Service.
- **ExplorerViewModel** — `onFileClick` собирает плейлист из всех медиафайлов папки (audio + video), заполняет `PlaylistHolder`, отправляет `NavigationEvent.OpenMediaPlayer(startIndex)`.
- **MediaPlayerScreen** — переписан как клиент MediaController. Подключается к PlaybackService через `SessionToken` + `MediaController.Builder`. Polling adapter каждые 100ms для responsive UI. Видео: VLCVideoLayout attach/detach по lifecycle (ON_STOP/ON_START). BackHandler: видео → stop service, аудио → service продолжает (фоновое воспроизведение). Кнопки Next/Prev при playlist > 1.
- **VLC опции** — `--file-caching=300` (вместо 3000) для корректного воспроизведения opus-файлов. Software decoding для максимальной совместимости кодеков.
- **Зависимости** — `media3-session:1.5.1`. ExoPlayer заменён на LibVLC как движок (ExoPlayer зависимости остались для Media3 session API).
- **QuickPreviewDialog → PlaybackService** — превью переведено на общий PlaybackService вместо standalone VLC/ExoPlayer. Даёт: повтор всей папки (REPEAT_MODE_ALL), управление на экране блокировки, фоновое воспроизведение из превью. Маппинг `pageToMediaIndex` / `mediaToPageIndex` для синхронизации pager-страниц и индексов плейлиста. Auto-advance: при смене трека в адаптере pager автоматически перелистывает на нужную страницу. При свайпе пользователем — service переключает трек.
- **Кнопка Play/Pause в FilePanel** — появляется слева от кнопки поиска когда PlaybackService активен. Polling каждые 500ms. Тап → play/pause через VLC player. Primary tint для визуального выделения.

### Batch 10 — SAF UI + Медиаплеер + Текстовый редактор
- **SAF UI** — секция "Хранилища" в `FavoritesPanel` (ModalBottomSheet): строка "Внутренняя память" (PhoneAndroid) + SAF roots (SdCard, кнопка удаления) + "Добавить хранилище" (→ SAF picker). `safRoots: List<Pair<String, String>>` в `ExplorerUiState`. `refreshSafRoots()`, `removeSafRoot(uri)` в ViewModel. `isSafPath: Boolean` в `PanelUiState`, ставится в `navigateTo()`. Бейдж SdCard (14dp) в шапке `FilePanel` при `isSafPath=true`.
- **Навигационная инфраструктура** — `NavigationEvent` sealed interface (`OpenMediaPlayer`, `OpenTextEditor`). `_navigationEvent: MutableSharedFlow` в ViewModel. `ExplorerScreen` коллектит flow и вызывает callback-и `onOpenMediaPlayer`/`onOpenTextEditor`. `HaronNavigation` — роуты `media_player/{filePath}/{fileName}/{fileType}` и `text_editor/{filePath}/{fileName}` с URL-encoded параметрами.
- **Медиаплеер** — `MediaPlayerScreen` на ExoPlayer (Media3). Видео: `PlayerView` через `AndroidView`, тёмный фон, автоскрытие контролов 3 сек. Аудио: обложка альбома из `MediaMetadataRetriever`, название/исполнитель/альбом. Общее: seekbar, ±10с, play/pause, имя файла в шапке. Без ViewModel — lifecycle привязан к экрану. Поддержка file:// и content://.
- **Текстовый редактор** — `TextEditorScreen`, `BasicTextField` (monospace 13sp). Номера строк синхронизированы через общий `verticalScroll`. Undo/Redo стек (max 50, debounce 500ms). Сохранение через File API или SAF `openOutputStream("wt")`. Лимит 1 МБ с предупреждением. `BackHandler` → диалог "Сохранить / Не сохранять / Остаться". Статус-бар: строка курсора + индикатор изменений.
- **Интеграция с Quick Preview** — `onPlay` callback → кнопка "Воспроизвести" при видео/аудио превью. `onEdit` callback → кнопка "Редактировать" при текст/код превью. Тап → dismiss + навигация.
- **onFileClick** — обновлён: тап на audio/video → навигация в плеер, тап на text/code → навигация в редактор.
- **Зависимости** — `media3-exoplayer`, `media3-ui`, `media3-common` v1.5.1.

### Batch 9 — Quick Preview + сетка до 6 + бейдж расширений
- **Quick Preview** — тап по иконке файла (не по строке) открывает `AlertDialog` с превью. В режиме выделения иконка работает как обычный тап (toggle). Для папок — навигация.
- **PreviewData** — `sealed interface` с 8 подтипами: `ImagePreview`, `VideoPreview`, `AudioPreview`, `TextPreview`, `PdfPreview`, `ArchivePreview`, `ApkPreview`, `UnsupportedPreview`.
- **LoadPreviewUseCase** — диспатч по `iconRes()`, всё на `Dispatchers.IO`. SAF: `contentResolver.openInputStream()` для image/text, `MediaMetadataRetriever.setDataSource(context, uri)` для video/audio, `openFileDescriptor` для PDF, `copyToTemp` для archive/APK.
- **Image** — `BitmapFactory` с `inSampleSize` ≤1024px + EXIF-ориентация через `ExifInterface` + `Matrix` rotation.
- **Video** — `MediaMetadataRetriever.getFrameAtTime()` + длительность. Try-catch: если setDataSource падает → VideoPreview с null thumbnail.
- **Audio** — `MediaMetadataRetriever`: title/artist/album/duration/albumArt (embedded picture).
- **Text/Code** — `BufferedReader`, первые 50 строк, моноширинный шрифт. Расширенный список: conf, cfg, ini, properties, env, toml, fb2, csv, sql, gradle + sh, bat, c, cpp, h, rs, go и др.
- **PDF** — `PdfRenderer` API 21+, первая страница, масштаб до 1024px.
- **Archive** — ZIP: `java.util.zip.ZipFile` + обработка ZipException (пароль/повреждён). 7z: `SevenZFile` из commons-compress. RAR: `junrar` + `slf4j-nop`.
- **Document** — DOCX: ZIP → `word/document.xml` → regex `<w:t>`. ODT: ZIP → `content.xml` → strip XML. DOC: Apache POI `HWPFDocument` + `WordExtractor`. RTF: regex strip control words.
- **Сетка до 6** — `gridColumns` лимит расширен с 4 до 6 в ViewModel, HaronPreferences, FilePanel.
- **Бейдж расширений** — на иконке файла в обоих режимах (list/grid). `Text` с `secondaryContainer` фоном, `8sp` grid / `7sp` list.
- **Пустые папки** — `outline` tint + `outlineVariant` border с `RoundedCornerShape`. Детекция: `entry.isDirectory && entry.childCount == 0`.
- **Зависимости** — Apache POI (poi + poi-scratchpad), commons-compress + xz, junrar, slf4j-nop, exifinterface.

### Batch 7 — Breadcrumbs + история навигации + тема + открыть в другой панели
- **Кликабельные breadcrumbs** — `BreadcrumbBar` разбивает `displayPath` на сегменты через `split("/")`, каждый (кроме последнего) — `Text` с `clickable` + цвет `primary`. Тап → `onSegmentClick(fullPath)`. Корень отображается как "Хранилище".
- **История навигации** — `navigationHistory: List<String>` + `historyIndex: Int` в `PanelUiState`. `navigateTo(pushHistory=true)` обрезает forward-стек и добавляет путь (лимит 50). `navigateBack/Forward` меняют `historyIndex` и вызывают `navigateTo(pushHistory=false)`. Кнопки ← → в шапке панели заменили одиночную кнопку "назад". Disabled с alpha 0.3f. BackHandler: rename → selection → history back → navigate up.
- **Тема** — `EcosystemPreferences.theme` (system/light/dark) читается в `MainActivity` через `SharedPreferences.OnSharedPreferenceChangeListener` → `mutableStateOf` → `HaronTheme(darkTheme)`. `cycleTheme()` в ViewModel: system→light→dark→system. Пункт в overflow-меню с иконкой (BrightnessAuto/LightMode/DarkMode).
- **Открыть в другой панели** — `openInOtherPanel(panelId)` берёт `currentPath` и вызывает `navigateTo` для противоположной панели. Пункт в overflow-меню с иконкой `OpenInNew`.
- **Фикс активной панели** — `onPanelTap()` добавлен в: текст "Выбрано:", кнопки ← →, кнопку отмены выделения, текст статуса. Раньше тап по шапке панели с выделением не переключал активную панель.

### Batch 6 — Диалог конфликтов + сохранение панелей
- **ConflictResolution** — `enum(REPLACE, RENAME, SKIP)`, пробрасывается через `FileRepository` → `UseCase` → `FileOperationService`.
- **Детекция конфликтов** — `detectConflicts()` в ViewModel проверяет наличие файлов с такими же именами в целевой папке перед операцией. Если нет конфликтов — операция идёт без диалога.
- **ConflictDialog** — кастомный `Dialog` (не AlertDialog) для контроля ширины. Три кнопки в ряд: "Пропустить" / "Переименовать" / "Заменить", `labelMedium` шрифт, `SpaceBetween`.
- **FileRepositoryImpl** — логика REPLACE (deleteRecursively + записать), RENAME (суффикс (1)), SKIP (continue в цикле).
- **FileOperationService** — `EXTRA_CONFLICT_RESOLUTION` через Intent, парсинг `ConflictResolution.valueOf()`.
- **DnD с конфликтами** — `endDrag()` проверяет конфликты, показывает диалог, `executeDragMove()` принимает resolution.
- **Рефакторинг copy/move** — `copySelectedToOtherPanel` → `executeCopy()`, `moveSelectedToOtherPanel` → `executeMove()` с параметром resolution.
- **Grid rename span** — `GridItemSpan(maxLineSpan)` при `state.renamingPath == entry.path`, поле ввода растянуто на всю ширину grid.
- **Сохранение панелей** — `topPanelPath`/`bottomPanelPath` в `HaronPreferences`, сохранение при навигации, восстановление при старте с проверкой `File.isDirectory`.
- **Единый формат счётчика** — `formatFileCount(dirs, files)` используется и в кнопочных операциях, и в DnD.

### Batch 5 — Pinch-to-Grid + Foreground Service
- **Pinch-to-Grid** — `LazyColumn` заменён на `LazyVerticalGrid(GridCells.Fixed(gridColumns))`. `gridColumns` (1–4) хранится в `PanelUiState` и `HaronPreferences`. Обе панели синхронизируются. `detectTransformGestures` на контейнере списка: pinch in (scale < 0.7) → +1 колонка, pinch out (scale > 1.4) → −1 колонка. Haptic feedback при каждом переключении.
- **FileListItem grid mode** — `isGridMode: Boolean` параметр. При `gridColumns >= 2`: вертикальный Column с крупной иконкой (48dp), именем в 2 строки, selection badge в углу. При `gridColumns == 1`: оригинальный горизонтальный Row.
- **findItemIndexAtPosition** — 2D поиск по `LazyGridLayoutInfo.visibleItemsInfo` вместо 1D `LazyListLayoutInfo`. Проверяет x/y координаты каждого видимого item, fallback на ближайший по строке/столбцу.
- **FileOperationService** — foreground service (`dataSync` type, `START_STICKY`). Notification channel `file_operations` с прогресс-баром. Кнопка "Отмена" через PendingIntent. `StateFlow<OperationProgress?>` через companion object для подписки из ViewModel.
- **OperationProgress** — `data class(current, total, currentFileName, isComplete, error)`.
- **Порог для service** — >10 файлов или >50MB total size. Мелкие операции по-прежнему через use case напрямую.
- **UI прогресс** — `AnimatedVisibility` overlay внизу ExplorerScreen: "Копирование: 3/10 — photo.jpg" + `LinearProgressIndicator`. Автоочистка через 3 сек после завершения.

### Batch 4 — Drag-and-Drop + Корзина
- **Корзина** — файловая реализация через `.haron_trash/` + `meta.json` (Graceful Degradation, без Room). `TrashRepository` с mutex для потокобезопасности. Удаление → `moveToTrash`, восстановление → `restoreFromTrash`, автоочистка 30 дней при старте.
- **TrashDialog** — ModalBottomSheet: список удалённых файлов с оригинальным путём, размером, датой удаления и оставшимися днями. Множественное выделение, восстановление, удаление навсегда, очистка корзины.
- **Drag-and-Drop** — cross-panel DnD через поднятое на уровень ExplorerScreen состояние `DragState`. Long press на **выделенном** файле → DnD (на невыделенном → range selection). Ghost overlay (`DragOverlay`) следует за пальцем с badge количества файлов.
- **Определение целевой панели** — по Y-координатам через `onGloballyPositioned`, подсветка целевой панели зелёной рамкой (3dp tertiary).
- **Haptic feedback** — `LongPress` при начале drag, `TextHandleMove` при входе в зону другой панели.
- **DeleteConfirmDialog** — текст обновлён на "В корзину" вместо "Удалить навсегда".
- **SelectionActionBar** — иконка корзины (`DeleteOutline`) вместо полного удаления.

### Batch 3 — UX-улучшения
- **Счётчик размера выделенного** — `getSelectedTotalSize()` суммирует размеры, SelectionActionBar показывает "3 файла · 12.5 МБ" с правильным склонением
- **Быстрый поиск** — `searchQuery` в PanelUiState, фильтрация в FilePanel через `filteredFiles`, BasicTextField в компактной шапке
- **Шаблоны создания** — `FileTemplate` enum (Папка / TXT / MD / Папка с датой), `CreateFromTemplateDialog` с RadioButton, `CreateFileUseCase`
- **Избранное и недавние** — `HaronPreferences.getFavorites()/addFavorite()/removeFavorite()` через JSON в SharedPrefs, `getRecentPaths()/addRecentPath()` FIFO 10, `FavoritesPanel` ModalBottomSheet
- **Range selection** — `detectDragGesturesAfterLongPress` на LazyColumn, `findItemIndexAtOffset` через `LazyListLayoutInfo`, `selectRange()` в ViewModel
- **Инлайн-переименование** — `renamingPath` в PanelUiState, BasicTextField с TextRange для выделения до расширения, FocusRequester для автофокуса
- **Компактная шапка** — TopAppBar заменён на Surface+Row (36dp высота), иконки 18dp в 32dp кнопках
- **Сохранение скролла** — `navigateTo` использует `it.copy()` вместо `PanelUiState()`, loading показывается только при пустом списке
- **Toggle select all** — если всё выбрано, повторный тап снимает выделение
- **Активация панели по клику** — `setActivePanel` вызывается в начале `onFileClick`

### Batch 2 — Двухпанельный проводник + базовые файловые операции
- **Один ViewModel на обе панели** — `PanelId` (TOP/BOTTOM) параметризует все методы, `PanelUiState` хранит состояние каждой панели
- **ExplorerUiState** — `topPanel`, `bottomPanel`, `activePanel`, `panelRatio`, `dialogState`
- **FileRepository** — 5 новых методов: `copyFiles`, `moveFiles`, `deleteFiles`, `renameFile`, `createDirectory`
- **resolveConflict** — при конфликте имён добавляет ` (1)`, ` (2)` и т.д.
- **moveFiles fallback** — если `renameTo` не работает (разные файловые системы), копирует + удаляет
- **Use cases** — валидация имён (пустые, слэши) в RenameFileUseCase и CreateDirectoryUseCase
- **PanelDivider** — drag меняет соотношение панелей (0.2–0.8), double tap → 50/50, сохранение в HaronPreferences
- **FileListItem** — чекбокс в режиме выделения, подсветка `primaryContainer`
- **FilePanel** — border для активной панели, TopAppBar переключается между навигацией и selection mode
- **SelectionActionBar** — 5 кнопок: копировать, переместить, удалить, переименовать (1 файл), новая папка
- **Диалоги** — DeleteConfirmDialog, RenameDialog, CreateDirectoryDialog через `DialogState` sealed interface
- **BackHandler** — сначала снимает выделение, потом навигация вверх
- **Deprecation fix** — `Icons.AutoMirrored.Filled.InsertDriveFile` и `DriveFileMove`

### Batch 1 — Разрешения + навигация + список файлов + сортировка
- **Один ViewModel на обе панели** — ExplorerViewModel управляет topPanel и bottomPanel, проще для будущего DnD
- **Сортировка в UseCase** — а не в Repository, чтобы data layer оставался тупым
- **Папки всегда сверху** — при любой сортировке сначала директории, потом файлы
- **SharedPreferences haron_prefs** — для локальных настроек (сортировка, скрытые, сетка), ecosystem_prefs не трогаем
- **MANAGE_EXTERNAL_STORAGE** — Intent на экране разрешений, для старых Android — `RequestMultiplePermissions`

---

## Changelog

### 0.22.5 — Batch 22.5 (Phase 2)
- Полнотекстовый поиск: отдельная таблица `file_content` + FTS4 виртуальная таблица `file_content_fts` с триггерами
- Три режима индексации (кнопки в toolbar поиска):
  - Базовая (↻): полный текст документов (TXT/MD/DOCX/ODT/DOC/RTF/PDF) + EXIF метаданные фото
  - Медиа (♪): ID3-теги аудио (title/artist/album/genre/year/composer) + видео метаданные
  - Визуальная (📷): ML Kit Image Labeling — распознавание объектов на фото (~400 категорий)
- ML Kit Image Labeling: downscale до 640px, confidence ≥ 70%, EN+RU теги для FTS
- Словарь `ImageLabelDictionary`: ~400 маппингов EN→RU (животные, транспорт, еда, природа, здания, люди, одежда, электроника, мебель, спорт, искусство, инструменты, абстракции)
- `ContentExtractor` рефакторинг: `extractFullText()` (до 100K символов), `extractImageMeta()` (EXIF), `extractMediaMeta()` (ID3 расширенный)
- `FileContentEntity` + `FileContentDao`: отдельная таблица для полного текста с upsert/clear/stale cleanup
- Поиск по содержимому через FTS MATCH (prefix search) с fallback на имя файла
- HaronDatabase v3: +entity, +dao, +FTS4 таблица с триггерами insert/delete/update
- Каждая кнопка индексации показывает свой спиннер и текст прогресса
- Индексация батчами по 100 (content) / 500 (file_index)
- Локализация: 6 новых строк EN + RU

### 0.21.0 — Batch 21 (Phase 2)
- Room DB (`haron_db`): таблица `file_index` с индексами + FTS5 виртуальная таблица с триггерами синхронизации
- Entity `FileIndexEntity`: path, name, extension, size, lastModified, mimeType, parentPath, isDirectory, isHidden, indexedAt
- DAO с поиском по имени, расширению, динамическими фильтрами (RawQuery) и FTS5
- `SearchRepositoryImpl`: обход файловой системы батчами по 500, динамический SQL
- `FileIndexWorker` (HiltWorker + CoroutineWorker): фоновая индексация через WorkManager
- `ScreenOnReceiver`: автоиндексация при включении экрана (throttle 30 мин)
- `FileContentObserver`: слушает MediaStore, debounce 5 сек → one-time WorkManager задача
- `HaronApp` → `Configuration.Provider` + регистрация ContentObserver и ScreenOnReceiver
- AndroidManifest: отключён стандартный WorkManager initializer
- `SearchScreen`: поле поиска (debounce 300 мс), фильтры-чипы (категория/размер/дата), LazyColumn с пагинацией
- `SearchViewModel`: управление поиском, индексацией, навигацией
- Долгий тап на кнопку поиска в панели → глобальный поиск (вместо пункта в меню)
- Тап на результат → навигация в папку файла через `SearchNavigationHolder`
- Навигация: роут `SEARCH`, `NavigationEvent.OpenGlobalSearch`
- Локализация: 21 строка EN + RU
- Превью видео в глобальном поиске отключено (VLC краш в контексте поиска)
- QuickPreviewDialog: overlay-подход для VLC (поверхность вне пейджера) — попытка решить чёрный экран, проблема НЕ решена (см. Известные проблемы)

### 0.20.4 — Batch 20.4 (Phase 3)
- Потоковое шифрование/расшифровка (CipherOutputStream/CipherInputStream) — без OOM на больших файлах
- Режим защиты не блокирует обычные файловые операции (копирование, перемещение, удаление)
- Shield в SelectionActionBar: кнопка защиты/снятия защиты работает корректно (передача путей напрямую)
- Shield в панели: долгий тап → войти в режим защиты, тап → выйти из режима
- Shield: иконка подсвечивается при активном режиме защиты (showProtected на панели)
- Превью защищённых файлов: кнопки «Открыть в...» расшифровывают и открывают во встроенных просмотрщиках
- Навигация: выход из виртуального режима по стрелке назад и по тапу на щит
- Shield в шапке панели: убран тап-переключатель (только долгий тап + выход)

### 0.20.3 — Batch 20.3 (Phase 3)
- Защищённые файлы: открытие через встроенные просмотрщики (видео/аудио → плеер, изображения → галерея, текст/код → редактор, PDF/документы → читалка, архивы → архиватор) вместо внешних приложений
- Защищённые директории: создание новых папок и текстовых файлов (только FOLDER и TXT)
- FilePanel: стрелка назад работает как navigateUp при отсутствии истории навигации
- CreateFromTemplateDialog: фильтрация доступных шаблонов через allowedTemplates

### 0.20.2 — Batch 20.2 (Phase 3)
- BackHandler: кнопка «назад» на корневой директории не выкидывает из приложения (consumed, no-op)
- Виртуальный режим защищённых файлов: копирование — расшифровка в кэш → копирование в целевую панель
- Виртуальный режим: перемещение — расшифровка → копирование в целевую панель + удаление из защищённого хранилища
- Виртуальный режим: удаление — безвозвратное удаление из защищённого хранилища (каскадно для папок)
- Виртуальный режим: превью — расшифровка в кэш для QuickPreview (иконка файла)
- Виртуальный режим: preload соседних превью работает с protected файлами
- Виртуальный режим: блокировка копирования/перемещения В виртуальный путь (toast предупреждение)
- Виртуальный режим: блокировка создания файлов/папок в виртуальном пути
- SecureFolderRepository: новый метод deleteFromSecureStorage — удаление без восстановления
- Локализация: 6 новых строк (EN + RU) для виртуальных операций

### 0.20.1 — Batch 20.1 (Phase 3)
- Блокировка: точки PIN отображаются по реальной длине PIN (не всегда 8)
- Блокировка: автоввод PIN — при наборе нужного количества цифр проверка без кнопки OK
- Блокировка: shake-анимация + вибрация при неверном PIN, автосброс
- Блокировка: BIOMETRIC_WITH_PIN — авто-запуск биометрии + PIN-pad как fallback
- Shield auth: кнопка биометрии теперь работает (BiometricPrompt из FragmentActivity)
- Shield auth: прокинута длина PIN для правильного отображения
- Защита папок: при защите директория добавляется в индекс и удаляется (не остаётся пустой)
- Снятие защиты: директории восстанавливаются (mkdirs)
- Долгий тап на Shield → «Все защищённые файлы» — виртуальная директория со всеми protected
- Виртуальный путь __haron_secure__ — не сохраняется в recent/panel prefs
- Локализация: строка "All secure files" / "Все защищённые файлы"

### 0.20.0 — Batch 20 (Phase 3)
- Блокировка приложения: PIN-код (4-8 цифр) и/или биометрия на вход
- Блокировка: 4 режима — отключено, PIN, биометрия, биометрия + PIN
- Блокировка: автолок при уходе в фон, grace period 3 секунды
- Блокировка: shake-анимация при неверном PIN
- Блокировка: FLAG_SECURE предотвращает скриншоты когда locked
- Настройки безопасности: секция между "Хаптик" и "Корзина"
- Настройки: установка/смена PIN через двухшаговый диалог
- Защищённая папка: AES-256-GCM шифрование, ключ в Android Keystore
- Защищённая папка: файлы шифруются и перемещаются в приватное хранилище
- Защищённая папка: зашифрованный индекс (JSON) с метаданными файлов
- Shield-режим: кнопка Shield в шапке каждой панели (рядом с поиском)
- Shield-режим: тап → аутентификация (PIN/биометрия) → защищённые файлы видны на своих местах
- Shield-режим: повторный тап → файлы снова невидимы
- Защита файлов: кнопка Shield в панели действий при выделении
- Защита файлов: рекурсивная для папок (все файлы внутри шифруются)
- Защищённые файлы: иконка замка на иконке файла (grid и list mode)
- Защищённые файлы: тап → расшифровка во временный кэш → открытие
- Снятие защиты: расшифровка на оригинальное место
- Drawer: пункт "Защищённые файлы" с счётчиком и размером
- AuthManager: общий для App Lock и Secure Folder, SHA-256 хеш PIN
- SecureFolderRepository: Mutex для потокобезопасности, in-memory кеш индекса
- MainActivity: FragmentActivity для BiometricPrompt, lifecycle observer
- Biometric: dependency androidx.biometric:biometric:1.1.0
- Локализация: ~35 строк EN + RU

### 0.19.0 — Batch 19
- Система меток: создание/редактирование/удаление меток с выбором из 8 цветов (Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink)
- Метки: назначение на выделенные файлы/папки через кнопку Label в панели действий
- Метки: цветные точки (до 3 + "+N") отображаются рядом с метаданными файла в списке и под именем в сетке
- Метки: фильтр по метке в меню сортировки — показать только файлы с определённой меткой
- Метки: миграция при переименовании файлов (метки сохраняются)
- Метки: удаление метки автоматически снимает её со всех файлов
- Метки: управление через боковое меню (пункт «Метки»)
- Метки: полноэкранный диалог управления с inline-созданием/редактированием
- Хранение в SharedPreferences (JSON): определения меток + привязки файл→метки

### 0.18.0 — Batch 18
- Закладки: пай-меню переписано — 6 плашек с именами папок вместо 9 кружков с цифрами
- Закладки: focusable=false — тап за пределами popup закрывает его и проходит к элементу (1 тап вместо 2)
- Инструменты: новое правое пай-меню на разделителе панелей (Корзина, Анализатор, Дупликатор, APK, Плеер, Читалка)
- Плеер/Читалка из пай-меню: открывают последний воспроизведённый файл или toast "Нет недавних"
- Поиск: кнопка «Назад» теперь закрывает поиск (раньше нужно было тапать стрелку в панели)
- Разделитель панелей: правая зона → пай-меню инструментов (было пусто)

### 0.17.1 — Post-Batch 17
- Принудительное удаление: инструмент для "матрёшечных" папок (бесконечная вложенность), удаление навсегда без корзины
- Принудительное удаление: итеративный обход с лимитом глубины 256
- Пофайловый прогресс: все операции (копирование, перемещение, удаление, принудительное удаление) показывают текущий файл
- Предрасчёт корзины: размер освобождаемого места считается один раз до начала удаления
- Лимит корзины: слайдер 0–5000 МБ в настройках (0 = без лимита)
- Обновление размера корзины в боковом меню при его открытии
- Закладки: пай-меню (полукруг из 9 кружков) вместо столбца
- Закладки: один тап на левую зону разделителя → мгновенное появление меню (было долгий тап)
- Разделитель панелей: 3 тап-зоны (левая = закладки, центральная = 50/50, правая = TBD)
- Удалён лог операций (экран + репозиторий + модель + все ссылки)
- Drawer: "Лог операций" заменён на "Принудительное удаление"

### 0.17.0 — Batch 17 (Phase 3+4)
- Хаптик-язык: 5 паттернов вибрации — успех (двойной), предупреждение (тройной), ошибка (длинный), завершение (восходящий), одиночный тик
- Хаптик при файловых операциях: копирование, перемещение, переименование, удаление, создание архива
- Экран настроек: ночной режим, масштаб шрифта/иконок, хаптик
- Ночной режим по расписанию: включение/выключение по часам (поддержка перехода через полночь)
- Размер шрифта: слайдер 80%–140%, применяется ко всему приложению
- Размер иконок: слайдер 80%–140%
- Поиск пустых папок: диалог со списком, чекбоксы, toggle "Вложенные", удаление в корзину
- Размер папки при выделении: async-расчёт с крутилкой, кеш размеров
- ~~Лог операций~~ (удалён в 0.17.1, заменён принудительным удалением)
- Виджет "Haron Избранное": список избранных папок на рабочем столе, тап → открытие в приложении
- Закладки 1-9: долгий тап на разделитель панелей → попап с 9 слотами
- Закладки: короткий тап → навигация, долгий тап → сохранить текущую папку
- Drawer: 3 новых пункта — "Пустые папки", "Принудительное удаление", "Настройки"

### 0.15.0 — Batch 15 (Phase 3)
- Читалка документов: DOC, DOCX, ODT, RTF открываются прямо в приложении
- Документы: весь текст файла отображается с возможностью скролла
- Документы: зум щипком (1x–5x) и двойным тапом (1x ↔ 2x)
- Документы: индикатор "Строк: N" внизу экрана
- Тап на DOC/DOCX/ODT/RTF файл из проводника → встроенная читалка
- Quick Preview: кнопка "Открыть в читалке" для документов
- Иконки файлов: увеличена зона тапа до 48dp (было 40dp) — проще попасть
- Иконки файлов: добавлен ripple-отклик при тапе
- Статус-бар: цвет синхронизирован с текущей темой (светлый/тёмный)

### 0.14.1 — Batch 14.1 (Phase 3)
- Детектор дубликатов: маркер "Оригинал" сохраняется между сессиями и пересканированиями
- Детектор: режим "Папки-оригиналы" — пометка папок как источников оригиналов (три точки → Папки-оригиналы)
- Детектор: трёхуровневый приоритет определения оригинала: индивидуальный маркер > файл из помеченной папки > старейший файл
- Детектор: долгий тап по правой части строки файла → смена маркера оригинала
- Детектор: долгий тап по левой/центральной части → превью файла во внешнем приложении
- Детектор: FAB "Готово" и список папок с чекбоксами в режиме пометки
- Детектор: помеченные папки сохраняются между запусками приложения

### 0.14.0 — Batch 14 (Phase 3+4)
- Детектор дубликатов: сканирование всего хранилища, поиск файлов с одинаковым содержимым
- Детектор: двухфазный алгоритм (размер → MD5) для экономии времени
- Детектор: прогресс сканирования (фаза 1: сканирование, фаза 2: хеширование)
- Детектор: группы дубликатов с указанием потраченного места
- Детектор: бейдж "Оригинал" у старейшего файла в группе
- Детектор: "Оставить старейшие" — автовыделение всех копий кроме оригинала
- Детектор: удаление выбранных дубликатов + автоматическое пересканирование
- Временная полка (Clipboard Shelf): буфер для файлов между папками
- Полка: добавление из панели действий (кнопка "На полку")
- Полка: сохранение между запусками приложения
- Полка: "Вставить сюда" (копирование) и "Переместить сюда"
- Полка: удаление отдельных элементов и полная очистка
- Боковое меню (drawer) вместо ModalBottomSheet
- Drawer: секции — Хранилища, Избранное, Недавние, Инструменты, Оформление
- Drawer: быстрый доступ к Корзине, Анализатору памяти, Детектору дубликатов
- Drawer: переключение темы (системная / светлая / тёмная) кнопками
- Гамбургер-меню в шапке панели вместо звёздочки
- Tab-индикаторы на левом краю: полка (с badge если есть файлы) и меню
- Свайп от левого края: верхняя половина → полка, нижняя → drawer

### 0.13.0 — Batch 13 (Phase 3)
- Свойства файла: диалог с именем, путём, размером, датой, MIME, разрешениями
- Свойства папки: рекурсивный подсчёт размера и количества файлов
- EXIF-данные для фото: камера, дата, размеры, ISO, выдержка, GPS и др.
- Удаление EXIF-метаданных из фото одной кнопкой
- Хеш-суммы: MD5 и SHA-256 с прогрессом вычисления
- Копирование хеша в буфер обмена
- Кнопка "Свойства" (i) в панели действий при выделении 1 файла
- Открыть во внешнем приложении: кнопка "Открыть в..." в панели действий
- Автоматическое открытие во внешнем приложении для файлов без встроенного обработчика
- Анализатор памяти: круговая диаграмма по типам файлов
- Анализатор: категории (Фото, Видео, Музыка, Документы, Архивы, APK, Прочее)
- Анализатор: информация о занятом/свободном месте
- Анализатор: список крупных файлов (>10 МБ, топ 50)
- Анализатор: раскрытие категории → список файлов
- Анализатор: выделение и удаление файлов прямо из анализатора
- Анализатор: пункт "Анализ памяти" в меню панели

### 0.12.0 — Batch 12 (Phase 3)
- Внутренняя галерея: полноэкранный просмотр изображений, свайп между картинками
- Галерея: зум щипком (1x–5x) и двойным тапом (1x ↔ 2x)
- Галерея: автоскрытие контролов через 3 сек, кнопка "Поделиться"
- PDF-читалка: скролл по страницам, зум, индикатор "Страница X / Y"
- PDF-читалка: тап на индикатор → диалог перехода к странице
- PDF-читалка: кеш до 5 страниц, рендер по требованию
- Просмотр архивов: навигация внутри ZIP/7z/RAR по папкам
- Просмотр архивов: breadcrumbs виртуального пути
- Извлечение из архива: "Извлечь всё" или выборочно с прогрессом
- Создание ZIP: выделить файлы → кнопка ZIP → ввести имя → архив создан
- Quick Preview: кнопки "Открыть в галерее/читалке/архиве" по типу файла
- Обнаружение защищённых паролем архивов

### 0.11.0 — Batch 11 (Phase 3)
- Плейлист папки: тап на медиафайл → воспроизведение всех audio/video из папки последовательно
- Авто-переход на следующий трек при завершении
- Повтор плейлиста (по умолчанию зациклен)
- Фоновое воспроизведение: аудио продолжает играть при свёрнутом приложении
- Управление на экране блокировки: уведомление MediaSession (play/pause/next/prev)
- Движок VLC (LibVLC): поддержка opus, AVI, DivX, Xvid, WMV и других экзотических кодеков
- Кнопки Next/Prev в плеере при playlist > 1
- Сохранение/восстановление позиции воспроизведения между сессиями
- Превью через PlaybackService: повтор всей папки + экран блокировки из превью
- Кнопка Play/Pause в шапке проводника (видна когда идёт воспроизведение)
- Зависимости: media3-session 1.5.1

### 0.10.0 — Batch 10 (Phase 1+3)
- SAF UI: секция "Хранилища" в избранном (внутренняя память + SAF roots + добавить)
- SAF: бейдж SD-карты в шапке панели при навигации по content:// URI
- SAF: управление корнями (добавление/удаление) через FavoritesPanel
- Медиаплеер: полноэкранный плеер на ExoPlayer (видео + аудио)
- Медиаплеер: seekbar, ±10с, play/pause, обложка альбома, метаданные
- Текстовый редактор: полноэкранный monospace с номерами строк
- Текстовый редактор: undo/redo (50 шагов), сохранение, лимит 1 МБ
- Quick Preview: кнопки "Воспроизвести" (видео/аудио) и "Редактировать" (текст/код)
- Навигация: тап на файл → плеер (audio/video) или редактор (text/code)
- Зависимости: Media3 ExoPlayer 1.5.1

### 0.9.0 — Batch 9 (Phase 3)
- Quick Preview: тап по иконке файла → диалог с превью содержимого
- Поддержка: изображения (EXIF), видео, аудио (ID3), текст/код, PDF, ZIP/7z/RAR, APK, DOC/DOCX/ODT/RTF
- Сетка файлов расширена до 6 колонок (было 4)
- Бейдж расширения на иконке файла (list + grid)
- Визуальное выделение пустых папок (рамка + приглушённый цвет)
- Зависимости: Apache POI, commons-compress, junrar, ExifInterface

### 0.7.0 — Batch 7 (Phase 1+3)
- Кликабельные breadcrumbs: тап на сегмент пути → навигация
- История навигации per-panel: кнопки ← → в шапке (стек до 50 записей)
- Тема: system / light / dark через EcosystemPreferences, переключение из overflow-меню
- "Открыть в другой панели" из overflow-меню
- Фикс: тап по шапке панели с выделением теперь переключает активную панель

### 0.6.0 — Batch 6 (Phase 1)
- Диалог конфликтов при копировании/перемещении/DnD (заменить/переименовать/пропустить)
- ConflictResolution пробрасывается через все слои до FileOperationService
- Поле переименования в grid-режиме растянуто на всю ширину (span all columns)
- Сохранение и восстановление путей панелей между запусками
- Единый формат счётчика файлов во всех статус-сообщениях

### 0.5.0 — Batch 5 (Phase 1)
- Pinch-to-Grid: сетка файлов 1–4 колонки через pinch жест
- Haptic feedback при переключении колонок
- Grid-вариант FileListItem (крупная иконка + имя)
- Сохранение gridColumns в SharedPreferences
- DnD и range selection работают в grid режиме (2D hit-test)
- Foreground Service (dataSync) для крупных файловых операций
- Notification с прогрессом и кнопкой "Отмена"
- Порог: >10 файлов или >50MB → service, иначе inline
- UI прогресс-бар внизу экрана (animated)
- OperationProgress model (current/total/fileName/complete/error)
- Автообновление панелей после завершения service

### 0.4.0 — Batch 4 (Phase 1)
- Drag-and-Drop между панелями (одиночный и групповой)
- Ghost overlay с именем файла и badge количества
- Подсветка целевой панели при перетаскивании
- Haptic feedback при начале и при входе в зону drop
- Корзина (.haron_trash) с 30-дневной автоочисткой
- Просмотр корзины через ModalBottomSheet из overflow меню
- Восстановление файлов из корзины
- Очистка корзины (полная и выборочная)
- Диалог удаления обновлён: "В корзину" вместо "Удалить навсегда"

### 0.3.0 — Batch 3 (Phase 1)
- Счётчик файлов + размер в панели выделения ("3 файла · 12.5 МБ")
- Быстрый поиск по текущей папке (фильтрация в реальном времени)
- Создание файлов с шаблонами (Папка / TXT / MD / Папка с датой)
- Избранное и недавние папки (ModalBottomSheet)
- Групповое выделение свайпом (range selection)
- Инлайн-переименование как в Windows (без диалога)
- Компактные шапки панелей (36dp вместо 64dp)
- Сохранение позиции скролла при операциях и сортировке
- Toggle "выбрать все / снять все"
- Активация панели по клику на файл

### 0.2.0 — Batch 2 (Phase 1)
- Вертикальный двухпанельный интерфейс (верх/низ) с независимой навигацией
- Перетаскиваемый разделитель панелей (drag + double tap → 50/50)
- Режим выделения файлов (long tap, чекбоксы, "выбрать все")
- Копирование и перемещение файлов между панелями
- Удаление файлов с диалогом подтверждения
- Переименование файла через диалог
- Создание новой папки через диалог
- Активная панель подсвечена рамкой
- Selection action bar с 5 операциями

### 0.1.0 — Batch 1 (Phase 1)
- Все разрешения в AndroidManifest (хранилище, Wi-Fi, BT, FG Service, камера)
- Экран запроса разрешений с автопропуском
- Навигация по папкам (тап на папку → внутрь, кнопка назад → вверх)
- Список файлов с иконками по типу
- Сортировка: имя/дата/размер/тип
- Показ/скрытие скрытых файлов
- Breadcrumb-бар с текущим путём
- Нижняя панель — заглушка для Batch 2

---

## Список функций (для пользователя)

> Полный список реализованных функций приложения.
> Обновляется после каждого батча. Потом будет встроен в настройки APK.

### Навигация и интерфейс
- Двухпанельный вертикальный интерфейс (верхняя + нижняя панель)
- Разделитель панелей: перетаскивание + 3 тап-зоны (закладки / 50-50 / TBD)
- Навигация по папкам (тап → внутрь, кнопка назад → вверх)
- Кликабельные breadcrumbs — тап на любой сегмент пути для быстрого перехода
- История навигации в каждой панели (кнопки ← → как в браузере)
- Открыть текущую папку в другой панели одним тапом
- Активная панель подсвечена рамкой

### Файлы и папки
- Список файлов с иконками по типу
- Масштабируемая сетка: от 1 до 6 колонок через pinch-жест
- Бейдж расширения на иконке файла
- Визуальное выделение пустых папок
- Сортировка: по имени, дате, размеру, расширению
- Показ/скрытие скрытых файлов
- Быстрый поиск по текущей папке (фильтрация в реальном времени)
- Создание файлов с шаблонами: папка, TXT, MD, папка с датой

### Выделение и операции
- Выделение файлов долгим тапом + свайп для выбора диапазона
- Выбрать все / снять все
- Счётчик файлов и общий размер выделенного (включая содержимое папок)
- Копирование и перемещение между панелями
- Удаление (в корзину)
- Инлайн-переименование (без диалога, как в Windows)
- Drag-and-Drop между панелями (одиночный и групповой)
- Подсветка целевой панели и haptic-фидбек при перетаскивании
- Диалог конфликтов: заменить / переименовать / пропустить
- Карточка сравнения при конфликте (размер, дата обоих файлов)

### Корзина
- Удалённые файлы хранятся 30 дней
- Просмотр корзины через меню
- Восстановление файлов из корзины
- Очистка корзины (полная и выборочная)
- Автоочистка старых файлов при запуске
- Настраиваемый лимит размера: 0–5000 МБ (0 = без лимита)
- Автоматическое удаление старых файлов при превышении лимита

### Фоновые операции
- Foreground Service для крупных файловых операций (>10 файлов или >50 МБ)
- Уведомление с прогрессом и кнопкой отмены
- Прогресс-бар внизу экрана в приложении

### Боковое меню (Drawer)
- Единая точка входа: гамбургер в шапке панели или свайп от левого нижнего края
- Секция "Хранилища": внутренняя память + SD-карты (подключение/отключение)
- Секция "Избранное": избранные папки с удалением
- Секция "Недавние": последние 10 посещённых папок
- Секция "Инструменты": корзина, анализатор памяти, детектор дубликатов, пустые папки, принудительное удаление, защищённые файлы
- Секция "Оформление": переключение темы кнопками (системная / светлая / тёмная)
- Секция "Настройки": переход в экран настроек
- Tab-индикатор на левом краю экрана (гамбургер)
- Сохранение открытых папок между запусками

### Временная полка (Clipboard Shelf)
- Буфер для файлов при перемещении между далёкими папками
- Кнопка "На полку" в панели действий при выделении файлов
- Свайп от левого верхнего края или tab-индикатор с badge
- "Вставить сюда" — копирование файлов с полки в текущую папку
- "Переместить сюда" — перемещение файлов с полки (полка очищается)
- Удаление отдельных элементов с полки
- Очистка полки целиком
- Сохранение полки между запусками приложения

### Хранилища (SAF)
- Поддержка SD-карты и внешних хранилищ через Storage Access Framework
- Секция "Хранилища" в панели избранного
- Добавление/удаление SAF-корней
- Бейдж SD-карты в шапке панели
- Все файловые операции работают с content:// URI

### Быстрый просмотр
- Тап по иконке файла → диалог с превью
- Изображения: миниатюра с учётом EXIF-ориентации
- Видео: кадр + длительность, воспроизведение прямо в превью
- Аудио: обложка альбома + ID3 теги, воспроизведение прямо в превью
- Текст/код: первые 50 строк
- PDF: первая страница
- Архивы (ZIP/7z/RAR): список файлов
- APK: иконка + версия
- Документы (DOC/DOCX/ODT/RTF): текст
- Повтор всей папки из превью (автопереход на следующий медиафайл)
- Экран блокировки из превью (уведомление MediaSession)
- Кнопка "Открыть в галерее" для изображений
- Кнопка "Открыть в читалке" для PDF и документов (DOC/DOCX/ODT/RTF)
- Кнопка "Открыть архив" для ZIP/7z/RAR

### Медиаплеер
- Встроенный плеер на движке VLC (LibVLC)
- Поддержка всех популярных кодеков: MP3, M4A, FLAC, OGG, Opus, WAV, AVI, MKV, MP4, WMV, DivX, Xvid и др.
- Плейлист папки: тап на медиафайл → воспроизведение всех аудио и видео из папки
- Автоматический переход на следующий трек
- Повтор плейлиста (зациклен по умолчанию)
- Фоновое воспроизведение: аудио играет при свёрнутом приложении
- Управление на экране блокировки: play/pause/next/prev через уведомление
- Кнопки Next/Prev при нескольких треках
- Seekbar + перемотка ±10 секунд тапом по экрану
- Сохранение позиции воспроизведения между сессиями
- Видео: полноэкранный режим, immersive mode
- Аудио: обложка альбома, название, исполнитель
- Кнопка Play/Pause в шапке проводника (видна при активном воспроизведении)

### Внутренняя галерея
- Полноэкранный просмотр изображений прямо в приложении
- Свайп между всеми картинками текущей папки
- Зум: щипком (1x–5x) и двойным тапом (1x ↔ 2x)
- Панорамирование при зуме
- Счётчик "3 / 25" + размер файла
- Автоскрытие контролов через 3 сек, показ по тапу
- Кнопка "Поделиться" (отправка через другие приложения)
- Предзагрузка соседних изображений
- Поддержка SD-карты (content:// URI)

### PDF-читалка
- Полноэкранная читалка PDF прямо в приложении
- Скролл по всем страницам документа
- Зум: щипком (1x–5x) и двойным тапом (1x ↔ 2x)
- Индикатор "Страница X / Y" внизу
- Тап на индикатор → быстрый переход к любой странице
- Кеш страниц, рендер по требованию
- Поддержка SD-карты (content:// URI)
- Подсветка найденных слов при открытии из поиска по содержимому (жёлтый цвет)
- Текущее совпадение выделяется оранжевым
- Навигация между совпадениями стрелками ↑↓ в тулбаре
- Счётчик совпадений «1/N» в тулбаре
- Автоскролл к первому совпадению при открытии

### Читалка документов
- Чтение DOC, DOCX, ODT, RTF, FB2 файлов прямо в приложении
- Весь текст документа отображается с прокруткой
- Зум: щипком (1x–5x) и двойным тапом (1x ↔ 2x)
- Индикатор "Строк: N" внизу экрана
- Тап на файл из проводника → встроенная читалка
- Кнопка "Открыть в читалке" из быстрого просмотра
- Поддержка SD-карты (content:// URI)
- Подсветка найденных слов при открытии из поиска по содержимому
- Навигация между совпадениями стрелками ↑↓ + счётчик «1/N»
- Автоскролл к текущему совпадению

### Работа с архивами
- Просмотр содержимого ZIP, 7z, RAR архивов
- Навигация по папкам внутри архива
- Breadcrumbs виртуального пути
- Режим выделения для выборочного извлечения
- "Извлечь всё" — все файлы в Downloads
- "Извлечь выделенные" — только выбранные файлы
- Прогресс извлечения в реальном времени
- Создание ZIP из выделенных файлов (кнопка ZIP в панели действий)
- Диалог ввода имени архива
- Обнаружение архивов защищённых паролем (с сообщением)

### Текстовый редактор
- Редактирование TXT и MD файлов прямо в приложении
- Моноширинный шрифт с номерами строк
- Undo/Redo (до 50 шагов)
- Сохранение файла (включая SAF content:// URI)
- Диалог "Сохранить / Не сохранять / Остаться" при выходе
- Лимит 1 МБ с предупреждением
- Индикатор изменений и позиция курсора в статус-баре

### Свойства файла
- Диалог свойств: имя, путь, размер, дата изменения, MIME-тип, разрешения
- Для папок: рекурсивный подсчёт размера и количества файлов
- EXIF-данные для фотографий: камера, дата, размеры, ISO, выдержка, GPS
- Удаление всех EXIF-метаданных из фото одной кнопкой
- Хеш-суммы MD5 и SHA-256 по кнопке "Вычислить хеш"
- Прогресс вычисления для больших файлов
- Копирование хеша в буфер обмена
- Кнопка "i" (Свойства) в панели действий при выделении одного файла

### Открыть во внешнем приложении
- Кнопка "Открыть в..." в панели действий при выделении одного файла (не папки)
- Файлы без встроенного обработчика (таблицы, презентации и др.) автоматически открываются во внешнем приложении
- Системный выбор приложения через "Открыть в..."
- Работает с любыми файлами на внутренней памяти и SD-карте

### Анализатор памяти
- Круговая диаграмма по типам файлов (Фото, Видео, Музыка, Документы, Архивы, APK, Прочее)
- Информация: "Занято X ГБ из Y ГБ, свободно Z ГБ"
- Категории с иконками, размером, полоской заполненности и счётчиком файлов
- Тап на категорию → список крупных файлов этой категории
- Секция "Крупные файлы (>10 МБ)" — топ-50 по размеру
- Выделение файлов чекбоксами + кнопка "Удалить выбранные"
- Прогресс сканирования: индикатор + счётчик файлов + текущая папка
- Повторное сканирование кнопкой "Обновить"
- Доступ из меню панели → "Анализ памяти"

### Детектор дубликатов
- Поиск файлов с одинаковым содержимым по всему хранилищу
- Двухфазный алгоритм: группировка по размеру → хеширование MD5
- Прогресс сканирования: фаза, счётчик, текущая папка
- Сводка: количество групп, файлов, потраченное место
- Раскрываемые группы с чекбоксами на каждом файле
- Бейдж "Оригинал" у старейшего файла в каждой группе
- Маркер "Оригинал" сохраняется между сессиями и пересканированиями
- Долгий тап по правой части строки файла → назначить оригинал
- Долгий тап по левой/центральной части → открыть файл во внешнем приложении (превью)
- Режим "Папки-оригиналы" (три точки → Папки-оригиналы): пометка папок как источников оригиналов
- Трёхуровневый приоритет: индивидуальный маркер > файл из помеченной папки > старейший файл
- Помеченные папки сохраняются между запусками
- "Оставить старейшие" — автовыделение всех копий кроме оригинала (по одной группе или по всем)
- Удаление выбранных дубликатов с автоматическим пересканированием
- Доступ из бокового меню → "Детектор дубликатов"

### Настройки
- Экран настроек: доступ из бокового меню
- Ночной режим по расписанию: независимо от системной темы
- Время начала и конца ночного режима (например 22:00 – 7:00)
- Поддержка перехода через полночь
- Размер шрифта: слайдер 80%–140% с превью текста
- Размер иконок: слайдер 80%–140%
- Включение/отключение хаптик-вибрации
- Секция "Безопасность": выбор метода блокировки, установка/смена PIN
- Лимит корзины: слайдер 0–5000 МБ (0 = без лимита)

### Хаптик-язык (вибрация)
- Осмысленные паттерны вибрации для разных событий
- Успех: короткий двойной импульс (копирование, перемещение, переименование)
- Предупреждение: тройной пульс (подтверждение удаления)
- Ошибка: длинная тяжёлая вибрация (сбой операции)
- Завершение: восходящий паттерн (конец фоновой операции)
- Одиночный тик: для мелких действий (сохранение закладки)
- Отключается в настройках

### Инструменты
- Поиск пустых папок: диалог со списком, чекбоксы, toggle "Вложенные"
- Удаление пустых папок в корзину (не навсегда)
- Размер папки при выделении: автоматический подсчёт с индикатором загрузки
- Кеш размеров папок (повторное выделение — мгновенно)
- Принудительное удаление: удаление файлов/папок навсегда (без корзины)
- Принудительное удаление: для "матрёшечных" папок с бесконечной вложенностью
- Пофайловый прогресс: все операции показывают текущий обрабатываемый файл
- Доступ к инструментам из бокового меню

### Метки (теги)
- Создание цветных меток с текстовым названием (8 цветов на выбор)
- Назначение одной или нескольких меток на файлы и папки
- Цветные точки рядом с именем файла в списке и в сетке
- Фильтрация файлов по метке через меню сортировки
- Управление метками: создание, редактирование, удаление из бокового меню → «Метки»
- При переименовании, перемещении, копировании — метки сохраняются
- При удалении файла — метки автоматически очищаются
- При удалении метки — она снимается со всех файлов

### Виджет на рабочий стол
- Виджет "Haron Избранное": список избранных папок прямо на рабочем столе
- Тап на папку в виджете → приложение открывается в этой папке
- Автообновление при изменении избранного

### Закладки быстрого доступа (1-6)
- Тап на левую зону разделителя панелей → пай-меню (полукруг из 6 плашек вправо)
- Пустой слот: тусклое "—"
- Сохранённый слот: имя папки на полупрозрачной плашке
- Короткий тап на пустой слот → сохранить текущую папку
- Короткий тап на сохранённый слот → мгновенная навигация
- Долгий тап на любой слот → сохранить/перезаписать текущую папку
- Тап за пределами меню закрывает его и проходит к элементу под ним (один тап)
- Закладки сохраняются между запусками

### Пай-меню инструментов
- Тап на правую зону разделителя панелей → пай-меню (полукруг из 6 плашек влево)
- 6 инструментов: Корзина, Анализатор, Дупликатор, APK, Плеер, Читалка
- Плеер: открывает последний воспроизведённый медиафайл (или toast "Нет недавних")
- Читалка: открывает последний PDF/документ (или toast "Нет недавних")
- Тап за пределами меню закрывает его и проходит к элементу под ним

### Разделитель панелей
- Перетаскивание для изменения соотношения панелей
- 3 тап-зоны: левая (закладки), центральная (сброс 50/50), правая (инструменты)

### Блокировка приложения
- PIN-код (4–8 цифр) на вход в приложение
- Точки PIN отображаются по реальной длине (6-значный PIN → 6 точек)
- Автоввод: набрал нужное количество цифр → проверка сразу, без кнопки OK
- Неверный PIN → точки трясутся + вибрация + автосброс
- Биометрия (отпечаток пальца) на вход в приложение
- Комбинированный режим: биометрия + PIN как запасной (биометрия запускается автоматически)
- Автоблокировка при сворачивании (с grace period 3 сек, чтобы поворот экрана не блокировал)
- Защита от скриншотов при заблокированном экране
- Установка и смена PIN через настройки
- Предупреждение если биометрия недоступна на устройстве

### Защищённая папка
- Шифрование файлов AES-256-GCM (ключ в Android Keystore — невозможно извлечь, потоковое без загрузки в память)
- Защищённые файлы полностью невидимы: ни в проводнике, ни в галерее, ни в других приложениях
- Кнопка "Щит" в шапке панели — включает режим видимости защищённых файлов (после ввода PIN/биометрии)
- Биометрия работает при аутентификации Shield (кнопка отпечатка)
- Защищённые файлы отображаются на своих исходных местах с иконкой замка
- Тап на защищённый файл — расшифровка и открытие во встроенном просмотрщике (плеер, галерея, редактор, PDF-читалка, архиватор)
- Защита выделенных файлов: выделить → кнопка "Щит" в панели действий
- Снятие защиты: выделить защищённые файлы → кнопка "Снять щит"
- Рекурсивная защита папок: все файлы внутри шифруются, сама папка тоже удаляется
- Снятие защиты папки: папка пересоздаётся на исходном месте
- Долгий тап на щит → "Все защищённые файлы" в одной панели (виртуальная директория)
- Тап на щит (подсвечен) → выход из режима защиты
- Виртуальный режим: копирование, перемещение, удаление, превью, открытие — работает как обычная панель
- Виртуальный режим: копирование расшифровывает файлы в целевую папку (оригиналы остаются защищёнными)
- Виртуальный режим: перемещение расшифровывает файлы в целевую папку и убирает защиту
- Виртуальный режим: удаление безвозвратно удаляет из защищённого хранилища
- Виртуальный режим: создание папок и текстовых файлов в защищённых директориях
- Пункт "Защищённые файлы" в боковом меню с количеством и размером
- Выключение режима видимости — защищённые файлы снова невидимы

### Глобальный поиск
- Долгий тап на кнопку поиска в панели → экран глобального поиска
- Поиск по имени файла по всему устройству (debounce 300 мс)
- Фильтры по категории: Все / Документы / Фото / Аудио / Видео / Архивы
- Фильтры по размеру: < 1 МБ, 1-10, 10-100, 100 МБ-1 ГБ, > 1 ГБ
- Фильтры по дате: Сегодня / 7 дней / 30 дней / Год / Старее
- Пагинация результатов (по 200, подгрузка при прокрутке)
- Результат: иконка по типу + имя + путь + размер + дата
- Тап на результат → открытие папки, где лежит файл
- Кнопка "Переиндексировать" в toolbar
- Статус индекса: количество файлов + когда последний раз
- Прогресс-бар при индексации
- Автоиндексация при включении экрана (throttle 30 мин)
- Авто-синхронизация по ContentObserver (debounce 5 сек)
- Room DB + FTS4 для быстрого поиска
- Поиск по содержимому файлов: текстовые файлы, DOCX, ODT, DOC, RTF, PDF
- Поиск по метаданным аудио (исполнитель, альбом, название трека)
- Поиск по метаданным видео (название)
- Переключение режима: по имени / по содержимому (кнопки в поле ввода)
- Раскрываемый сниппет (3 строки) с подсветкой найденного слова
- Подсказка про время индексации при включении поиска по содержимому
- Полнотекстовый поиск: весь текст документов индексируется в отдельную FTS-таблицу (не только 500 символов)
- Три кнопки индексации в toolbar:
  - ↻ Базовая — индексирует текст документов + EXIF метаданные фото (камера, дата, GPS)
  - ♪ Медиа — индексирует метаданные аудио/видео (исполнитель, альбом, жанр, год, композитор)
  - 📷 Визуальная — ML Kit Image Labeling: распознаёт объекты на фото (собака, машина, пляж и т.д.)
- Поиск по результатам ML Kit: на русском и английском ("собака" и "dog" найдут одно фото)
- Каждая кнопка индексации показывает свой спиннер и текст прогресса

### Локальный поиск (в панели)
- Тап на кнопку поиска (лупа) → строка поиска в тулбаре панели
- По умолчанию — поиск по имени файла (кнопка Тт активна)
- Кнопка FindInPage (📄) — переключение на поиск по содержимому в текущей папке
- Поиск по содержимому использует ту же проиндексированную базу (file_content)
- При поиске по содержимому папки с подходящим именем остаются видны
- Результаты обновляются с debounce 300 мс
- Поиск автоматически сбрасывается при переходе в другую папку

### Оформление
- Тема: системная / светлая / тёмная
- Переключение темы из бокового меню (3 кнопки)
- Material Design 3

---

## Заметки

> Свободная секция для идей, вопросов, решений которые ещё не оформились.

- **TODO: Проверка SD-карты** — перемещение между разными хранилищами (внутренняя память ↔ SD-карта) использует fallback через copy+delete. Нужно протестировать на устройстве с SD-картой.
