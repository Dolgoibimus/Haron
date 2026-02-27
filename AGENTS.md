# AGENTS.md — Проводник

Живой файл. Обновляется в процессе разработки.
Выполненные задачи → из TODO в Журнал решений.
Новые фичи → в Changelog.

---

## Статус проекта

**Текущая версия:** 0.41 (Phase 4, Batch 41)
**Текущая фаза:** Phase 4 — продвинутые функции (v2.0 features)

---

## В работе (текущая задача)

> Сюда записывается задача которая выполняется прямо сейчас.
> При /compact — сохранить прогресс здесь перед сжатием.

Batch 41 — Доверенные устройства + ренейм + условный Quick Send. Реализовано.

**Решено:** Долгий тап на пульсирующем кругу приёма — исправлен (см. Batch 41 bugfix).

### План до релиза 1.0:
| Батч | Задача | Статус |
|------|--------|--------|
| 30 | Поиск в архивах + OCR + USB OTG | ✅ проверено |
| 31 | Сетевое обнаружение (SMB-браузер — не реализован) | ⚠️ не проверено |
| 32 | Встроенный терминал (простой) | ✅ проверено |
| 33 | Система жестов | ⚠️ не проверено |
| 34 | Голосовые команды | ⚠️ не проверено |
| — | Проверка всех батчей | ожидает |
| — | Полировка UI/UX | ожидает |
| — | **Релиз 1.0** | — |

### Фичи v2.0:
| Батч | Задача | Статус |
|------|--------|--------|
| 35 | Открывалка по умолчанию | ✅ проверено |
| 36 | Сравнение файлов и папок | ✅ проверено |
| 37 | Полный терминал (VT100/ANSI) | ✅ проверено |
| 38 | Стеганография | ⚠️ не проверено |
| 39 | Расширенный шаринг на ТВ | ⚠️ не проверено |
| 40 | Quick Send DnD (быстрая отправка) | ⚠️ не проверено |
| 41 | Доверенные устройства + ренейм + условный Quick Send | ⚠️ не проверено |

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
- [x] Поиск внутри архивов (ZIP/7Z/RAR — имена файлов + мелкий текстовый контент) ← Batch 30
- [x] OCR-поиск по фотографиям (ML Kit Text Recognition офлайн, timeout 10с/файл) ← Batch 30
- [x] Передача файлов — умный выбор протокола: Wi-Fi Direct / HTTP / Bluetooth ← Batch 24-26, 29
  - Wi-Fi Direct P2P: Проводник на обоих устройствах, без роутера
  - HTTP (Ktor CIO): встроенный веб-сервер, QR-код с URL (ZXing), HTML-страница для любого браузера
  - Bluetooth RFCOMM: медленно, но работает без Wi-Fi (отправка + приём)
  - Автоматический выбор лучшего протокола
  - Приём файлов: TCP ServerSocket + NSD регистрация + BT RFCOMM ServerSocket ← Batch 29
  - Resume при обрыве: offset в FILE_HEADER, дозапись через RandomAccessFile ← Batch 29
- [x] USB OTG: автообнаружение + toast, отображение в боковом меню с free/total, навигация, безопасное извлечение (sync+unmount), автовозврат на root при отключении ← Batch 30
- [x] Обработка обрывов сетевых соединений: battery saver warning, WakeLock, retry с exponential backoff ← Batch 25
- [x] Cast на ТВ: Google Cast (Chromecast) + Miracast (зеркалирование экрана) ← Batch 27
- [x] Телефон как пульт: play/pause/seek/volume/next/prev при Cast-подключении ← Batch 28

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

### Phase 4 — Продвинутые функции

#### Релиз 1.0 (текущий):
- [x] Счётчик на иконке приложения: badge через notification .setNumber(), activeOperations counter ← Batch 31
- [x] Сетевое обнаружение: NSD (_haron._tcp. + _smb._tcp.) + subnet scan порт 445, секция "Сеть" в drawer ← Batch 31
- [x] Встроенный терминал (простой): ProcessBuilder, ls/cp/mv/cat/grep, скролл, история команд ← Batch 32
- [x] Система жестов: настраиваемые action-коды, экран настроек, назначение жестов ← Batch 33
- [x] Система голосовых команд: те же action-коды, Android Speech Recognition офлайн ← Batch 34
- [ ] Полировка UI/UX, анимации, Physics-based Drag ← **после проверки всех батчей**

#### Следующий релиз (v2.0):
- [x] Открывалка по умолчанию (регистрация как обработчик типов файлов) ← Batch 35
- [x] Сравнение файлов и папок (java-diff-utils, две панели, синхронный скролл) ← Batch 36
- [x] Встроенный терминал (полный): VT100/ANSI, цветной вывод, автодополнение, кликабельные пути ← Batch 37
- [x] Стеганография (Tail-Append метод, AES-256-GCM) ← Batch 38
- [x] Расширенный шаринг на ТВ: слайд-шоу, PDF-презентация, инфо о файле, зеркалирование экрана ← Batch 39
- [ ] Интеграция с облаком (Google Drive, Dropbox, OneDrive как папки)
- [ ] Машина времени (версии текстовых файлов)
- [ ] Переименование по содержимому (ML Kit предлагает имя для фото)
- [ ] Мониторинг мусора в реальном времени (WorkManager, уведомление)
- [ ] Монтажный стол (FFmpeg: обрезка, склейка видео, таймлайн)
- [ ] Чистильщик мессенджеров (Telegram/WhatsApp кэш и медиа)
- [ ] Root-режим (опциональный): chmod, /data, системные папки
- [ ] Система подсказок (мигающие рамки, onboarding)
- [ ] Телефон как пульт (тачпад + клавиатура)

#### Уже сделано (Phase 4):
- [x] Пакетное переименование ← Batch 19
- [x] Боковое меню (drawer) ← Batch 14
- [x] Установка APK ← Batch 16
- [x] Менеджер приложений ← Batch 16
- [x] Хаптик-язык ← Batch 17
- [x] Пустые папки ← Batch 17
- [x] Размер папки в реальном времени ← Batch 17
- [x] ~~Лог операций~~ (удалён) ← Batch 17
- [x] Быстрый переход ← Batch 7
- [x] История навигации ← Batch 7
- [x] Открыть во внешнем приложении ← Batch 13
- [x] Хеш файла ← Batch 13
- [x] Ночной режим по расписанию ← Batch 17
- [x] Размер шрифта и иконок ← Batch 17
- [x] Виджет на рабочий стол ← Batch 17
- [x] Закладки ← Batch 17

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

### UI-фиксы — Терминал + Текстовый редактор ⚠️ не проверено

**Проверить при первом запуске:**

1. **❌ НЕ РЕШЕНО — Терминал — капитализация после пробела:**
   - Пробовали: `KeyboardCapitalization.None`, `KeyboardType.Uri`, `KeyboardType.Password`, замена `TextField` на `BasicTextField` — клавиатура всё равно ставит заглавную после пробела
   - Текущее состояние: `BasicTextField` + `KeyboardType.Password` + `KeyboardCapitalization.None` + `autoCorrectEnabled = false`
   - Документация Android: `KeyboardCapitalization` — это hint, клавиатура может игнорировать
   - Файл: `TerminalScreen.kt`
   - **TODO:** возможно единственный надёжный вариант — перехват в `onValueChange` или кастомный InputConnection

2. **❌ НЕ РЕШЕНО — Текстовый редактор — полоса между текстом и клавиатурой:**
   - Открой текстовый файл → открой клавиатуру → между строкой "Сохранён" и клавиатурой серая полоса
   - Пробовали: `contentWindowInsets = WindowInsets.ime`, `contentWindowInsets = WindowInsets(0)` + `consumeWindowInsets` + `imePadding` + `navigationBarsPadding`, дефолтные `contentWindowInsets` + `consumeWindowInsets` + `imePadding` — ни один вариант не убрал полосу
   - Текущее состояние: дефолтные contentWindowInsets + `consumeWindowInsets(paddingValues)` + `imePadding()` на Column, статус-бар внутри Column (не в bottomBar)
   - Файл: `TextEditorScreen.kt`
   - **TODO:** разобраться откуда берётся полоса — возможно Scaffold background, навбар, или padding от дефолтных insets. Нужен скриншот для точной диагностики

**Дополнительные UI-фиксы (сделано ранее в этой сессии):**
- Убран пункт "Защищённые файлы" из бокового меню (есть кнопка щит)
- PDF превью: перелистывание страниц через HorizontalPager
- Текстовое превью: перенос строк + номера строк
- Текстовый редактор: перенос строк + номера строк с отступом 2dp
- Меню сортировки: расстояние между пунктами уменьшено в 2 раза
- Терминал: клавиатура не сворачивается после выполнения команды (FocusRequester)
- Терминал: строка ввода привязана к верху клавиатуры (contentWindowInsets = WindowInsets.ime)

### Batch 41 — Доверенные устройства + ренейм + условный Quick Send ⚠️ не проверено

**Доверенные устройства (звёздочка):**
- **HaronPreferences** (`data/datastore/`): `getDeviceAlias(nsdName)` / `setDeviceAlias(nsdName, alias)` — алиасы устройств (JSON Map в SharedPreferences, ключ `device_aliases`). `getTrustedDevices()` / `setDeviceTrusted(nsdName, trusted)` / `isDeviceTrusted(nsdName)` — доверенные устройства (StringSet, ключ `trusted_devices`).
- **DiscoveredDevice** (`domain/model/`): +`alias: String? = null`, +`isTrusted: Boolean = false`, +`displayName: String get() = alias ?: name`.
- **NetworkDevice** (`data/network/`): +`alias: String? = null`, +`isTrusted: Boolean = false`, +`displayName: String get() = alias ?: name`.

**Ренейм устройств + звёздочка в UI:**
- **TransferViewModel**: обогащение discovered devices из preferences (alias + isTrusted). `toggleDeviceTrust(device)`, `renameDevice(device, newAlias)`.
- **DeviceList** (`presentation/transfer/components/`): звёздочка справа (`Star` / `StarBorder`), тап → toggle trust. Показ `device.displayName`. Долгий тап → диалог ренейма (TextField + OK/Отмена).
- **TransferScreen**: прокинуты callback'и `onToggleTrust`, `onRenameDevice`.
- **DrawerMenu**: показ `displayName` для Haron-устройств.
- **QuickSendOverlay**: показ `displayName` в кружках.

**Условный Quick Send (доверие):**
- **TransferProtocolNegotiator**: `buildQuickSend(files, senderName)` — добавлено поле `"sender"` в JSON. `parseQuickSendSender(json)` — парсинг имени отправителя.
- **ReceiveFileManager**: проверка `preferences.isDeviceTrusted(senderName)` при получении TYPE_QUICK_SEND. Доверенный → `handleQuickSend()` (авто-приём). Не доверенный → `IncomingTransferRequest` (диалог подтверждения). `_friendReceived: SharedFlow<String>` — эмитит display name при получении от доверенного устройства.
- **TransferRepository** (`domain/repository/`): +`val friendReceived: SharedFlow<String>`.
- **TransferRepositoryImpl**: проксирует `friendReceived` из ReceiveFileManager.
- **GlobalReceiveViewModel** (`presentation/transfer/`, НОВЫЙ файл): глобальная ViewModel в MainActivity для входящих запросов от недоверенных устройств. Коллектит `incomingRequests`, показывает accept/decline. `accept()` → `acceptTransfer()` + foreground service. `decline()` → `declineTransfer()`.
- **ReceiveNotificationViewModel**: переработан — использует `friendReceived` (SharedFlow<String>) вместо `receiveCompleted` (SharedFlow<Int>). Хранит имя отправителя (`senderName: StateFlow<String?>`).

**WiFi-привязка сокетов:**
- **TransferRepositoryImpl**: `findWifiNetwork()` через ConnectivityManager + TRANSPORT_WIFI. `connectWithPortScan(address, port)` — сканирование портов 8080-8090 + `Network.bindSocket(sock)` для маршрутизации через WiFi (не через мобильные данные).
- **ExplorerViewModel**: аналогичные `findWifiNetwork()` + `bindSocket()` в `connectWithPortScan()`.

**Обновление списка файлов после приёма:**
- **ReceiveFileManager.acceptTransfer()**: +`_quickReceiveCompleted.tryEmit(saveDir.absolutePath)` (ранее эмитил только `_receiveCompleted`). +`MediaScannerConnection.scanFile()` для принятых файлов.
- **ReceiveFileManager.handleQuickSend()**: +`MediaScannerConnection.scanFile()` для принятых файлов.

**UI — пульсирующий круг приёма (только для друзей):**
- **MainActivity**: `ReceiveCompleteOverlay` — принимает `senderName`, показывает имя отправителя + иконку. `RoundedCornerShape(32.dp)` вместо `CircleShape` для растягивания при длинном имени. Анимация пульсации (alpha 0.3-0.6). Overlay рендерится последним в Box (поверх экрана блокировки).
- **MainActivity**: `IncomingTransferDialog` — AlertDialog для accept/decline от недоверенных устройств. Поверх всего.

**Строки:** `transfer_receive` в обоих strings.xml.

### Batch 41 bugfix — Долгий тап на кругу приёма ✅ проверено

**Проблема:** `detectTapGestures(onLongPress)` и `combinedClickable(onLongClick)` на Column внутри overlay Box не работали — касания перехватывались нижележащими composable (ExplorerScreen gesture handlers). Добавление `clickable` на родительский Box тоже не помогало — оно перехватывало ВСЕ события, до дочернего Column ничего не доходило.

**Решение (3 проблемы):**
1. **Жест:** `pointerInput` перенесён на Box (fillMaxSize) уровень. `awaitEachGesture` + ручной `withTimeoutOrNull(longPressMs)` — не зависит от `isConsumed`. `onGloballyPositioned` на Column отслеживает координаты круга, `circleRect.contains(down.position)` определяет hit. Вне круга → dismiss, в круге + tap → dismiss, в круге + hold → navigate.
2. **Краш:** после вызова `onNavigateToFolder()` overlay удалялся из composition, но `pointerInput` корутина пыталась продолжить цикл `awaitPointerEvent()` → `CancellationException` → процесс падал. Убран цикл ожидания подъёма пальца после long press.
3. **Навигация:** `TransferHolder.pendingNavigationPath` был простой `var`, читался в `LaunchedEffect(Unit)` один раз при старте ExplorerScreen. Заменён на `MutableStateFlow<String?>`, ExplorerScreen собирает через `collectAsState` + реактивный `LaunchedEffect(transferPath)`.

**Файлы:** `MainActivity.kt` (ReceiveCompleteOverlay), `TransferHolder.kt`, `ExplorerScreen.kt`.

### Batch 40 — Quick Send DnD ⚠️ не проверено
- **TransferProtocolNegotiator**: новый тип `TYPE_DROP_REQUEST` + `buildDropRequest(senderName, senderPort)` + `parseDropRequest()` → `DropRequestData`.
- **ReceiveFileManager**: обработка DROP_REQUEST в listen loop, `DropRequestInfo` data class, `dropRequests: SharedFlow<DropRequestInfo>`.
- **QuickSendState** (`state/QuickSendState.kt`): sealed interface — Idle, DraggingToDevice(filePath, fileName, dragOffset, haronDevices), DropTarget(deviceName, deviceAddress, devicePort), Sending(deviceName).
- **ExplorerUiState**: +`quickSendState: QuickSendState`.
- **ExplorerViewModel**: зависимости +TransferRepository +ReceiveFileManager. Методы: `startQuickSend`, `updateQuickSendDrag`, `endQuickSendAtPosition` (вычисление ближайшего кружка по arc-геометрии), `cancelQuickSend`, `requestDropTarget` (TCP → DROP_REQUEST), `dismissDropTarget`, `handleDropOnTarget`, `performQuickSendToDropTarget`, `performQuickSend` (sendFiles через TransferRepository). Подписка на dropRequests → DropTarget state.
- **QuickSendOverlay** (`components/QuickSendOverlay.kt`): 3 режима отображения. DraggingToDevice: кружки устройств полукругом (arc 210°-330°) над пальцем с подсветкой ближайшего (<60dp), превью файла под пальцем. DropTarget: пульсирующий кружок в правом нижнем углу с именем устройства. Sending: мигающий кружок с прогрессом.
- **FilePanel**: параметры `onQuickSendStart/Drag/End`. Разделение зон в `onDragStart`: правая половина + файл (не папка) + не выделен → Quick Send; выделенный → cross-panel DnD; остальное → range selection.
- **DrawerMenu**: параметр `onRequestDropTarget`. `DrawerItemLongClickable` с `combinedClickable` для Haron-устройств — long click → запрос DROP_REQUEST.
- **ExplorerScreen**: QuickSendOverlay рендеринг, передача quickSend параметров в FilePanel, `onRequestDropTarget` в DrawerMenu, `handleDropOnTarget` при cross-panel drag на DropTarget.
- **Строки**: 6 новых строк (quick_send_no_devices, _sending, _done, _failed, _request_sent, _drop_here) в обоих strings.xml.

### Batch 39 — Расширенный шаринг на ТВ ⚠️ не проверено
- **CastMode enum** (`domain/model/CastMode.kt`): 5 режимов — SINGLE_MEDIA, SLIDESHOW, PDF_PRESENTATION, FILE_INFO, SCREEN_MIRROR. `SlideshowConfig(intervalSec, loop, shuffle)`, `PresentationState(currentPage, totalPages, pdfPath)`.
- **CastModeSheet** (`presentation/cast/components/CastModeSheet.kt`): ModalBottomSheet с иконками и описаниями для каждого режима. Extension-функции `CastMode.icon()`, `titleRes()`, `subtitleRes()`.
- **SlideshowConfigDialog** (`presentation/cast/components/SlideshowConfigDialog.kt`): AlertDialog со слайдером интервала (2-30 сек), чекбоксами «Зациклить» и «Перемешать».
- **PdfPresentationController** (`presentation/cast/components/PdfPresentationController.kt`): Card с кнопками prev/next, счётчик страниц, LinearProgressIndicator, имя устройства.
- **ScreenMirrorService** (`service/ScreenMirrorService.kt`): Foreground Service с MediaProjection + VirtualDisplay + ImageReader. Встроенный Ktor HTTP-сервер на портах 8090-8095: `/mirror` (HTML с auto-refresh img), `/frame` (JPEG снимок 50% quality, 200мс интервал). Разрешение 720×1280, плотность уменьшена вдвое.
- **HttpFileServer расширен**: 4 новых endpoint — `/slideshow` (HTML+JS авто-листание), `/slideshow/image/{index}`, `/presentation/{page}` (рендер PDF через PdfRenderer → PNG 2x), `/fileinfo` (HTML-карточка). Методы: `setupSlideshow()`, `setupPdf()`, `setupFileInfo()`, `renderPdfPage()`.
- **CastViewModel расширен**: `castSlideshow()`, `castPdfPresentation()`, `castFileInfo()`, `presentationPrevPage/NextPage()`, `setCastMode()`. StateFlow для castMode и presentationState.
- **CastOverlay**: адаптивный UI по `castMode` — PDF → PdfPresentationController, SLIDESHOW/FILE_INFO/SCREEN_MIRROR → минимальная панель без seekbar, SINGLE_MEDIA → полная панель MediaRemotePanel.
- **ExplorerViewModel**: `castSelected()` — определение доступных режимов по типам файлов (images → SLIDESHOW, pdf → PDF_PRESENTATION, media → SINGLE_MEDIA, всегда FILE_INFO + SCREEN_MIRROR). DialogState.CastModeSelect.
- **SelectionActionBar**: кнопка Cast (Icons.Filled.Cast).
- **ExplorerScreen + HaronNavigation**: `CastActionHolder` static object для передачи mode/paths между non-composable и composable контекстом. `handleCastModeSelected()` — SCREEN_MIRROR через MediaProjection intent, остальные через LaunchedEffect + hiltViewModel.
- **MainActivity**: `mediaProjectionLauncher` (ActivityResultLauncher) для MediaProjection consent → запуск ScreenMirrorService.
- **AndroidManifest**: permission `FOREGROUND_SERVICE_MEDIA_PROJECTION`, service ScreenMirrorService с `foregroundServiceType="mediaProjection"`.

### Batch 38 — Стеганография ⚠️ не проверено
- **SteganographyModels** (`domain/model/`): `StegoHeader`, `StegoResult` (sealed: Hidden, Extracted, Error), `StegoDetectResult`, `StegoProgress`, `StegoPhase` (COPYING_CARRIER, ENCRYPTING, APPENDING, DONE).
- **StegoHolder** (`domain/model/StegoHolder.kt`): static holder для передачи carrierPath/payloadPath между экранами.
- **SteganographyRepository** (`domain/repository/`): интерфейс с `hidePayload()` (Flow), `hidePayloadComplete()`, `detectHiddenData()`, `extractPayload()`.
- **SteganographyRepositoryImpl** (`data/repository/`): Tail-Append метод. Формат: `[carrier][HRNSTEG magic(7)+version(1)+nameLen(2)+name(N)+payloadSize(8)+IV(12)][encrypted data][headerOffset(8)][HRNSTEG! footer(8)]`. AES-256-GCM через Android Keystore (alias `haron_stego_key`). Потоковое шифрование через CipherOutputStream (без OOM). Детекция: чтение footer → offset → header → валидация magic.
- **HideInFileUseCase**, **ExtractHiddenUseCase** — делегируют к repository.
- **SteganographyViewModel**: два режима (Hide/Extract). Выбор носителя и payload, прогресс, детекция скрытых данных. NavigationEvent.OpenSteganography.
- **SteganographyScreen**: карточки выбора файлов (носитель + payload/источник), кнопки «Скрыть» / «Извлечь», прогресс-бар по фазам, автодетекция при выборе файла с footer.
- **SelectionActionBar**: кнопка «Скрыть в файле» (Icons.Filled.VisibilityOff).
- **ExplorerViewModel**: `openSteganography()` — заполняет StegoHolder и эмитит NavigationEvent.
- **HaronNavigation**: маршрут STEGANOGRAPHY.
- **di/RepositoryModule**: bind SteganographyRepository → SteganographyRepositoryImpl.

### Batch 37 — Полный терминал (VT100/ANSI) ⚠️ не проверено
- **AnsiParser** (`data/terminal/AnsiParser.kt`): парсер CSI-последовательностей. SGR: 16 базовых цветов + 256-цветная палитра + RGB (38;2;r;g;b / 48;2;r;g;b). Стили: bold, italic, underline, strikethrough. Reset (0). `StyledSpan(text, fg, bg, bold, italic, underline, strikethrough)`, `ParsedLine(spans)`.
- **TerminalColorPalette** (`data/terminal/TerminalColorPalette.kt`): маппинг ANSI-кодов 0-255 в Compose Color. 16 стандартных + 216 куб (6×6×6) + 24 оттенка серого.
- **TabCompletionEngine** (`data/terminal/TabCompletionEngine.kt`): автодополнение по файлам в текущей директории. Парсинг последнего токена из строки ввода. Разрешение относительных путей. Экранирование пробелов в путях. Сортировка: папки первые.
- **PathDetector** (`data/terminal/PathDetector.kt`): regex-детекция абсолютных (`/...`) и относительных (`./...`) путей в выводе терминала. Ссылки для навигации из терминала в проводник.
- **TerminalViewModel переработан**: `ParsedTerminalLine` вместо простого текста. Потоковый вывод через AnsiParser. Буфер 2000 строк (было 500). Timeout 60 сек (было 30). Tab-автодополнение. Кликабельные пути через PathDetector. Панель быстрых символов.
- **TerminalScreen переработан**: AnnotatedString рендеринг со стилями (цвет, bold, italic, underline). Чипы автодополнения над полем ввода. Панель быстрых символов (~ / | > < & ; " ' . - _ Tab Ctrl+C) между клавиатурой и полем ввода. Кликабельные пути (primary цвет, pushStringAnnotation). Настройки: размер буфера, таймаут, размер шрифта. BottomSheet настроек.

### Batch 36 — Сравнение файлов и папок ✅ проверено
- **Зависимость**: `io.github.java-diff-utils:java-diff-utils:4.12`.
- **ComparisonResult** (`domain/model/ComparisonResult.kt`): `DiffLineType` (EQUAL, ADDED, REMOVED, CHANGED), `DiffLine(type, leftLine, rightLine, leftNum, rightNum)`, `TextDiffResult(lines, stats)`, `DiffStats(equal, added, removed, changed)`, `FolderComparisonEntry(relativePath, status, leftSize, rightSize, leftModified, rightModified)`, `ComparisonStatus` (SAME, DIFFERENT, LEFT_ONLY, RIGHT_ONLY), `FileMetadataComparison(leftPath, rightPath, leftSize, rightSize, ...)`.
- **ComparisonHolder** (`domain/model/ComparisonHolder.kt`): static holder для leftPath/rightPath.
- **CompareTextFilesUseCase**: `DiffUtils.diff()` из java-diff-utils. Построчное сравнение. Преобразование Delta в DiffLine с номерами строк. Статистика.
- **CompareFoldersUseCase**: рекурсивный обход двух папок. Группировка по relativePath. MD5-хеши для файлов одинакового размера. `FolderComparisonEntry` для каждого файла.
- **ComparisonViewModel**: автодетекция режима (оба папки → folder, оба текст → text, иначе → binary metadata). States: textDiff, folderDiff, metadataComparison. Фильтры для folder diff (show same/different/left only/right only).
- **ComparisonScreen**: TopAppBar с именами файлов. Переключение контента по режиму.
- **TextDiffView**: два `LazyColumn` с синхронным скроллом (`snapshotFlow` + `scrollToItem`). Цветовая кодировка: зелёный (добавлено), красный (удалено), жёлтый (изменено), нейтральный (равно). Номера строк слева. Статистика внизу.
- **FolderDiffView**: LazyColumn с иконками статуса (✓/≠/←/→). Фильтр-чипы сверху. Тап → раскрытие деталей (размер, дата обоих файлов).
- **SelectionActionBar**: кнопка «Сравнить» (Icons.Filled.Compare) при выделении 2 файлов.
- **ExplorerViewModel**: `compareFiles()` — заполняет ComparisonHolder, эмитит NavigationEvent.OpenComparison.
- **HaronNavigation**: маршрут COMPARISON.
- **Доп. фикс**: прогресс-бар при сравнении папок (LinearProgressIndicator + счётчик). Тап на DIFFERENT файл → открытие текстового diff.

### Batch 35 — Открывалка по умолчанию ✅ проверено
- **IntentHandler** (`common/util/IntentHandler.kt`): разбор ACTION_VIEW, ACTION_SEND, ACTION_SEND_MULTIPLE. `ReceivedFile(displayName, localPath, mimeType, size)`. Резолв content:// URI через `contentResolver.openInputStream()` → копирование в `cacheDir/received/`. `queryDisplayName()` через OpenableColumns. `generateUniqueFile()` для конфликтов имён.
- **ReceiveFilesDialog** (`presentation/receive/ReceiveFilesDialog.kt`): AlertDialog со списком полученных файлов (имя + размер). Кнопки «Сохранить в текущую папку» и «Отмена». Иконки по типу файла.
- **AndroidManifest**: intent-filter на ACTION_VIEW и ACTION_SEND для типов: text/*, image/*, video/*, audio/*, application/pdf, application/zip, application/x-7z-compressed, application/x-rar-compressed, application/vnd.android.package-archive. Также ACTION_SEND_MULTIPLE для image/* и video/*.
- **MainActivity**: обработка intent в `onCreate()` и `onNewIntent()`. `IntentHandler.handleIntent()` → `receivedFiles` state → NavigationEvent.HandleExternalFile.
- **HaronNavigation**: обработка receivedFiles — единичный медиа/PDF/архив → открытие во встроенном просмотрщике, множественные/другие → ReceiveFilesDialog с сохранением в текущую папку проводника.
- **NavigationEvent.HandleExternalFile**: передаёт список ReceivedFile.
- **Доп. фикс**: ODT/ODS/ODP/RTF MIME-типы добавлены в intent-filter. ACTION_VIEW → прямое открытие без диалога. DOCX/DOC/ODT/RTF/FB2 → DocumentViewerScreen (извлечение текста через ContentExtractor). APK → ACTION_INSTALL_PACKAGE вместо ACTION_VIEW (убрано зацикливание). **DocumentViewerScreen** (`presentation/document/DocumentViewerScreen.kt`): новый экран для просмотра документов. NavigationEvent.OpenDocumentViewer. HaronRoutes.DOCUMENT_VIEWER.

### Batch 34 — Голосовые команды ⚠️ не проверено
- **VoiceCommandManager** (`data/voice/VoiceCommandManager.kt`): @Singleton, обёртка над `SpeechRecognizer`. `VoiceState` (IDLE, LISTENING, PROCESSING, ERROR). `startListening()` → создаёт SpeechRecognizer, intent на ru-RU + en-US (additional languages). `stop()` с полным cleanup. `matchPhrase()` — сопоставление распознанного текста с `GestureAction` через PHRASE_MAP (12 пар, `contains` match).
- **PHRASE_MAP**: русские + английские фразы для каждого действия: "меню"/"menu" → OPEN_DRAWER, "полка"/"shelf" → OPEN_SHELF, "скрытые"/"hidden" → TOGGLE_HIDDEN, "создать"/"create" → CREATE_NEW, "поиск"/"search" → GLOBAL_SEARCH, "терминал"/"terminal" → OPEN_TERMINAL, "выделить все"/"select all" → SELECT_ALL, "обновить"/"refresh" → REFRESH, "домой"/"home" → GO_HOME, "сортировка"/"sort" → SORT_CYCLE, "настройки"/"settings" → OPEN_SETTINGS, "передача"/"transfer" → OPEN_TRANSFER.
- **ExplorerViewModel**: инжекция `VoiceCommandManager` (public val для доступа из Screen). Подписка на `lastResult` в init — при распознавании действия вызывает `executeGestureAction()` (те же action-коды что и жесты) + `consumeResult()`.
- **ExplorerScreen**: SmallFloatingActionButton (Mic/MicOff иконки) в правом нижнем углу. Показывается только когда SpeechRecognizer доступен и нет выделения/drag. Runtime permission RECORD_AUDIO через `rememberLauncherForActivityResult`. При тапе: если listening → stop, иначе → проверка permission → startListening. Цвет: primary при listening, surfaceContainerHigh при idle.
- **Manifest**: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`

### Batch 33 — Система жестов ⚠️ не проверено
- **GestureAction enum** (13 действий): NONE, OPEN_DRAWER, OPEN_SHELF, TOGGLE_HIDDEN, CREATE_NEW, GLOBAL_SEARCH, OPEN_TERMINAL, SELECT_ALL, REFRESH, GO_HOME, SORT_CYCLE, OPEN_SETTINGS, OPEN_TRANSFER. Каждое с `labelRes` для UI.
- **GestureType enum** (4 жеста): LEFT_EDGE_TOP (→полка), LEFT_EDGE_BOTTOM (→меню), RIGHT_EDGE_TOP (→скрытые файлы), RIGHT_EDGE_BOTTOM (→обновить). Дефолты в `defaultAction`.
- **HaronPreferences**: `getGestureAction()`, `setGestureAction()`, `getGestureMappings()` — хранение в SharedPreferences с ключом `gesture_<TYPE_NAME>`.
- **ExplorerViewModel**: `executeGestureAction()` — switch по всем 13 действиям. `cycleSortOrder()` — переключение NAME→DATE→SIZE→EXTENSION. `reloadGestureMappings()` — обновление из preferences.
- **ExplorerScreen**: переписан `pointerInput` — теперь 4 зоны (лево-верх, лево-низ, право-верх, право-низ). Свайп вправо от левого края или влево от правого. Действие берётся из `state.gestureMappings`. LifecycleEventObserver для reload при ON_RESUME.
- **SettingsScreen + SettingsViewModel**: секция "Жесты" с 4 строками (GestureRow), каждая с ExposedDropdownMenuBox для выбора действия. Кнопка "Сбросить по умолчанию".

### Batch 32 — Встроенный терминал (простой) ⚠️ не проверено
- **TerminalViewModel**: `ProcessBuilder("sh", "-c", command)` с `currentDir` как рабочей директорией. Timeout 30 сек, max 500 строк вывода. Встроенные команды: `cd`, `pwd`, `clear`, `help`, `exit`. Остальные — через `sh -c`. История команд (до 100, навигация up/down). Парсинг командной строки с поддержкой кавычек.
- **TerminalScreen**: тёмный фон (#1E1E1E), моноширинный шрифт, LazyColumn для вывода. Цветовое кодирование: зелёный для команд, красный для ошибок, серый для вывода. Поле ввода с prompt (текущая папка + $). Кнопки: отправить, история вверх/вниз. Авто-скролл к последней строке.
- **Навигация**: `NavigationEvent.OpenTerminal`, `HaronRoutes.TERMINAL`, пункт "Терминал" в боковом меню (секция Инструменты, иконка Code).

### Batch 31 — Счётчик на иконке + Сетевое обнаружение ⚠️ не проверено
- **Счётчик на иконке**: `FileOperationService.activeOperations` — `MutableStateFlow<Int>`, инкремент при `start()`, декремент при завершении/cancel/destroy. `.setNumber(count)` на notification → лаунчер показывает badge. TransferService тоже инкрементирует/декрементирует через тот же счётчик. ExplorerViewModel подписан на `activeOperationsCount` в UI state.
- **Сетевое обнаружение**: `NetworkDeviceScanner` (@Singleton) — два NSD discovery параллельно: `_haron._tcp.` (другие Haron) и `_smb._tcp.` (SMB шары). Плюс subnet scan порт 445 (batch по 20 IP, timeout 300мс) для SMB без mDNS. `StateFlow<List<NetworkDevice>>` → DrawerMenu секция "Сеть" с кнопкой Refresh. Тап на Haron → открывает Transfer. Тап на SMB → toast с IP:port.

### Batch 30 — Завершение Phase 2 ⚠️ не проверено
- **Поиск внутри архивов**: `ContentExtractor.extractArchiveEntries()` — ZIP (`ZipFile.entries()`), 7Z (`SevenZFile`), RAR (`junrar Archive`). Для маленьких текстовых файлов внутри ZIP (<64KB) — извлечение содержимого. Интеграция в BASIC индексацию через `SearchRepositoryImpl`.
- **OCR-поиск по фото**: `ImageLabeler.recognizeText()` — ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`). Downscale до 1024px (больше чем для labeling — нужна читаемость). Timeout 10 сек/файл, только файлы <10MB. Результат объединяется с labels при VISUAL индексации.
- **USB OTG**: `UsbStorageManager` — обнаружение removable volumes через `StorageVolumeHelper`, `BroadcastReceiver` на `ACTION_MEDIA_MOUNTED/UNMOUNTED/EJECT/BAD_REMOVAL`, `StateFlow<List<UsbVolume>>`. Безопасное извлечение: `sync` + reflection unmount через `StorageManager` hidden API, fallback. DrawerMenu — секция USB с иконкой, free/total space, кнопка Eject. ExplorerViewModel — toast при подключении, auto-navigate root при отключении.

### Batch 29 bugfix — Исправление передачи файлов ⚠️ не проверено
- **HTTP сервер недоступен**: `getLocalIpAddress()` возвращал IP мобильных данных (rmnet) вместо WiFi. Fix: сначала `ConnectivityManager.getLinkProperties()` (возвращает active network = WiFi), затем fallback на `NetworkInterface` с приоритетом `wlan*`. Добавлен `host = "0.0.0.0"` в Ktor `embeddedServer()`. Логирование выбранного IP.
- **Устройства не находятся**: `combine()` трёх Flow (WiFi Direct + NSD + Bluetooth) блокировался навсегда — WiFi Direct не эмитил начальное значение. Fix: добавлен `trySend(emptyList())` в `WifiDirectManager.discoverPeers()` до начала discovery. Теперь `combine()` сразу работает и показывает хотя бы BT-спаренные устройства.
- **Bluetooth не отправляет (RFCOMM)**: `createRfcommSocketToServiceRecord()` выбрасывал `IOException: read failed, socket might closed or timeout, read ret: -1` на Samsung/LG/Huawei. Fix: reflection fallback `device.createRfcommSocket(1)` при неудаче стандартного метода. Информативное сообщение об ошибке при неудаче обоих методов.

### Batch 29 — Доделка передачи файлов ⚠️ не проверено
- **ZXing QR-код**: заменён фейковый генератор на `QRCodeWriter` из `com.google.zxing:core:3.5.3`. Размер 512×512 для чёткости при масштабировании. Камера теперь реально считывает QR.
- **ReceiveFileManager**: `@Singleton`, TCP `ServerSocket` на порту из диапазона 8080-8090. `startListening()` → `Flow<IncomingTransferRequest>` через `callbackFlow`. Парсит REQUEST через `TransferProtocolNegotiator`. `acceptTransfer()` → `Flow<TransferProgressInfo>` с побайтовым приёмом файлов. Файлы сохраняются в `Downloads/Haron/` с auto-rename при конфликте. Поддержка resume через offset в FILE_HEADER (`RandomAccessFile.seek()`).
- **NSD регистрация**: при старте receive mode вызывается `nsdDiscoveryManager.registerService(port)`. При остановке — `unregisterService()`. Добавлен `NsdManager.RegistrationListener` с корректным `unregisterService()`. Другие Haron-устройства видят этот девайс через NSD discovery.
- **Bluetooth приём (RFCOMM)**: `BluetoothTransferManager.startListening()` — `BluetoothServerSocket` с HARON_UUID. `acceptBtTransfer()` — приём файлов по тому же протоколу что TCP. `declineBtTransfer()` — отказ с reason. `stopListening()` — закрытие серверного сокета.
- **Resume**: `TransferProgressInfo.resumeOffset` — отслеживание позиции для retry. `WifiDirectManager.sendFiles()` — параметр `resumeFromByte`, skip уже отправленных файлов и offset внутри текущего файла. Приёмная сторона: `RandomAccessFile` с seek при `offset > 0`.
- **TransferRepository**: 5 новых методов — `startReceiving()`, `stopReceiving()`, `acceptTransfer()`, `declineTransfer()`, `getReceivePort()`.
- **TransferViewModel**: auto-start receive mode при открытии экрана. `startReceiving()` подписывается на входящие запросы, показывает ReceiveDialog. `acceptIncoming()` запускает foreground service + подписка на прогресс. `stopReceiving()` при onCleared и DisposableEffect.
- **TransferScreen**: кнопка Download в TopAppBar (toggle receive mode, подсветка primary при активном). Карточка "Ожидание входящих файлов" при активном receive mode. Cleanup в DisposableEffect.

### Batch 24-28 — Передача файлов + Cast на ТВ + Пульт управления
- **Domain models**: TransferSession, TransferProtocol (WIFI_DIRECT/HTTP/BLUETOOTH), TransferState, TransferProgressInfo, DiscoveredDevice, CastDevice, RemoteInputEvent (sealed: PlayPause/SeekTo/VolumeChange/Next/Prev), TransferHolder (static object)
- **Repository + UseCase**: TransferRepository, CastRepository, DiscoverDevicesUseCase, SendFilesUseCase, StartCastUseCase
- **WifiDirectManager**: WifiP2pManager + BroadcastReceiver, discovery + direct socket transfer. Rename: `channel` → `p2pChannel` (callbackFlow `channel` conflict)
- **BluetoothTransferManager**: BT discovery + RFCOMM socket, UUID `a1b2c3d4-e5f6-7890-abcd-ef1234567890`
- **NsdDiscoveryManager**: Network Service Discovery `_haron._tcp.` + service registration
- **HttpFileServer (Ktor CIO)**: embedded server, routes: `/` (HTML dark UI), `/download/{index}`, `/stream/{index}`. Port scanning 8080-8090. Fix: wildcard imports for Ktor (specific imports don't expose `call` property), `ApplicationEngine` type instead of `EmbeddedServer<*, *>`
- **TransferProtocolNegotiator**: JSON handshake (REQUEST/ACCEPT/DECLINE/FILE_HEADER/COMPLETE) via `org.json.JSONObject`
- **TransferRepositoryImpl**: combines 3 discovery flows via `combine()`, retry with exponential backoff from HaronConstants.TRANSFER_RETRY_DELAYS
- **TransferService**: Foreground Service, PARTIAL_WAKE_LOCK (1hr max), actions: CANCEL/PAUSE/RESUME/START_SERVER/STOP_SERVER/START_RECEIVE/STOP_RECEIVE
- **TransferScreen + TransferViewModel**: full UI: battery warning, file info, progress card, device list, QR dialog, receive dialog. Runtime permissions (BT_SCAN, BT_CONNECT, NEARBY_WIFI_DEVICES, ACCESS_FINE_LOCATION)
- **SelectionActionBar**: Send button (Icons.AutoMirrored.Filled.Send)
- **DrawerMenu**: "File Transfer" item in Tools section
- **GoogleCastManager**: CastContext + SessionManager + SessionManagerListener, graceful degradation (no GMS → Cast hidden). Device discovery via AndroidX MediaRouter + `mergedSelector`. RemoteMediaClient.Callback for media state (isPlaying, position, duration). `selectCastDevice()` via MediaRouter route selection
- **MiracastManager**: `android.media.MediaRouter` ROUTE_TYPE_LIVE_VIDEO discovery. Fix: implement all abstract Callback methods (onRouteSelected, onRouteGrouped, etc.)
- **CastRepositoryImpl + CastModule (Hilt)**: delegates to GoogleCastManager + MiracastManager
- **CastOptionsProvider**: OptionsProvider, CC1AD845 (Default Media Receiver)
- **CastViewModel**: shared ViewModel for Cast state. Device discovery (combine Cast + Miracast), selectDeviceAndCast (pending cast + auto-cast on connect), castMedia (HTTP server + RemoteMediaClient), media position polling every 1s, sendRemoteInput
- **CastButton**: Cast/CastConnected icon, hidden when unavailable
- **CastDeviceSheet**: Dialog listing devices (Chromecast + Miracast), connected device with disconnect
- **MediaRemotePanel**: Card: seekbar + play/pause + next/prev + volume. Fix: deprecated VolumeDown/VolumeUp → AutoMirrored
- **CastOverlay**: AnimatedVisibility at bottom of screen, uses CastViewModel for real media state
- **Integration**: CastButton in MediaPlayerScreen TopBar + GalleryScreen TopAppBar. CastDeviceSheet in both. CastOverlay in MainActivity (global)
- **HaronApp**: `@Inject castManager: GoogleCastManager` + `castManager.initialize()` in onCreate
- **Dependencies**: ktor-server-core:2.3.12, ktor-server-cio:2.3.12, play-services-cast-framework:22.0.0, mediarouter:1.7.0

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

### 0.41.0 — Batch 41 (Phase 4, v2.0)
- Доверенные устройства: звёздочка на экране передачи, тап для переключения доверия
- Переименование устройств: долгий тап на устройство → диалог ренейма, алиас хранится в настройках
- Условный Quick Send: авто-приём только от доверенных устройств, остальные через диалог подтверждения
- Глобальный диалог приёма: accept/decline работает с любого экрана (не только Transfer)
- Пульсирующий круг при приёме от друзей: показывает имя отправителя, растягивается по ширине
- Круг поверх всего: рендерится выше экрана блокировки
- WiFi-привязка сокетов: маршрутизация через WiFi при наличии мобильных данных (Network.bindSocket)
- Сканирование портов 8080-8090 при TCP-подключении к устройству Haron
- Обновление списка файлов после приёма: автоматический refresh панели + MediaScanner
- DisplayName в боковом меню и Quick Send оверлее (алиас или NSD-имя)

### 0.39.0 — Batch 39 (Phase 4, v2.0)
- Расширенный шаринг на ТВ: 5 режимов Cast
- Кнопка Cast в панели действий при выделении файлов
- BottomSheet выбора режима трансляции (иконки + описания)
- Слайд-шоу фото на ТВ: HTML+JS авто-листание через HTTP, настройки (интервал, зацикливание, перемешивание)
- PDF-презентация на ТВ: листай на телефоне, показывает на ТВ (рендер страниц через PdfRenderer → PNG)
- Контроллер PDF-презентации: prev/next, номер страницы, прогресс
- Информация о файле на ТВ: HTML-карточка с именем, размером, типом, датой
- Зеркалирование экрана: MediaProjection + VirtualDisplay → JPEG-стрим через HTTP
- ScreenMirrorService: Foreground Service, JPEG 50% quality, 200мс refresh, порты 8090-8095
- Адаптивный CastOverlay: переключение UI по режиму (полная панель / минимальная / PDF-контроллер)
- CastActionHolder: статический объект для передачи режима между non-composable и composable

### 0.38.0 — Batch 38 (Phase 4, v2.0)
- Стеганография: скрытие файлов внутри медиа (JPEG, PNG, MP4, MP3)
- Tail-Append метод: зашифрованные данные дописываются после конца файла-носителя
- AES-256-GCM шифрование через Android Keystore (ключ haron_stego_key)
- Потоковое шифрование через CipherOutputStream (без загрузки всего файла в память)
- Формат: carrier + header (magic HRNSTEG + version + payload name + size + IV) + encrypted data + offset + footer HRNSTEG!
- Экран стеганографии: два режима — Скрыть и Извлечь
- Прогресс по фазам: копирование носителя → шифрование → запись
- Автодетекция скрытых данных при выборе файла с footer
- Кнопка «Скрыть в файле» в панели действий при выделении
- Навигация: маршрут STEGANOGRAPHY, пункт из SelectionActionBar

### 0.37.0 — Batch 37 (Phase 4, v2.0)
- Полный терминал с поддержкой ANSI escape-кодов
- Цветной вывод: 16 базовых цветов + 256-цветная палитра + RGB (true color)
- Стили текста: bold, italic, underline, strikethrough
- Автодополнение путей по Tab (файлы в текущей директории, папки первые)
- Кликабельные пути в выводе (абсолютные и относительные) → переход в проводник
- Панель быстрых символов: ~ / | > < & ; " ' . - _ Tab Ctrl+C
- Увеличенный буфер: 2000 строк (было 500)
- Увеличенный таймаут: 60 сек (было 30)
- Настройки терминала: размер буфера, таймаут, размер шрифта (BottomSheet)

### 0.36.0 — Batch 36 (Phase 4, v2.0)
- Сравнение файлов: side-by-side текстовый diff с синхронной прокруткой
- Цветовая кодировка: зелёный (добавлено), красный (удалено), жёлтый (изменено)
- Номера строк в обоих колонках
- Статистика различий: сколько строк добавлено/удалено/изменено
- Сравнение папок: рекурсивный обход, MD5-хеши, статусы (одинаковые/различные/только слева/только справа)
- Фильтр-чипы для результатов сравнения папок
- Автодетекция режима: текст → текстовый diff, папки → сравнение папок, иначе → метаданные
- Кнопка «Сравнить» в панели действий при выделении 2 файлов
- Навигация: маршрут COMPARISON
- Зависимость: java-diff-utils 4.12

### 0.35.0 — Batch 35 (Phase 4, v2.0)
- Открывалка по умолчанию: Haron регистрируется как обработчик файлов в системе
- Поддержка: text/*, image/*, video/*, audio/*, PDF, ZIP, 7Z, RAR, APK
- ACTION_VIEW: открытие файла из другого приложения через «Открыть с помощью»
- ACTION_SEND / SEND_MULTIPLE: приём файлов через «Поделиться»
- Одиночный медиа/PDF/архив → автоматическое открытие во встроенном просмотрщике
- Множественные файлы → диалог сохранения в текущую папку проводника
- IntentHandler: резолв content:// URI, копирование в кэш, уникальные имена
- ReceiveFilesDialog: список полученных файлов с именами и размерами

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
- Кнопка "Открыть в читалке" для PDF и документов (DOC/DOCX/ODT/RTF/FB2)
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
- Поиск внутри архивов (ZIP, 7Z, RAR): находит архивы по именам файлов внутри, без распаковки
- Для маленьких текстовых файлов внутри ZIP (<64 КБ) — индексируется и содержимое
- OCR-поиск по фотографиям: распознавание текста на вывесках, скриншотах, документах (ML Kit офлайн)
- OCR работает при визуальной индексации — текст с фото добавляется к меткам объектов

### Локальный поиск (в панели)
- Тап на кнопку поиска (лупа) → строка поиска в тулбаре панели
- По умолчанию — поиск по имени файла (кнопка Тт активна)
- Кнопка FindInPage (📄) — переключение на поиск по содержимому в текущей папке
- Поиск по содержимому использует ту же проиндексированную базу (file_content)
- При поиске по содержимому папки с подходящим именем остаются видны
- Результаты обновляются с debounce 300 мс
- Поиск автоматически сбрасывается при переходе в другую папку

### Передача файлов
- Кнопка "Отправить" в панели действий при выделении файлов
- Пункт "Передача файлов" в боковом меню (Инструменты)
- Обнаружение устройств: Wi-Fi Direct + NSD (LAN) + Bluetooth — одновременно
- Автоматический выбор лучшего протокола (Wi-Fi Direct → HTTP → Bluetooth)
- Wi-Fi Direct P2P: прямая передача между устройствами без роутера
- HTTP-сервер (Ktor CIO): передача через браузер на любом устройстве
- QR-код с URL сервера для не-Haron устройств (iPhone, ПК) — реальный ZXing QR, сканируется любой камерой
- HTML-страница с темным дизайном для скачивания файлов в браузере
- Bluetooth RFCOMM: передача без Wi-Fi (медленнее, но универсальнее) — и отправка, и приём
- Прогресс передачи: скорость, ETA, текущий файл
- Foreground Service для надёжной передачи в фоне
- WakeLock: передача не прерывается при выключении экрана
- Предупреждение при включённом режиме энергосбережения
- Retry при обрыве: автоматический повтор (exponential backoff: 1с, 2с, 4с, 8с, 16с)
- Resume: продолжение передачи с места обрыва (offset в протоколе + дозапись файлов)
- Запрос runtime-разрешений (Bluetooth, Wi-Fi) перед началом
- Режим приёма файлов: автостарт при открытии экрана, кнопка в шапке, индикатор активности
- NSD-регистрация при приёме: другие Haron-устройства автоматически видят принимающее устройство
- Приём файлов по TCP и Bluetooth — сохранение в Downloads/Haron/ с auto-rename при конфликтах
- Диалог подтверждения входящей передачи (принять/отклонить)

### Cast на ТВ
- Кнопка Cast в медиаплеере и галерее (скрыта если Cast недоступен)
- Graceful degradation: если Google Play Services нет — Cast не показывается
- Поиск Chromecast и Miracast устройств одним диалогом
- Google Cast (Chromecast): трансляция видео, аудио и фото на ТВ
- Miracast: зеркалирование экрана телефона на ТВ
- HTTP-сервер для раздачи медиафайлов на Chromecast
- Пульт управления: появляется автоматически при подключении к Chromecast
- Play/Pause, Prev/Next, перемотка (seekbar), громкость +/−
- Реальное время: позиция и длительность обновляются каждую секунду
- Автоматическое начало трансляции при выборе устройства
- Отключение от устройства одной кнопкой

### Счётчик фоновых операций
- На иконке приложения показывается число активных фоновых операций (копирование, передача)
- Badge обновляется автоматически при старте и завершении любой операции
- Считает и файловые операции (FileOperationService), и передачу (TransferService)

### Сетевое обнаружение
- Автоматический поиск устройств в локальной сети при запуске
- Обнаружение других экземпляров Haron (через NSD)
- Обнаружение SMB-шар (через mDNS + сканирование подсети порт 445)
- Секция "Сеть" в боковом меню с найденными устройствами
- Кнопка "Обновить" для повторного сканирования
- Тап на устройство Haron → экран передачи файлов
- Тап на SMB-шару → информация об адресе

### Быстрая отправка (Quick Send DnD)
- Долгий тап на правой части файла → появляются кружки устройств Haron → перетаскиваешь на нужный кружок → файл отправляется
- Кружки расположены полукругом над пальцем, ближайший подсвечивается
- Долгий тап на устройство Haron в боковом меню → на том устройстве появляется кружок-приёмник
- Кружок-приёмник висит поверх всего (можно навигировать по папкам) с пульсирующей анимацией
- Перетащи файл на кружок-приёмник → файл отправляется
- Toast-уведомления: нет устройств, отправка, успех, ошибка

### Доверенные устройства и ренейм
- Звёздочка на каждом устройстве на экране передачи файлов: тап → доверенное/не доверенное
- Доверенные устройства: Quick Send принимается автоматически без подтверждения
- Не доверенные устройства: при Quick Send появляется диалог «Принять / Отмена» на любом экране
- Переименование устройств: долгий тап на устройство → ввод нового имени (алиас)
- Алиас показывается вместо NSD-имени в списке, боковом меню и Quick Send
- Пульсирующий круг при получении от друзей: имя отправителя внутри, поверх всего
- Принятые файлы сразу появляются в проводнике и в других приложениях

### USB OTG
- Автоматическое обнаружение USB-накопителей (флешки, кард-ридеры, внешние диски)
- Toast-уведомление при подключении USB
- USB-накопители отображаются в боковом меню с иконкой USB
- Индикатор свободного/общего места на каждом USB-накопителе
- Тап → навигация в файлы USB-накопителя
- Безопасное извлечение: кнопка «Извлечь» (сброс буферов + программный unmount)
- Автоматический возврат на root при отключении USB (если панель была на USB)
- Toast при отключении без безопасного извлечения

### Система жестов
- 4 настраиваемых edge-свайпа: левый край верх, левый край низ, правый край верх, правый край низ
- 13 действий на выбор: открыть меню, открыть полку, показать/скрыть скрытые файлы, создать новое, глобальный поиск, терминал, выделить все, обновить, перейти в корень, сменить сортировку, настройки, передача файлов, отключено
- Настройки по умолчанию: левый-верх → полка, левый-низ → меню, правый-верх → скрытые файлы, правый-низ → обновить
- Настройка через Настройки → Жесты (выпадающий список для каждого жеста)
- Кнопка "Сбросить по умолчанию" для возврата к заводским настройкам
- Изменения применяются сразу при возврате на главный экран

### Встроенный терминал
- Пункт "Терминал" в боковом меню (секция Инструменты)
- Тёмная тема с моноширинным шрифтом (стиль командной строки)
- Выполнение shell-команд (ls, cat, cp, mv, rm, mkdir, grep, find, chmod и др.)
- Поддержка пайпов (|) и перенаправлений (>, >>)
- Встроенные команды: cd (смена папки), pwd (текущая папка), clear (очистка), help (справка)
- Цветовое выделение: команды зелёным, ошибки красным
- История команд (до 100) с навигацией кнопками вверх/вниз
- Автопрокрутка к последнему выводу
- Timeout 30 секунд на команду (защита от зависания)

### Голосовые команды
- Кнопка микрофона в правом нижнем углу (появляется если устройство поддерживает распознавание речи)
- Нажмите на микрофон → произнесите команду → действие выполняется автоматически
- Поддерживаемые команды (русский + английский):
  - "Меню" / "Menu" → открыть боковое меню
  - "Полка" / "Shelf" → открыть полку
  - "Скрытые" / "Hidden" → показать/скрыть скрытые файлы
  - "Создать" / "Create" → создать новый файл/папку
  - "Поиск" / "Search" → глобальный поиск
  - "Терминал" / "Terminal" → открыть терминал
  - "Выделить все" / "Select all" → выделить все файлы
  - "Обновить" / "Refresh" → обновить список файлов
  - "Домой" / "Home" → перейти в корневую папку
  - "Сортировка" / "Sort" → сменить порядок сортировки
  - "Настройки" / "Settings" → открыть настройки
  - "Передача" / "Transfer" → открыть передачу файлов
- Микрофон подсвечивается во время прослушивания
- Разрешение на запись запрашивается при первом нажатии

### Открывалка по умолчанию
- Haron регистрируется в системе как обработчик файлов (текст, изображения, видео, аудио, PDF, архивы, APK)
- Открытие файла из любого приложения через «Открыть с помощью» → файл показывается во встроенном просмотрщике
- Приём файлов через «Поделиться» (одиночный и множественный)
- Одиночный медиа/PDF/архив → автоматически открывается (плеер, галерея, читалка, архиватор)
- Несколько файлов → диалог сохранения в текущую папку проводника

### Сравнение файлов и папок
- Кнопка «Сравнить» в панели действий при выделении 2 файлов или папок
- Текстовый diff: два столбца рядом с синхронной прокруткой
- Цветовая кодировка: зелёный — добавлено, красный — удалено, жёлтый — изменено
- Номера строк в обоих столбцах
- Статистика различий (сколько строк добавлено/удалено/изменено)
- Сравнение папок: какие файлы совпадают, различаются, есть только с одной стороны
- Фильтры для результатов сравнения папок
- Сравнение метаданных для бинарных файлов (размер, дата)
- Автоопределение режима: текстовые файлы → diff, папки → сравнение, иначе → метаданные

### Стеганография (скрытие файлов)
- Скрытие любого файла внутри медиафайла (JPEG, PNG, MP4, MP3)
- Файл-носитель продолжает открываться как обычно (фото/видео/аудио)
- Шифрование AES-256-GCM (ключ в Android Keystore — невозможно извлечь)
- Кнопка «Скрыть в файле» в панели действий при выделении
- Экран стеганографии: выбор файла-носителя + файла для скрытия
- Прогресс-бар по фазам: копирование → шифрование → запись
- Автоматическая детекция скрытых данных при выборе файла
- Извлечение скрытого файла обратно с расшифровкой
- Потоковая обработка: работает с большими файлами без переполнения памяти

### Полный терминал (VT100/ANSI)
- Цветной вывод: поддержка 16 базовых цветов, 256-цветной палитры и RGB (true color)
- Стили текста: жирный, курсив, подчёркивание, зачёркивание
- Автодополнение путей по Tab: начните вводить путь → нажмите Tab → варианты появятся над полем ввода
- Кликабельные пути в выводе: тап на путь → переход в эту папку в проводнике
- Панель быстрых символов над клавиатурой: ~ / | > < & ; " ' . - _ Tab Ctrl+C
- Увеличенный буфер вывода: 2000 строк (было 500)
- Увеличенный таймаут команд: 60 секунд (было 30)
- Настройки терминала: размер буфера, таймаут, размер шрифта

### Расширенный Cast на ТВ
- Кнопка Cast в панели действий при выделении файлов
- Выбор режима трансляции через BottomSheet с иконками
- 5 режимов Cast:
  - Медиа: трансляция видео/аудио/фото на ТВ (как раньше)
  - Слайд-шоу: автоматическое листание фото на ТВ с настраиваемым интервалом
  - PDF-презентация: листай на телефоне — показывает на ТВ (контроллер с prev/next и номером страницы)
  - Информация о файле: карточка с именем, размером, типом и датой на экране ТВ
  - Зеркало экрана: дублирование экрана телефона на ТВ через HTTP-стрим
- Настройки слайд-шоу: интервал (2-30 сек), зацикливание, перемешивание
- Зеркалирование через Foreground Service с MediaProjection (запрос разрешения у системы)

### Оформление
- Тема: системная / светлая / тёмная
- Переключение темы из бокового меню (3 кнопки)
- Material Design 3

---

## Заметки

> Свободная секция для идей, вопросов, решений которые ещё не оформились.

- **TODO: Проверка SD-карты** — перемещение между разными хранилищами (внутренняя память ↔ SD-карта) использует fallback через copy+delete. Нужно протестировать на устройстве с SD-картой.
