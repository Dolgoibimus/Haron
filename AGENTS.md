# AGENTS.md — Проводник

Живой файл. Обновляется в процессе разработки.
Выполненные задачи → из TODO в Журнал решений.
Новые фичи → в Changelog.

---

## Статус проекта

**Текущая версия:** 0.90 (Phase 4, Batch 90)
**Текущая фаза:** Phase 4 — продвинутые функции (v2.0 features)

---

## В работе (текущая задача)

> Сюда записывается задача которая выполняется прямо сейчас.
> При /compact — сохранить прогресс здесь перед сжатием.

**Кастомный навбар — развитие (в процессе):**
- ✅ Системный навбар скрыт (immersive mode, статус-бар остаётся)
- ✅ Кастомный навбар: HorizontalPager, N страниц, 5-7 кнопок
- ✅ Кнопка Exit — hold 2 сек с кружком-таймером
- ✅ Простой тап на Exit = системная "назад"
- ✅ Все 25 действий подключены к ExplorerViewModel
- ✅ NavbarConfig модель (NavbarAction, NavbarButton, NavbarPage, NavbarConfig)
- ✅ HaronPreferences — JSON сохранение/загрузка конфига
- ✅ NavbarSettingsScreen — экран настроек (страницы, кнопки, слайдер 5-7, выбор действий)
- ✅ Обновление конфига при возврате из настроек (ON_RESUME)
- ✅ Фикс тапа: двухфазная детекция (фаза 1 — прямой await UP без поллинга, фаза 2 — countdown с поллингом)
- ✅ Фикс свайпа страниц: userScrollEnabled=false + свой pointerInput (горизонтальный → страница, вертикальный → системе)
- ✅ Фикс подпрыгивания: Scaffold contentWindowInsets = statusBars only (игнорирует navigationBars)
- ✅ Radial menu для Copy/Move (long tap → два кружка: копировать/переместить)
- ✅ Radial menu для Delete (long tap → корзина/навсегда)
- ✅ Radial menu для Create (long tap → файл/папка)
- ✅ NavbarIconsScreen — кастомные иконки для кнопок (pick image → crop square/circle → 96×96 PNG)
- ✅ ButtonEditorDialog — два чекбокса (тап/долгий тап) вместо отдельных picker'ов
- ✅ Radial actions скрывают чекбокс "долгий тап" (long встроен)
- ✅ APP_ICON полностью удалён (enum, composable, дефолтный конфиг)
- ✅ Фикс подпрыгивания при долгом тапе: height(48.dp) на Box навбара
- ✅ Иконки навбара 28dp (было 22dp)
- ✅ ARROW_UP/DOWN/LEFT/RIGHT — полная навигация стрелками
- ✅ Курсор = выделение (Windows-стиль, полная заливка)
- ✅ Shift-режим (долгий тап ↓): мульти-выделение диапазонами с lockedPaths
- ✅ Убраны кружки выделения (CheckCircle) — только заливка
- ✅ Автоскролл к файлу под курсором (scrollToItem(newIndex))
- ⬜ Долгий тап с кружком — продумать на какие кнопки и как (отложено)

**Анимации-темы** ✅ реализовано:
- ✅ **4 анимации**: Matrix Rain, Snowfall, Starfield, Dust Particles
- ✅ ThemesScreen (Настройки → Анимации): сетка 2×2 карточек с живым превью 60dp
- ✅ MatrixRainCanvas — падающие символы катакана/бинарный/латиница/кириллица/микс, glow на головах
- ✅ SnowfallCanvas — снежинки с Perlin noise drift, вращение, 7 символов ❄❅❆✻✼✽❋
- ✅ StarfieldCanvas — мерцающие звёзды + падающие кометы (градиентный хвост, glow голова)
- ✅ DustCanvas — парящие золотистые пылинки с Perlin noise + пульсация яркости
- ✅ AnimSettingsScreen (универсальный): вкл/выкл, скорость, плотность, прозрачность, размер, только при зарядке
- ✅ MatrixSettingsScreen (расширенный): + палитра цвета (8 пресетов + RGB), набор символов
- ✅ Авто-отключение: включение одной анимации выключает остальные
- ✅ Работает везде кроме читалок (Doc/PDF) и видеоплеера
- ✅ Навбар прозрачный при включённой анимации
- ✅ Реактивный конфиг через SharedPreferences.OnSharedPreferenceChangeListener
- ✅ Автопауза при onPause (LifecycleEventObserver)

**Следующие задачи навбара:**
- Рамки/маски для иконок навбара (как icon pack в лаунчерах — кольца, формы, цвета, применяются ко всем иконкам одновременно)
- Темы иконок навбара (встроенные наборы: Material Filled/Outlined/Rounded)

---

### Batch 103 — Сессия 2026-03-24: навбар развитие + фиксы ⚠️ не проверено

**Что сделано:**

**1. Фикс тапа на кнопках навбара (NavbarHoldButton)** ✅ проверено
- Проблема: `withTimeoutOrNull(16)` + `awaitPointerEvent()` — гонка, UP-событие терялось при совпадении с таймаутом → тап не срабатывал (долгий тап работал)
- Решение: двухфазная детекция:
  - Фаза 1 (0-500мс): `awaitPointerEvent()` напрямую без поллинга — надёжно ловит UP
  - Фаза 2 (500мс+): поллинг каждые 16мс для анимации countdown circle
- Тайминги: tapThreshold=500мс, countdownDelay=600мс, duration=2600мс

**2. Фикс свайпа страниц при вызове системного навбара** ✅ проверено
- Проблема: вертикальный свайп снизу (вызов системного навбара) менял страницу HorizontalPager
- Решение: `userScrollEnabled = false` + свой `pointerInput` на родительском Box:
  - Определяет направление жеста по touchSlop (dx vs dy)
  - Горизонтальный → consume + `animateScrollToPage()` (порог 40dp)
  - Вертикальный → не consume → проходит к системе

**3. Фикс подпрыгивания навбара при появлении/исчезновении системного навбара** ✅ проверено
- Проблема: Scaffold по умолчанию учитывает `navigationBars` в `contentWindowInsets` → transient navbar менял `innerPadding` → контент прыгал

**4. Radial menu для кнопок навбара** ✅ проверено
- Новые NavbarAction: COPY_MOVE, DELETE_MENU, CREATE_MENU (+ внутренние FORCE_DELETE, CREATE_FILE)
- NavbarRadialButton: short tap → действие по умолчанию, long tap (>400мс) → два кружка над кнопкой
- Палец ведёшь к кружку → подсвечивается (graphicsLayer scaleX/Y = 2f), отпускаешь → действие, мимо → отмена
- Copy/Move: левый = Copy, правый = Move
- Delete: левый = Корзина, правый = Навсегда (requestForceDelete)
- Create: левый = Файл (requestCreateFromTemplate), правый = Папка (requestCreateFolder)
- Кружки на 30dp выше кнопки, spacing 16dp, edge clamping по ширине экрана

**5. NavbarIconsScreen — кастомные иконки** ✅ проверено
- Настройки навбара → кнопка палитры → экран со списком всех действий
- Тап на действие → выбор картинки из галереи → кроп-редактор (квадрат/круг, зум/пан)
- Сохранение 96×96 PNG в filesDir/navbar_icons/
- CustomNavbar подгружает кастомные иконки вместо Material Icons
- Сброс через ❌ справа от действия

**6. ButtonEditorDialog — переработка** ✅ проверено
- Вместо отдельных picker'ов для tap/long — единый диалог с двумя чекбоксами
- Каждое действие: синий чекбокс (тап) + красный чекбокс (долгий тап)
- Одну функцию нельзя назначить на оба — взаимоисключение
- Radial actions (Copy/Move, Delete menu, Create menu) — только чекбокс тапа, долгий встроен
- Диалог 5/6 высоты экрана (Dialog + Surface вместо AlertDialog)

**7. APP_ICON полностью удалён**
- Enum, AppIconButton composable, дефолтный конфиг (заменён на HOME)

**8. Визуальные улучшения**
- Иконки навбара: 22dp → 28dp
- Фикс подпрыгивания при долгом тапе: height(48.dp) на корневом Box навбара
- Решение: `Scaffold(contentWindowInsets = WindowInsets.statusBars)` — учитывает только статус-бар, игнорирует навигационный

**9. Навигация стрелками (ARROW_UP/DOWN/LEFT/RIGHT)** ✅ проверено
- ARROW_UP: тап = курсор вверх, долгий тап = переключить панель (1.3 сек countdown)
- ARROW_DOWN: тап = курсор вниз, долгий тап = toggle Shift-режим (1.3 сек)
- ARROW_LEFT: тап = назад по истории, долгий тап = вверх (родительская папка)
- ARROW_RIGHT: тап = вперёд по истории, долгий тап = зайти в папку под курсором (ENTER_FOLDER)
- Курсор = выделение: перемещение курсора автоматически выделяет файл (Windows-стиль)
- Выделение: полная заливка primaryContainer alpha 0.6 (убраны CheckCircle иконки)
- Автоскролл: scrollToItem(newIndex) — курсор всегда видим
- Первый тап стартует с видимого файла (panelScrollIndex), не с начала списка

**10. Shift-режим (мульти-выделение)** ✅ проверено
- Долгий тап ↓ = toggle Shift (кнопка подсвечивается tertiary alpha 0.5)
- Shift ON: anchor фиксируется, курсор ↑/↓ выделяет весь диапазон от anchor до курсора
- Shift OFF: выделение сохраняется в lockedPaths, обычный курсор добавляет только текущий файл
- Мульти-диапазон: Shift ON → выделить диапазон → Shift OFF → перейти → Shift ON → выделить ещё
- lockedPaths сбрасываются при смене папки

**12. Smart contrast — фикс тёмных тем в читалке** ✅ проверено
- **3 источника цветов** в документах, все требовали smart contrast:
  1. `span.textColor` / `span.highlightColor` — цвета из span'ов (RichParagraph + CompactTableRow)
  2. `paragraph.backgroundColor` — фон параграфа из `w:shd fill` (главная причина белых таблиц!)
  3. `cellBgs` — фон ячеек таблицы (cellBgs от парсера, может быть null)
- **Алгоритм инверсии**: если цвет конфликтует с фоном темы (разница luminance > 0.4) → `Color(1-r, 1-g, 1-b, a)`
  - Белый `#FFFFFF` → чёрный `#000000`, тёмный `#222222` → светлый `#DDDDDD`
- **Фиксы по месту**:
  - `cellStyle.color` = textColor темы (было захардкожено `Color.Black`)
  - Smart contrast + инверсия в CompactTableRow spans (textColor + highlightColor)
  - Smart contrast + инверсия для `paragraph.backgroundColor` в RichParagraph
  - Явный фон `themeBgColor` на ячейках таблицы (effectiveCellBg)
  - Рамки таблиц: `0x555555` в тёмной теме, `0xBDBDBD` в светлой
  - Передача textColor и themeBgColor в CompactTableRow (новые параметры)
- **Пороги**: текст 0.25, фон span 0.4, фон параграфа 0.4

**11. Стрелки — скрытые внутренние action'ы**
- SWITCH_PANEL, ENTER_FOLDER, CURSOR_LEFT, CURSOR_RIGHT, TOGGLE_SHIFT — скрыты из picker'ов и icon editor
- Стрелки автоматически получают hardcoded long actions (arrowLong в CustomNavbar)
- holdDurationMs: стрелки 1300мс, остальные 2600мс

---

### Batch 102 — Сессия 2026-03-23: APK размер, библиотека, читалка, SD-карта, навбар ⚠️ не проверено

**Что сделано:**

**1. APK размер — удаление интернет-поиска (-15 МБ)**
- Удалены: `domain/usecase/websearch/` (8 файлов), `presentation/search/` (4 файла — только web-таб), `WebDownloadService`, `WebSearchResult`, libtorrent4j
- Глобальный поиск по устройству СОХРАНЁН (SearchScreen, SearchViewModel, IndexNotificationViewModel)
- Из SearchScreen убран только таб "Интернет-поиск"
- Бэкап наработок: `I:\...\Haron\InetFind\`
- features.txt обновлён (EN + RU)
- Release APK: ~165 МБ (было 180 МБ)

**2. Библиотека — полная переработка**
- Три таба: FB2/EPUB, PDF, Остальные
- PDF и Остальные — группировка по папкам (FolderHeader: имя + путь)
- Сканер: добавлены форматы doc, docx, odt, rtf, xlsx, xls, csv
- extractMetadata для новых форматов
- Тап = открыть книгу (не preview), info через кнопку на обложке
- Grid columns per-tab (сохраняются в SharedPreferences)
- Дисковый кеш обложек (`filesDir/book_covers/`, WEBP)
- Автосинхронизация кеша с Room (удалённые книги чистятся)
- Исключённые папки (excludedFolders) — не сканировать
- Удалённые папки (removedFolders) — не возвращаются после ресканирования
- Кнопка ресканирования в TopAppBar
- FB2/EPUB → DocumentViewerScreen, PDF → PdfReaderScreen

**3. DocumentViewerScreen — полноэкранный режим**
- Убран Scaffold + TopAppBar
- Текст на весь экран (4dp сверху от статус-бара, 4dp + navBarInsets снизу)
- Overlay по тапу: сверху — назад + имя файла, снизу — темы + прогресс-слайдер
- 4 темы: Auto, Light, Sepia, Dark (кнопки 42dp)
- Smart color contrast: если цвет текста документа сливается с фоном темы — игнорируется
- Ложный тап при скроле: проверка drag distance > touchSlop
- Микрофон синхронизирован с overlay (появляется/исчезает вместе)

**4. PdfReaderScreen — очистка**
- Удалён дубликат DocumentReaderContent (~640 строк)
- Overlay по тапу: темы + прогресс-слайдер (как в DocumentViewerScreen)
- Оставлен только PDF-рендер (PdfReaderContent)

**5. SD-карта — прямой доступ**
- SafRootInfo.path — прямой путь к SD-карте
- Если MANAGE_EXTERNAL_STORAGE грантован и File API читает — навигация напрямую, без SAF пикера
- BreadcrumbBar: правильный корень для SD-карты (не ROOT_PATH + segments)
- Баг хлебных крошек: `/storage/emulated/0/storage/F9F1-7D91` → починен

**6. Rename overlay**
- Поле ввода всплывает над клавиатурой (imePadding + 30dp)
- До 3 строк для длинных имён
- Enter = переименовать, тап на фон = отмена
- Inline-поле в списке заменено на маркер (подсветка primary)

**7. Кастомный навбар**
- Системный навбар скрыт (hide navigationBars + BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE)
- HorizontalPager: N страниц × 5-7 кнопок
- Дефолт: [Back/Exit] [?] [AppIcon] [?] [?] + пустая страница 2
- Кнопка Exit: тап = назад, hold 2 сек = кружок + выход
- NavbarConfig модель + JSON persistence в HaronPreferences
- NavbarSettingsScreen: список страниц, слайдер кнопок, выбор действий
- 25 действий: навигация, файловые операции, экраны
- Конфиг обновляется при возврате из настроек (ON_RESUME)
- Панели контента: padding bottom 48dp (над навбаром)
- SelectionActionBar: padding bottom 48dp

**8. HaronTV — форк для Android TV**
- Создана копия проекта в `C:\Users\User\AndroidStudioProjects\HaronTV\`
- AGENTSTV.md — полный план двухмодульной структуры (shared + tv)
- Этапы 1-5 выполнены в параллельной сессии (shared + tv модули)

**9. Прочее**
- WebDAV тестовый сервер: wsgidav установлен, конфиг с кириллицей
- Глобальный CLAUDE.md: секция "Визуальное оформление" (отступы полноэкранных режимов)
- Logcat буфер на Redmi увеличен до 16 МБ

**Коммиты:**
- `c34103a` — remove internet search module and libtorrent4j
- `a102f9b` — remove internet search from features.txt
- `9126cc5` — restore global device search, remove only internet search tab
- `8cda5dd` — library tabs, reader themes, SD card direct access

---

### В работе — нужно решение (пауза)

**Проблема:** OD-поиск не находит аудио ни через какие источники. Работает только Archive.org, но там мало современного русского контента (только public domain / CC-лицензии).

**Протестировано и не работает:**
- SearXNG — все публичные инстансы заблокированы (429/403)
- Bing — bot detection + timeout 48с → убран
- DDG — bot detection, 0 ссылок → убран
- mmnt.ru — нужно проверить логи, возможно тоже требует JS
- FilePursuit — нужно проверить логи, возможно блокирует

**Почему браузер находит, а мы нет:** TLS fingerprint (JA3) блокируется на уровне TCP, до HTTP; нельзя подделать без замены SSL-стека ОС. HTTP/2 frames — ещё один слой. JS — нужен WebView (50-100 МБ, медленно).

**Варианты для обдумывания:**
1. WebView как скрытый движок для поиска директорий (тяжело, но технически работает — JA3 реального Chrome)
2. Добавить специализированные источники по типу контента: VK Music (требует авторизацию), Jamendo (только CC), SoundCloud (нужен API key)
3. Другой подход: поиск через Telegram-боты (есть боты с индексами mp3)
4. Использовать Common Crawl index (crawl.cc) — есть API, но медленный
5. Принять что аудио-поиск не будет работать без API-ключей и сосредоточиться на книгах/документах где LibGen работает хорошо

**Текущее состояние кода:** всё скомпилировано, Archive работает, остальные источники дают 0 для аудио.

---

### Batch 91 — OD поиск: mmnt.ru + FilePursuit + Yandex; прогрессбар поиска streaming ❌ OD не работает

**Цель:** (1) Добавить mmnt.ru и FilePursuit как специализированные OD-индексы (имеют собственный краулер, не нужен поисковик). (2) Добавить Яндекс HTML как движок (лучше кириллицы, менее агрессивен к ботам). (3) Убрать нерабочие: Bing (timeout 48с), DDG (bot detection всегда 0), 4 из 6 SearXNG-инстансов. (4) Прогрессбар поиска: LinearProgressIndicator вместо CircularProgressIndicator, per-source статус чипы (OD⟳/✓N, Archive⟳/✓N, ...), результаты видны сразу по мере поступления.

**Что сделано:**
- `OpenDirectorySearchUseCase.kt` — полностью переработан:
  - Phase 1 (параллельно): `searchMmntRu()` + `searchFilePursuit()` → прямые ссылки на файлы, без парсинга директорий
  - Phase 2 (параллельно): `searchEngines()` → директории → `parseDirectoryListing()`
  - Цепочка движков: Yandex HTML (новый, первый) → SearXNG (2 инстанса, fallback)
  - Удалены: Bing (`bingClient`), DDG (`searchDdgHtml`), 4 SearXNG инстанса
  - Извлечён `buildAcceptedExts()` — общий хелпер, нет дублирования кода
- `SearchScreen.kt` — InternetSearchTab:
  - `LinearProgressIndicator` (тонкая полоска сверху) вместо полноэкранного `CircularProgressIndicator`
  - Строка статус-чипов `WebSourceChip`: ⟳ пока ищет, ✓N когда нашёл, ✓0 (серый) если пусто
  - Результаты появляются сразу по мере поступления от каждого источника (`weight(1f)` Box)

**Почему нельзя перенять тактику браузера:** TLS fingerprint (JA3) — поисковики блокируют на уровне TCP до HTTP; HTTP/2 SETTINGS-frames — ещё один слой идентификации; JS-рендеринг (Cloudflare) — можно обойти через WebView, но требует 50-100 МБ / главный поток / медленно.

**Проверено:**
- Прогрессбар (LinearProgressIndicator + чипы) — ✅ работает визуально
- LibGen ✓0 для аудио — ✅ ожидаемо (книги, не музыка)
- mmnt.ru — ❌ возвращает 0 результатов (JS или отсутствие в индексе)
- FilePursuit — ❌ возвращает 0 результатов
- Yandex — не протестирован напрямую
- Archive.org — ✅ единственный рабочий источник, но мало современного контента

---

### Batch 90 — Поиск: keyword-типы + multi-type + LibGen + Web Navigator ⚠️ не проверено

**Цель:** (1) Писать "книга" вместо "epub" → автоопределение типа, поиск по всем форматам. (2) Несколько типов одновременно: "книга аудиокнига фильм". (3) Пропуск confirmation card при keyword. (4) LibGen добавлен. (5) Web Navigator — обзор ссылок на странице.

**Что сделано:**
- `QueryParser.kt` — полностью переписан: добавлен `CONTENT_TYPE_WORDS` (~50 слов: книга/music/фильм/...) → `contentHints: Set<String>`; слова типа контента вырезаются из `searchQuery`
- `IntentDetectorUseCase.kt` — fast path 2: `contentHints.isNotEmpty()` → немедленно строит `Set<ContentType>` без DDG. `IntentDetectionResult.contentTypes: Set<ContentType>` + `effectiveTypes` property
- `InternetArchiveSearchUseCase.kt` — принимает `Set<ContentType>`, параллельный поиск по всем типам × языковым вариантам; широкий набор расширений на тип (AUDIO→mp3/flac/ogg/..., BOOK→pdf/epub/fb2/...)
- `OpenDirectorySearchUseCase.kt` — принимает `Set<ContentType>`, `extGroup` = объединение расширений всех типов; `parseDirectoryListing` с объединённым `typeExtensions`
- `LibGenSearchUseCase.kt` — принимает `Set<ContentType>`; пропускает аудио/видео/ПО; принимает все форматы книг когда тип=BOOK
- `WebNavigateUseCase.kt` — новый: загружает URL, извлекает `<a href>` ссылки, определяет файл по расширению. Возвращает `WebNavigatorPage` со списком `WebNavigatorLink`
- `SearchViewModel.kt` — fast path при `contentHints`: сразу в поиск без confirmation card; `runWebSearch(searchQuery, contentTypes)` — общая логика поиска; Web Navigator: `openWebNavigator/webNavigatorNavigateTo/webNavigatorBack/closeWebNavigator`
- `WebNavigatorSheet.kt` — новый: ModalBottomSheet со стеком страниц. Хлебные крошки, список ссылок с иконками (файл=Download, страница=Language), тап по файлу=скачивание, тап по странице=навигация. Progress при загрузке.
- `SearchScreen.kt` — кнопка "Обзор" (глобус) у OD-результатов → открывает Web Navigator с родительской директорией; интеграция `WebNavigatorSheet`
- Новые строки: `web_browse`, `web_no_links`, `web_navigator_files`, `web_navigator_links`, `action_close` (EN + RU)

**Проверить:**
- Набери "книга война и мир" → должен перейти сразу к поиску (без confirmation card), найти PDF/EPUB/FB2 в Archive и LibGen
- Набери "война и мир книга аудиокнига" → должен искать и книги, и аудиокниги одновременно
- Набери "diana ankudinova mp3" → extension fast path, confirmation card (старое поведение)
- В результатах OD нажми иконку глобуса → должен открыться Web Navigator с ссылками из директории
- В Web Navigator тапни ссылку на файл → скачивание; тапни ссылку на страницу → переход с обновлением списка

---

### Batch 89 — Поиск: SearXNG + умные дорки + contentType в движках ⚠️ не проверено

**Цель:** Улучшить качество поиска — "diana ankudinova mp3" давала только 1 результат из Архива. Проблемы: DDG HTML-скрапинг ненадёжен; дорк слабый; фильтр по ключевым словам отбрасывал файлы типа "01_Tishe.mp3" в папке "/diana_ankudinova/"; contentType не передавался в движки.

**Что сделано:**
- `OpenDirectorySearchUseCase.kt`:
  - Добавлен `contentType` параметр
  - Дорк строится умно: AUDIO → `intitle:"index of" (mp3|flac|ogg) "artist"`, VIDEO → `(mp4|mkv|avi)` и т.д.
  - Добавлен **SearXNG** как основной движок (пробуем 3 публичных инстанса: searx.be, search.bus-hit.me, searx.tiekoetter.com) с DDG HTML как fallback
  - Улучшен DDG fallback: 2 дорк-вариации + uddg= извлечение URL
  - **Relaxed keyword filter**: если extension/contentType известен — не фильтруем по ключевым словам в именах файлов (папка найдена поисковиком специально для запроса → все mp3 в ней релевантны)
  - Лимит директорий: 8 → 10
- `InternetArchiveSearchUseCase.kt`:
  - Добавлен `contentType` параметр
  - Применяет `mediatype:audio/movies/texts` по contentType даже без явного расширения в запросе
- `SearchViewModel.kt` — `confirmWebSearch()` передаёт `contentType` в `openDirectorySearch` и `archiveSearch`

**Файлы:** `OpenDirectorySearchUseCase.kt`, `InternetArchiveSearchUseCase.kt`, `SearchViewModel.kt`

---

### Batch 88 — Поиск: confirmation card + прогресс-бар облака ⚠️ не проверено

**Цель:**
1. Глобальный поиск: после ввода запроса и нажатия "Поиск" — сначала определяем тип контента через DuckDuckGo, показываем карточку с резюме (заголовок, краткое описание) и кнопками "Да, искать" / "Нет". При подтверждении — запускаем поиск по трём источникам.
2. Облачная загрузка: исправлена анимация прогресс-бара — раньше показывал статичные 0% на весь процесс. Теперь: animated indeterminate пока не пошли байты, потом плавное заполнение.

**Что сделано:**
- `IntentDetectorUseCase.kt` — возвращает `IntentDetectionResult` (contentType + heading + abstractText из DDG JSON); добавлен `extractField()` для парсинга JSON без зависимостей
- `SearchViewModel.kt` — `WebSearchConfirmation` data class; `searchWeb()` теперь только определяет тип и показывает confirmation; `confirmWebSearch()` запускает реальный поиск; `dismissWebSearchConfirmation()` сбрасывает
- `SearchScreen.kt` — `WebSearchConfirmationCard` composable: иконка типа + label + heading + abstractText + кнопки; вставлена в if/else chain InternetSearchTab
- `ExplorerScreen.kt` — исправлено условие `isIndeterminate`: добавлен `isWaitingForBytes = filePercent==0 && speedBytesPerSec==0 && !isComplete` → bar анимируется пока байты не пошли
- `YandexDiskProvider.kt` — добавлен `producerScope`; per-buffer progress внутри `writeTo()` (каждые 8 буферов = ~512KB) для плавного заполнения бара даже для маленьких файлов
- `strings.xml` (EN + RU) — добавлены `web_confirm_yes/no` + `web_confirm_type_*`

**Файлы:** `IntentDetectorUseCase.kt`, `SearchViewModel.kt`, `SearchScreen.kt`, `ExplorerScreen.kt`, `YandexDiskProvider.kt`, `strings.xml` (оба)

---

### Batch 87 — Breadcrumb "44 МБ / 567 МБ" + облако: скорость + FB2 ✅ проверено

**Цель:** (1) Показывать размер папки / общий размер раздела в breadcrumb. (2) Облако: скорость загрузки в прогресс-баре. (3) FB2 в облаке — эскиз в иконке.

**Что сделано:**
- `ExplorerUiState.kt` — `storageSizeCache: Map<String, Long>`
- `ExplorerViewModel.kt` — `ensureStorageTotalCalculated()`, `getStorageTotalFor()`, `getVolumeRoot()`; `speedBytesPerSec = progress.speedBytesPerSec` в 3 местах upload
- `ExplorerScreen.kt` — LaunchedEffect для вызова ensure..., передача storageTotalSize в FilePanel
- `FilePanel.kt`, `BreadcrumbBar.kt` — параметр storageTotalSize, формат "X / Y"
- `ThumbnailCache.kt` — temp файл теперь сохраняет расширение из cacheKey → FB2 detection работает

**Файлы:** `ExplorerViewModel.kt`, `ExplorerScreen.kt`, `FilePanel.kt`, `BreadcrumbBar.kt`, `ThumbnailCache.kt`, `ExplorerUiState.kt`

---

### Batch 86 — Размер выделенных файлов в дивайдере ✅ проверено — Размер выделенных файлов в дивайдере ✅ проверено

**Цель:** Показывать суммарный размер выделенных файлов прямо в дивайдере между панелями.

**Что сделано:**
- `ExplorerViewModel.getSelectedTotalSizeForPanel(panelId)` — аналог `getSelectedTotalSizeWithFolders()`, но для конкретной панели (TOP/BOTTOM)
- `PanelDivider` — новые параметры `topSelectedSize` / `bottomSelectedSize`, зоны размера между счётчиком файлов и центром (занимают место COPY/MOVE при отсутствии drag)
- `ExplorerScreen.Divider()` — вычисление размеров при `isSelectionMode` + передача в PanelDivider
- Portrait и Landscape поддержаны
- При drag&drop размеры исчезают, показываются COPY/MOVE иконки

**Файлы:** `ExplorerViewModel.kt`, `PanelDivider.kt`, `ExplorerScreen.kt`

---

### Batch 85 — Полная стандартизация прогресс-баров ✅ проверено

**Цель:** Все прогресс-бары в приложении приведены к единому стандарту: скорость (кроме удаления), пофайловый прогресс, кнопка отмены (IconButton с крестиком), визуальный стандарт (6.dp, RoundedCornerShape(3.dp)).

**Что сделано:**
- **Скорость передачи добавлена во все провайдеры:**
  - `FtpClientManager` — speed calc в download/upload
  - `SmbManager` — speed calc в download/upload
  - `WebDavManager` — speed calc в download/upload
  - `DropboxProvider` — speed calc в download/upload/updateFileContent
  - `GoogleDriveProvider` — speed calc в download/upload
  - `YandexDiskProvider` — speed calc в download/upload/updateFileContent
- **Модели данных расширены:**
  - `FtpTransferProgress` — `speedBytesPerSec: Long = 0L`
  - `SmbTransferProgress` — `speedBytesPerSec: Long = 0L`
  - `WebDavTransferProgress` — `speedBytesPerSec: Long = 0L`
  - `CloudTransferProgress` — `speedBytesPerSec: Long = 0L`
  - `CloudTransferEntry` — `bytesTransferred`, `totalBytes`, `speedBytesPerSec`
  - `CloudTransferItem` — `bytesTransferred`, `totalBytes`, `speedBytesPerSec`
- **UI стандартизирован — все TransferProgressCard'ы:**
  - `FtpBrowserTab` — Row(Column + IconButton(Close)), speed + counter + percent, 6.dp bar
  - `SmbBrowserTab` — аналогично FTP (было: Column + TextButton)
  - `WebDavBrowserTab` — аналогично FTP (было: Column + TextButton)
  - `CloudTransferDialog` — speed + counter + percent, 6.dp bar
  - `TransferProgressCard` (BT/WiFi Direct) — 6.dp bar (было: без height/clip)
- **Пофайловый прогресс для папок:**
  - `FileOperationService.executeOperation()` — pre-count файлов, прогресс по каждому файлу
  - `safeCopyDirectory()` — callback `onFileCopied` для каждого файла
  - `executeDelete()` — pre-count, walkBottomUp пофайлово
- **Отмена операций:**
  - `scope.isActive` checks во всех циклах: copy/move, safeCopyDirectory, delete, delete folder, archive 1:1
- **Скорость в FileOperationService:**
  - `executeOperation()` — bytesDone + calcSpeed()
  - `executeDelete()` — bytesDone + calcSpeed()
- **Cloud download цепочка:** `updateTransferProgress()` → `CloudTransferEntry` → `CloudTransferItem` → `TransferRow` — передаются bytes/speed
- **Визуальная унификация всех LinearProgressIndicator → 6.dp + clip(3.dp):**
  - QuickSendOverlay/QuickReceiveOverlay (было 4.dp/2.dp)
  - MediaRemotePanel (было 4.dp/2.dp)
  - StorageAnalysisScreen scan bar (было 4.dp/2.dp)
  - FilePropertiesDialog hash bar (было 4.dp/2.dp)
  - TrashDialog (было 4.dp, без clip)
  - SteganographyScreen (было без height/clip)
  - ComparisonScreen (было без height/clip)
  - SearchScreen index + download (было без height/clip)
  - ArchiveViewerScreen extract bar (было без height/clip)

---

### Batch 84 — Унифицированные прогресс-бары: скорость / счётчик / процент ✅ проверено

**Цель:** Под каждым прогресс-баром в приложении — единый ряд: скорость (слева) | счётчик (по центру) | процент (справа, на уровне конца полосы).

**Что сделано:**
- `ProgressInfoRow.kt` (новый) — переиспользуемый Composable: speed / counter / percent, каждый опциональный
- `OperationProgress` — добавлено поле `speedBytesPerSec: Long = 0`
- **Применено к 14 прогресс-барам:**
  - ExplorerScreen (archiveExtractProgress + operationProgress) — counter + percent
  - TransferProgressCard — speed + counter + percent (заменена bytes/total + speed + ETA строка)
  - SearchScreen DownloadProgressItem — speed + percent (порядок исправлен: speed слева, percent справа)
  - CloudTransferDialog — percent
  - ArchiveViewerScreen — counter + percent
  - FtpBrowserTab — counter (bytes) + percent
  - SmbBrowserTab — counter (bytes) + percent
  - WebDavBrowserTab — counter (bytes) + percent
  - DuplicateDetectorScreen — counter + percent
  - ComparisonScreen — counter + percent
  - MediaRemotePanel (транскод) — percent
  - QuickSendOverlay — speed + counter + percent
  - QuickReceiveOverlay — speed + counter + percent
  - TrashDialog — percent
  - FilePropertiesDialog (хеш) — percent
  - SteganographyScreen — percent

---

### Batch 83 — Неубиваемые файловые операции ✅ проверено

**Цель:** Все операции с прогресс-баром (копирование, перемещение, удаление, архивация, извлечение) продолжают работать после смахивания приложения из рецентов. При возврате в приложение прогресс виден.

**Что сделано:**
- Манифест: `android:stopWithTask="false"` для `FileOperationService`, `TransferService`, `WebDownloadService`
- `onTaskRemoved()` в трёх сервисах — если нет активных операций → stopSelf(), иначе продолжаем
- `OperationHolder.kt` (новый) — static holder для передачи больших списков в сервис (Intent extras ограничен ~500KB)
- `OperationType.EXTRACT` добавлен в enum + строки EN/RU
- `FileOperationService` расширен: новые actions (DELETE, ARCHIVE, ARCHIVE_ONE_TO_ONE, EXTRACT), Hilt EntryPoint для зависимостей, static start-методы
- `ExplorerViewModel`: убран порог `SERVICE_FILE_THRESHOLD`/`SERVICE_SIZE_THRESHOLD`, убран `inlineOperationJob`, убран `runInlineOperation()`. Все copy/move/delete/archive/extract → через `FileOperationService`
- Подписка на прогресс в init{} расширена: DELETE → updateTrashSizeInfo()
- Cloud delete, protected delete, per-file-decision copy/move — остались в VM (зависят от session-scoped managers / per-file conflict resolution)

---

### Batch 82 — Поиск файлов в интернете (второй таб в глобальном поиске) ✅ проверено

**Цель:** Второй таб "Интернет" в глобальном поиске. Три источника: Open Directory (DuckDuckGo), торренты (apibay.org + libtorrent4j, zero upload), Internet Archive. Результаты смешиваются, тап — скачивание в Downloads/Haron/.

**Что сделано:**
- `WebSearchResult` + `SearchSource` — модели (domain/model)
- `Transliterator` — RU→EN транслитерация для двуязычного поиска (common/util)
- `OpenDirectorySearchUseCase` — DuckDuckGo HTML → парсинг Apache/nginx listings → файлы
- `TorrentSearchUseCase` — apibay.org API → JSON → magnet URI
- `InternetArchiveSearchUseCase` — archive.org API → metadata → прямые ссылки
- `TorrentEngine` — singleton, libtorrent4j, zero upload (uploadRateLimit=0, listen_interfaces=""), lazy init
- `WebDownloadService` — Foreground Service (dataSync), HTTP + торрент, wake lock, wifi lock, progress через static StateFlow
- `SearchViewModel` — расширен: selectedTab, webQuery, webResults, searchWeb(), downloadFile(), copyLink()
- `SearchScreen` — PrimaryTabRow: "Устройство" / "Интернет". Device tab = существующий функционал без изменений. Internet tab: поле + кнопка "Искать", результаты с бейджами источника, тап = скачивание, лонг-тап = копировать ссылку, прогресс-бар активных закачек
- AndroidManifest — зарегистрирован WebDownloadService
- ProGuard — keep rules для libtorrent4j
- build.gradle.kts — libtorrent4j зависимости
- strings.xml (EN + RU) — 15 новых строк
- features.txt (EN + RU) — убраны из описания, пойдут на Релиз 3

**Строки для features.txt (Релиз 3):**
```
EN:
- Internet search tab: find and download files from the web [NEW]
- Three sources: Open Directory, Torrent (zero upload), Internet Archive [NEW]
- Tap result to download to Downloads/Haron/ [NEW]
- Long tap result to copy link [NEW]
- Download progress in notification and on search screen [NEW]

RU:
- Поиск в интернете: поиск и скачивание файлов из сети [NEW]
- Три источника: Open Directory, торренты (нулевая раздача), Internet Archive [NEW]
- Тап по результату — скачивание в Downloads/Haron/ [NEW]
- Долгий тап — копирование ссылки [NEW]
- Прогресс скачивания в уведомлении и на экране поиска [NEW]
```

**Фикс релевантности (v2):**
- `QueryParser` — парсинг запроса: извлечение расширения файла и ключевых слов. "it berufe pdf" → ext=pdf, keywords=[it, berufe]
- `OpenDirectorySearchUseCase` — фильтрация по расширению + ключевым словам (файл должен содержать хотя бы одно слово из запроса)
- `TorrentSearchUseCase` — фильтрация по расширению в названии торрента
- `InternetArchiveSearchUseCase` — фильтрация по расширению + mediatype фильтр в API (texts/audio/movies)
- Результаты: OD первыми (прямые ссылки), затем Archive, затем Torrent

---

### Batch 80 — Аудио теги + подгрузка обложки из интернета ✅ проверено

**Цель:** Показывать аудио-теги (Artist, Album, Title и т.д.) в свойствах файла. Подгружать обложку из интернета по тегам и записывать в файл. Умный поиск — один запрос заполняет все недостающие теги + обложку.

**Что сделано:**
- `GetFilePropertiesUseCase` — добавлены `audioMetadata`, `audioTags: AudioTags?`, `hasEmbeddedCover` в `FileProperties`. Методы `buildAudioMetadata()` и `checkEmbeddedCover()`
- `FetchAlbumCoverUseCase` — Deezer `/search/track` + `/album/{id}` (year, genre) → iTunes `entity=musicTrack` fallback. Возвращает все метаданные (title, artist, album, year, genre) + обложку. Сохранение через JAudiotagger
- `SaveAudioTagsUseCase` — запись тегов через JAudiotagger (Title, Artist, Album, Year, Genre)
- `FilePropertiesDialog` — секция "Аудио теги": плоские редактируемые поля (как PropertyRow), обложка, кнопка "Сохранить всё"
- `AudioTagsEditor` — автозаполняет пустые поля при получении результата поиска (LaunchedEffect)
- `AudioCoverSection` — кнопки "Поиск по имени файла" и ручной ввод Artist/Title
- `ExplorerViewModel.saveAllAudioData()` — единый метод, пишет теги → обложку последовательно (без race condition)
- `ThumbnailCache.remove()` + `PanelUiState.thumbnailVersion` — обновление иконок после сохранения обложки
- `app/build.gradle.kts` — JAudiotagger 3.0.1, proguard keep-правила
- Строки в strings.xml (EN + RU): 20 строк для аудио секции

---

### Batch 81 — Метаданные PDF и FB2 в свойствах файла ✅ проверено

**Цель:** Показывать метаданные документов (PDF, FB2) в свойствах файла — аналогично EXIF для фото и тегам для аудио.

**Что сделано:**
- `FileProperties.documentMetadata` — новое поле для метаданных документов
- `buildPdfMetadata()` — через PDFBox: title, author, subject, keywords, creator, producer, creation date, page count
- `buildFb2Metadata()` — XML-парсинг `<description>`: title, author(s), genre, language, series+number, date, annotation (300 символов)
- Поддержка `.fb2.zip` — автоматическое извлечение .fb2 из ZIP
- `FilePropertiesDialog` — секция "Документ" с PropertyRow для каждого поля
- Строки в strings.xml (EN + RU): 14 строк для документов (doc_section, doc_title, doc_author и т.д.)
- features.txt (EN + RU) обновлён

---

### Batch 79 — Поддержка tar/tar.gz/tar.bz2/tar.xz архивов ✅ проверено

**Цель:** Добавить просмотр, извлечение и чтение одиночных entry для tar-форматов (.tar, .tar.gz/.tgz, .tar.bz2/.tbz2, .tar.xz/.txz).

**Что сделано:**
- `BrowseArchiveUseCase` — добавлен `archiveType()` (определяет составные расширения), `browseTar()` (TarArchiveInputStream + декомпрессия)
- `ExtractArchiveUseCase` — добавлен `extractTar()` с двухпроходной логикой (подсчёт + извлечение), `createTarStream()` хелпер
- `ReadArchiveEntryUseCase` — добавлен `readFromTar()` для чтения одиночного entry
- `LoadPreviewUseCase` — добавлен `loadTar()` для QuickPreview
- `FileUtils.kt` — расширен `iconRes()`: добавлены `xz`, `tgz`, `tbz2`, `txz`
- `ContentExtractor.kt` — расширен `ARCHIVE_EXTENSIONS`, добавлен `extractTarEntries()`, обновлён `isArchiveFile()`
- `SearchScreen.kt` — обновлены списки расширений архивов
- `HaronNavigation.kt` — обновлён список расширений для навигации в архив
- `ExplorerScreen.kt` — обновлён `archiveExtsForSelection`
- Все use cases используют общий `BrowseArchiveUseCase.archiveType()` вместо `substringAfterLast('.')`
- `.gtar` расширение — распознаётся как tar-архив
- **Bugfix**: `onIconClick()` не проверял `isArchiveMode` для папок → клик по иконке папки внутри архива вызывал `navigateTo()` вместо `navigateIntoArchive()`, показывая "Путь не существует". Исправлено для всех типов архивов (ZIP/7z/RAR/tar)

---

### Batch 80 — Расширенные контрольные суммы ✅ проверено

**Цель:** Добавить CRC32, SHA-1 и SHA-512 к существующим MD5 и SHA-256 в свойствах файла.

**Что сделано:**
- `CalculateHashUseCase` — добавлены CRC32 (`java.util.zip.CRC32`), SHA-1, SHA-512 в оба метода (`invoke` и `invokeFromUri`)
- `HashResult` — расширен полями `crc32`, `sha1`, `sha512`
- `FilePropertiesDialog` — отображает 5 строк хешей: CRC32, MD5, SHA-1, SHA-256, SHA-512
- `HashRow` — кнопка сравнения (CompareArrows) рядом с копированием, поле ввода между именем и кнопками, цветовая индикация (зелёный = совпадает, красный = не совпадает)
- Текст хеша укорочен на ширину одной кнопки (padding end 28dp)
- `features.txt` EN/RU — обновлено описание

---

### Batch 78 — Кнопка "1 в 1" — индивидуальная архивация ✅ проверено

**Цель:** При выборе нескольких файлов → "Архив" → кнопка "1 в 1" создаёт отдельный ZIP для каждого файла.

#### Что реализовано
- **Кнопка "1 в 1"** в `CreateArchiveDialog`: показывается только при 2+ выбранных файлах, расположена слева от "Отмена". Все три кнопки ("1 в 1", "Отмена", "Создать") в одном ряду.
- **`createArchiveOneToOne()`** в ViewModel: для каждого выбранного файла создаёт отдельный ZIP (имя = имя файла без расширения). При конфликте имён — `findUniqueZipPath()`. Прогресс: файл N из M. Toast с количеством созданных архивов.
- **Локализация**: EN + RU строки (`archive_one_to_one`, `archive_one_to_one_done`).

#### Затронутые файлы
- `presentation/explorer/components/CreateArchiveDialog.kt` — параметр `onOneToOne`, кнопка в ряд с остальными
- `presentation/explorer/ExplorerViewModel.kt` — метод `createArchiveOneToOne()`
- `presentation/explorer/ExplorerScreen.kt` — прокидка callback (2+ файлов)
- `res/values/strings.xml` — 2 строки EN
- `res/values-ru/strings.xml` — 2 строки RU

---

### Batch 77 — Эскизы картинок внутри архивов + настройка кеша ✅ проверено

**Цель:** При навигации внутри архива (ZIP/7z/RAR) показывать превью картинок вместо дефолтной иконки.

#### Что реализовано
- **ReadArchiveEntryUseCase**: чтение байтов одного entry из архива в ByteArray без записи на диск (ZIP/7z/RAR, включая RAR5 через 7-Zip-JBinding). Лимит 10MB на entry.
- **ArchiveThumbnailCache** (@Singleton): двухуровневый кеш — LruCache (memory, 1/8 maxMemory) + disk cache (`cacheDir/archive_thumbs/`). JPEG quality 85, max 256×256, MD5 cache key. Eviction по lastModified при превышении лимита.
- **Настройка в HaronPreferences**: `archiveThumbCacheSizeMb` (default 100, 0 = без лимита).
- **UI интеграция**: FileListItem показывает эскиз для архивных картинок (entry.path содержит `!/` и fileType == "image"). Параметры `archiveThumbnailCache`, `archivePath`, `archivePassword` прокинуты через FilePanel → ExplorerScreen.
- **Settings секция**: "Кеш миниатюр" с иконкой Image, справа — текущий размер кеша. Слайдер 0–500 MB (шаг 50). Кнопка "Очистить кеш".
- **Превью по тапу на иконку**: `resolvePreviewEntry` обрабатывает архивные пути (`!/`). Файлы ≤10MB — через `ReadArchiveEntryUseCase` в память → temp file. Файлы >10MB — через `ExtractArchiveUseCase`. Temp-кеш в `cacheDir/archive_preview/`, 5 мин TTL. Работает для **всех** типов файлов (картинки, текст, PDF, видео, документы).
- **Эскизы для всех типов в архиве**: расширен `isArchivePreviewable` на image, video, audio, text, code, apk, document, pdf. Для не-image типов используется `loadOrGenerateThumbnail()` (извлечение во temp → ThumbnailCache).
- **Аудио-превью (обложка альбома)**: `ThumbnailCache.loadAudioThumbnail()` извлекает embedded album art через MediaMetadataRetriever. Работает и для обычных, и для архивных аудиофайлов.
- **Фикс гонки LaunchedEffect**: добавлен флаг `isArchiveEntry` — архивные записи исключены из `showLocalThumbnail`, предотвращая перезапись bitmap от ArchiveThumbnailCache нулём от ThumbnailCache.
- **APK превью внутри архива**: тап на иконку APK в архиве теперь открывает QuickPreview (иконка приложения + инфо) вместо install dialog.
- **Все файлы в архиве → QuickPreview**: тап на любой файл внутри архива открывает превью вместо внешнего приложения (видеоплеер, галерея и т.д.). Навигация по папкам архива работает как раньше.
- **Локализация**: EN + RU строки.

#### Затронутые файлы
- `domain/usecase/ReadArchiveEntryUseCase.kt` — **новый**
- `common/util/ArchiveThumbnailCache.kt` — **новый**
- `common/util/ThumbnailCache.kt` — добавлен `loadAudioThumbnail()` (обложка альбома)
- `data/datastore/HaronPreferences.kt` — добавлен `archiveThumbCacheSizeMb`
- `presentation/explorer/ExplorerViewModel.kt` — inject ArchiveThumbnailCache
- `presentation/explorer/components/FileListItem.kt` — archive thumbnail loading
- `presentation/explorer/components/FilePanel.kt` — прокидка archiveThumbnailCache
- `presentation/explorer/ExplorerScreen.kt` — прокидка archiveThumbnailCache в оба FilePanel
- `presentation/settings/SettingsViewModel.kt` — inject ArchiveThumbnailCache, методы set/refresh/clear
- `presentation/settings/SettingsScreen.kt` — секция "Кеш миниатюр"
- `res/values/strings.xml` — 4 строки EN
- `res/values-ru/strings.xml` — 4 строки RU

---

### Batch 76 — ANR fix + Sora Editor для больших файлов ✅ проверено

**Цель:** Исправить ANR при открытии/редактировании больших текстовых файлов (600KB+).

#### Что реализовано
- **Просмотр больших файлов**: LazyColumn + чанки вместо BasicTextField. Длинные строки (>4096 символов) разбиваются на куски. Файл 655KB/1 строка открывается за 14мс без ANR.
- **Sora Editor** (библиотека): подключена `io.github.Rosemoe.sora-editor:editor:0.23.6` для редактирования файлов >256KB. Рендерит только видимые строки через Canvas.drawText().
- **Гибридный режим**: файлы ≤256KB → BasicTextField (нативный Compose), >256KB → Sora Editor через AndroidView.
- **Предобработка длинных строк**: строки >400 символов разбиваются по 200 для Sora Editor (word wrap не справляется с 655K-символьной строкой). При сохранении вставленные переносы убираются.
- **TextFieldValue deferred init**: инициализация перенесена из LaunchedEffect в onClick кнопки Edit. Убрана пустая первая рамка при входе в редактор.
- **Undo/Redo для Sora**: реактивные state-переменные `soraCanUndo`/`soraCanRedo`, обновляются при ContentChangeEvent и после undo/redo.
- **Скрытие клавиатуры**: InputMethodManager при выходе из Sora Editor (Android View клава не управляется Compose keyboardController).
- **Визуал Sora**: Material 3 цвета (фон, текст, курсор, выделение), убраны номера строк и разделитель, 10dp внутренние отступы.
- **Отступы читалки**: 2dp слева и справа.

#### Затронутые файлы
- `TextEditorScreen.kt` — полная переработка: view mode (LazyColumn+chunks), edit mode (BasicTextField для маленьких / Sora Editor для больших), keyboard hide
- `build.gradle.kts` — зависимость sora-editor BOM 0.23.6

---

### Batch 75 — Self-copy (дублирование) + Извлечение архивов с пульсирующей иконкой ✅ проверено

**Цель:** Две новых функции в панели выделения.

#### Что реализовано
- **Self-copy (дублирование файлов)**: долгий тап по Copy → диалог с количеством копий и 3 вариантами назначения (подпапка здесь / подпапка в другой панели / открытая папка другой панели). Имена копий: `file(1).ext`, `file(2).ext`. Для каталогов: `dirname(1)`, `dirname(2)`.
- **Извлечение архивов с пульсирующей иконкой**: при выделении только архивов (zip/7z/rar) в обычной папке иконка Archive заменяется на пульсирующую Unarchive. Тап → диалог с выбором куда: рядом с архивом / текущая панель / другая панель. Всегда в подпапку с именем архива.
- **Размер папки при дублировании**: кеш размеров обновляется в реальном времени после каждой копии.
- **Размер корня**: StorageStatsManager (точный размер пользовательского раздела) вместо walkTopDown. Тап на размер в корне → тост с пояснением.
- **Shizuku calculateDirSize**: AIDL метод для подсчёта размера restricted-папок (Android/data, obb).
- **features.txt** — описания дублирования и извлечения архивов (EN + RU).

#### Затронутые файлы
- `ExplorerUiState.kt` — `DialogState.DuplicateDialog`, `DialogState.ExtractArchivesDialog`, `DuplicateDestination`, `ExtractDestination` enums
- `SelectionActionBar.kt` — `onCopyLongClick`, `allSelectedAreArchives`, `onExtractArchives`, пульсация Unarchive
- `ExplorerViewModel.kt` — `showDuplicateDialog()`, `executeDuplicate()`, `showExtractArchivesDialog()`, `executeExtractArchives()`, `invalidateFolderSizeCache()`
- `ExplorerScreen.kt` — `allSelectedAreArchives` вычисление, `DuplicateDialog` + `ExtractArchivesDialog` composables, передача параметров
- `BreadcrumbBar.kt` — `onSizeClick` callback, клик по размеру
- `FilePanel.kt` — `onSizeClick` параметр
- `IShizukuFileService.aidl` — `calculateDirSize(path)` метод
- `ShizukuFileService.kt` — реализация calculateDirSize
- `ShizukuManager.kt` — `isServiceBound()`, `calculateDirSize()` wrapper
- `strings.xml` (EN + RU) — строки для дублирования, извлечения и storage info
- `features.txt` (EN + RU) — описания новых функций

---

### Batch 74 — Принудительный хотспот + авто-подключение по QR ✅ проверено

**Цель:** Передача файлов через хотспот даже когда есть Wi-Fi. Два режима: Wi-Fi (одна сеть) и Точка доступа (разные сети). Для Haron-to-Haron и для Haron-to-любое-устройство.

#### Что реализовано
- **WifiConnector.kt** (новый) — программное подключение к Wi-Fi (API 29+: WifiNetworkSpecifier, API 26-28: addNetwork)
- **TransferViewModel** — `toggleHotspotMode()`, `connectAndDownload()`, автоостановка хотспота при закрытии диалога и при onCleared()
- **QrCodeDialog** — тап по QR переключает Wi-Fi ↔ Точка доступа:
  - Wi-Fi: один QR с URL
  - Точка доступа, таб «Haron»: combined JSON QR (авто-подключение + скачивание)
  - Точка доступа, таб «Другие»: два QR для устройств без Haron (Wi-Fi + URL в браузере)
  - Цвета QR: тёмно-синий на кремовом (уменьшены блики с экрана)
- **TransferScreen** — `tryParseHaronQr()` с поддержкой коротких ключей (h/s/p/u) + автодобавление http://
- **Фиксы:**
  - Хотспот автостоп при «Готово», уходе с экрана, закрытии приложения
  - `.fb2.zip` открывается как книга даже с переименованным файлом (fb2_(1).zip)
  - Папка Downloads/Haron создаётся перед навигацией (фикс «кидает в корень»)
- **AndroidManifest.xml** — `CHANGE_NETWORK_STATE`, `application/x-zip-compressed`
- **features.txt** — обновлено описание передачи (EN + RU)

---

### Batch 73 — Wi-Fi Direct: полноценное P2P-соединение и передача файлов ✅ проверено

**Цель:** Исправить Wi-Fi Direct — `connect()` возвращался до установления P2P-группы, `sendFiles()` пытался открыть TCP сразу → timeout. Теперь полноценный P2P flow.

#### Что реализовано
- **WifiDirectManager** — полный рефакторинг:
  - `_p2pInfo: MutableStateFlow<WifiP2pInfo?>` — отслеживание состояния P2P-соединения
  - `isInitiator` флаг — отличает отправителя от получателя
  - `CONNECTION_CHANGED_ACTION` обработка в BroadcastReceiver — обновляет _p2pInfo, на стороне получателя запускает P2P receive server
  - `connectAndWait(address, timeout)` — suspend, connect() + ожидание groupFormed через StateFlow
  - `startP2pReceiveServer(info)` — group owner слушает ServerSocket(8988), non-owner коннектится к group owner; сокеты эмитятся в `incomingP2pSocket`
  - `sendFiles(info, files)` — принимает `WifiP2pInfo` вместо `hostAddress`/`isGroupOwner`, использует реальный P2P IP
  - `disconnect()` — полная очистка: isInitiator, _p2pInfo, p2pServerJob, removeGroup
  - `incomingP2pSocket: SharedFlow<Socket>` — ReceiveFileManager подписывается
- **TransferRepositoryImpl.sendViaWifiDirect()** — использует `connectAndWait()` + передаёт `WifiP2pInfo` в `sendFiles()`
- **ReceiveFileManager**:
  - Добавлен `WifiDirectManager` в constructor injection
  - Извлечён `handleIncomingSocket(socket)` — единый обработчик для TCP и P2P сокетов (REQUEST, QUICK_SEND, DROP_REQUEST)
  - `p2pJob` — подписка на `wifiDirectManager.incomingP2pSocket` в `ensureListening()`
  - `stopListening()` — отмена p2pJob
- Подробное логирование на каждом шаге P2P flow

#### Изменённые файлы
- `data/transfer/WifiDirectManager.kt` — полный рефакторинг
- `data/repository/TransferRepositoryImpl.kt` — sendViaWifiDirect fix
- `data/transfer/ReceiveFileManager.kt` — handleIncomingSocket extraction + P2P subscription

---

### Batch 72 — SFTP в FTP-таб + WebDAV-браузер ✅ проверено

**Цель:** SFTP через JSch в существующий FTP-таб (переключатель протокола) + WebDAV как новый 4-й таб.

#### Что реализовано
- **SFTP data layer**: `SftpFileInfo.kt`, `SftpClientManager.kt` — JSch Session + ChannelSftp, connect/listFiles/download/upload/mkdir/delete/rename
- **FtpViewModel** модифицирован: `isSftp` в state, `SftpClientManager` + `SshCredentialStore` в конструктор, ветвление во всех операциях (connect, loadFiles, download, upload, mkdir, delete, rename, disconnect)
- **FtpBrowserTab** модифицирован: список серверов показывает FTP/SFTP/FTPS метки протокола, manual connect dialog с переключателем FTP/SFTP + автосмена порта (21⇄22)
- **FtpAuthDialog** модифицирован: в SFTP-режиме скрывает FTPS checkbox и кнопку Anonymous, заголовок показывает "SFTP — host:port"
- **WebDAV data layer**: `WebDavFileInfo.kt`, `WebDavCredential.kt`, `WebDavCredentialStore.kt` (AES-256-GCM), `WebDavManager.kt` (OkHttp PROPFIND/GET/PUT/MKCOL/DELETE/MOVE + XmlPullParser)
- **WebDavViewModel**: полная копия структуры FtpViewModel — URL-based навигация, breadcrumbs, download/upload/mkdir/delete/rename, dual-panel с local files
- **WebDavBrowserTab**: auth dialog (URL + user + password), server list, dual-panel layout, file operations
- **TransferScreen**: 4 таба (Transfer, SMB, S(FTP), WebDAV), BackHandler для tab 3, TopBar навигация для tab 3, toast collector для WebDAV
- **Строки**: EN + RU для webdav_tab_title, webdav_connect, webdav_disconnect, webdav_url_hint, webdav_saved_servers, webdav_add_server, webdav_connection_error
- **Константы**: WEBDAV_CREDENTIAL_FILE, WEBDAV_CREDENTIAL_KEYSTORE_ALIAS
- FTP tab title переименован в "S(FTP)"
- Сохранённые SFTP-серверы хранятся в SshCredentialStore (уже существовал), FTP — в FtpCredentialStore

#### Новые файлы
- `data/sftp/SftpFileInfo.kt`
- `data/sftp/SftpClientManager.kt`
- `data/webdav/WebDavFileInfo.kt`
- `data/webdav/WebDavCredential.kt`
- `data/webdav/WebDavCredentialStore.kt`
- `data/webdav/WebDavManager.kt`
- `presentation/transfer/WebDavViewModel.kt`
- `presentation/transfer/components/WebDavBrowserTab.kt`

#### Изменённые файлы
- `presentation/transfer/FtpViewModel.kt` — +SFTP поддержка
- `presentation/transfer/components/FtpBrowserTab.kt` — +SFTP в UI
- `presentation/transfer/components/FtpAuthDialog.kt` — +SFTP режим
- `presentation/transfer/TransferScreen.kt` — +WebDAV tab
- `common/constants/HaronConstants.kt` — +WebDAV константы
- `res/values/strings.xml` — +WebDAV строки, ftp_tab_title → "S(FTP)"
- `res/values-ru/strings.xml` — +WebDAV строки, ftp_tab_title → "S(FTP)"

---

### Batch 71 — Shizuku: файловые операции + Android/media ✅ проверено

**Цель:** Включить копирование/вставку/удаление/переименование в Android/data, Android/obb, Android/media через Shizuku. Ранее Shizuku использовался только для чтения (listFiles).

#### Что реализовано
- **Android/media** добавлена в `isRestrictedAndroidPath()` (FileRepositoryImpl) и `isRestrictedAndroidDir()` (ExplorerViewModel) — теперь файлы в `/Android/media` читаются через Shizuku, как data и obb
- **AIDL расширен**: `IShizukuFileService` — добавлены `isDirectory`, `copyFile`, `copyDirectoryRecursively`, `deleteRecursively`, `renameTo`, `mkdirs`
- **ShizukuFileService** — реализация всех новых методов (UID 2000 / shell)
- **ShizukuManager** — обёртки для всех новых IPC-методов + `exists()`, `isDirectory()`
- **FileRepositoryImpl**:
  - `copyFilesWithResolutions` — ветка File→File: если src или dst restricted → Shizuku copy
  - `moveFilesWithResolutions` — аналогично, Shizuku renameTo + fallback copy+delete
  - `deleteFiles` — restricted paths → `shizukuManager.deleteRecursively()`
  - `renameFile` — restricted paths → `shizukuManager.renameTo()`
  - `createDirectory` — restricted paths → `shizukuManager.mkdirs()`
  - `resolveConflictViaShizuku()` — разрешение конфликтов имён через Shizuku `exists()`
  - Валидация dest dir пропускает `File.isDirectory` для restricted paths (FUSE блокирует)

---

### Batch 70 — DnD Copy/Move полоска на дивайдере ✅ проверено

**Цель:** При перетаскивании файлов между панелями — на дивайдере появляется полоска Copy | Move для выбора операции.

#### Что реализовано
- `DragOperation` enum (COPY, MOVE) + поле `dragOperation` в `DragState.Dragging`
- `DragDividerStrip` — overlay-полоска поверх дивайдера (portrait: горизонтальная, landscape: вертикальная)
- Левая/верхняя половина = Copy (tertiary цвет), правая/нижняя = Move (primary цвет)
- Активная зона alpha 1.0, неактивная 0.3
- Haptic при смене зоны на полоске
- Иконка операции (Copy/Move) в DragOverlay-призраке
- `executeDragCopy()` + `executeDragCopyWithDecisions()` — аналоги drag move для копирования
- `endDrag()` — ветвление: same-panel folder = всегда MOVE, cross-panel = по выбору на полоске
- `executeWithDecisions()` — обработка COPY с panel IDs (DnD case)
- Строки: `dnd_copy` / `dnd_move` (EN + RU)

---

### Batch 69 — Bluetooth HID пульт: доработка ✅ проверено

**Цель:** Довести BT HID пульт до рабочего состояния — подключение, клавиатура, пунктуация, UX.

#### Подключение
- Переделан flow: вместо "make discoverable + ждать" → показ списка спаренных устройств + подключение с телефона
- `connect()` для переподключения к уже спаренным устройствам
- `requestBtDiscoverable()` — fallback для первого сопряжения
- `replyReport()` в onGetReport + `reportError(SUCCESS)` в onSetReport — хост не отключается
- Фикс `init()`: re-registration при `hidDevice!=null` но `isRegistered=false`

#### BtDevicePickerDialog — полная переделка
- Список спаренных устройств (переподключение в 1 тап)
- Пошаговая инструкция для первого сопряжения (шаги 0-4)
- Предупреждение (красным): удалить старое сопряжение перед HID-подключением
- Упоминание что устройство может показаться как "Неизвестное устройство"
- Индикатор discoverable-режима (спиннер + текст)

#### VirtualKeyboardPanel — клавиатура
- Фикс backspace: KEYCODE_DEL=67 (было 8=KEYCODE_1)
- Фикс Enter: KEYCODE_ENTER=66 (было 13=ASCII CR)
- Smart diff: `commonPrefix` вместо `substring(length)` — голосовой ввод корректно обрабатывает вставку/замену в середине текста
- Расширяемое поле ввода: min 40dp → max 160dp (~8 строк), потом прокрутка
- Убран placeholder-текст (достаточно мигающего курсора)
- Кнопка "Очистить текст" (корзина, красная) — Ctrl+A + Backspace на удалённом устройстве

#### Кириллица и пунктуация
- Полная карта ЙЦУКЕН → QWERTY (32 буквы + ё) в `cyrillicToHid()`
- Автоопределение режима (`isRussianMode`) по последним введённым буквам
- `russianPunctuationToHid()` — пунктуация по позициям русской раскладки (`.`→`/`, `,`→Shift+`/`, `?`→Shift+7, и т.д.)
- Без этого: `.` → `ю`, `,` → `б`, `?` → `,` на русской раскладке хоста

#### Производительность и надёжность
- `keyboardMutex` — сериализация HID keyboard-событий (backspace/текст не теряются при массовом удалении)
- `sendClearAll()` — Ctrl+A + Backspace через HID (мгновенная очистка вместо N backspaces)
- `imePadding()` на CastOverlay — панель поднимается над системной клавиатурой

#### Модифицированные файлы
| Файл | Изменение |
|------|-----------|
| BluetoothHidManager.kt | Cyrillic mapping, Russian punct, Mutex, replyReport, ClearAll |
| BtDevicePickerDialog.kt | Paired devices + step-by-step instructions |
| VirtualKeyboardPanel.kt | Smart diff, expandable field, clear button |
| CastOverlay.kt | imePadding, reduced bottom padding |
| CastViewModel.kt | connectBtHidToDevice() |
| RemoteInputEvent.kt | +ClearAll event |
| DlnaManager.kt | +ClearAll branch (no-op) |
| GoogleCastManager.kt | +ClearAll branch (no-op) |
| strings.xml (EN + RU) | BT HID strings + clear text |

---

### Batch 68 — FTP-сервер + FTP-клиент ✅ проверено

**Цель:** Полноценная FTP-функциональность: встроенный FTP-сервер (раздавать файлы) + FTP-клиент (подключаться к удалённым серверам).

#### Зависимости
- `org.apache.commons:commons-net:3.11.1` — FTP-клиент
- `org.apache.ftpserver:ftpserver-core:1.2.0` — встраиваемый FTP-сервер

#### Data layer — FTP Client
- **FtpCredential** — модель учётных данных (host, port, username, password, useFtps)
- **FtpFileInfo** — модель файла (name, isDirectory, size, lastModified, path, permissions) + `toFileEntry()` для Explorer
- **FtpTransferProgress** — прогресс загрузки/скачивания
- **FtpPathUtils** — парсинг ftp:// путей (parseHost, parsePort, parseRelativePath, buildPath, getParentPath, isRoot, connectionKey)
- **FtpCredentialStore** — шифрованное хранилище (AES-256-GCM + Android Keystore), файл `ftp_credentials.enc`
- **FtpClientManager** — `@Singleton`, пул соединений (ConcurrentHashMap + Mutex), passive mode, binary transfer. Методы: connect, listFiles, downloadFile, uploadFile, createDirectory, delete, rename, disconnect, autoReconnect

#### Data layer — FTP Server
- **FtpServerConfig** — настройки (port, anonymous, username, password, readOnly)
- **FtpServerManager** — `@Singleton`, Apache FtpServer. Port fallback 2121→2131, passive ports 50000-50100. IP через HttpFileServer
- **HaronFtplet** — логирование событий сервера (login, upload, download, delete, mkdir)

#### Presentation — FTP Client
- **FtpViewModel** — dual-panel (FTP сверху, локальные снизу), connect/browse/download/upload/createFolder/delete/rename
- **FtpAuthDialog** — host, port, username, password, FTPS checkbox, save credentials
- **FtpBrowserTab** — вкладка в TransferScreen: сохранённые серверы + браузер файлов

#### Presentation — FTP Server
- **FtpServerViewModel** — start/stop сервер, настройки (port, anonymous, readOnly, credentials)
- **FtpServerSection** — UI карточка в Transfer tab: toggle, URL, настройки

#### Интеграция
- **TransferScreen** — 3-я вкладка "FTP" + FTP Server секция в Transfer tab
- **TransferService** — ACTION_START_FTP_SERVER / ACTION_STOP_FTP_SERVER (foreground + wake lock)
- **ExplorerViewModel** — навигация по ftp:// путям в панелях (navigateTo), skip size calc для ftp://

#### Новые файлы (11)
| Файл | Назначение |
|------|-----------|
| data/ftp/FtpCredential.kt | Модель учётных данных |
| data/ftp/FtpFileInfo.kt | Модели файла + прогресса + toFileEntry() |
| data/ftp/FtpPathUtils.kt | Парсинг ftp:// путей |
| data/ftp/FtpCredentialStore.kt | Шифрованное хранилище |
| data/ftp/FtpClientManager.kt | Фасад FTP-клиента |
| data/ftp/FtpServerManager.kt | Встроенный FTP-сервер |
| data/ftp/HaronFtplet.kt | Логирование событий сервера |
| presentation/transfer/FtpViewModel.kt | VM клиента |
| presentation/transfer/FtpServerViewModel.kt | VM сервера |
| presentation/transfer/components/FtpAuthDialog.kt | Диалог подключения |
| presentation/transfer/components/FtpBrowserTab.kt | Вкладка FTP-браузера |

---

### Batch 67 — Батарейные оптимизации ✅ проверено

**Цель:** Снизить расход батареи — idle timeout для сервисов, оптимизация поллинга, пропуск лишней работы.

#### CastMediaService
- Idle watchdog: проверка каждые 60 сек, если 15 мин без активности → stopSelfAndCleanup()
- `lastActivityTime` обновляется при play/pause и updatePlayingState
- Wake lock 30 мин остаётся как safety net

#### TransferService
- Wake lock отпускается при паузе (`pauseTransfer()` → `releaseWakeLock()`)
- Wake lock снова берётся при возобновлении (`resumeTransfer()` → `acquireWakeLock()`)
- Idle watchdog: 15 мин без обновления прогресса → cancel transfer
- `touchActivity()` вызывается через companion `updateProgress()`

#### FileOperationService
- Wake lock сокращён с 60 мин до 30 мин
- Progress-idle watchdog: 5 мин без прогресса → лог предупреждения, 15 мин → cancel операции
- `lastProgressTime` обновляется при каждом файле в цикле

#### ScreenMirrorService
- `activeClients` (AtomicInteger) — счётчик HTTP-клиентов `/frame` и `/mjpeg`
- Когда клиентов нет → ImageReader listener пропускает JPEG compress (`image.close()` без обработки)
- Экономит CPU когда никто не смотрит зеркалирование

#### DlnaManager
- Интервал поллинга увеличен с 2 до 3 сек
- Заменены orphan `CoroutineScope(Dispatchers.IO)` на class-level `scope` (structured concurrency)

#### UsbStorageManager
- Интервал поллинга увеличен с 30 до 60 сек (BroadcastReceiver остаётся основным)

---

### Batch 66 — Мульти-аккаунт для облачных хранилищ ✅ проверено

**Цель:** Поддержка нескольких аккаунтов одного облачного провайдера (например, два Google Drive аккаунта).

#### Backend
- **CloudTokenStore** — ключ хранилища `"gdrive"` → `"gdrive:alice@gmail.com"`. Новые методы: `saveByKey`, `loadByKey`, `removeByKey`, `getAllAccounts`, `getAccountIds`. Автомиграция старого формата.
- **CloudAccount** — добавлено поле `accountId: String` (`"gdrive:alice@gmail.com"`)
- **CloudProviderInterface** — `handleAuthCode` возвращает `Result<String>` (accountId) вместо `Result<Unit>`
- **GoogleDriveProvider, DropboxProvider, YandexDiskProvider** — `tokenKey: String` в конструкторе. Pending-key flow при OAuth: сохранить временно → получить email → сохранить с финальным ключом
- **CloudManager** — две карты: `authProviders` (один на тип, для OAuth) и `accountProviders` (один на аккаунт, для данных). Все методы принимают `accountId: String` вместо `CloudProvider`. Новый `CloudPath` data class с backward-compatible destructuring
- **CloudPath** — `data class CloudPath(provider, path, accountId)` — `component1()=provider`, `component2()=path` для совместимости с существующими деструктуризациями
- **Use cases** — все 5 use cases обновлены: `CloudProvider` → `String` (accountId)
- **HttpFileServer** — `CloudStreamConfig.provider` → `accountId` + computed `providerScheme`

#### ViewModel / UI
- **ExplorerViewModel** — `navigateToCloud(accountId)`, `cloudSignOut(accountId)`, все ~30 мест с `parseCloudUri` обновлены на `parsed.accountId`
- **ExplorerScreen** — callbacks `onSignOut` и `onNavigateToCloud` принимают `String` (accountId)
- **TextEditorScreen** — cloud save использует `parsed.accountId`
- **CloudAuthDialog** — полная переделка: группировка по провайдерам, dropdown (▼) для 2+ аккаунтов, кнопка "+ Добавить" с выпадающим меню провайдеров
- **DrawerMenu** — key по `accountId`, onClick по `accountId`, `onNavigateToCloud: (String)`

#### URI формат
- `cloud://gdrive/path` — backward compat (первый аккаунт GDrive)
- `cloud://gdrive:alice@gmail.com/path` — конкретный аккаунт

---

### Batch 65 — Удаление OneDrive + исправление QR-сканера ✅ проверено

#### Удаление OneDrive
- **OneDrive никогда не работал** — CLIENT_ID = placeholder "YOUR_ONEDRIVE_CLIENT_ID"
- Удалён `OneDriveProvider.kt` целиком
- Удалён `ONEDRIVE` из enum `CloudProvider`
- Убраны все ссылки из `CloudManager`, `CloudOAuthHelper`, `CloudAuthDialog`, `DrawerMenu`, `HttpFileServer`, `CloudProviderInterface`
- Удалены строки `cloud_onedrive` из `strings.xml` (EN + RU)
- Обновлены `features.txt` (EN + RU) — убран OneDrive из списка провайдеров

#### Исправление QR-сканера
- **Проблема**: камера не инициализировалась при вызове через долгий тап — `Dialog {}` создаёт отдельную субкомпозицию, `LocalLifecycleOwner.current` внутри Dialog возвращает lifecycle `DialogWrapper` (не Activity), CameraX `bindToLifecycle()` привязывалась к нему
- **Решение**: `lifecycleOwner` захватывается **до** `Dialog {}` (на уровне Activity) и передаётся параметром в `CameraPreviewWithScanner`
- Добавлено логирование через `EcosystemLogger`: открытие сканера, получение камеры, распознание кода, ошибки привязки

---

### Batch 64 — Cloud upload reliability + crash fix ✅ проверено

**Проблема:** Большие файлы (140-440MB) падали с ConnectionResetException через ~40 секунд у всех провайдеров. Root cause — сетевое оборудование (роутер/ISP) убивает долгие upload-соединения.

#### Общая инфраструктура
- **Sequential upload mutex** (`cloudUploadMutex` — `kotlinx.coroutines.sync.Mutex`): параллельные PUT-запросы вызывали массовый ConnectionReset у всех провайдеров одновременно. 3 обёртки `withLock {}` вокруг `cloudManager.uploadFile()` в `cloudUploadFromLocal`, `executeDragCloudUpload`, `executeDragCloudUploadWithDecisions`
- **OkHttp 4.12.0**: добавлен в зависимости (`build.gradle.kts`) — лучше connection pooling, keepalive, HTTP/2, write timeout
- **ConcurrentHashMap вместо mutableMapOf**: `cloudTransferJobs` заменён на `ConcurrentHashMap` — при нажатии «Отмена» (main thread) во время upload (IO thread вызывает `.remove()`) был `ConcurrentModificationException` с крашем

#### Google Drive ✅ проверено
- **Retry на ANY IOException**: раньше проверял только "reset"/"refused"/"timeout" в тексте ошибки — пропускал `IOException: unexpected end of stream`. Теперь: `if (e is java.io.IOException)` без string matching
- **10MB chunks** (было 2MB), **3 retries** (было 2), **exponential backoff** (2с, 4с)
- **`driveService = null`** при ретрае — force re-init на случай stale connection

#### Yandex Disk ✅ проверено
- **Content-Range chunked upload**: полная замена одного PUT на чанки по 10MB. Каждый чанк — отдельный HTTP-запрос с заголовком `Content-Range: bytes start-end/total`. При падении ретраится только этот чанк (до 5 попыток). Реализовано в `uploadFile()` и `updateFileContent()`
- **OkHttp для чанков**: `uploadClient` (companion object) — `writeTimeout(10 мин)`, `readTimeout(10 мин)`, `connectTimeout(60с)`, `retryOnConnectionFailure(true)`
- **RandomAccessFile для seek**: при ретрае чанка — `raf.seek(offset)` вместо создания нового потока + skip
- **MIME-type throttling bypass**: Яндекс троттлит .mp4/.avi/.mkv/.zip/.rar/.7z до ~128KB/s. Обход: загрузка как `.tmp` → rename через `GET /resources/move?from=...&path=...&overwrite=true`
- **User-Agent "Haron/1.0"**: явный заголовок — Яндекс блокирует/троттлит unknown clients
- **Старый `uploadFileAttempt()`** удалён — логика чанков inline в каждом методе

#### Dropbox ✅ проверено
- **8MB chunks** (было 4MB, рекомендация Dropbox для стабильности)
- **Empty session start**: `uploadSessionStart().uploadAndFinish(ByteArrayInputStream(ByteArray(0)), 0)` — все данные через append, легче ретраить при обрыве
- **Per-chunk retry** (до 5 попыток): новый `FileInputStream(file)` + `fis.skip(offset)` на каждую попытку (старый поток exhausted)
- **IncorrectOffsetError handling**: два catch-блока:
  - `UploadSessionAppendErrorException` → `errorValue.isIncorrectOffset` → `getIncorrectOffsetValue().correctOffset`
  - `UploadSessionFinishErrorException` → `errorValue.isLookupFailed` → `getLookupFailedValue().isIncorrectOffset` → `correctOffset`
  - Корректирует offset когда сервер получил данные, но HTTP-ответ потерялся
- Реализовано в обоих методах: `uploadFile()` и `updateFileContent()`

#### Файлы изменены
| Файл | Изменения |
|------|-----------|
| `app/build.gradle.kts` | +OkHttp 4.12.0 |
| `YandexDiskProvider.kt` | Content-Range chunked upload, OkHttp client, .tmp rename, удалён `uploadFileAttempt()` |
| `DropboxProvider.kt` | 8MB chunks, empty session start, per-chunk retry, IncorrectOffsetError |
| `GoogleDriveProvider.kt` | Retry ANY IOException, 10MB chunks, 3 retries, backoff |
| `ExplorerViewModel.kt` | `cloudUploadMutex` (Mutex), `ConcurrentHashMap` |

#### Коммиты
- `7f1f3f0` — retry 2→3, exponential backoff, keep-alive
- `0ff3a86` — sequential cloud uploads via Mutex + flush
- `020ff32` — always fixed-length streaming
- `3c0a4ef` — switch to OkHttp for Yandex uploads
- `e93cc8c` — GDrive retry on ANY IOException, 10MB chunks
- `dd71c75` — Yandex Content-Range chunked + Dropbox per-chunk retry
- `379b00a` — ConcurrentModificationException fix (ConcurrentHashMap)

---

### Batch 63 — Яндекс: upload reliability + childCount + HttpFileServer streaming ✅ проверено

**Что сделано:**
- **childCount для папок**: параллельные `GET /resources?path=...&limit=0` запросы для получения `_embedded.total` — раньше все папки показывали "0 элементов"
- **HttpFileServer Yandex streaming**: добавлен case `"yandex"` в `when(config.provider)` — двухшаговая загрузка (temp URL), правильный auth prefix `"OAuth"` вместо `"Bearer"`, `needsAuth` исключает Yandex (temp URL self-authenticated)
- **downloadCloudThumbnail auth**: добавлена авторизация для Yandex (`OAuth` prefix), увеличение размера thumbnail (`size=XXXL`)
- **adaptCloudPreview**: конвертация `ImagePreview` → `VideoPreview`/`AudioPreview` для облачных медиа-файлов (thumbnail всегда JPEG)

---

### Batch 62 — Скорость облачных трансферов + превью фиксы ✅ проверено

**Что сделано:**
- **channelFlow вместо flow**: `downloadFile/uploadFile/updateFileContent` в `YandexDiskProvider` переведены с `flow { emit() }` на `channelFlow { trySend() }` — `trySend()` не блокирует write-loop (upload глох на 6-7% из-за `emit()` приостанавливающего запись)
- **Буферы 8KB→256KB** во всех облачных провайдерах (Yandex, Dropbox, GDrive) — ~32x меньше syscalls
- **readTimeout 120s→300s** для больших файлов в Yandex
- **CancellationException re-throw**: добавлен `if (e is CancellationException) throw e` в 8 catch-блоках облачных операций — фантомный прогресс-бар после отмены
- **Превью фикс расширений**: thumbnail всегда сохраняется с `.jpg` (раньше с оригинальным расширением `.pdf`/`.docx` → LoadPreviewUseCase пытался парсить JPEG как PDF)
- **Яндекс thumbnail размер**: `size=S` (150px) → `size=XL` (800px) в списке, `size=XXXL` (1280px) в QuickPreview
- **Текст/код на GDrive/Yandex**: полная загрузка файла вместо бесполезного thumbnail

---

### Batch 61 — Параллельные облачные трансферы + OOM fix ✅ проверено

**Что сделано:**
- **OOM fix**: `setFixedLengthStreamingMode(totalSize)` в обоих upload-методах `YandexDiskProvider` — больше не буферит всё тело запроса в памяти
- **Параллельные трансферы**: полная миграция с единого `cloudTransferJob` на `cloudTransferJobs: MutableMap<String, Job>`
  - `launchCloudTransfer(fileName, isUpload) { transferId -> ... }` — для одиночных файлов (CloudTransfer диалог)
  - `launchCloudJob { ... }` — для batch-операций (operationProgress)
  - Все 13 call sites мигрированы, compat alias удалён
  - Новый трансфер НЕ отменяет предыдущие — работают параллельно
- **CloudTransferDialog**: стэкированные прогресс-бары (2dp gap), кнопка × на каждом трансфере при мульти-режиме
- **Индивидуальная отмена**: `cancelSingleCloudTransfer(transferId)` — отменяет конкретный трансфер, остальные продолжают
- Строки: `cloud_transferring` (EN/RU) для смешанных upload+download

---

### Batch 60 — Яндекс Диск (облачный провайдер) ✅ проверено

**Что сделано:**
- Добавлен `YANDEX_DISK("yandex", "Yandex Disk")` в `CloudProvider` enum
- Создан `YandexDiskProvider` — полная реализация `CloudProviderInterface` через REST API (без SDK):
  - OAuth PKCE авторизация через `oauth.yandex.com` (с scopes `cloud_api:disk.read/write/info`)
  - `listFiles()` — пагинация, childCount для папок, thumbnails (preview URL из API)
  - `downloadFile()` — двухшаговый: получить temp URL → GET с progress
  - `uploadFile()` — двухшаговый: получить upload URL → PUT с progress + `setFixedLengthStreamingMode`
  - `delete()` — удаление в корзину
  - `createFolder()`, `rename()`, `moveFile()` — через `/resources` и `/resources/move`
  - `updateFileContent()` — upload с overwrite=true
  - `withRefresh{}` — перехват 401 → refresh token → повтор
  - `getFreshAccessToken()` — для proxy-streaming
  - User info: `GET /` → login, display_name
  - Логирование EcosystemLogger на каждом шаге
- Зарегистрирован в `CloudManager.init{}`, `CloudOAuthHelper` (redirect URI), `CloudAuthDialog` (when branch), `DrawerMenu` (when branch)
- Превью Яндекс-файлов: auth header `OAuth <token>` → `ThumbnailCache.loadFromUrl(authHeader)` → `FileListItem` + `FilePanel`
- Строки: `cloud_yandex_disk` в EN и RU strings.xml
- Deep link `haron://oauth/yandex` — работает через существующий intent-filter (wildcard path)
- Все универсальные механизмы (use cases, DnD, cloud:// навигация, токен-стор) работают автоматически

---

### Batch 59 — Grid long press: выделение вместо DnD ✅ проверено

**Проблема:** В grid-режиме long press на иконку невыделенного файла сразу активировал DnD при движении пальца, убивая свайп-выделение диапазона.

**Исправление:** `FilePanel.kt` строка ~1206 — заменил `samePanelDragActivated = false` на `isSamePanelDrag = false` + `dragStartIndex = index`. Теперь при движении пальца `onDrag` попадает в ветку range selection → соседние файлы выделяются свайпом.

---

### Batch 58 — Облачные превью в сетке + фикс PDF/fb2 превью ✅ проверено

**Что сделано (Batch 58):**

**1. Облачные превью в сетке — ВСЕ форматы:**
- `ThumbnailCache.loadFromUrl()` — загрузка bitmap из URL (для cloud thumbnail-картинок)
- `ThumbnailCache.loadCloudThumbnail()` — скачивание файла во temp → генерация thumbnail → удаление temp (для PDF, текст, код, документы, APK)
- Dropbox `enrichWithThumbnails` расширен: temp links для ВСЕХ previewable типов (images, PDF, text, code, documents, APK), лимит 10МБ/файл, до 50 файлов
- Google Drive: `thumbnailLink` и fallback URL дополнены `access_token` → работают без доп. авторизации
- `FileListItem`: Google Drive → `loadFromUrl` (URL = thumbnail-картинка); Dropbox images → `loadFromUrl`; Dropbox non-images → `loadCloudThumbnail` (URL = полный файл → скачать + сгенерировать)

**2. Фикс PDF превью — конфликт вложенных пейджеров:**
- Убран внутренний `HorizontalPager` для страниц PDF в `QuickPreviewDialog`
- PDF теперь показывает только первую страницу + счётчик "N страниц"
- Свайп всегда листает между файлами, а не между страницами PDF

**3. Фикс fb2 превью для облачных файлов:**
- Добавлен тип `"document"` в previewable types в `resolvePreviewEntry`
- Облачные fb2/docx/odt/doc/rtf теперь скачиваются в cache перед превью

**4. Фикс ошибки QuickPreview при листании:**
- `preloadJobs` — трекинг preload-джобов, отмена при смене страницы (предотвращает OOM от множественных параллельных decoding)
- try/catch в `preloadPreview` — ошибки preload не крашат корутину
- Atomic write в `resolveCloudArchiveForPreview` — temp file + rename (race condition при concurrent downloads)
- Atomic write в `resolvePreviewEntry` thumbnail download — temp file + rename

**Изменённые файлы:**
- `common/util/ThumbnailCache.kt` — `loadFromUrl()` + `loadCloudThumbnail()`
- `presentation/explorer/components/FileListItem.kt` — cloud thumbnail для всех типов
- `presentation/explorer/components/QuickPreviewDialog.kt` — убран вложенный PDF пейджер
- `presentation/explorer/ExplorerViewModel.kt` — preloadJobs, atomic writes, "document" в previewable types
- `data/cloud/provider/GoogleDriveProvider.kt` — `access_token` в thumbnailUrl
- `data/cloud/provider/DropboxProvider.kt` — `enrichWithThumbnails` расширен на все previewable типы

---

### Batch 57 — Финализация Dropbox провайдера ✅ проверено (проценты)

**Что сделано (Batch 57):**

**Батч 1: Token Refresh + пагинация + API ключ**
- API ключ: `w16lhavuph6eee8` (был placeholder)
- `refreshToken()` — POST к `oauth2/token` с `grant_type=refresh_token`, сохраняет новый accessToken
- `withRefresh()` — обёртка над API-вызовами, ловит `InvalidAccessTokenException` → refresh → retry
- `withRefreshSuspend()` — suspend-вариант для download/upload (где block сам suspend)
- `getFreshAccessToken()` — принудительный refresh для стриминга/прокси
- Пагинация `listFiles()`: `while (result.hasMore) { listFolderContinue(cursor) }` — папки >500 файлов

**Батч 2: moveFile**
- `moveFile()` реализован: извлекает имя файла из пути, строит `toPath = "$newParentId/$fileName"`, вызывает `moveV2`
- Работает для same-panel DnD в облаке

**Батч 3: Прогресс download/upload**
- Download: `downloader.inputStream` + ручное чтение буфером 8KB + emit каждый процент
- Upload: `CountingInputStream` (FilterInputStream + AtomicLong) + async upload + polling каждые 200мс
- `updateFileContent` — тот же подход с CountingInputStream

**ID → path**: Dropbox использует пути как идентификаторы (не внутренние ID), исправлен `toCloudFileEntry()` — `id = pathDisplay`

**Батч 4: UI прогресс-бара + chunked upload**
- Прогресс-бар: формат `filename... (1/5) 42%` — счётчик перед процентами, имя обрезается
- Dropbox chunked upload: файлы >150МБ → upload sessions кусками по 4МБ с прогрессом после каждого chunk
- `updateFileContent` — тот же chunked подход для больших файлов

**Батч 5: Dropbox streaming + preview**
- HTTP-прокси (`HttpFileServer.kt`): добавлен Dropbox — POST к `content.dropboxapi.com/2/files/download` с `Dropbox-API-Arg` header
- Preview (`resolvePreviewEntry`): для cloud-файлов без thumbnailUrl (Dropbox) — скачивание в cache для image/text/code/pdf
- Видео/аудио из Dropbox теперь стримится через локальный прокси (как GDrive)

**Батч 6: Открытие файлов + превью-иконки Dropbox**
- Видео: HTTP-прокси для Dropbox через `get_temporary_link` (direct URL с Range support)
- Архивы: `cloudDownloadAndNavigateArchive()` — скачивает и навигирует внутрь архива
- fb2/fb2.zip: `cloudDownloadAndOpenDocument()` — скачивает и открывает в DocumentViewer
- PDF: `cloudDownloadAndOpenPdf()` — скачивает и открывает в PDF reader
- Превью-иконки: `enrichWithThumbnails()` — после listing, parallel `getTemporaryLink` для image-файлов (до 50)
- Preview: для cloud без thumbnailUrl — скачивание в cache для image/text/code/pdf

**Изменённые файлы:**
- `data/cloud/provider/DropboxProvider.kt` — полная переработка + thumbnail enrichment
- `data/transfer/HttpFileServer.kt` — streaming proxy: Dropbox temporary link + helper method
- `presentation/explorer/ExplorerScreen.kt` — layout прогресс-бара (счётчик перед процентами)
- `presentation/explorer/ExplorerViewModel.kt` — 3 новых cloud open метода + preview fix

---

### Batch 56 — Три тап-зоны + Same-panel DnD + Cloud↔Local DnD ✅ проверено

**Что сделано (Batch 56):**

**Три тап-зоны на имени файла (список, 1 колонка):**
- Левая треть: выделение диапазона свайпом (как было)
- Средняя треть: долгий тап → выделить файл → начать перетаскивание (same-panel DnD)
- Правая треть: QuickSend / cross-panel DnD / fallback select (как было)

**DnD в сетке (2+ колонки):**
- Долгий тап на иконку: 1 файл не выделен → выделить → первое движение пальца → DnD (с вибрацией); уже выделен → сразу DnD
- Зона имени: поведение не изменено (QuickSend / cross-panel / select)

**Same-panel DnD:**
- Перетащить файл на папку в той же панели (облако или локально)
- При наведении на папку — подсветка через `hoveredFolderPath`
- Cloud move через Google Drive API (`files.update` с `addParents`/`removeParents`)

**Cross-panel Cloud↔Local DnD:**
- Cloud → Local: файлы скачиваются в целевую локальную папку с прогрессом
- Local → Cloud: файлы загружаются в целевую облачную папку с прогрессом
- Cloud → Cloud: перемещение через API облака (без скачивания)

**Cloud thumbnail fallback:**
- Файлы без `thumbnailLink` от Google API → fallback URL `files/{id}?alt=media` с Bearer токеном

**Изменённые файлы:**
- `presentation/explorer/components/FilePanel.kt` — три зоны, isSamePanelDrag, samePanelDragActivated, вибрация
- `data/cloud/provider/CloudProviderInterface.kt` — +moveFile()
- `data/cloud/provider/GoogleDriveProvider.kt` — moveFile + thumbnail fallback
- `data/cloud/provider/DropboxProvider.kt` — moveFile (заглушка)
- `data/cloud/provider/OneDriveProvider.kt` — moveFile (заглушка)
- `data/cloud/CloudManager.kt` — moveFile фасад
- `presentation/explorer/ExplorerViewModel.kt` — executeCloudDragMove, executeDragCloudDownload, executeDragCloudUpload, thumbnail auth
- `res/values/strings.xml` — +cloud_moved, +cloud_move_not_supported
- `res/values-ru/strings.xml` — +cloud_moved, +cloud_move_not_supported
- `res/raw/features.txt` — расширен раздел облачных хранилищ
- `res/raw-ru/features.txt` — расширен раздел облачных хранилищ

---

### Batch 55 — Облачные файлы: полноценная работа в панели ✅ проверено

**Что сделано (Batch 55):**

**Батч 1: Thumbnail больше + Удаление из QuickPreview**
- Увеличен thumbnail Google Drive: `=s220` → `=s800`
- Кнопка корзины в QuickPreview теперь работает для облачных файлов (`silentDelete` → `cloudManager.delete()`)

**Батч 2: Cloud Rename**
- `rename()` добавлен в `CloudProviderInterface` + реализован для Google Drive, OneDrive, Dropbox
- `getAccessToken()` — метод получения токена для стриминга
- `updateFileContent()` — перезаливка файла в облако (все 3 провайдера)
- `confirmInlineRename()` — теперь работает для cloud-файлов

**Батч 3: Галерея из облачных thumbnails**
- `downloadCloudThumbnail()` — скачивает `=s1600` thumbnail в `cacheDir/cloud_gallery/`
- `openCloudGallery()` — параллельная загрузка всех image thumbnails → `GalleryHolder` → навигация
- QuickPreview → "Открыть в галерее" для облачных файлов → вызывает `openCloudGallery()`

**Батч 4: Стриминг видео/аудио через локальный прокси**
- `CloudStreamConfig` + маршрут `GET /cloud/stream/{streamId}` в HttpFileServer
- Прокси к Google Drive / OneDrive API с поддержкой `Range` header (перемотка)
- `cloudStreamAndPlay()` — строит плейлист из стрим-URL, запускает VLC
- VlcPlayerAdapter — поддержка `http://` URL
- `onFileClick` — облачные видео/аудио стримятся без полной загрузки

**Батч 5: Текстовые файлы — save-to-cloud**
- `OpenTextEditorCloud` event + маршрут `text_editor_cloud` в навигации
- `cloudDownloadAndOpenText()` — скачивает файл в кэш → открывает редактор с `cloudUri`
- TextEditorScreen — опциональный `cloudUri`: при сохранении показывает диалог "Сохранить в облако / Локально / Отмена"
- `CloudManagerEntryPoint` — Hilt EntryPoint для доступа к CloudManager из TextEditorScreen

**Батч 6: Строки + очистка кэша**
- Добавлены строки EN + RU: `cloud_save_title`, `cloud_save_to_cloud`, `cloud_save_to_local`, `cloud_save_discard`, `cloud_save_success`, `cloud_rename_success`, `cloud_streaming`
- Автоочистка `cloud_thumbs/` и `cloud_gallery/` при запуске (файлы старше 7 дней)

**Изменённые файлы:**
- `data/cloud/provider/CloudProviderInterface.kt` — +3 метода (rename, getAccessToken, updateFileContent)
- `data/cloud/provider/GoogleDriveProvider.kt` — thumbnail =s800, rename, getAccessToken, updateFileContent
- `data/cloud/provider/OneDriveProvider.kt` — rename, getAccessToken, updateFileContent
- `data/cloud/provider/DropboxProvider.kt` — rename, getAccessToken, updateFileContent
- `data/cloud/CloudManager.kt` — +3 фасадных метода
- `data/transfer/HttpFileServer.kt` — cloud streaming proxy endpoint
- `service/VlcPlayerAdapter.kt` — поддержка http:// URL
- `domain/model/NavigationEvent.kt` — +OpenTextEditorCloud
- `presentation/explorer/ExplorerViewModel.kt` — cloud streaming, gallery, text edit, rename, cache cleanup
- `presentation/explorer/ExplorerScreen.kt` — cloud-aware preview actions
- `presentation/editor/TextEditorScreen.kt` — cloudUri + cloud save dialog
- `presentation/navigation/HaronNavigation.kt` — text_editor_cloud route
- `res/values/strings.xml` — +8 строк
- `res/values-ru/strings.xml` — +8 строк

---

### Batch 53 — HLS-транскодирование + прогрессивный каст ✅ проверено (каст работает)

**Что сделано (Batch 53):**
- Заменён Media3 Transformer на FFmpeg (`com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`)
- Кеш перенесён из `cacheDir` в `filesDir/transcode_cache/` — переживает смахивание и очистку кеша
- Добавлена TTL-очистка кеша (настраивается в Настройках → Трансляция: 1ч / 6ч / 12ч / 24ч)
- **HLS прогрессивный каст**: FFmpeg выводит HLS-сегменты (.ts) + плейлист (.m3u8), Chromecast начинает воспроизведение через ~30 сек (3 сегмента), пока транскод продолжается
- Энкодер: приоритет `libx264` > `h264_mediacodec` > `mpeg4` (на устройстве используется h264_mediacodec)
- HTTP-сервер: эндпоинт `/hls/{filename}` с правильными Content-Type (m3u8/ts) + CORS
- Аудио: принудительный даунмикс в стерео (`-ac 2`) для совместимости с Chromecast
- GOP: `-g 60` (ключевые кадры каждые 2 сек при 30fps)
- HLS: `-hls_playlist_type event` + version 3 (совместимость с Chromecast)
- При завершении транскода — автоматическая перезагрузка каста как VOD (STREAM_TYPE_BUFFERED) для перемотки
- Удалены debug-элементы (`_debugCastInfo`, `audioStripped`)
- Ручной `HaronWorkerFactory` вместо `@HiltWorker` (обход KSP бага Dagger 2.59.1)

**Изменённые файлы:**
- `app/build.gradle.kts` — убраны media3-transformer/effect, добавлен ffmpeg-kit-16kb, убран hilt-work/hilt-compiler
- `domain/usecase/TranscodeVideoUseCase.kt` — полная переработка: HLS-выход, кеш HLS-директорий
- `presentation/cast/CastViewModel.kt` — HLS-каст (live → VOD), убраны debug-поля
- `data/transfer/HttpFileServer.kt` — HLS-эндпоинт вместо stream-live, CORS
- `data/cast/GoogleCastManager.kt` — параметр streamType в castMedia()
- `presentation/cast/components/MediaRemotePanel.kt` — убран синий фон транскода
- `service/FileIndexWorker.kt` — ручная @AssistedFactory вместо @HiltWorker
- `di/WorkerModule.kt` — новый файл, HaronWorkerFactory
- `HaronApp.kt` — HaronWorkerFactory вместо HiltWorkerFactory
- `data/datastore/HaronPreferences.kt` — добавлен `transcodeCacheTtlHours`
- `presentation/settings/SettingsViewModel.kt` — TTL state + setter
- `presentation/settings/SettingsScreen.kt` — секция "Трансляция" с RadioButton TTL
- `res/values/strings.xml` + `res/values-ru/strings.xml` — строки для Cast-секции

**Текущий статус Cast-системы:**
- ✅ Каст видео/аудио на Chromecast — работает
- ✅ Каст видео на DLNA — работает
- ✅ Транскодирование AVI/MKV → HLS через FFmpeg (h264_mediacodec + AAC stereo) — работает
- ✅ Прогрессивный каст (воспроизведение до окончания транскода) — работает
- ✅ Перемотка после завершения транскода (VOD reload) — работает
- ✅ Стойкий кеш в filesDir — проверено
- ✅ TTL-очистка кеша — проверено
- ⚠️ Перемотка ВО ВРЕМЯ транскода — не работает (STREAM_TYPE_LIVE не поддерживает seek)
- ✅ Зеркалирование экрана — работает через браузер (URL)
- ✅ Инфо о файле — работает через браузер (URL)
- ✅ Foreground Service для каста — проверено (видео продолжает играть при закрытии, уведомление с play/pause и disconnect)
- ✅ R8/ProGuard — минификация, обфускация, shrink resources (release build)
- ✅ Signing config — release APK подписывается автоматически (keystore в local.properties)
- ✅ Gradle heap — 4 ГБ (для release-сборки с R8)

---

### libaums + NTFS-предупреждение (ОТЛОЖЕНО)

**Что делали:** определение неподдерживаемой FS (NTFS) на USB через libaums, красное предупреждение в меню.

**Статус:** работает на Sony (NTFS → предупреждение), но вызывает регрессию доступа к другим флешкам. Отложено.

**Файлы:**
- `app/build.gradle.kts` — зависимость + packaging excludes
- `data/usb/LibaumsManager.kt` — новый файл
- `data/usb/UsbStorageManager.kt` — интеграция
- `presentation/explorer/components/DrawerMenu.kt` — UI

**Следующий шаг при возврате:** проверить, является ли сама зависимость libaums причиной регрессии USB на Sony

### План до релиза 1.0:
| Батч | Задача | Статус |
|------|--------|--------|
| 30 | Поиск в архивах + OCR + USB OTG | ✅ проверено |
| 31 | Сетевое обнаружение | ✅ проверено |
| 32 | Встроенный терминал (простой) | ✅ проверено |
| 33 | Система жестов | ✅ проверено |
| 34 | Голосовые команды | ✅ проверено |
| — | Проверка всех батчей | ✅ проверено |
| — | Полировка UI/UX | ✅ (в процессе, по обратной связи) |
| — | **Релиз 1.0** | готов |

### Фичи v2.0:
| Батч | Задача | Статус |
|------|--------|--------|
| 35 | Открывалка по умолчанию | ✅ проверено |
| 36 | Сравнение файлов и папок | ✅ проверено |
| 37 | Полный терминал (VT100/ANSI) | ✅ проверено |
| 38 | Стеганография | 🔒 скрыта до релиза |
| 39 | Расширенный шаринг на ТВ | ✅ проверено |
| 40 | Quick Send DnD (быстрая отправка) | ✅ проверено |
| 41 | Доверенные устройства + ренейм + условный Quick Send | ✅ проверено |
| 42 | Просмотрщик документов — 100% оригинальное форматирование | ✅ проверено |
| 43 | Запоминание позиции чтения во всех читалках | ✅ проверено |
| 44 | Пароли для архивов/PDF + FB2 из ZIP + RAR5 + авто-закрытие архива + запоминание скрола | ✅ проверено |
| 45 | Архив прямо в панели (inline archive browsing) | ✅ проверено |
| 46 | Управление видимостью микрофона + режим просмотра/редактирования TXT | ✅ проверено |
| 47 | Безрамочные таблицы DOCX/ODT + ODT column spans | ✅ проверено |
| 48 | XLSX: открытие, пропорции колонок, per-cell borders, заголовки | ✅ проверено |
| 50 | Архивирование с паролем (AES-256), split ZIP, кросс-панельное сравнение | ✅ проверено |
| 51 | SMB-браузер (вкладка в Передача) | ✅ проверено |
| 51b | Двухпанельный SMB-режим (SMB + локальные файлы) | ✅ проверено |
| 52 | Фиксы корзины + тап на пустом месте | ✅ проверено |
| 54 | Облака (GDrive/Dropbox/Yandex) + пульт ТВ (тачпад/клавиатура) | ✅ проверено |

### Хотелки (после релиза):
| Фича | Описание |
|------|----------|
| Shizuku — доступ к Android/data и obb | Доступ к `/Android/data` и `/Android/obb` через Shizuku (shell UID 2000). SAF заблокирован Google. Shizuku — стандартное решение (Solid Explorer, MiXplorer). Нужно: `dev.rikka.shizuku:api`, AIDL-сервис `ShizukuFileService`, fallback в `FileRepositoryImpl.getFiles()`, UI авторизации. SAF-код уже есть (fallback при `listFiles() == null`), Shizuku — второй fallback после SAF |
| Перемотка во время транскода | HLS live seek — перемотка видео на Chromecast пока транскодирование ещё идёт (STREAM_TYPE_LIVE не поддерживает seek, нужен ▶ Media Source Extensions или ▶ частичный VOD reload) |
| Торрент-клиент | Встроенный торрент: последовательная загрузка + стрим на ТВ через Cast (libtorrent4j) |
| Сетевой поток на Cast | Ввод URL видео (HTTP/HLS/DASH/m3u8) → трансляция на телевизор через Chromecast/DLNA без скачивания на телефон. IPTV-ссылки, видео с NAS, прямые эфиры — всё кидается на ТВ, телефон как пульт |
| Вложенные архивы | tar.gz внутри ZIP — извлечь во temp, открыть как вложенный архив |
| Убрать RFCOMM | Удалить кастомный Bluetooth RFCOMM протокол (sendFiles/acceptBtTransfer/startListening) — мёртвый код, Haron-to-Haron идёт через TCP |
| Голосовой поиск (длинный запрос) | Поиск файлов голосом по длинному запросу — возможность TBD |
| Голосовая сортировка по типу + скролл | «сортировка тип» — сортировка по расширению; добавить слово «тип» в конце команды → скролл к первому файлу этого типа сверху (пример: «сортировка jpg» → сорт по расширению + скролл к первому .jpg) |
| DjVu формат | Поддержка просмотра файлов .djvu в читалке |
| Анализатор памяти — путь в две строки | Длинный путь файла в анализаторе памяти переносить на вторую строку вместо обрезки |
| FTP/SFTP/WebDAV | Доступ к файлам на удалённых серверах: FTP (хостинги, NAS), SFTP (любой Linux-сервер через SSH, визуальный браузер вместо команд), WebDAV (Nextcloud, Яндекс.Диск, NAS). Файлы сервера отображаются в панели как обычные папки — копирование, перемещение, скачивание/загрузка через двухпанельный интерфейс. Расширение SMB для других типов серверов |
| Облака (GDrive, Dropbox, Yandex) | Доступ к облачным хранилищам через их API — просмотр, загрузка, скачивание |
| Root-доступ | Работа с файловой системой через su — просмотр/редактирование системных файлов |
| Подсветка синтаксиса | Подсветка кода в текстовом редакторе (Kotlin, Java, XML, JSON, HTML, CSS, JS и др.) |
| Доп. форматы архивов | TAR, TAR.GZ, TAR.BZ2, TAR.XZ, GZ, BZ2, XZ (Linux-архивы), ISO (образы дисков), CAB (Windows). Самые важные — TAR.GZ и ISO |
| Вкладки-табы | Несколько вкладок в каждой панели как в браузере — переключение между папками без потери позиции |
| SQLite-просмотрщик | Открытие .db файлов — просмотр таблиц, строк, схемы базы данных |
| Hex-просмотрщик | Побайтовый просмотр любого файла в hex + ASCII |
| JSON/XML форматирование | Красивый просмотр JSON и XML с подсветкой, отступами и сворачиванием узлов |
| Просмотр шрифтов | Превью .ttf/.otf файлов — отображение всех символов шрифта |
| FTP-сервер | Раздача файлов с телефона по FTP — другие устройства подключаются к телефону как к серверу |
| Markdown-рендер | Просмотр .md файлов как отформатированный текст (заголовки, списки, ссылки), а не исходник |
| Редактор изображений | Базовые операции: обрезка, поворот, ресайз фото прямо в проводнике |
| Редактор музыкальных тегов | Редактирование ID3-тегов MP3 (исполнитель, альбом, обложка, год) |
| Кастомные темы | Пользовательские иконки-паки и темы оформления помимо светлой/тёмной |
| Менеджер приложений | Полный менеджер: список установленных, бэкап APK, удаление, размер кеша, информация о приложении |
| Субтитры в плеере | Поддержка .srt/.ass субтитров в видеоплеере — выбор файла, вкл/выкл, размер/цвет. VLC-движок уже умеет, нужен UI |
| Эквалайзер | Настройка звука в медиаплеере — пресеты (рок, поп, джаз) и ручная настройка полос |
| Плейлисты | Создание, сохранение и редактирование .m3u плейлистов, не только автоплейлист папки |
| Синхронизация папок | Сравнение двух папок + синхронизация (как rsync) — копирование новых/изменённых файлов в одну или обе стороны |
| Шифрование отдельных файлов | Зашифровать/расшифровать конкретный файл на месте (AES), не перемещая в защищённую папку |
| Сжатие изображений | Уменьшение размера/качества фото прямо в проводнике — выбор качества, ресайз |
| PDF объединение/разделение | Склеить несколько PDF в один или разрезать PDF на отдельные страницы |
| Печать файлов | Печать документов, изображений, PDF через системный сервис печати Android |
| SVG-просмотр | Просмотр векторных изображений .svg в галерее/превью |
| GIF-анимация | Воспроизведение анимированных GIF в галерее и превью |
| Material You | Динамические цвета из обоев пользователя (Android 12+) — тема подстраивается под обои |
| Ярлыки файлов | Создание ярлыка конкретного файла на рабочий стол, не только папки |
| Кастомные ассоциации | Настройка каким встроенным/внешним просмотрщиком открывать какой тип файлов |
| Безопасное удаление (shredder) | Перезапись файла случайными данными перед удалением — невозможно восстановить даже специальным софтом |
| Фото при неверном PIN | Фронтальная камера снимает того, кто ввёл неправильный пароль — фото сохраняется скрыто |
| Обманный PIN | Второй PIN-код показывает пустой/фейковый контент вместо реальных защищённых файлов |
| Разные пароли на папки | Отдельный пароль для каждой защищённой папки, а не один PIN на всё |
| Разрезание/склейка файлов | Разделить большой файл на части заданного размера, потом собрать обратно в один |
| Визуальная карта памяти (treemap) | Блоки пропорциональны размеру файлов/папок (как WinDirStat) — сразу видно что занимает место |
| Проверка контрольных сумм | Вставить ожидаемый хеш (MD5/SHA-256), сравнить с вычисленным — верификация скачанных файлов |
| Символические ссылки | Создание и управление симлинками (требует root) |
| Редактор EXIF | Редактирование EXIF-данных фото: дата, геолокация, камера, не только просмотр и удаление |
| Очередь Cast | Поставить несколько медиафайлов в очередь на трансляцию на ТВ, не по одному |
| Озвучка текста (TTS) | Чтение текстовых файлов вслух через системный синтезатор речи |
| Tasker-интеграция | Запуск действий Haron из Tasker/автоматизаций — копирование, перемещение, открытие папки |
| Наблюдатель за папкой | Автодействие при появлении файла: автосортировка загрузок по типу, автоперемещение и т.д. |
| Запланированные операции | Копирование, синхронизация, бэкап по расписанию (ежедневно, еженедельно) |
| Клавиатурные сочетания | Горячие клавиши для Bluetooth-клавиатур и Samsung DeX (Ctrl+C, Ctrl+V, F2, Delete и т.д.) |
| Ввод пути вручную | Тап на хлебные крошки → текстовое поле для ввода/вставки полного пути |
| EPUB-читалка | Чтение электронных книг .epub — популярнейший формат, оглавление, закладки, зум |
| CBR/CBZ комиксы | Просмотр комиксов — архивы с картинками, листание по страницам, зум |
| Просмотр логов | .log файлы с фильтрацией по уровню (ERROR/WARN/INFO), автообновлением как tail -f |
| Конвертер изображений | PNG↔JPG, HEIC→JPG, WebP→PNG — конвертация формата прямо в проводнике |
| APK-инспектор | Подробный просмотр APK: манифест, разрешения, ресурсы, активности, SDK-версии |
| Скорость воспроизведения | Регулировка скорости медиаплеера: 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x |
| Таймер сна | Остановить воспроизведение через X минут — для аудиокниг и музыки на ночь |
| Жесты в видеоплеере | Свайп вверх/вниз слева — яркость, справа — громкость (как в MX Player) |
| Повтор A-B | Зацикливание выбранного отрезка аудио/видео — для изучения языков, музыки |
| HTTP-сервер с паролем | Защищённая раздача файлов — логин/пароль для доступа к HTTP-серверу |
| Авто-загрузка на сервер | Автобэкап выбранных папок на FTP/SMB/облако по расписанию или при изменении |
| Обмен панелей | Поменять содержимое верхней и нижней панели местами одной кнопкой |
| Сортировка по папкам | Каждая папка запоминает свою сортировку (фото по дате, документы по имени) |
| Закреплённые папки | Приколоть важную папку вверху списка файлов, всегда видна первой |
| Цветовая маркировка файлов | Автоматический цвет фона/текста по типу файла, возрасту или размеру |
| Кастомные иконки папок | Назначить свою иконку/цвет для конкретной папки |
| QR-генератор | Создать QR-код из текста, ссылки или пути к файлу — для быстрого обмена |

| AI-сортировка файлов | ИИ анализирует файлы в папке и предлагает разложить по папкам: фото по дате/месту/людям, документы по типу. "У тебя 500 фото в Downloads — разложить?" |
| Умные папки | Виртуальные папки по правилам — "все PDF за эту неделю", "видео больше 100 МБ", "фото с геолокацией Москва". Папка обновляется автоматически, не надо искать каждый раз |
| Естественный поиск | Поиск на человеческом языке: "найди презентацию с прошлого вторника", "фото с пляжа летом" — ИИ понимает контекст, а не только точное имя |
| Умная очистка | Конкретные советы что удалить: "15 дубликатов WhatsApp-фото (340 МБ)", "кеш 3 приложений (500 МБ)", "скриншоты 2022 года (1.2 ГБ)". Не просто анализатор, а готовые действия |
| История версий файлов | Автосохранение версий при каждом редактировании — можно откатиться к вчерашней версии документа. Как в Google Docs, но локально на устройстве |
| Заметки на папках | Прикрепить текстовый стикер к любой папке: "документы для налоговой", "разобрать позже", "не удалять". Видно при открытии папки |
| Шаблоны папок | Создать структуру папок из шаблона одним тапом: "Проект" → автоматически создаёт /docs, /images, /src, /backup внутри |
| Связи между файлами | Связать файлы в группу/проект: заметка + 3 фото + PDF = один проект. Открываешь один — видишь все связанные |
| Визуальное сравнение изображений | Наложить два похожих фото друг на друга, подсветить разницу — найти что изменилось между версиями |
| Drag-and-drop на ПК через браузер | Открыл страницу на компе → перетаскиваешь файлы мышкой в обе стороны. Без установки программ на ПК, работает через браузер |
| Кросс-устройственный буфер | Скопировал файл на телефоне → вставил на другом телефоне/ПК. Буфер обмена между устройствами через Wi-Fi |
| Аналитика использования файлов | Статистика: какие файлы открываешь чаще, какие не трогал год, сколько места добавилось за месяц — тренды и графики |
| Предупреждение о дубликате | При скачивании/копировании файла предупреждает: "такой файл уже есть в /Photos/Camera" — до создания дубликата, а не после |
| Песочница для файлов | Открыть подозрительный файл в изолированной среде, без доступа к остальным данным устройства |
| Аудит доступа | Лог кто/когда/какие файлы открывал — для общего планшета, семейного устройства, контроля |
| Машина времени | Визуальная временная шкала: как выглядела папка на любую дату. Удалённые файлы показаны серым, можно восстановить |
| Частые действия | Запоминает паттерны: "каждый понедельник копируешь отчёты сюда" → предлагает сделать автоматически |
| Контекстные папки | При подключении к рабочему Wi-Fi показывает рабочие папки, дома — медиа. Автоматически по сети/геолокации |
| Общая папка между устройствами | Реалтайм-синхронизация папки между двумя Haron по Wi-Fi без облака — как общий сетевой диск |
| Комментарии к файлам | Оставить текстовый комментарий на файл для другого пользователя: "проверь третью страницу", "не удалять до пятницы" |
| Мини-утилиты | Калькулятор размера ("сколько влезет на флешку 16 ГБ"), конвертер МБ↔ГБ, генератор паролей для архивов |
| Скриншот → действие | Сделал скриншот → Haron предлагает сразу переместить, переименовать, отправить или удалить |
| Буфер обмена файловый | История последних 20 копирований — можно вставить не только последний скопированный файл, а любой из истории |
| 3D-навигация по папкам | Файловая система как город: папки — здания, размер здания = размер папки. Заходишь внутрь — видишь файлы как объекты |
| Тепловая карта файлов | Цвет файла по частоте использования: часто открываешь — красный (горячий), забытые — синий (холодный) |
| Рабочие пространства | Сохранённые состояния панелей: "Работа" (рабочие папки), "Медиа" (музыка + видео). Переключение одним тапом |
| Макросы (цепочка действий) | Записать последовательность: скопировать → переименовать → сжать → отправить. Запускать одной кнопкой |
| Заметка при удалении | "Почему удалил?" → запись причины, потом в корзине видно зачем файл удалили |
| Тегирование по фото | Сфоткал физический документ → Haron распознаёт текст (OCR) и автоматически именует/тегирует файл |
| Голосовая навигация | Полноценная навигация голосом: "открой загрузки", "покажи фото за март", "скопируй в документы" |
| Жесты рисованием | Нарисовал букву/символ на экране → действие: "L" → загрузки, "C" → камера. Настраиваемые символы |
| Плавающее окно | Мини-проводник поверх других приложений — быстро скопировать/переместить файл не закрывая текущее приложение |
| PiP для видео | Видео в маленьком окне (Picture-in-Picture) пока ходишь по папкам в проводнике |
| Чёрная AMOLED-тема | Чисто чёрный фон (#000000) для OLED-экранов — экономит батарею, приятно в темноте |
| Настраиваемый тулбар | Пользователь выбирает какие кнопки показывать в панели инструментов, убирает ненужные |
| Свайп по файлу | Свайп влево → удалить, свайп вправо → поделиться (как в почтовых приложениях). Настраиваемые действия |
| App Shortcuts | Долгий тап на иконку Haron на рабочем столе → быстрые действия: недавние, избранное, поиск, новый файл |
| Кадр из видео | Сохранить скриншот из видео на конкретной секунде — кнопка "снимок" в видеоплеере |
| Визуализация аудио | Волна/эквалайзер анимация при воспроизведении музыки — красивый визуал в плеере |
| Слайд-шоу с музыкой на Cast | Фото-слайд-шоу с фоновой музыкой при трансляции на ТВ — для вечеринок и семейных просмотров |
| Управление телефоном с ПК | Открыл браузер на компе → видишь файлы телефона, управляешь мышкой. Без установки программ на ПК |
| Менеджер загрузок | Скачивание файлов по URL с прогрессом, паузой, возобновлением. Встроенный загрузчик |
| Зашифрованные заметки | Защищённый блокнот внутри приложения — заметки под PIN/биометрией |
| Просмотр защищённого ZIP | Увидеть список файлов внутри запароленного архива без распаковки (имена, размеры) |
| Режим крупных кнопок | Увеличенные элементы интерфейса для пожилых и слабовидящих пользователей |
| Оптимизация TalkBack | Полная поддержка скрин-ридера Android для незрячих — описания всех элементов, навигация |
| Виджет недавних файлов | Последние изменённые файлы прямо на рабочем столе — тап → открытие |
| Виджет поиска | Строка поиска файлов на рабочем столе — тап → сразу в поиск Haron |

| Рейтинг файлов | Звёзды (1–5) на файлах, сортировка по рейтингу — "лучшие фото", "любимая музыка" |
| Файловый дневник | Автозапись: "сегодня создал 5 файлов, удалил 3, отправил 2". Статистика за неделю/месяц |
| Порталы между папками | Связать две папки порталом — ярлык виден прямо в списке файлов, тап → мгновенный переход. Быстрее закладок |
| Умная сортировка по контексту | Папка с фото автоматически по дате, документы по имени, музыка по исполнителю. Без ручной настройки |
| Карта файловой системы | Интерактивная карта всех папок как метро-схема — видно структуру, тап на узел → переход |
| Коллажи | Выбрал 4 фото → автоматический коллаж, сохранил как изображение |
| GIF из видео | Выбрал отрезок видео → конвертация в GIF прямо в проводнике |
| Аудио-нарезка | Вырезать кусок из MP3 — сделать рингтон из песни |
| Детектор подозрительных APK | Предупреждение: "этот APK запрашивает SMS и контакты", "скачан с неизвестного источника" |
| Цифровой сейф с таймером | Файлы доступны только в определённое время — "открыть после 1 января" |
| QR-сканер из галереи | Выбрал фото с QR-кодом → Haron распознаёт и открывает ссылку |
| Текст из фото в буфер | Тап на фото → OCR → текст в буфер обмена одной кнопкой |
| Сравнение размера до/после | При сжатии/конвертации: "было 5 МБ → стало 1.2 МБ, экономия 76%" |

### ⚠️ Проблемные для Google Play

| Фича | Проблема Google Play |
|-------|---------------------|
| Торрент-клиент | 🔴 Google регулярно удаляет торрент-клиенты из Play Store. Только для RuStore/APK |
| Root-доступ | 🟡 Не запрещён, но приложение попадёт под усиленную проверку |
| Фото при неверном PIN | 🔴 Скрытая съёмка — нарушение политики конфиденциальности Google. Только RuStore |
| Плавающее окно | 🟡 Требует SYSTEM_ALERT_WINDOW — нужно обоснование, Google часто отклоняет |
| Скриншот → действие | 🟡 Если через Accessibility Service — Google очень строго проверяет, часто отклоняет |
| Менеджер приложений | 🟡 QUERY_ALL_PACKAGES требует декларацию, REQUEST_DELETE_PACKAGES ограничен |
| APK-инспектор | 🟡 Может вызвать подозрения в реверс-инжиниринге, нужно обоснование |
| Детектор подозрительных APK | 🟡 Сканирование APK может конфликтовать с Google Play Protect |
| Песочница для файлов | 🟡 Если эмулирует среду выполнения — может быть отклонён |
| Безопасное удаление (shredder) | 🟢 ОК, но описание не должно намекать на сокрытие улик |
| Обманный PIN | 🟢 ОК для RuStore, но Google может посчитать инструментом обмана |

🔴 = скорее всего отклонят, 🟡 = нужна декларация/обоснование, 🟢 = скорее ОК но осторожно

**Примечание:** У Haron уже есть MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, REQUEST_INSTALL_PACKAGES — для Google Play потребуется заполнить декларации для каждого. RuStore таких ограничений не имеет.

### Приоритеты хотелок

**Высокий (нужно пользователям, есть у конкурентов):**
- Ввод пути вручную
- Клавиатурные сочетания (DeX)
- FTP/SFTP/WebDAV
- Облака (GDrive, Dropbox, Yandex)
- EPUB-читалка
- Скорость воспроизведения
- Жесты в видеоплеере
- Субтитры в плеере
- Material You
- Доп. форматы архивов (TAR.GZ, ISO)
- Обмен панелей
- HTTP-сервер с паролем
- Проверка контрольных сумм
- App Shortcuts (долгий тап на иконку)
- Чёрная AMOLED-тема
- PiP для видео
- Оптимизация TalkBack

**Средний (выделит среди конкурентов):**
- Root-доступ
- Подсветка синтаксиса
- Markdown-рендер
- JSON/XML форматирование
- Hex-просмотрщик
- SQLite-просмотрщик
- Конвертер изображений
- Таймер сна
- Безопасное удаление (shredder)
- Пакетное шифрование файлов
- Синхронизация папок
- PDF объединение/разделение
- Сортировка по папкам
- Фото при неверном PIN

**Низкий (нишевое, можно позже):**
- Вкладки-табы
- FTP-сервер
- CBR/CBZ комиксы
- Просмотр логов
- APK-инспектор
- Просмотр шрифтов
- SVG-просмотр
- Повтор A-B
- Эквалайзер
- Плейлисты .m3u
- Озвучка текста (TTS)
- Tasker-интеграция
- Наблюдатель за папкой
- Запланированные операции
- Обманный PIN
- Разные пароли на папки
- Символические ссылки
- Разрезание/склейка файлов
- Визуальная карта (treemap)
- Очередь Cast
- Авто-загрузка на сервер
- Закреплённые папки
- Цветовая маркировка файлов
- Кастомные иконки папок
- QR-генератор
- Кастомные темы
- Ярлыки файлов
- Кастомные ассоциации
- Менеджер приложений
- Редактор EXIF
- Сжатие изображений
- Печать файлов
- GIF-анимация
- Редактор музыкальных тегов
- Сетевой поток на Cast
- FFmpeg транскодинг
- Торрент-клиент
- DjVu формат
- Вложенные архивы
- Голосовая сортировка по типу + скролл
- Заметка при удалении
- Комментарии к файлам
- Мини-утилиты (калькулятор размера, конвертер, генератор паролей)
- Плавающее окно
- Настраиваемый тулбар
- Свайп по файлу (действия)
- Кадр из видео
- Визуализация аудио
- Слайд-шоу с музыкой на Cast
- Управление телефоном с ПК
- Менеджер загрузок
- Зашифрованные заметки
- Просмотр защищённого ZIP
- Режим крупных кнопок
- Виджет недавних файлов
- Виджет поиска

**Инновационный (ни у кого нет — долгосрочно):**
- AI-сортировка файлов
- Умные папки (виртуальные по правилам)
- Естественный поиск (ИИ)
- Умная очистка (советы что удалить)
- История версий файлов
- Связи между файлами (проекты)
- Визуальное сравнение изображений
- Drag-and-drop на ПК через браузер
- Кросс-устройственный буфер
- Аналитика использования файлов
- Предупреждение о дубликате
- Машина времени (состояние папки на дату)
- Частые действия (автоматизация паттернов)
- Контекстные папки (по Wi-Fi/геолокации)
- Общая папка между устройствами (реалтайм)
- Песочница для файлов
- Аудит доступа
- 3D-навигация по папкам
- Тепловая карта файлов
- Рабочие пространства
- Макросы (цепочка действий)
- Тегирование по фото (OCR → автоимя)
- Голосовая навигация (полноценная)
- Жесты рисованием
- Заметки на папках
- Шаблоны папок
- Скриншот → действие
- Буфер обмена файловый (история 20)

### План релиза 2:
| Батч | Задача | Статус |
|------|--------|--------|
| 52 | Альбомная ориентация — панели рядом (Row) с вертикальным разделителем | ✅ проверено |
| 53 | Навигация в сравнении папок — возврат из diff файла к списку | ✅ проверено |
| 54 | История сравнений с кешем результатов | ⬜ |

#### Батч 54 — История сравнений
- Индекс истории в SharedPreferences (JSONArray, до 10 записей: leftPath, rightPath, timestamp, type, summary)
- Кеш результатов FOLDER-сравнений в `cacheDir/comparisons/` (JSON файлы, ключ = MD5 путей)
- Новый mode HISTORY в ComparisonScreen: список прошлых сравнений, тап → загрузка из кеша (мгновенно) или пересканирование
- Доступ: кнопка History в TopAppBar экрана сравнения + 7-й пункт в ToolsPopup ("Сравнения")
- Валидация: если файлы/папки удалены — при попытке открыть показать ошибку "Путь не найден"
- Удаление записей из истории (иконка корзины)
- Файлы: ComparisonHistoryEntry (новый), HaronPreferences (+3 метода), ComparisonViewModel (кеш/history), ComparisonScreen (UI), ToolsPopup (+7-й пункт), ExplorerViewModel (index 6), strings.xml (+5 строк)

### Технический долг (не срочно, делать когда будет время):
| Задача | Описание | Риск |
|--------|----------|------|
| Accessibility | Заменить 103 `contentDescription = null` на `stringResource` в 37 файлах. Добавить ~100 строк в strings.xml (EN + RU) | Нулевой — чисто текстовые метки |
| R8/ProGuard | Включить `isMinifyEnabled = true` для release. Написать keep-правила для Room, JSch, VLC, Gson. Тестировать release-сборку | Средний — нужно 2-3 итерации тестирования |

### Релиз «Кастомные темы» — рефакторинг визуальной системы

Отдельный релиз. Цель: любая сторонняя тема может полностью изменить внешний вид APK.

**Текущее состояние (аудит):**
- ~95 хардкод-цветов (`Color(0xFF...)`) в 50+ файлах
- 76 хардкод-форм (`RoundedCornerShape`) в 29 файлах
- 1/14 стилей типографики определён
- 333 прямых `Icons.Filled.*` в 52 файлах, нет абстракции
- Терминал, галерея, плеер — полностью отключены от MaterialTheme

**Фаза 1 — Фундамент (централизация токенов):**
| Задача | Описание |
|--------|----------|
| Shapes.kt | Централизовать все 76 форм в токены: `HaronShapes.small/medium/large/card/dialog/chip` |
| Type.kt | Дополнить все 14 стилей Material 3 (сейчас только bodyLarge) |
| SemanticColors.kt | Создать семантические токены: `terminalBg`, `terminalText`, `diffAdded`, `diffRemoved`, `chartColors`, `highlightYellow` и др. |
| Провести CompositionLocal | `LocalHaronColors`, `LocalHaronShapes` — доступ из любого composable |

**Фаза 2 — Рефакторинг (~100 файлов):**
| Задача | Описание |
|--------|----------|
| Убрать хардкод-цвета | Заменить 95 `Color(0xFF...)` на семантические токены |
| Убрать хардкод-формы | Заменить 76 `RoundedCornerShape(Xdp)` на `HaronShapes.*` |
| Терминал → тема | 35+ хардкод цветов → `TerminalTheme` data class, подключённый к общей теме |
| Галерея/Плеер → тема | Чёрные фоны, белые тексты → семантические токены |
| TagColors → тема | Вынести из domain-модели в ui/theme |
| StorageAnalysis → тема | Палитра графиков → токены |

**Фаза 3 — Иконки:**
| Задача | Описание |
|--------|----------|
| HaronIcons object | Обёртка: `HaronIcons.back`, `HaronIcons.delete` и т.д. вместо `Icons.Filled.*` |
| Замена 333 использований | Все файлы → через `HaronIcons.*` |
| Поддержка icon-паков | `HaronIcons` загружает иконки из темы или fallback на Material |

**Фаза 4 — Инфраструктура тем:**
| Задача | Описание |
|--------|----------|
| ThemeConfig data class | Сериализуемый JSON: цвета, формы, типографика, иконки |
| Загрузка тем из файла | Парсинг JSON → применение к CompositionLocal |
| Material You интеграция | Динамические цвета из обоев (Android 12+) как встроенная тема |
| AMOLED-тема | Чёрный фон (#000000) как встроенная тема |
| UI выбора темы | Экран в настройках: превью, импорт, экспорт |

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

#### Релиз 1.0 (versionCode=1, versionName=1.0):
- [x] Счётчик на иконке приложения: badge через notification .setNumber(), activeOperations counter ← Batch 31
- [x] Сетевое обнаружение: NSD (_haron._tcp. + _smb._tcp.) + subnet scan порт 445, секция "Сеть" в drawer ← Batch 31
- [x] Встроенный терминал (простой): ProcessBuilder, ls/cp/mv/cat/grep, скролл, история команд ← Batch 32
- [x] Система жестов: настраиваемые action-коды, экран настроек, назначение жестов ← Batch 33
- [x] Система голосовых команд: те же action-коды, Android Speech Recognition офлайн ← Batch 34
- [x] Полировка UI/UX — выполняется итеративно по обратной связи пользователя

#### Релиз 2 (versionCode=3, versionName=1.4) — текущий:
- [x] Открывалка по умолчанию (регистрация как обработчик типов файлов) ← Batch 35
- [x] Сравнение файлов и папок (java-diff-utils, две панели, синхронный скролл) ← Batch 36
- [x] Встроенный терминал (полный): VT100/ANSI, цветной вывод, автодополнение, кликабельные пути ← Batch 37
- [x] Стеганография (Tail-Append метод, AES-256-GCM) ← Batch 38
- [x] Расширенный шаринг на ТВ: слайд-шоу, PDF-презентация, инфо о файле, зеркалирование экрана ← Batch 39
- [ ] Интеграция с облаком (Google Drive, Dropbox, Yandex Disk как папки)
- [ ] Машина времени (версии текстовых файлов)
- [ ] Переименование по содержимому (ML Kit предлагает имя для фото)
- [ ] Мониторинг мусора в реальном времени (WorkManager, уведомление)
- [x] Терминал 2.0: Termux-стиль, SSH через JSch, VT100/xterm эмуляция, Claude Code ← Batch 98
- [ ] Монтажный стол (FFmpeg: обрезка, склейка видео, таймлайн)
- [ ] Чистильщик мессенджеров (Telegram/WhatsApp кэш и медиа)
- [ ] Root-режим (опциональный): chmod, /data, системные папки
- [ ] Телефон как пульт (тачпад + клавиатура)
- [ ] Книжная полка + Универсальная читалка (BookReader)

### Книжная полка + Универсальная читалка — план

**Исследование проведено** (Batch 99): EPUB, FB2, MOBI/AZW3, DJVU — структура форматов, библиотеки, UX топовых читалок (Moon Reader, ReadEra, KOReader, Librera, FBReader, CoolReader).

#### LibraryScreen (книжная полка)
- Одна панель, grid обложек (1-6 рядов, pinch-zoom как в панели проводника)
- Первый запуск → диалог: "Сканировать всё хранилище" или "Выбрать папки". Папки можно добавлять позже
- Сканирование: расширения `.epub`, `.fb2`, `.fb2.zip`, `.mobi`, `.azw3`, `.djvu`, `.pdf`
- Имя книги — из метаданных файла (title), fallback на имя файла
- Обложка — из метаданных (cover image), fallback на иконку формата
- Докачка инфы — Google Books API (без ключа, GET запрос по title+author → обложка, описание, жанр)
- Короткий тап → QuickPreviewDialog (как в панели)
- Долгий тап → сразу открыть в читалке
- Сортировка по дате добавления (по умолчанию)
- Room таблица: BookEntry (path, title, author, cover, format, lastRead, progress, scanFolder)

#### BookReaderScreen (единая читалка)
Два режима внутри одного экрана:

**Reflow-режим** (EPUB, FB2, MOBI):
- EPUB: свой парсер (ZIP → container.xml → OPF → XHTML) + WebView с CSS columns для пагинации
- FB2: XmlPullParser → Compose-нативный рендеринг (LazyColumn + AnnotatedString). Улучшить существующий парсер
- MOBI/AZW3: libmobi (C, Apache-2.0, JNI) → конвертация в EPUB-like → тот же WebView рендерер
- Настройки: шрифт, размер, межстрочный, поля, тема (Day/Night/Sepia)

**Page-режим** (PDF, DJVU):
- PDF: PDFBox (уже есть в проекте)
- DJVU: DjVuLibre (C, GPL v2, JNI, dynamic linking .so). LRU-кеш 3-5 bitmap страниц, tile-рендеринг при zoom, RGB_565
- Zoom + pan (transformable), слайдер страниц

**Общее для обоих режимов:**
- Тап-зоны: лево=назад, право=вперёд, центр=показать/скрыть UI
- Оглавление (TOC из метаданных файла)
- Закладки (Room, привязка к позиции)
- Прогресс чтения (процент, страница)
- Поиск по тексту
- 3 темы: Day / Night / Sepia

**Нативные зависимости (JNI/NDK):**
- libmobi (Apache-2.0) — парсинг MOBI/AZW3, конвертация в EPUB
- DjVuLibre (GPL v2, dynamic linking .so) — рендеринг DJVU страниц

#### Этапы реализации
- **Этап 1 (база):** BookReaderScreen + EPUB парсер/рендерер + FB2 улучшенный + LibraryScreen (grid + сканирование)
- **Этап 2 (форматы):** MOBI/AZW3 через libmobi JNI + DJVU через DjVuLibre JNI
- **Этап 3 (полировка):** Popup-сноски, highlights, аннотации, анимации перелистывания, TTS, per-document настройки, докачка инфы (Google Books API)

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
- **DLNA не имеет шага "connect"** → в отличие от Chromecast, DLNA-устройство не требует предварительного подключения. `selectDevice()` ничего не делает для DLNA → `pendingAction` через `isConnected` collector не срабатывает. Решение: `pendingDlnaDeviceId` + немедленный `executePendingAction()`.
- **Chromecast default receiver не рендерит HTML/MJPEG** → зеркалирование экрана и инфо о файле нельзя показать через Chromecast. Решение: браузерный подход — показывать URL для открытия в браузере на ТВ.
- **Транскод кеш удалялся при disconnect** → `cleanupTempFiles()` вызывался в `disconnect()` и `onCleared()`, что удаляло кеш при каждом отключении. Решение: удалять только при `cancelTranscode()`.
- **Trash meta.json может быть пустым** → если запись прерывается (краш, нет места), `meta.json` становится `[]` (2 байта), UI показывает 0 файлов хотя файлы в `.haron_trash/` есть. Решение: `recoverOrphanEntries()` + atomic write через `.tmp` файл.
- **findItemIndexAtPosition() fallback** → функция возвращала индекс ближайшего элемента при тапе на пустое место в LazyVerticalGrid. Решение: убрать fallback, возвращать `-1` для пустого пространства.
- **deleteRecursively() не даёт прогресса** → для папки с 1000 файлами `deleteRecursively()` — одна операция, прогрессбар = 0% → готово. Решение: `walkBottomUp()` + удаление по одному файлу с колбэком прогресса.
- **Media3 Transformer profile ignored for H264** → `setEncodingProfileLevel()` игнорируется при использовании `DefaultEncoderFactory` + H264. Вместо этого: `experimentalSetEnableHighQualityTargeting(true)` для автоподбора битрейта.
- **VLC HW decoder ломает AVI (XviD/DivX)** → `setHWDecoderEnabled(true, true)` (force=true) заставляет MediaCodec декодировать MPEG-4 ASP → артефакты, unknown output format (2130708361). `(true, false)` тоже не помогает — VLC всё равно выбирает HW. Решение: whitelist по расширению — HW для mkv/mp4/mov/webm/ts/m2ts, software для avi/wmv/flv и остального. Также `--avcodec-skiploopfilter=4` (skip ALL) даёт артефакты на старых кодеках → заменён на `=0` (не пропускать).

---

## Терминал — текущая архитектура

### Реализовано (Batch 98):
- **Termux-стиль** — одно окно, ввод через скрытый TextField, каждый символ → PTY
- **Shell** — persistent bash через native PTY (JNI/NDK)
- **SSH** — JSch, кнопка в хедере, диалог пароля, available() + Dispatchers.IO
- **VT100/xterm** — alt screen, scroll regions, cursor save/restore, DEC modes, wcWidth

### Shell — что улучшить
- [x] Persistent shell через PTY ← сделано
- [x] Ctrl+C через SIGINT ← сделано
- [x] PTY (vi/nano/htop) через JNI/NDK ← сделано
- [ ] Copy/paste: long tap по тексту в grid → выделить → скопировать
- [ ] Foreground Service + WakeLock для живучести сессии

### SSH — что улучшить
- [x] Keepalive: `session.setServerAliveInterval(30_000)` ← сделано
- [ ] Динамический resize: `setPtySize()` ломает JSch pipe, нужен другой подход или другая SSH-библиотека
- [ ] Автореконнект при обрыве сети
- [ ] Public key auth (помимо пароля)

### Что работает сейчас
Shell: persistent bash через PTY, export, alias, cd, pipe, redirect, фоновые, Ctrl+C/D/Z, vi/nano/htop/less, ANSI 16/256/RGB, pinch-zoom шрифта
SSH: JSch, кнопка подключения, диалог пароля, keepalive 30s, Claude Code работает через SSH

### Пакеты (TODO)
- [ ] Busybox (~1МБ) встроить в APK — 300 утилит сразу (vi, wget, less, tar, awk, sed и др.)
- [ ] Экран «Пакеты» в настройках терминала — курированный список (nano, vim, htop, curl, git, python)
- [ ] Скачивание из Termux-репо (`packages.termux.dev`): .deb → извлечь бинарник → удалить .deb
- [ ] Установка в `filesDir/usr/bin/`, добавление в PATH при старте shell
- [ ] Автоматическая проверка совместимости пакетов: readelf (зависимости .so), strings (hardcoded paths), пробный запуск (--version). Запустить на полном репо → создать JSON-список с пометками ✅/⚠️/❌

### Мелкие доработки (TODO)
- [ ] Copy/paste: долгий тап по тексту в grid → выделить → скопировать в буфер обмена
- [ ] Динамический resize SSH PTY при повороте экрана

### Чего НЕ будет
- Полный пакетный менеджер (pkg/apt) — нужен rootfs (~50МБ+ инфраструктура)
- Собственные бинарники (python, gcc, git) — пока только через скачиваемые пакеты или SSH на сервер

---

## Известные проблемы (нерешённые)

- **Dropbox: Production access** — приложение в статусе "Development", лимит пользователей исчерпан. Перед релизом подать "Apply for production" в Dropbox App Console → снимет лимит. Текущий подключённый аккаунт работает.
- **SSH setPtySize()** — ломает JSch PipedInputStream. Resize SSH терминала при изменении шрифта/ориентации не работает. Размер задаётся один раз при подключении.
- **Терминал: разделительные линии Claude Code** — `─` (U+2500) могут быть тусклыми/невидимыми
- **Терминал: synchronized update mode** — `?2026h/l` не реализован, возможны косметические мерцания при перерисовке Claude Code
- **Терминал: debug логи** — TermBuf PUT/CUU/CUD логи включены, убрать когда стабильно

### Решённые проблемы ввода терминала

- **Enter удалял последний символ** — клавиатура при тапе Enter модифицировала скрытый TextField (укорачивала текст), `onValueChange` видел укорочение и отправлял BACKSPACE вместо ENTER. **Решение:** `pendingBackspace` флаг + delay 150мс. При укорочении текста — не отправлять backspace сразу, а ждать. Если за 150мс `keyboardActions.onDone` вызовется → это Enter (backspace отменяется). Если нет → реальный backspace. Плюс `ImeAction.Done` вместо `ImeAction.None` — чтобы `keyboardActions.onDone` гарантированно срабатывал.
- **Caps Lock после каждого символа** — sentinel `" "` (пробел) сбрасывался → Android считал начало предложения → включал Caps. **Решение:** sentinel `"x "` (символ + пробел) — Android видит середину слова.
- **Свайп/предсказания не работали** — `autoCorrectEnabled = false` отключал всю клавиатурную магию. **Решение:** `autoCorrectEnabled = true`.
- **История команд** — долгий тап ↑ в quick panel → выезжающий список (AnimatedVisibility + slideInVertically) до 200 команд. Тап по команде → вставляет (Ctrl+U + sendChar) и закрывает. Кнопка ✕ для удаления. Повторный долгий тап → скрывает. Команды захватываются из buffer grid при Enter (ищет промпт `$ `/`# `/`> `, берёт текст после него). Фильтрует пустые, пароли, дубликаты. Persist в SharedPreferences.
- **LazyColumn в DropdownMenu крашит** — `IllegalStateException: Asking for intrinsic measurements of SubcomposeLayout`. Решение: `Column` + `verticalScroll` вместо `LazyColumn`.

---

## Журнал решений

> Выполненные задачи с описанием как решили.

### Batch 100 — Сетевой стриминг + DND фикс + SMB/FTP сохранение серверов ✅ проверено

**Стриминг медиа по сети (без скачивания):**
- FTP/FTPS/SFTP: тап на медиа-файл → HTTP-прокси (телефон читает с FTP, отдаёт VLC по HTTP). Обходит баг VLC с кириллицей/пробелами в FTP-путях
- SMB: тап на медиа-файл → VLC стримит по `smb://` URL напрямую
- WebDAV: тап на медиа-файл → VLC стримит по `http://`/`https://` URL напрямую
- Плейлист: медиа-файлы в текущей папке (до 50 вокруг выбранного) добавляются в плейлист
- `VlcPlayerAdapter` — поддержка `smb://`, `http://`, `https://` URI напрямую; FTP/SFTP через HTTP-прокси
- `HttpFileServer` — новый endpoint `/ftp-proxy?host=X&port=Y&path=Z&proto=ftp|sftp` с Range-поддержкой (перемотка)
- `FtpClientManager` — новые методы `getFileSize()`, `openInputStream(offset)` для стриминга
- `SftpClientManager` — аналогичные методы для SFTP
- Не-медиа файлы — по-прежнему скачиваются
- **Известный баг VLC**: libVLC не поддерживает FTP с не-ASCII путями (баг #26963, #1475, не исправлен). Наш HTTP-прокси решает проблему

**DND фикс — звук при плеере:**
- `DndManager`: заменён `RINGER_MODE_SILENT` на `RINGER_MODE_VIBRATE` + заглушение `STREAM_NOTIFICATION`
- Медиа-громкость не блокируется, кнопки громкости работают при DND
- Уведомления подавляются, рингтон на вибрации — не мешает смотреть видео

**SMB — сохранение серверов с никнеймами:**
- `SmbCredential` — добавлено поле `displayName`, сохраняется в зашифрованном хранилище
- `SmbSavedServerItem` — показывает никнейм (крупно) + адрес (мелко серым)
- Кнопка карандаша → диалог переименования сервера
- Кнопка удаления (LinkOff) — удаление из сохранённых
- Гостевое подключение автоматически сохраняется с пометкой "(Гость)"
- При тапе на сохранённый гостевой сервер → подключение без пароля

**FTP — сохранение серверов с никнеймами:**
- Анонимное подключение автоматически сохраняется с пометкой "(Анонимно)"
- Кнопка карандаша → диалог переименования, кнопка удаления
- Хлебные крошки: тап на IP → переход в корень FTP (вместо отключения)
- UTF-8 поддержка: `setAutodetectUTF8(true)` + `OPTS UTF8 ON` — русские имена файлов отображаются корректно

**Навигация:**
- `selectedTab` в TransferScreen через `rememberSaveable` — при возврате из плеера остаётся на том же табе (SMB/FTP/WebDAV)

### Batch 99 — BT HID таймаут + каст при выключенном экране ⚠️ BT HID не проверено / ✅ каст проверено

**BT HID — таймаут при отсутствии поддержки:**
- `BluetoothHidManager.init()` — добавлен таймаут 3 сек на ожидание `onServiceConnected`. Если профиль HID Device отсутствует в Bluetooth-стеке (Sony, некоторые OEM) — через 3 сек выставляется `HidConnectionState.NotSupported` вместо бесконечного ожидания
- `getProfileProxy returned false` — тоже сразу ставит `NotSupported`
- `BtDevicePickerDialog` — при `NotSupported` показывает понятное сообщение + кнопку «Wi-Fi пульт» (пока TODO)
- Обновлены строки EN/RU — вместо "требуется Android 9+" теперь "отсутствует профиль HID Device"

**Каст при выключенном экране:**
- `CastMediaService` — добавлен `WifiLock` (`WIFI_MODE_FULL_HIGH_PERF`): Wi-Fi не отключается при выключении экрана, HTTP-сервер остаётся доступным для ТВ
- `WakeLock` привязан к длительности медиа: duration файла + 5 минут буфер (мин 30 мин, макс 6 часов). Fallback 4 часа если duration неизвестен
- `CastViewModel` — добавлен `getMediaDurationMs()` через `MediaMetadataRetriever`, duration передаётся в сервис во всех 3 сценариях: DLNA прямой, Chromecast прямой, HLS (транскод)
- Foreground сервис стартует **до** транскода (а не после), чтобы WakeLock+WifiLock защищали процесс во время подготовки
- HLS сегменты увеличены 10→30 сек, порог начала каста 30→60 сек (2 сегмента буфера)

**TODO каст — буферизация при 100% транскоде:**
- Сегменты ~10-12 МБ = нужна стабильная скорость Wi-Fi ~10 Мбит/с. При просадках — кружок загрузки на ТВ
- Вариант 1: снизить битрейт транскода (8M → 4-5M) — меньше размер сегментов, визуально минимальная разница на ТВ
- Вариант 2: 30-сек сегменты (уже сделано) дают больший буфер, устойчивость к просадкам

### Batch 98 — Termux-стиль терминал, SSH через JSch, VT100 эмуляция ⚠️ не проверено

**Терминал — полная переделка в стиль Termux:**
- Один экран, нет табов Shell/SSH/AI
- Ввод напрямую в PTY через скрытый 1dp TextField (каждый символ → sendRaw)
- Sentinel `"x "` вместо `" "` — убрал автоматический Caps Lock
- Quick keys: Esc, Tab, ^C/D/Z/L, Paste, ↑↓←→, Home, End, символы
- Клавиатура: `focusRequester + keyboardController?.show()` после SSH-диалога

**SSH через JSch (push I/O):**
- Кнопка SSH в хедере → диалог (host, user, port, пароль, save)
- JSch: `getInputStream()` до connect + `available()` + `Thread.sleep(50)` в `withContext(Dispatchers.IO)`
- `sendRaw` = `suspend fun` с `withContext(Dispatchers.IO)` — ОБЯЗАТЕЛЬНО, иначе pipe ломается
- `setPtySize()` отключён — ломает JSch PipedInputStream
- PTY size из buffer.cols/rows при connect через `setPtyType()`
- Disconnect → возврат в shell, кнопка disconnect в хедере
- Keepalive 30s, пароль AES-256

**TerminalBuffer — VT100/xterm по паттернам Termux:**
- Alternate screen buffer (?1049h/l, ?47h/l)
- Scroll regions (DECSTBM), origin mode, auto-wrap
- `aboutToAutoWrap` — курсор в последней колонке, wrap при следующем символе
- Раздельные saved states main/alt screen
- Erase с текущим стилем (не дефолтным)
- Tab stops настраиваемые (ESC H, CSI g)
- Wide chars: `wcWidth()`, trail-ячейки. Dingbats (U+2600-27BF) = width 1
- DEC modes: cursor visibility, bracketed paste, focus events

**TerminalGrid:**
- DejaVu Sans Mono (2.1MB, полное Unicode)
- Bar cursor (2dp линия) вместо block
- Wide char trail cells пропускаются
- Pinch-to-zoom (4-24sp), debounce resize 250ms

**ExplorerViewModel:**
- ВСЕ файловые операции обновляют ОБЕ панели

**Файлы:** TerminalViewModel.kt, TerminalScreen.kt, TerminalGrid.kt, TerminalBuffer.kt, SshSessionManager.kt, ExplorerViewModel.kt, features.txt (EN+RU), DejaVuSansMono.ttf, JetBrainsMono.ttf

---

### Batch 97 — Терминал: надёжный resize, raw mode по ANSI, обновление обеих панелей ⚠️ поглощён Batch 98

**Надёжный pinch-to-zoom + resize:**
- `fontSizeSp` вынесен из TerminalGrid в TerminalScreen — отдельный для Shell и SSH, не теряется при смене таба
- TerminalGrid принимает fontSizeSp + onFontSizeChanged как параметры
- `onSizeCalculated` вызывается через `LaunchedEffect` с debounce 250мс (trailing edge) — без SIGWINCH-шторма при pinch
- Убран дублирующий resize в `onSizeChanged` — одна точка расчёта cols/rows
- `resizePty()` ресайзит ОБА (Shell PTY + SSH channel), не только activeTab — оба всегда знают актуальный размер

**Raw mode по факту ANSI, не по имени команды:**
- Раньше: `claude`/`vi`/`htop` → raw mode включался СРАЗУ → пользователь не мог ничего набрать пока приложение не запустилось
- Теперь: команда → `pendingRawMode = true` → raw mode включается только когда приходит ANSI-вывод (`\u001B[`) от приложения
- Логирование активации/деактивации raw mode

**Выход из raw mode:**
- `^C` в SSH-панели теперь отправляет сигнал И выходит из raw mode (раньше только сигнал)
- Кнопка `RAW` (красная) появляется в SSH-панели когда raw mode активен — тап = выход
- Кнопка Send работает в raw mode — отправляет Enter (раньше была disabled из-за пробела-sentinel)

**Обновление обеих панелей после операций:**
- ВСЕ файловые операции (copy, move, delete, rename, duplicate, archive, extract) теперь обновляют ОБЕ панели
- Облачные операции (download, upload, delete, create folder, DnD) — обе панели
- `refreshBothIfSamePath()` → всегда обе панели безусловно

**AI-таб убран из features.txt** — «Three tabs: Shell, SSH, AI» → «Two tabs: Shell and SSH»

**Файлы:** TerminalGrid.kt, TerminalScreen.kt, TerminalViewModel.kt, ExplorerViewModel.kt, features.txt, features_ru.txt

---

### Batch 96 — Терминал: pinch-zoom, SSH resize, copy/paste, полная изоляция табов ⚠️ не проверено

**Pinch-to-zoom шрифта:**
- `detectTransformGestures` для pinch — Compose API, без конфликтов
- Шрифт 4–24sp, по умолчанию 8sp
- При изменении шрифта grid синхронно пересчитывает cols/rows
- Paint пересоздаётся через `remember(fontSizeSp)` — charWidth корректно обновляется
- Отступ 4dp слева и справа в Canvas

**SSH resize:**
- `SshSessionManager.resizePty()` — `channel.setPtySize()` при изменении размера grid
- SSH-сервер получает SIGWINCH и перерисовывает вывод
- `resizePty()` в ViewModel маршрутизирует по activeTab (Shell или SSH)

**Copy/Paste:**
- Paste: кнопка в панели быстрых клавиш — вставляет из буфера обмена в терминал
- Long tap + drag для выделения текста — временно убрано (конфликт с pinch)

**Полная изоляция табов:**
- rawMode раздельный: shellRawMode / sshRawMode
- sendChar/sendEnter/sendRaw маршрутизируются по activeTab
- activeTab синхронизируется из UI
- История команд по activeTab, не по sshMode
- `claude` добавлен в список интерактивных приложений (raw mode в SSH)

**AI-таб убран** — OAuth токены заблокированы Anthropic для сторонних приложений

**Клавиатура:** KeyboardType.Text вместо Password — русский ввод не сбрасывается

**Файлы:** TerminalGrid.kt, TerminalViewModel.kt, TerminalScreen.kt, SshSessionManager.kt

---

### Batch 95 — Терминал: grid renderer, tabs, раздельные буферы ✅ проверено

**Grid renderer (Canvas):**
- TerminalBuffer: rows×cols матрица с полной ANSI-поддержкой (cursor, erase, scroll, SGR)
- TerminalGrid: Canvas-рендер с авторесайзом по размеру экрана
- vi/nano/htop отрисовываются полноценно (тильды, статус-бар, курсор)

**Raw mode для интерактивных приложений:**
- Автодетект vi/nano/htop → каждая клавиша сразу в PTY
- Backspace работает (DEL 0x7F)
- Автовыход из raw mode при появлении промпта shell
- История команд не засоряется вводом из vi

**Три таба: Shell | SSH | AI:**
- Отдельный grid-буфер для Shell и SSH
- Отдельное поле ввода для каждого таба
- Отдельная история команд (200 на каждый)
- Автопереключение на SSH-таб при подключении
- AI-таб: заглушка "Coming soon"

**SSH keepalive:**
- `setServerAliveInterval(30000)` — пинг каждые 30 сек
- Сессия не отваливается при idle

**Панель быстрых клавиш:**
- Esc, Enter, Tab, ^C, ^D, ^Z, ↑↓←→ + символы

**Файлы:** TerminalBuffer.kt, TerminalGrid.kt, TerminalViewModel.kt, TerminalScreen.kt, SshSessionManager.kt

---

### Batch 94 — Терминал: persistent shell + PTY ✅ проверено

**Persistent shell с настоящим PTY:**
- Нативный JNI модуль `pty.c` (~120 строк C): `forkpty()` → создаёт дочерний процесс с PTY
- `PtyNative.kt` — Kotlin JNI-обёртка: createSubprocess, setWindowSize, sendSignal, waitFor
- `ShellSession.kt` — управление сессией: start, sendCommand, sendRaw, sendInterrupt, resize, stop
- CMake + NDK сборка (`app/src/main/cpp/CMakeLists.txt`)

**Что работает:**
- ✅ Persistent shell — export, alias, cd живут между командами
- ✅ Ctrl+C прерывает команду мгновенно (настоящий SIGINT через PTY)
- ✅ Поле ввода не блокируется во время выполнения команды
- ✅ Автоперезапуск shell при крэше
- ✅ Ctrl+D (EOF), Ctrl+Z (suspend)
- ✅ Resize PTY: `PtyNative.setWindowSize()`

**Что пока НЕ работает:**
- ❌ vi/nano/htop — нужен grid-рендер (ANSI cursor positioning), сейчас построчный LazyColumn
- ❌ Табы Shell/SSH/AI — следующий батч

**Файлы:** pty.c, CMakeLists.txt, PtyNative.kt, ShellSession.kt, TerminalViewModel.kt, TerminalScreen.kt, build.gradle.kts

---

### Batch 93 — Видеоплеер: свайпы, настройки DND, описание функций ⚠️ не проверено

**Свайп-жесты в видеоплеере:**
- Свайп вверх/вниз по левой половине → яркость (0–100)
- Свайп вверх/вниз по правой половине → громкость (0–100)
- Свайп вправо от левого края (< 30dp) → выход из плеера
- Единый `awaitEachGesture` handler, совместим с тап-зонами (перемотка/пауза)
- Индикатор-полоска с иконкой и значением при свайпе

**Настройки плеера:**
- Иконка шестерёнки (⚙) в top bar → полноэкранный overlay настроек (видео на паузе)
- Два таба: Видео / Аудио — независимые настройки DND для каждого
- DND через `NotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_PRIORITY)`
- 5 категорий: мастер-тогл, что пропускать, кто звонит, визуальные эффекты, вибрация
- `ACCESS_NOTIFICATION_POLICY` permission + системный грант
- `DndManager` сохраняет/восстанавливает оригинальное состояние DND и ringer mode
- Настройки раздельны по ключам: `vdnd_*` для видео, `adnd_*` для аудио

**Тап в неактивной панели:**
- Короткий тап в области файлов неактивной панели только активирует панель — файл не открывается
- Долгий тап работает как обычно (выделение + DnD)
- Шапка панели не затронута — кнопки работают всегда

**Тост по размеру:**
- Тап по отображению размера в хлебных крошках → тост с размером папки / общий объём хранилища
- Работает и в корне (StorageStatsManager), и в подпапках (StatFs + folderSizeCache)

**Реорганизация features.txt (EN + RU):**
- Объединены 9 секций: SAF+USB→Хранилища, Lock+Protected→Безопасность, GlobalSearch+LocalSearch→Поиск, Transfer+SMB+FTP+QuickSend→Сеть, Cast+BT HID→ТВ, Bookmarks+Tools→Разделитель, Gestures+Voice→Жесты и голос, Widget+DefaultOpener→в Навигацию, Appearance→в Настройки
- Медиаплеер: подзаголовки ## Видео и ## Аудио
- FeaturesScreen: парсер `##` подзаголовков + стрелка → у заголовков с деталями
- FeatureCategoryDetailScreen: полноэкранный экран инструкций по тапу на стрелку
- BackHandler для возврата в список + сохранение позиции скролла
- Формат `> ` для деталей в features.txt
- Полное флоу для ВСЕХ категорий: как открыть, что делает каждая кнопка, пошаговые инструкции, голосовые команды

**Файлы:** PlaybackService.kt, VlcPlayerAdapter.kt, MediaPlayerScreen.kt, PlayerSettingsScreen.kt, DndManager.kt, HaronPreferences.kt, HaronNavigation.kt, FeaturesScreen.kt, FilePanel.kt, ExplorerViewModel.kt, AndroidManifest.xml, features.txt (EN+RU), strings.xml (EN+RU)

---

### Batch 92 — Фикс AVI артефактов (VLC decoder) ✅ проверено

**Проблема:** после Batch MKV-фикса (86926d5) AVI файлы показывали жуткие артефакты. Причина: `setHWDecoderEnabled(true, true)` принудительно использовал HW-декодер для XviD/DivX (MPEG-4 ASP), который MediaCodec не поддерживает нормально. Плюс `--avcodec-skiploopfilter=4` пропускал весь деблокинг.

**Решение:**
- `VlcPlayerAdapter.kt`: whitelist расширений для HW-декодера (mkv, mp4, m4v, mov, webm, ts, m2ts). Остальные (avi, wmv, flv и т.д.) → софтверный декодер
- `PlaybackService.kt`: `--avcodec-skiploopfilter=0` — не пропускать деблокинг
- Добавлено логирование выбора декодера

**Файлы:** `VlcPlayerAdapter.kt`, `PlaybackService.kt`

---

### Batch 54 — Облака + Пульт ТВ ✅ проверено

**Облачные хранилища (Google Drive, Dropbox, Yandex Disk):**
- OAuth2 PKCE через custom URI scheme `haron://oauth/{provider}` (deep link)
- `CloudOAuthHelper` — генерация code_verifier/code_challenge, обмен кода на токен
- `CloudManager` — singleton facade (аналог SmbManager) для всех провайдеров
- `CloudTokenStore` — зашифрованное хранение токенов (AES-256-GCM, Android Keystore)
- `CloudProviderInterface` — общий интерфейс: listFiles, download, upload, delete, createFolder, getAuthUrl, handleAuthCode
- 3 провайдера: `GoogleDriveProvider`, `DropboxProvider`, `YandexDiskProvider`
- 5 use cases: CloudListFiles, CloudDownload, CloudUpload, CloudDelete, CloudCreateFolder
- `CloudModule` (Hilt DI) + `CloudAuthDialog` (UI авторизации в ExplorerScreen)
- Навигация: `cloud://gdrive/`, `cloud://dropbox/`, `cloud://yandex/` в панели
- Тап на облачный файл → скачивание в cacheDir/cloud_downloads/ → открытие локально
- Копирование между панелями: cloud→local = download, local→cloud = upload
- Перемещение cloud-файлов заблокировано (только copy)
- Удаление: через CloudManager.delete()
- Создание папки в облаке: `CloudCreateFolderDialog`
- Прогресс скачивания/загрузки: `CloudTransferDialog`
- Deep link handling: `AndroidManifest.xml` (intent-filter) + `MainActivity.kt` (processExternalIntent)

**Пульт ТВ (Remote Control):**
- `RemoteInputChannel` — WebSocket-канал для команд пульта (мышь, клавиатура, клики)
- `TouchpadPanel` — тачпад с поддержкой перемещения курсора, тапа, long press, скролла
- `VirtualKeyboardPanel` — виртуальная клавиатура для ввода текста на ТВ
- Интегрированы в CastOverlay как дополнительные режимы пульта

**Файлы (новые):**
- `data/cloud/CloudOAuthHelper.kt`
- `data/cloud/CloudManager.kt`, `CloudTokenStore.kt`
- `data/cloud/provider/CloudProviderInterface.kt`, `GoogleDriveProvider.kt`, `DropboxProvider.kt`, `YandexDiskProvider.kt`
- `di/CloudModule.kt`
- `domain/model/CloudAccount.kt`, `CloudFileEntry.kt`, `CloudProvider.kt`, `CloudTransferProgress.kt`
- `domain/usecase/CloudListFilesUseCase.kt`, `CloudDownloadUseCase.kt`, `CloudUploadUseCase.kt`, `CloudDeleteUseCase.kt`, `CloudCreateFolderUseCase.kt`
- `presentation/cloud/CloudAuthDialog.kt`, `CloudTransferDialog.kt`, `CloudCreateFolderDialog.kt`
- `data/cast/RemoteInputChannel.kt`
- `presentation/cast/components/TouchpadPanel.kt`, `VirtualKeyboardPanel.kt`

**Файлы (изменённые):**
- `AndroidManifest.xml` (OAuth deep link), `MainActivity.kt` (OAuth callback)
- `ExplorerUiState.kt` (+CloudTransfer, +CloudCreateFolder), `ExplorerViewModel.kt` (cloud file ops)
- `ExplorerScreen.kt` (cloud dialogs), `DrawerMenu.kt` (cloud entries)
- `CastOverlay.kt`, `CastViewModel.kt`, `MediaRemotePanel.kt` (remote input integration)
- `strings.xml` EN+RU (cloud strings)

**⚠️ Placeholder credentials**: `YOUR_GOOGLE_CLIENT_ID`, `YOUR_DROPBOX_APP_KEY`, `YOUR_ONEDRIVE_CLIENT_ID` — нужно заменить реальными ключами из консолей разработчиков

### Shizuku — доступ к Android/data и Android/obb ✅ проверено

На Android 11+ папки Android/data и Android/obb недоступны даже с MANAGE_EXTERNAL_STORAGE. Shizuku (UserService UID 2000) обходит FUSE-ограничения.

**Цепочка fallback:** `File.listFiles()` → SAF → Shizuku → пустой список

**Файлы:** `data/shizuku/` (AIDL + ShizukuFileEntry + ShizukuFileService + ShizukuManager), `ShizukuInstallDialog.kt`, `FileRepositoryImpl.kt` (fallback + enrichment childCount), `ExplorerViewModel.kt` (ShizukuState проверка), `ExplorerScreen.kt` (диалоги), `SettingsScreen.kt` (секция статуса), `AndroidManifest.xml` (ShizukuProvider), `proguard-rules.pro`, `strings.xml` EN+RU

**Исправленные баги:** File.exists() false для restricted paths → обход; Shizuku.unbindUserService 3 параметра; childCount без скрытых файлов; enrichment через IPC для родительского Android/

### Удаление из превью картинок ✅ проверено

Кнопка корзины справа от «Открыть в галерее» в QuickPreviewDialog для всех изображений. Удаляет в корзину без подтверждения. При удалении превью переходит на следующее/предыдущее фото, закрывается только если файл был последним.

**Файлы:** `QuickPreviewDialog.kt` (+onDelete, Row с кнопками, isImage по расширению при loading), `ExplorerViewModel.kt` (+deleteFromPreview, +silentDelete — удаление без dismiss диалога), `ExplorerScreen.kt` (onDelete callback)

**Нюансы:** silentDelete вместо confirmDelete (тот вызывает dismissDialog); isImage учитывает iconRes() при loading чтобы кнопки не исчезали; предзагрузка соседей после каждого удаления

### Корзина — фикс размеров + диалог переполнения ✅ проверено

**Баг размеров (с первого коммита dd34d79):** `src.isDirectory` вызывался после `renameTo()` → `src` уже не существует → `false` → папки записывались с размером directory entry (~3 КБ) вместо реального.
**Фикс:** `isDir` сохраняется до `renameTo`. `getTrashEntries()` пересчитывает реальные размеры с диска. `getTrashSize()` считает по файлам, не по meta.json.

**Диалог переполнения:** если размер удаляемых файлов > лимита корзины → диалог «Корзина слишком мала, удалить безвозвратно?» (как в Windows).

**Файлы:** `TrashRepositoryImpl.kt`, `ExplorerUiState.kt` (+TrashOverflow), `ExplorerViewModel.kt`, `ExplorerScreen.kt`, `strings.xml` EN+RU

### Диалоги создания/извлечения архивов ✅ проверено

**Создание архива — конфликт имён:**
- При создании ZIP, если файл с таким именем уже существует → диалог: Заменить / Переименовать / Отмена
- Раньше молча переименовывал (archive → archive (1).zip)
- `findUniqueZipPath()` перенесён в companion object `CreateZipUseCase`

**Извлечение — выбор места:**
- При извлечении архива → диалог: "Извлечь сюда" / "Извлечь в папку «имя_архива»"
- Если в архиве одна корневая папка — диалог не показывается, извлекается как есть (toast-подсказка)
- Работает и из archive mode (просмотр архива), и из обычной папки (выделенный .zip)
- Конфликт имён проверяется ПОСЛЕ выбора варианта (потому что destination может измениться)

**Файлы:** `ExplorerUiState.kt` (+ArchiveCreateConflict, +ArchiveExtractOptions), `ExplorerViewModel.kt` (рефакторинг confirmCreateArchive/extractFromArchive/extractSelectedArchiveFiles), `ExplorerScreen.kt` (+2 диалога), `ExtractOptionsDialog.kt` (новый), `CreateZipUseCase.kt` (findUniqueZipPath → companion), `strings.xml` EN+RU

### QuickSend — редизайн оверлея ✅ проверено

**Было:** круглые иконки устройств, расположенные дугой.
**Стало:** вертикальный список горизонтальных элементов с полным именем устройства + marquee-анимация для длинных имён.
- Позиционирование: если drag из нижней панели → оверлей вверху, из верхней → внизу
- Hit-test через `onGloballyPositioned` + `boundsInWindow()` (без ручного расчёта координат)
- Marquee: custom `Modifier.layout` с `Constraints.Infinity` для unbounded измерения

**Файлы:** `QuickSendOverlay.kt` (полная перезапись), `QuickSendState.kt` (+fromTopPanel), `ExplorerViewModel.kt` (startQuickSend + endQuickSendAtPosition), `ExplorerScreen.kt` (fromTopPanel=true/false)

### R8 crash-аудит — Exception→Throwable ✅ проверено

**Проблема:** R8 стрипает зависимости библиотек → `NoClassDefFoundError` (extends Error, не Exception). `catch (Exception)` не ловит Error-подклассы → краш.
- Apache POI: R8 убрал Apache Commons → `NoClassDefFoundError: HWPFDocument`
- Потенциально: FFmpegKit, 7-Zip-JBinding, JSch, smbj, junrar

**Решение:**
- ProGuard: `-keep class org.apache.commons.** { *; }` (все Apache Commons, не только compress)
- 16 файлов: `catch (Exception)` → `catch (Throwable)` вокруг вызовов внешних библиотек
- FTS triggers: `dropFtsTriggers()` перемещён в начало try-блока в `indexAllFiles`, `indexByMode`, `indexFolderContent`

**Файлы:** `proguard-rules.pro`, `SearchRepositoryImpl.kt`, `ContentExtractor.kt`, `DocumentParser.kt`, `LoadPreviewUseCase.kt`, `ThumbnailCache.kt`, `PdfReaderScreen.kt`, `TranscodeVideoUseCase.kt`, `BrowseArchiveUseCase.kt`, `ExtractArchiveUseCase.kt`, `SshSessionManager.kt`, `SmbManager.kt`

### OOM fix — mutex для индексации ✅ проверено

**Проблема:** глобальная индексация (`indexByMode`) и поиск по содержимому папки (`indexFolderContent`) работали одновременно → OOM (253MB/256MB heap).

**Решение:**
- `Mutex` (`indexingMutex`) — все три метода индексации (`indexAllFiles`, `indexByMode`, `indexFolderContent`) эксклюзивны
- `cancelGlobalIndexing()` — при запуске `indexFolderContent` автоматически отменяется глобальная индексация (сохранённый `Job`)
- Лимит контента: 256 KB текста на файл (`MAX_CONTENT_LENGTH`)
- Периодический flush батчей (каждые 50 файлов) + GC (каждые 20 файлов) в `indexFolderContent`

**Файлы:** `SearchRepository.kt` (+cancelGlobalIndexing), `SearchRepositoryImpl.kt` (mutex, job, content limit, GC)

### SMB — утечка IPC$ соединений + фильтр подсетей ✅ проверено

**Проблема 1:** `listShares()` вызывал `SMBTransportFactories.SRVSVC.getTransport(session)`, который каждый раз открывал новый `IPC$` share через `session.connectShare("IPC$")`. Share никогда не закрывался → накопление соединений.

**Решение:** ручное создание транспорта (`PipeShare` + `NamedPipe` + `SMBTransport` + `bind`) с закрытием `IPC$` share в `finally`.

**Проблема 2:** subnet scan по порту 445 заполнял список SMB-серверов десятками IP-адресов из подсети.

**Решение:** в `SmbViewModel` отфильтрованы устройства с `id.startsWith("SMB_SCAN_")` — показываются только NSD-обнаруженные серверы.

**Файлы:** `SmbManager.kt` (ручной транспорт, close IPC$, close stale shares, логирование), `SmbViewModel.kt` (фильтр SMB_SCAN_)

### R8/ProGuard + Release signing ✅ проверено

**Что сделано:**
- Включён R8: `isMinifyEnabled = true`, `isShrinkResources = true` в release build type
- Написаны ProGuard-правила для всех библиотек: Room entities/DAOs (Haron + ecosystem-core), VLC, FFmpegKit, 7-Zip-JBinding, JSch, smbj, BouncyCastle, Apache POI, PDFBox, Ktor, Tesseract, libaums, ML Kit, ZXing, Google Cast
- Добавлены dontwarn для missing classes (JNA/Windows API, RMI, JGSS/Kerberos, OSGI, Unix sockets, log4j, mbassy)
- Signing config: keystore из `local.properties` (gitignored), alias `key0`
- Gradle heap увеличен с 2 ГБ до 4 ГБ (R8 release сборка требует больше памяти)

**Файлы:** `app/build.gradle.kts` (signingConfig + R8), `app/proguard-rules.pro` (полные правила), `gradle.properties` (heap 4G), `local.properties` (keystore пароли, gitignored)

### Batch 53 — HLS-транскодирование + прогрессивный каст ✅ проверено

**Проблема:** Media3 Transformer (аппаратный MediaCodec) даёт артефакты и дёрганье при транскодировании AVI/MKV→MP4. Chromecast не поддерживает mpeg4 кодек, chunked transfer не работает (Chromecast ждёт Content-Length).

**Решение — HLS прогрессивный каст:**
- FFmpeg (`ffmpeg-kit-16kb:6.1.1`) транскодирует видео в HLS-сегменты (.ts по 10 сек) + плейлист (.m3u8)
- Приоритет энкодеров: `libx264` > `h264_mediacodec` > `mpeg4` (определяется через `-encoders`)
- Параметры: `-profile:v high -level:v 4.1 -maxrate 8M -bufsize 16M -g 60 -r 30 -c:a aac -ac 2 -b:a 192k`
- `-ac 2` обязательно — Chromecast не поддерживает многоканальный AAC (AC3 5.1 → AAC stereo)
- `-g 60` — ключевые кадры каждые 2 сек (без этого h264_mediacodec ставит gop_size=1)
- HLS version 3 (не 6!) — Chromecast отказывается загружать сегменты при version 6 (`independent_segments`)
- `-hls_playlist_type event` — плейлист растёт, сегменты не удаляются

**HTTP-сервер (HttpFileServer):**
- Эндпоинт `/hls/{filename}` вместо старого `/stream-live`
- Content-Type: `.m3u8` → `application/vnd.apple.mpegurl`, `.ts` → `video/mp2t`
- CORS: `Access-Control-Allow-Origin: *` — Chromecast использует JS-based HLS плеер
- `respondOutputStream(contentType = ct)` вместо `respondFile()` — иначе .m3u8 получает `application/octet-stream`

**CastViewModel — потоковый каст:**
- `readyToStream` (30 сек = 3 сегмента) → `castHls(live=true)` с `STREAM_TYPE_LIVE`
- Завершение транскода → `reloadHlsAsVod()` с `STREAM_TYPE_BUFFERED` → перемотка работает
- Reconnect: проверяет `file.isDirectory && File(file, "playlist.m3u8").exists()` для HLS-директорий

**GoogleCastManager:**
- Параметр `streamType` в `castMedia()` (default `STREAM_TYPE_BUFFERED`)
- Живой HLS → `STREAM_TYPE_LIVE`, завершённый → `STREAM_TYPE_BUFFERED`

**Кеш:**
- `filesDir/transcode_cache/hls_<hash>/` — стойкий, переживает смахивание
- TTL-очистка при каждом новом транскоде (настраивается: 1ч / 6ч / 12ч / 24ч)
- Версия кеша в хеше: `q6` (инвалидация старого)

**Дополнительно:**
- Ручной `HaronWorkerFactory` вместо `@HiltWorker` (обход Dagger KSP бага `@AssistedFactory` + generic)
- Убран debug: `_debugCastInfo`, `audioStripped`, синий фон транскода в MediaRemotePanel

**Файлы:** `TranscodeVideoUseCase.kt`, `CastViewModel.kt`, `HttpFileServer.kt`, `GoogleCastManager.kt`, `MediaRemotePanel.kt`, `HaronApp.kt`, `FileIndexWorker.kt`, `WorkerModule.kt` (новый), `HaronPreferences.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `strings.xml` (EN+RU), `app/build.gradle.kts`

### Голосовые команды Level 1 + Level 2 ✅ проверено

- **FuzzyMatch** (`common\util\FuzzyMatch.kt`): Levenshtein distance O(n*m), normalized similarity 0..1, `findBestMatch(query, candidates, threshold)` — exact > contains > startsWith > fuzzy.
- **14 GestureAction**: NAVIGATE_BACK, NAVIGATE_FORWARD, NAVIGATE_UP, DELETE_SELECTED, COPY_SELECTED, MOVE_SELECTED, RENAME, CREATE_ARCHIVE, EXTRACT_ARCHIVE, FILE_PROPERTIES, DESELECT_ALL, NAVIGATE_TO_FOLDER, REFRESH_FOLDER_CACHE, OPEN_SECURE_FOLDER.
- **VoiceCommandManager**: полностью переписан.
  - Pipeline: tryMatchSort → tryMatchLogs → tryMatchRename → tryMatchNavigation → PHRASE_MAP.
  - **Wake word "Харон"**: непрерывное прослушивание, при обнаружении "харон" + команда → выполнение. "Харон" без команды → WAKE_ACTIVATED, ожидание команды. Auto-restart на timeout/no-match. Сохраняется в preferences.
  - **5 вариантов wake word**: харон, харун, хорон, хару, haron — все альтернативы распознавания проверяются (не только первая).
  - **Panel override**: `pendingPanelOverride` — детектор "вверху"/"внизу"/"верхн"/"нижн" в фразе → PanelId.TOP/BOTTOM. Позволяет "назад вверху" → back в верхней панели.
  - `pendingFolderQuery`, `pendingRenameName` + consume-методы.
  - Таймауты: wake=5с/3с, manual=1.5с/1с, wake_activated=1.5с/1с.
  - VoiceState: IDLE, LISTENING, PROCESSING, ERROR, WAKE_LISTENING, WAKE_ACTIVATED.
  - **Lifecycle**: pause() / resume() — останавливает микрофон при уходе в фон, возобновляет при возврате.
- **ExplorerViewModel**: `executeGestureAction` использует `consumePanelOverride()` для определения целевой панели. Автозакрытие диалога/меню/полки при голосовой команде. `navigateToFolderFromVoice` — кэш папок (permanent, сортировка по глубине — корневые приоритетнее вложенных, обновление по команде "обнови кэш"). `folderStemAliases` — стем-маппинг русских названий (камер*→Camera, загрузк*→Download и т.д.), обрабатывает все падежные формы. `OPEN_SECURE_FOLDER` → `showAllProtectedFiles()` (запрашивает PIN/биометрию).
- **GesturesVoiceViewModel** + **GesturesVoiceScreen**: toggle "Активация голосом «Харон»".
- **VoiceFab**: индикатор wake mode (tertiary цвет для WAKE_LISTENING, primary для WAKE_ACTIVATED). Init wake word из preferences при первой композиции. Lifecycle observer: ON_STOP → pause, ON_START → resume.
- **FuzzyMatchTest**: 11 тестов (levenshtein, similarity, findBestMatch).

### Передача файлов — корпоративные сети, системная точка доступа, QR UX ✅ проверено

**Проблема корпоративных сетей (CGNAT):**
- `startLocalOnlyHotspot()` не может создать интерфейс SoftAP пока активен Wi-Fi клиент (HAL ошибка). `setWifiEnabled(false)` не работает на Android 10+ для не-системных приложений
- На некоторых устройствах `startLocalOnlyHotspot()` не работает вообще (даже без Wi-Fi)
- Корпоративные сети возвращают CGNAT IP (100.64.0.0/10) — недоступен для других устройств

**Решение — диалог "Включите точку доступа":**
- При неудаче (CGNAT IP или нет сети) показывается диалог с кнопкой "Открыть настройки точки доступа"
- Кнопка открывает `android.settings.TETHERING_SETTINGS` — пользователь включает системную точку
- После возврата — тап на QR → IP автоматически определяется с системной точки

**Улучшенное определение IP (`getLocalIpAddress()`):**
- CM-путь: пропускает CGNAT IP, ищет дальше
- Fallback: собирает все IP, приоритет: non-CGNAT на wlan/ap > другие > CGNAT
- Корректно находит IP системной точки доступа (ap0/swlan0)

**Отслеживание скачиваний:**
- `HttpDownloadEvent` + `SharedFlow` в HttpFileServer — эмит после каждого скачанного файла
- TransferViewModel подписывается и обновляет прогресс (файлы/байты)
- Счётчик "Отправлено X из Y — N Б / M Б" обновляется при каждом скачивании целевым устройством
- По завершении всех файлов → статус COMPLETED

**Двухшаговый QR с блюром:**
- Если есть Wi-Fi QR (ручной SSID или авто-хотспот) — показываются два QR кода
- Шаг 1: Wi-Fi QR чёткий, Download QR заблюрен (blur 16dp + полупрозрачный оверлей)
- Кнопка переключения (SwapVert) между шагами
- Заголовки и URL меняют цвет в зависимости от активного шага

**Форма ввода SSID:**
- Разделены `editingSsid/editingPassword` (форма) и `savedSsid/savedPassword` (QR генерация)
- QR генерируется только после нажатия "Сохранить"

**UI-фиксы:**
- Кнопка "Готово" рядом с "Отмена" в диалоге QR
- Текст "Выберите файлы" скрывается после завершения передачи

Файлы: `HotspotManager.kt`, `HttpFileServer.kt`, `TransferViewModel.kt`, `TransferScreen.kt`, `QrCodeDialog.kt`, `strings.xml`, `strings-ru.xml`

### Анализатор хранилища: свои 200 файлов на категорию + тап-зоны в сетке ✅ проверено

**Анализатор хранилища — файлы по категориям:**
- Каждая категория (Фото, Видео, Музыка, Документы, Архивы, APK, Прочее) собирает свои топ-200 самых больших файлов без порога 10 МБ
- При раскрытии категории показываются первые 10 файлов + кнопка "Показать все (N)" если больше 10
- Секция "Крупные файлы" внизу работает как раньше (отдельно, >10 МБ, общие для всего хранилища)
- Файлы: `AnalyzeStorageUseCase.kt` (categoryHeaps PriorityQueue), `StorageAnalysisViewModel.kt` (categoryFilesExpanded), `StorageAnalysisScreen.kt`

**Тап-зоны в сетке (2-6 колонок):**
- Иконка: тап → превью, долгий тап → выделение
- Имя: тап → открыть файл, долгий тап → DnD/быстрая отправка
- Все жесты обрабатываются на уровне контейнера (FilePanel), дочерние элементы (FileListItem) НЕ имеют click-модификаторов в режиме сетки
- Список (1 колонка) — без изменений
- Файлы: `FilePanel.kt` (detectTapGestures + detectDragGesturesAfterLongPress), `FileListItem.kt` (убраны click-модификаторы в grid)

### UI-фиксы — Терминал + Текстовый редактор ✅ проверено

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

### Batch 41 — Доверенные устройства + ренейм + условный Quick Send ✅ проверено

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

### Batch 40 — Quick Send DnD ✅ проверено
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

### Batch 39 — Расширенный шаринг на ТВ ✅ проверено (Chromecast + DLNA работают, Mirror через браузер)
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

### Batch 38 — Стеганография ⏳ в ожидании (код будет закомментирован)
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

### Batch 37 — Полный терминал (VT100/ANSI) ✅ проверено
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

### Batch 44 — Пароли для архивов/PDF + FB2 из ZIP + RAR5 + UX ✅ проверено
- **zip4j** (`net.lingala.zip4j:zip4j:2.11.5`): зависимость для работы с запароленными ZIP-архивами.
- **7-Zip-JBinding-4Android** (`com.github.omicronapps:7-Zip-JBinding-4Android:Release-16.02-2.03`): нативный 7-zip движок для Android. Поддержка RAR5 + пароли. Используется как fallback после junrar (RAR4).
- **PasswordDialog** (`presentation/common/PasswordDialog.kt`): переиспользуемый AlertDialog с полем пароля (PasswordVisualTransformation), ошибкой, кнопками OK/Отмена. Автофокус на поле.
- **BrowseArchiveUseCase**: параметр `password: String?`. ZIP: zip4j (encrypted + non-encrypted). 7Z: `SevenZFile.builder().setPassword()`. RAR: junrar (RAR4) → fallback `browseRarWith7Zip()` (RAR5, 7-Zip-JBinding). Централизованная детекция шифрования в `invoke()` catch block.
- **ExtractArchiveUseCase**: параметр `password: String?`. ZIP с паролем: zip4j `getInputStream(header)`. 7Z: `setPassword()`. RAR: junrar → fallback `extractRarWith7Zip()` (archive.extractSlow + ISequentialOutStream).
- **ArchiveViewerViewModel**: состояния `showPasswordDialog`, `password`, `passwordError`. При ошибке "password/encrypted" → показ диалога. При повторной ошибке → "Неверный пароль". `onPasswordSubmit()` → retry `loadEntries()`. Пароль передаётся в `extract()`. `closeEvent` — SharedFlow, emit при `isComplete` → автозакрытие экрана архива.
- **ArchiveViewerScreen**: `PasswordDialog` перед `Scaffold`. `LaunchedEffect` на `closeEvent` → `onBack()` после завершения извлечения.
- **PdfReaderContent**: состояния `isEncrypted`, `showPasswordDialog`, `pdfPassword`, `decryptedTempFile`. При `SecurityException` от `PdfRenderer` → показ диалога. При вводе пароля: `PDDocument.load(file, password)` → сохранение расшифрованного PDF во temp → открытие через `PdfRenderer`. Cleanup temp в `DisposableEffect`.
- **DocumentReaderContent**: при `EncryptedDocumentException` или "encrypted/password" в сообщении → "Зашифрованные документы не поддерживаются".
- **DocumentViewerScreen**: аналогичная детекция шифрования при парсинге документов.
- **DocumentParser**: `parseFb2FromZip(zipFile)` — открывает ZIP, находит первый `.fb2` entry, парсит как FB2 с полным форматированием (картинки, стили). `parseFb2Internal(bytes)` — вынесена общая логика из `parseFb2()`.
- **PdfReaderScreen**: `extractFb2FromZip()` для текстового режима. `isDocumentMode` проверяет `.fb2.zip`. `extractDocumentText()` поддерживает `"fb2.zip"` extension.
- **ExplorerViewModel** (`onFileClick`): `.fb2.zip` → `OpenDocumentViewer` (перед `iconRes()` switch).
- **HaronNavigation** (`openReceivedFile`): `.fb2.zip` → `documentViewer` (перед проверкой `ext in listOf("zip", ...)`).
- **FilePanel**: `rememberLazyGridState(initialFirstVisibleItemIndex)` вместо `LaunchedEffect(Unit) { scrollToItem }` — восстановление позиции скрола до первой отрисовки, без гонки с snapshotFlow.
- **Строковые ресурсы**: `password_required`, `enter_password`, `wrong_password`, `pdf_encrypted`, `doc_encrypted_not_supported` (EN + RU).

### Batch 43 — Запоминание позиции чтения + зума ✅ проверено
- **ReadingPositionEntity** (`data/db/entity/ReadingPositionEntity.kt`): Room-entity, таблица `reading_position`. Поля: `file_path` (PK), `position` (Int — страница/индекс/курсор), `position_extra` (Long — мс/пиксельный offset), `last_opened` (Long).
- **ReadingPositionDao** (`data/db/dao/ReadingPositionDao.kt`): `get(filePath)`, `upsert(entity)`, `delete(filePath)`, `deleteOld(threshold)`.
- **ReadingPositionManager** (`data/reading/ReadingPositionManager.kt`): Singleton-обёртка. `init(dao)` вызывается в `HaronApp.onCreate()`. `save()`/`saveAsync()`/`get()`.
- **HaronDatabase**: version 3 → 4, добавлена `ReadingPositionEntity`. `fallbackToDestructiveMigration(true)`.
- **DatabaseModule**: добавлен `provideReadingPositionDao()`.
- **Зум**: хранится как отдельная запись с ключом `"zoom:<filePath>"`, position = (zoom * 100).toInt(). Восстанавливается при открытии, сохраняется вместе с позицией.
- **PdfReaderContent**: страница + зум. Debounce 1с + при выходе. Не переопределяет позицию если открыт из поиска.
- **DocumentReaderContent**: скролл (пиксели) + зум. Debounce 1с + при выходе.
- **DocumentViewerScreen**: позиция LazyColumn (index + offset) + textScale. Debounce 1с + при выходе.
- **TextEditorScreen**: курсор + скролл + fontSizeSp. Debounce 1с + при выходе. Не переопределяет если открыт из поиска.
- **MediaPlayerScreen**: позиция воспроизведения (seekTo), сохранение каждые 5 сек + при выходе.

### Batch 52 — Фиксы корзины + тап на пустом месте ✅ проверено
- **Корзина — восстановление из orphan-файлов**: `TrashRepositoryImpl.recoverOrphanEntries()` — при пустом/повреждённом `meta.json` сканирует `.haron_trash/` и восстанавливает записи из имён файлов (формат `{timestamp}_{name}`). Atomic write через `.tmp` файл.
- **Корзина — пофайловый прогресс удаления**: `deleteFromTrashWithProgress()` — обходит содержимое папок через `walkBottomUp()`, удаляет по одному файлу, вызывает колбэк с прогрессом `(deleted/total, currentName)`. Ранее для папки progress = 0/1 = 0%.
- **Корзина — обновление панелей**: `deleteFromTrashPermanently()` и `emptyTrash()` теперь вызывают `refreshPanel(TOP)` + `refreshPanel(BOTTOM)` после удаления (как `restoreFromTrash`).
- **Тап на пустом месте**: `findItemIndexAtPosition()` в FilePanel — убран fallback, который возвращал индекс ближайшего элемента при тапе на пустое пространство. Теперь возвращает `-1`, обработчик игнорирует.

### Транскодирование видео для Chromecast ✅ проверено (качество `experimentalSetEnableHighQualityTargeting`)
- **TranscodeVideoUseCase.kt**: `domain/usecase/TranscodeVideoUseCase.kt` — транскодирование через Media3 Transformer. AVI, MKV, WMV, MOV, FLV, 3GP, TS → MP4 (H.264 + AAC). Кеширование: `getCacheFile()` генерирует `cast_transcode_{hash}_q3.mp4` из имени+размера файла, при повторном вызове возвращает кешированный. AC3/DTS fallback: при ошибке аудиокодека автоматический retry с `setRemoveAudio(true)`. `fastStartMp4()` — перемещение moov box перед mdat для Chromecast progressive download. `cleanupTempFiles()` удаляет все `cast_transcode_*` файлы.
- **Качество кодирования**: `experimentalSetEnableHighQualityTargeting(true)` — автоматический подбор оптимального битрейта (вместо фиксированных 8 Mbps). Нельзя совмещать с `setBitrate()`. Кеш инвалидирован: `_q2` → `_q3`.
- **CastViewModel.kt**: `castMedia()` проверяет формат: Chromecast + неподдерживаемый → `startTranscodedCast()`, DLNA → как есть. `_transcodeProgress: StateFlow<TranscodeProgress?>`. Реконнект при disconnect во время транскода. `castTranscodedFile()` — HTTP-сервер + каст. `_browserUrl` для зеркалирования/инфо. `_debugCastInfo` — StateFlow для отладки. Кеш не удаляется при disconnect/onCleared (только при `cancelTranscode()`).
- **DLNA fix**: `pendingDlnaDeviceId` — DLNA не имеет отдельного шага "connect", поэтому при выборе DLNA-устройства `pendingAction` сохраняется и выполняется немедленно.
- **Браузерный подход для Mirror/FileInfo**: Chromecast default receiver не рендерит HTML/MJPEG. `castMirrorUrl()` и `castFileInfo()` устанавливают `_browserUrl` вместо каста. `BrowserCastPanel.kt` — URL + кнопка копирования + закрытие.
- **CastOverlay.kt**: `BrowserCastPanel` как первый branch (приоритет). `transcodePercent` напрямую из `transcodeProgress`. Alpha 0.6 на пульте. Debug text (убрать после проверки).
- **MediaRemotePanel.kt**: параметры `transcodePercent: Int?` и `onCancelTranscode: (() -> Unit)?`.
- **ViewModel scoping fix**: все 4 точки `hiltViewModel<CastViewModel>()` используют `viewModelStoreOwner = context as ComponentActivity`.
- **HaronNavigation.kt**: SCREEN_MIRROR — без проверки соединения, сразу MediaProjection. FILE_INFO — без проверки, сразу `castFileInfo()`.
- **build.gradle.kts**: `media3-transformer:1.5.1`, `media3-effect:1.5.1`.
- **strings.xml**: `cast_transcoding_progress`, `cast_transcoding_error`, `cast_browser_hint`, `cast_copy_url`, `cast_url_copied`.
- **Поведение**: MP4/WebM → напрямую. Остальные → Transformer. DLNA → без конвертации.

### Зеркалирование экрана ✅ проверено (работает через браузер)
- **ScreenMirrorService.kt**: фикс `getIntExtra(EXTRA_RESULT_CODE, -1)` → `Int.MIN_VALUE` (Activity.RESULT_OK == -1 совпадал с default). HTTP-сервер `/mirror` (HTML + auto-refresh) + `/frame` (JPEG).
- **Браузерный подход**: Chromecast default receiver не может рендерить HTML/MJPEG. Вместо каста — показывается URL для открытия в браузере на ТВ. `castMirrorUrl()` устанавливает `_browserUrl`, `BrowserCastPanel` показывает URL + кнопка копирования.
- **HaronNavigation.kt**: SCREEN_MIRROR запускает MediaProjection напрямую, без проверки соединения с Chromecast.
- **CastOverlay.kt**: `BrowserCastPanel` — первый branch, приоритет над другими режимами Cast.

### DLNA-поддержка для Cast ✅ проверено (видео на ТВ работает)
- **DlnaManager.kt** (НОВЫЙ): `data/cast/DlnaManager.kt` — @Singleton, SSDP M-SEARCH discovery (UDP multicast 239.255.255.250:1900, ST: MediaRenderer:1), HTTP GET device description + XmlPullParser (friendlyName, UDN, controlURL для AVTransport:1 и RenderingControl:1). SOAP POST для SetAVTransportURI + Play/Pause/Stop/Seek/SetVolume. Polling каждые 1с (GetTransportInfo + GetPositionInfo). DIDL-Lite metadata. StateFlows: isConnected, connectedDeviceName, mediaIsPlaying, mediaPositionMs, mediaDurationMs. Без внешних библиотек — чистый UPnP.
- **CastDevice.kt**: `CastType.DLNA` добавлен в enum.
- **CastRepositoryImpl.kt**: DlnaManager внедрён через Hilt, `discoverCastDevices()` combine Miracast + DLNA, `castMedia()` маршрутизирует по типу, `sendRemoteInput()` делегирует в подключённый менеджер, `disconnect()` вызывает оба.
- **CastViewModel.kt**: DlnaManager добавлен в конструктор, все StateFlows (isConnected, connectedDeviceName, mediaIsPlaying, mediaPositionMs, mediaDurationMs) объединены через combine. Discovery: 3-way combine (Chromecast + Miracast + DLNA). selectDeviceAndCast: DLNA → немедленный каст без pending. Extended modes (slideshow, PDF, fileInfo) маршрутизируются по активному менеджеру.
- **CastButton.kt**: убрана зависимость от GoogleCastManager, принимает StateFlow<Boolean> isConnected, видна всегда (DLNA не требует GMS).
- **CastDeviceSheet.kt**: DLNA-устройства с иконкой SettingsRemote и подписью "DLNA".
- **DLNA device selection fix**: DLNA не имеет отдельного шага "connect" (в отличие от Chromecast). При выборе DLNA-устройства `selectDevice()` ничего не делал → `pendingAction` через `isConnected` collector никогда не срабатывал. Фикс: `pendingDlnaDeviceId` + немедленный `executePendingAction()` для DLNA.
- **strings.xml / strings-ru.xml**: строка `cast_dlna` = "DLNA".

### Batch 51b — Двухпанельный SMB-режим ✅ проверено
- **Концепция**: после подключения к SMB-серверу экран переходит в двухпанельный режим — верхняя панель = SMB-файлы, нижняя = локальные файлы устройства. До подключения — однопанельный список серверов.
- **state/LocalPanelState.kt** (NEW): `LocalFileEntry` (name, path, isDirectory, size, lastModified) + `LocalPanelState` (currentPath, files, isLoading, error, selectedPaths).
- **components/LocalFilePanel.kt** (NEW): composable нижней панели — `LocalBreadcrumb` (кликабельные сегменты пути с авто-скроллом), `LocalActionBar` (Upload↑/CreateFolder/Delete/Rename/Refresh/Clear), `LazyColumn` с `combinedClickable` (тап + долгий тап), бордер активной панели. Функция `formatLocalSize()`.
- **SmbViewModel.kt**: +`localPanel: LocalPanelState`, +`activePanel: PanelId`, +`panelRatio: Float` в SmbUiState. Локальная навигация: `loadLocalFiles()`, `onLocalFileTap()`, `onLocalFileLongPress()`, `navigateLocalUp()`, `getLocalBreadcrumbs()`, `navigateLocalBreadcrumb()`. Панели: `setActivePanel()`, `applyPanelRatioDelta()`, `resetPanelRatio()`. Кросс-панельные: `downloadToLocalPanel()`, `uploadFromLocalPanel()`. Panel-aware: `onCreateFolderInActivePanel()`, `onDeleteSelectedInActivePanel()`, `onRenameInActivePanel()`, `onNavigateUpActivePanel()`. Локальные файловые операции: `createLocalFolder()`, `deleteLocalSelected()`, `renameLocal()`. Инициализация локальной панели при подключении, сброс при отключении.
- **SmbBrowserTab.kt**: рефакторинг — `DualPanelLayout` (SmbPanel + PanelDivider + LocalFilePanel), `SmbPanel` извлечён из существующего кода. Разделитель переиспользован из Explorer (`PanelDivider`). Диалоги panel-aware.
- **TransferScreen.kt**: убран `uploadPickerLauncher`, обновлён `BackHandler` (selection clear → navigateUp active → switch panel → disconnect).
- **strings.xml**: +`smb_download_to_local`, +`smb_upload_to_smb`, +`smb_local_panel`, +`selected_count`, +`folder_empty`.
- **Фиксы**: PanelDivider не реагировал на drag (`.clickable()` на root Column перехватывал события — убран), PanelDivider реагировал но не двигался (stale state capture — заменён на `applyPanelRatioDelta(delta)`), навигация по хлебным крошкам SMB (index 0 = список шар, index 1 = корень шары, index 2+ = `take(index-1)`).

### Batch 51 — SMB-браузер (вкладка в экране Передача) ✅ проверено
- **build.gradle.kts**: добавлены `com.hierynomus:smbj:0.13.0`, `com.rapid7.client:dcerpc:0.12.13`. Глобальный exclude `bcprov-jdk18on` для устранения конфликта с `bcprov-jdk15to18` от smbj.
- **HaronConstants.kt**: 5 новых SMB-констант (SMB_PREFIX, SMB_CREDENTIAL_KEYSTORE_ALIAS, SMB_CREDENTIAL_FILE, SMB_CONNECTION_TIMEOUT_SEC, SMB_IDLE_TIMEOUT_MS).
- **data/smb/SmbCredential.kt**: data class (host, username, password, domain).
- **data/smb/SmbPathUtils.kt**: утилита парсинга SMB-путей (isSmbPath, parseHost, parseShare, parseRelativePath, buildPath, getParentPath, getFileName).
- **data/smb/SmbCredentialStore.kt**: encrypted хранилище credentials — AES-256-GCM через AndroidKeystore, JSON-сериализация, паттерн из SecureFolderRepositoryImpl.
- **data/smb/SmbManager.kt**: ядро SMB-операций через smbj. Пул соединений (ConcurrentHashMap), connect/connectAsGuest, listShares (через MSRPC SRVSVC), listFiles, download/upload (Flow с прогрессом), createDirectory, delete, rename, disconnect. Фильтрация системных шар (IPC$, ADMIN$). Auto-conflict resolution при скачивании.
- **di/SmbModule.kt**: Hilt module — SmbConfig (timeouts, multiProtocol) + SMBClient singleton.
- **presentation/transfer/SmbViewModel.kt**: полный ViewModel — SmbUiState (serverListMode, shares, files, selection, transfer progress, dialogs), навигация (breadcrumbs, history), все операции. Подписка на NetworkDeviceScanner для SMB-устройств. Auto-connect при saved credentials.
- **presentation/transfer/components/SmbAuthDialog.kt**: диалог авторизации — username/password/domain, чекбокс сохранения, кнопка "Гость", индикатор подключения.
- **presentation/transfer/components/SmbBrowserTab.kt**: главный composable — SmbServerList (обнаруженные + сохранённые серверы), ShareList, SmbFileList с multi-select, SmbBreadcrumb, SmbActionBar (upload/createFolder/download/delete/rename), SmbTransferProgressCard, диалоги создания папки/переименования/ручного подключения по IP.
- **TransferScreen.kt**: рефакторинг — PrimaryTabRow с двумя вкладками ("Передача" / "SMB"). Существующий контент вынесен в TransferTabContent. Вкладка 1 → SmbBrowserTab с отдельным SmbViewModel. File picker для загрузки файлов на SMB.
- **strings.xml / strings-ru.xml**: 27 новых SMB-строк (EN + RU).
- **SmbPathUtilsTest.kt**: 8 юнит-тестов на парсинг SMB-путей.

### Batch 50 — Архивирование с паролем и разбиением на части ✅ проверено
- **CreateZipUseCase.kt**: полностью переписан с `java.util.zip.ZipOutputStream` на `net.lingala.zip4j.ZipFile`. Новые параметры: `password: String?` (AES-256), `splitSizeMb: Int` (split ZIP). zip4j сам обходит папки через `addFolder()`. Split через `createSplitZipFile()`.
- **CreateArchiveDialog.kt**: расширен — поле пароля с `PasswordVisualTransformation` и глазиком, Switch "Разбить на части", поле размера части с числовым вводом, выпадающий список пресетов (100/500/700/1024/2048 МБ). Сигнатура `onConfirm` расширена до `(archiveName, password, splitSizeMb)`.
- **ExplorerViewModel.kt**: `confirmCreateArchive()` принимает `password` и `splitSizeMb`, прокидывает в `createZipUseCase()`.
- **ExplorerScreen.kt**: обновлён вызов `CreateArchiveDialog` — прокидка `password` и `splitSizeMb` в ViewModel.
- **strings.xml / strings-ru.xml**: 7 новых строк (archive_password_label, archive_password_show/hide, archive_split_toggle, archive_split_size_label, archive_split_size_mb, archive_split_preset_custom).
- **CreateZipUseCaseTest.kt**: 3 новых теста (пароль, пароль vs без пароля, пустой пароль = без шифрования).
- **Фикс split ZIP**: `createSplitZipFile(List<File>)` не обходит директории рекурсивно — только заголовки без содержимого. Для папок используется `createSplitZipFileFromFolder()` + `ExcludeFileFilter` + `isIncludeRootFolder = false`.
- **ExtractArchiveUseCase.kt**: унифицировано извлечение ZIP — всегда через zip4j (`Zip4jFile`) вместо `java.util.zip.ZipFile`. Стандартный `ZipFile` не поддерживает split ZIP (.z01, .z02).
- **Кросс-панельное сравнение файлов**: `FilePanel.kt` — новый параметр `otherPanelSelectionCount`, кнопка «Сравнить» активна при 2 файлах в одной панели ИЛИ по 1 файлу в каждой панели. `ExplorerViewModel.compareSelected()` — обработка случая 1+1 (берёт по одному из каждой панели). `ExplorerScreen.kt` — прокидка `otherPanelSelectionCount` для обеих панелей.

### Batch 49 — Менеджер приложений: удаление APK, анимация, защищённая папка ✅ проверено
- **AndroidManifest.xml**: добавлен `REQUEST_DELETE_PACKAGES` — обязателен с Android 9+ для работы интентов удаления приложений.
- **AppManagerViewModel.kt**: удаление через `ActivityResultLauncher` вместо `startActivity`. Новые методы: `markUninstalling()`, `onUninstallResult()`, `onRemovalAnimationDone()`. Состояние: `uninstallingPackage`, `removingPackage` в UiState. Проверка через `PackageManager.getPackageInfo()` как fallback.
- **AppManagerScreen.kt**: `rememberLauncherForActivityResult(StartActivityForResult)` — запуск `ACTION_UNINSTALL_PACKAGE` через Activity lifecycle. Анимация удаления: `Animatable` sweep fraction (800мс обратный круговой прогресс красным на иконке) → fade-out строки (400мс) → `animateItem()` для smooth removal из LazyColumn.
- **ExplorerViewModel.kt**: `canNavigateUp()` возвращает `false` для `VIRTUAL_SECURE_PATH` — кнопка Назад не выходит из защищённого режима, только навигация внутри. Выход — только через кнопку Shield.
- **DocumentViewerScreen.kt**: фикс копирования текста — `ClipboardManager.OnPrimaryClipChangedListener` + `key(selectionKey)` для сброса SelectionContainer после копирования. `BackHandler` для мгновенного закрытия ActionMode при выходе.
- **DrawerMenu.kt**: `distinctBy { it.path }` для USB-томов — предотвращение краша от дублирующих ключей в LazyColumn.

### Batch 48 — XLSX: открытие, пропорции колонок, per-cell borders, заголовки ✅ проверено
- **ExplorerViewModel.kt**: добавлен тип `"spreadsheet"` в роутинг открытия файлов → таблицы (xlsx/xls/csv/ods) теперь открываются во встроенном просмотрщике документов, а не в системном диалоге.
- **DocumentParser.kt (XLSX column widths)**: парсинг секции `<cols>` из sheet XML → извлечение ширин колонок по индексу. Передача `tableColWidths` → пропорциональные ширины колонок вместо контентного fallback.
- **DocumentParser.kt (XLSX self-closing cells)**: новая regex `<c\b([^>]*?)(?:>(.*?)</c>|/>)` корректно обрабатывает оба варианта — самозакрывающиеся `<c ... />` (пустые ячейки merged-регионов) и обычные `<c ...>...</c>`. Предыдущая regex «проглатывала» контент следующей ячейки.
- **DocumentParser.kt (XLSX empty column trimming)**: двухпроходный парсинг — первый проход определяет `globalMinCol`/`globalMaxCol`, второй строит таблицу только в этом диапазоне. Пустые ведущие колонки (например A без данных) автоматически исключаются.
- **DocumentParser.kt (XLSX styles.xml)**: парсинг `<borders>` → маски рамок (bitmask: 1=left, 2=right, 4=top, 8=bottom). Парсинг `<cellXfs>` → маппинг style index → borderId. Для каждой ячейки определяется индивидуальная маска рамок через атрибут `s`.
- **DocumentParser.kt (XLSX title rows)**: заголовочные строки (все ячейки имеют неполную сетку, mask != 15) выносятся из таблицы как центрированный жирный текст.
- **DocParagraph**: новое поле `tableCellBorders: List<Int>?` — per-cell border bitmask.
- **DocumentViewerScreen.kt**: `DocItem.TblRow.cellBorders`, передача через `flushTable()` → `CompactTableRow()`. Посторонная отрисовка рамок через `drawBehind` + `drawLine` для каждой стороны (left/right/top/bottom). Mask 15 → обычный `.border()`, mask 0 → без рамок, иначе → индивидуальные линии.

### Batch 47 — Безрамочные таблицы DOCX/ODT + ODT column spans ✅ проверено
- **DocumentParser.kt (DOCX)**: поле `tableHasBorders` в `DocParagraph`. Парсинг `<w:tblBorders>` из `<w:tblPr>` — если элемент отсутствует или все `w:val="none"` → `tableHasBorders = false`. Шапки типа «УТВЕРЖДАЮ» без серой сетки.
- **DocumentParser.kt (ODT)**: парсинг стилей `table-cell` (`fo:border`, `fo:border-top/left/bottom/right`). Подсчёт количества сторон с рамками. Эвристика: таблица = сетка, только если хоть одна ячейка имеет >= 2 сторон с рамками. Layout-таблицы (0-1 сторона, подчёркивания для полей ввода) рендерятся без сетки.
- **DocumentParser.kt (ODT)**: парсинг `table:number-columns-spanned` для горизонтального объединения ячеек. Передача `tableCellGridSpans` → правильные пропорции колонок для таблиц с column span (подписи, даты).
- **DocumentViewerScreen.kt**: `DocItem.TblRow.hasBorders`, передача через `flushTable()` → `CompactTableRow()`. Условный `.border(0.5.dp, borderColor)` — рисуется только при `hasBorders == true`. Применяется к основным ячейкам и пустым ячейкам-заполнителям.

### Batch 42 — Просмотрщик документов: 100% оригинальное форматирование ✅ проверено
- **DocumentParser.kt**: полная переработка DOCX-парсера для максимально точного воспроизведения документа:
  - `parseDocxStyles()` — парсинг `word/styles.xml` (docDefaults + именованные стили). Резолвит paragraph и run свойства по styleId. Fallback: inline → style → docDefaults.
  - `parseDocxNumbering()` — парсинг `word/numbering.xml` (abstractNum + num маппинг). Поддержка форматов: decimal, lowerLetter, upperLetter, lowerRoman, upperRoman, bullet.
  - `formatNumBullet()` + `toRoman()` — форматирование номеров списков с поддержкой lvlText шаблонов (%1., %1) и т.д.).
  - Data-классы: `DocxRunDef`, `DocxParaDef`, `DocxNumLevel`, `DocxTabStop`.
  - `docxParagraph()`: стили из styles.xml как fallback, `w:pBdr` → `hasBorderBottom`, tab stops с лидерами, нумерация из numbering.xml.
  - `docxRunSpans()`: `w:rFonts` → fontFamily, `w:br` → перенос строки, табы с лидерами (underscore/dot/hyphen → символы-заполнители).
  - Таблицы: `w:gridSpan` → горизонтальное объединение ячеек, `w:vAlign` → вертикальное выравнивание.
- **DocumentViewerScreen.kt**: обновлён рендеринг:
  - `TblRow` + `buildDocItems()`: поддержка gridSpans и vAligns, правильный подсчёт логических колонок.
  - `CompactTableRow()`: gridSpan → объединённые веса колонок, vAlign → contentAlignment в Box, fontFamily в SpanStyle.
  - `RichParagraph()`: `hasBorderBottom` → горизонтальная линия, fontFamily → маппинг на FontFamily (Serif/SansSerif/Monospace).
  - `mapFontFamily()` — хелпер для маппинга имён шрифтов документа на Android FontFamily.

### Batch 35 — Открывалка по умолчанию ✅ проверено
- **IntentHandler** (`common/util/IntentHandler.kt`): разбор ACTION_VIEW, ACTION_SEND, ACTION_SEND_MULTIPLE. `ReceivedFile(displayName, localPath, mimeType, size)`. Резолв content:// URI через `contentResolver.openInputStream()` → копирование в `cacheDir/received/`. `queryDisplayName()` через OpenableColumns. `generateUniqueFile()` для конфликтов имён.
- **ReceiveFilesDialog** (`presentation/receive/ReceiveFilesDialog.kt`): AlertDialog со списком полученных файлов (имя + размер). Кнопки «Сохранить в текущую папку» и «Отмена». Иконки по типу файла.
- **AndroidManifest**: intent-filter на ACTION_VIEW и ACTION_SEND для типов: text/*, image/*, video/*, audio/*, application/pdf, application/zip, application/x-7z-compressed, application/x-rar-compressed, application/vnd.android.package-archive. Также ACTION_SEND_MULTIPLE для image/* и video/*.
- **MainActivity**: обработка intent в `onCreate()` и `onNewIntent()`. `IntentHandler.handleIntent()` → `receivedFiles` state → NavigationEvent.HandleExternalFile.
- **HaronNavigation**: обработка receivedFiles — единичный медиа/PDF/архив → открытие во встроенном просмотрщике, множественные/другие → ReceiveFilesDialog с сохранением в текущую папку проводника.
- **NavigationEvent.HandleExternalFile**: передаёт список ReceivedFile.
- **Доп. фикс**: ODT/ODS/ODP/RTF MIME-типы добавлены в intent-filter. ACTION_VIEW → прямое открытие без диалога. DOCX/DOC/ODT/RTF/FB2 → DocumentViewerScreen (извлечение текста через ContentExtractor). APK → ACTION_INSTALL_PACKAGE вместо ACTION_VIEW (убрано зацикливание). **DocumentViewerScreen** (`presentation/document/DocumentViewerScreen.kt`): новый экран для просмотра документов. NavigationEvent.OpenDocumentViewer. HaronRoutes.DOCUMENT_VIEWER.

### Batch 34 — Голосовые команды ✅ проверено
- **VoiceCommandManager** (`data/voice/VoiceCommandManager.kt`): @Singleton, обёртка над `SpeechRecognizer`. `VoiceState` (IDLE, LISTENING, PROCESSING, ERROR). `startListening()` → создаёт SpeechRecognizer, intent на ru-RU + en-US (additional languages). `stop()` с полным cleanup. `matchPhrase()` — сопоставление распознанного текста с `GestureAction` через PHRASE_MAP (16 пар, `contains` match).
- **PHRASE_MAP**: русские + английские фразы для каждого действия: "меню"/"menu" → OPEN_DRAWER, "полка"/"shelf" → OPEN_SHELF, "скрытые"/"hidden" → TOGGLE_HIDDEN, "создать"/"create" → CREATE_NEW, "поиск"/"search" → GLOBAL_SEARCH, "терминал"/"terminal" → OPEN_TERMINAL, "выделить все"/"select all" → SELECT_ALL, "обновить"/"refresh" → REFRESH, "домой"/"home" → GO_HOME, "сортировка"/"sort" → SORT_CYCLE, "настройки"/"settings" → OPEN_SETTINGS, "передача"/"transfer" → OPEN_TRANSFER, "корзина"/"trash" → OPEN_TRASH, "анализ"/"storage" → OPEN_STORAGE, "дубликат"/"duplicates" → OPEN_DUPLICATES, "приложения"/"apps" → OPEN_APPS.
- **Глобальный диспатчер**: HaronNavigation — единственный коллектор `lastResult`. Навигация между экранами (settings, terminal, transfer, search, storage, duplicates, apps) с `popUpTo(EXPLORER)`. Локальные действия (drawer, shelf, trash и пр.) передаются через `TransferHolder.pendingVoiceAction` → ExplorerScreen.
- **VoiceFab** (`presentation/voice/VoiceFab.kt`): глобальная кнопка микрофона с drag (позиция сохраняется в HaronPreferences). Long press → переход к экрану голосовых команд. Одноразовая подсказка при первом запуске.
- **Manifest**: `<uses-permission android:name="android.permission.RECORD_AUDIO" />`

### Batch 33 — Система жестов ✅ проверено
- **GestureAction enum** (13 действий): NONE, OPEN_DRAWER, OPEN_SHELF, TOGGLE_HIDDEN, CREATE_NEW, GLOBAL_SEARCH, OPEN_TERMINAL, SELECT_ALL, REFRESH, GO_HOME, SORT_CYCLE, OPEN_SETTINGS, OPEN_TRANSFER. Каждое с `labelRes` для UI.
- **GestureType enum** (4 жеста): LEFT_EDGE_TOP (→полка), LEFT_EDGE_BOTTOM (→меню), RIGHT_EDGE_TOP (→скрытые файлы), RIGHT_EDGE_BOTTOM (→обновить). Дефолты в `defaultAction`.
- **HaronPreferences**: `getGestureAction()`, `setGestureAction()`, `getGestureMappings()` — хранение в SharedPreferences с ключом `gesture_<TYPE_NAME>`.
- **ExplorerViewModel**: `executeGestureAction()` — switch по всем 13 действиям. `cycleSortOrder()` — переключение NAME→DATE→SIZE→EXTENSION. `reloadGestureMappings()` — обновление из preferences.
- **ExplorerScreen**: переписан `pointerInput` — теперь 4 зоны (лево-верх, лево-низ, право-верх, право-низ). Свайп вправо от левого края или влево от правого. Действие берётся из `state.gestureMappings`. LifecycleEventObserver для reload при ON_RESUME.
- **SettingsScreen + SettingsViewModel**: секция "Жесты" с 4 строками (GestureRow), каждая с ExposedDropdownMenuBox для выбора действия. Кнопка "Сбросить по умолчанию".

### Batch 32 — Встроенный терминал (простой) ✅ проверено
- **TerminalViewModel**: `ProcessBuilder("sh", "-c", command)` с `currentDir` как рабочей директорией. Timeout 30 сек, max 500 строк вывода. Встроенные команды: `cd`, `pwd`, `clear`, `help`, `exit`. Остальные — через `sh -c`. История команд (до 100, навигация up/down). Парсинг командной строки с поддержкой кавычек.
- **TerminalScreen**: тёмный фон (#1E1E1E), моноширинный шрифт, LazyColumn для вывода. Цветовое кодирование: зелёный для команд, красный для ошибок, серый для вывода. Поле ввода с prompt (текущая папка + $). Кнопки: отправить, история вверх/вниз. Авто-скролл к последней строке.
- **Навигация**: `NavigationEvent.OpenTerminal`, `HaronRoutes.TERMINAL`, пункт "Терминал" в боковом меню (секция Инструменты, иконка Code).

### Batch 31 — Счётчик на иконке + Сетевое обнаружение ✅ проверено
- **Счётчик на иконке**: `FileOperationService.activeOperations` — `MutableStateFlow<Int>`, инкремент при `start()`, декремент при завершении/cancel/destroy. `.setNumber(count)` на notification → лаунчер показывает badge. TransferService тоже инкрементирует/декрементирует через тот же счётчик. ExplorerViewModel подписан на `activeOperationsCount` в UI state.
- **Сетевое обнаружение**: `NetworkDeviceScanner` (@Singleton) — два NSD discovery параллельно: `_haron._tcp.` (другие Haron) и `_smb._tcp.` (SMB шары). Плюс subnet scan порт 445 (batch по 20 IP, timeout 300мс) для SMB без mDNS. `StateFlow<List<NetworkDevice>>` → DrawerMenu секция "Сеть" с кнопкой Refresh. Тап на Haron → открывает Transfer. Тап на SMB → toast с IP:port.

### Batch 29 bugfix — Исправление передачи файлов ✅ проверено
- **HTTP сервер недоступен**: `getLocalIpAddress()` возвращал IP мобильных данных (rmnet) вместо WiFi. Fix: сначала `ConnectivityManager.getLinkProperties()` (возвращает active network = WiFi), затем fallback на `NetworkInterface` с приоритетом `wlan*`. Добавлен `host = "0.0.0.0"` в Ktor `embeddedServer()`. Логирование выбранного IP.
- **Устройства не находятся**: `combine()` трёх Flow (WiFi Direct + NSD + Bluetooth) блокировался навсегда — WiFi Direct не эмитил начальное значение. Fix: добавлен `trySend(emptyList())` в `WifiDirectManager.discoverPeers()` до начала discovery. Теперь `combine()` сразу работает и показывает хотя бы BT-спаренные устройства.
- **Bluetooth не отправляет (RFCOMM)**: `createRfcommSocketToServiceRecord()` выбрасывал `IOException: read failed, socket might closed or timeout, read ret: -1` на Samsung/LG/Huawei. Fix: reflection fallback `device.createRfcommSocket(1)` при неудаче стандартного метода. Информативное сообщение об ошибке при неудаче обоих методов.

### Batch 29 — Доделка передачи файлов ✅ проверено
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

### 0.53.0 — Batch 53 (Phase 4, v2.0)
- HLS прогрессивный каст: FFmpeg выводит HLS-сегменты (.ts) + плейлист (.m3u8), Chromecast начинает воспроизведение через ~30 сек пока транскод продолжается
- Замена Media3 Transformer на FFmpeg (`ffmpeg-kit-16kb:6.1.1`): энкодер h264_mediacodec/libx264, AAC stereo даунмикс, GOP 60
- HTTP-сервер: эндпоинт `/hls/{filename}` с Content-Type (m3u8/ts) + CORS заголовки
- HLS version 3 + `-hls_playlist_type event` для совместимости с Chromecast
- Автоматическая перезагрузка каста как VOD (STREAM_TYPE_BUFFERED) после завершения транскода — перемотка работает
- Стойкий кеш в `filesDir/transcode_cache/` — переживает смахивание и очистку кеша
- TTL-очистка кеша: настраивается в Настройки → Трансляция (1ч / 6ч / 12ч / 24ч)
- Ручной HaronWorkerFactory вместо @HiltWorker (обход KSP бага Dagger 2.59.1)
- Убраны debug-элементы (`_debugCastInfo`, `audioStripped`, синий фон транскода)
- Параметр `streamType` в `GoogleCastManager.castMedia()` (LIVE для HLS, BUFFERED для VOD)

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
- Создание ZIP: выделить файлы → кнопка ZIP → ввести имя → архив создан (с опциональным паролем AES-256 и разбиением на части)
- Quick Preview: кнопки "Открыть в галерее/читалке/архиве" по типу файла
- Обнаружение защищённых паролем архивов
- Inline-просмотр архива: архив открывается прямо в панели как обычная папка (без перехода на отдельный экран)
- Inline-извлечение: выделенные файлы из архива извлекаются в папку активной панели (с рамкой)
- Кросс-панельное извлечение: извлечь файлы из архива в одной панели в папку другой панели
- Пароль для архива: автозапрос при открытии запароленного архива прямо в панели

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
> Встроен в APK: боковое меню → «Описание функций».
> Источник истины — `res/raw-ru/features.txt` (RU) и `res/raw/features.txt` (EN).
>
> **Релизы:**
> - **Релиз 1.0** (versionCode=1) — базовый функционал проводника
> - **Релиз 1.4** (versionCode=3) — облака, FTP, Bluetooth HID, аудио теги, дублирование, Shizuku, быстрая распаковка
> - **Релиз 1.5** (versionCode=4, в разработке) — библиотека, читалка тем, кастомный навбар, стрелочная навигация, Shift-режим, radial menu, rename overlay
>
> Функции помеченные `[NEW]` в features.txt — добавлены в релизе 1.5. При выпуске `[NEW]` убираются.

### Навигация и интерфейс
- Двухпанельный вертикальный интерфейс (верх + низ)
- Разделитель панелей: перетаскивание + 3 тап-зоны (закладки / 50-50 / инструменты)
- Навигация по папкам кнопкой назад
- Кликабельные хлебные крошки для быстрого перехода
- История навигации в каждой панели (← → как в браузере)
- Открыть текущую папку в другой панели
- Активная панель подсвечена рамкой
- Запоминание позиции прокрутки в каждой панели

### Файлы и папки
- Список файлов с иконками по типу
- Превью в иконках: изображения, видео, текст, PDF, документы, APK, FB2
- Масштабируемая сетка: 1–6 колонок через жест щипка
- Тап-зоны в списке: тап → открыть, долгий тап слева → выделение, долгий тап справа → быстрая отправка, тап на иконку → превью
- Тап-зоны в сетке: тап на иконку → превью, долгий тап → выделение, тап на имя → открыть, долгий тап на имя → быстрая отправка
- Бейдж расширения на иконке
- Визуальное выделение пустых папок
- Сортировка: по имени, дате, размеру, расширению
- Показ/скрытие скрытых файлов
- Быстрый поиск в текущей папке
- Создание файлов: папка, TXT, MD, папка с датой

### Выделение и операции
- Долгий тап + свайп для выбора диапазона
- Выбрать все / снять все (долгий тап → выбор по расширению)
- Счётчик файлов и общий размер выделенного
- Копирование и перемещение между панелями
- Удаление в корзину
- Инлайн-переименование (как в Windows)
- Пакетное переименование: шаблон, нумерация, поиск и замена
- Перетаскивание между панелями (одиночное и групповое)
- Диалог конфликтов: заменить / переименовать / пропустить

### Корзина
- Удалённые файлы хранятся 30 дней
- Просмотр, восстановление, очистка корзины
- Автоочистка при превышении лимита
- Лимит размера: 0–5000 МБ
- Пофайловый прогресс удаления (показывает каждый удаляемый файл)
- Автовосстановление записей из файловой системы при повреждении метаданных

### Фоновые операции
- Фоновая служба для крупных операций (>10 файлов или >50 МБ)
- Уведомление с прогрессом и кнопкой отмены
- Прогресс-бар внизу экрана

### Боковое меню
- Хранилища: внутренняя память + SD-карты
- Избранное: избранные папки
- Недавние: последние 5 папок
- Инструменты: корзина, анализатор, дубликаты, пустые папки, принудительное удаление, метки, передача, терминал
- Оформление: системная / светлая / тёмная тема
- Настройки

### Временная полка
- Буфер для файлов при перемещении между далёкими папками
- Кнопка «На полку» при выделении файлов
- Вставить / переместить с полки
- Полка сохраняется между запусками

### Хранилища (SAF)
- Поддержка SD-карты и внешних хранилищ
- Все файловые операции работают с внешними носителями
- Доступ к Android/data и Android/obb через Shizuku (Android 11+)

### Быстрый просмотр
- Тап по иконке файла → диалог превью
- Изображения, видео, аудио, текст, PDF, архивы, APK, документы, FB2
- Воспроизведение медиа прямо в превью
- Кнопки «Открыть в галерее / читалке / архиваторе»
- Удаление картинок прямо из превью (кнопка корзины) — переходит к следующему фото

### Медиаплеер
- Встроенный плеер на движке VLC (LibVLC)
- Все популярные кодеки: MP3, FLAC, OGG, WAV, AVI, MKV, MP4 и др.
- Плейлист папки с автопереходом
- Фоновое воспроизведение с управлением на экране блокировки
- Полоса перемотки + перемотка ±10 сек
- Позиция сохраняется между сессиями
- Видео: полный экран, погружённый режим

### Внутренняя галерея
- Полноэкранный просмотр изображений
- Свайп между картинками в папке
- Зум: щипком (1x–5x) и двойным тапом
- Кнопка «Поделиться»
- Предзагрузка соседних изображений

### PDF-читалка
- Полноэкранная читалка PDF
- Прокрутка, зум (щипок + двойной тап)
- Индикатор страницы с быстрым переходом
- Подсветка результатов поиска с навигацией
- Поддержка запароленных PDF
- Запоминание страницы между сессиями

### Читалка документов
- DOC, DOCX, ODT, RTF, FB2, XLSX, XLS, CSV
- FB2: обложка + аннотация перед текстом
- Зум и прокрутка
- Подсветка результатов поиска с навигацией
- Запоминание позиции между сессиями

### Архивы
- Просмотр ZIP, 7z, RAR (включая RAR5)
- Инлайн-просмотр в панели как обычная папка
- Навигация по папкам внутри архива
- Выборочное извлечение
- Создание ZIP с паролем (AES-256) и разбиением на части

### Текстовый редактор
- Просмотр и редактирование TXT/MD файлов
- Режим просмотра (по умолчанию) и редактирования
- Отмена/повтор (до 50 шагов)
- Диалог сохранения при выходе с изменениями
- Запоминание позиции курсора

### Свойства файла
- Имя, путь, размер, дата, тип, разрешения
- Рекурсивный подсчёт размера папки
- EXIF-данные для фото (с удалением)
- Хеш-суммы MD5 и SHA-256

### Анализатор памяти
- Круговая диаграмма по типам файлов
- Категории с размером и счётчиком файлов
- Топ-200 крупнейших файлов по категории
- Секция «Крупные файлы (>10 МБ)»

### Детектор дубликатов
- Поиск одинаковых файлов по всему хранилищу
- Двухфазный алгоритм: группировка по размеру → хеширование MD5
- Маркеры оригинала: оригинал остаётся, копии предлагаются к удалению
- Режим «Папки-оригиналы»: файлы в выбранных папках автоматически считаются оригиналами
- Автовыделение копий для удаления

### Настройки
- Ночной режим по расписанию
- Размер шрифта и иконок (слайдеры)
- Включение/отключение вибрации
- Безопасность: PIN, биометрия, секретный вопрос
- Переключатель «Требовать PIN при запуске»
- Лимит корзины
- Статус и управление Shizuku (установить, запустить, разрешение)

### Метки (теги)
- Цветные метки с названием (8 цветов)
- Назначение на файлы и папки
- Фильтрация по метке
- Метки сохраняются при переименовании/перемещении/копировании

### Виджет на рабочий стол
- Список избранных папок на домашнем экране
- Тап на папку → приложение открывается в ней

### Закладки быстрого доступа (1–6)
- Тап на левую зону разделителя → круговое меню
- Сохранение / навигация к папкам мгновенно
- Сохраняются между запусками

### Круговое меню инструментов
- Тап на правую зону разделителя → круговое меню
- Корзина, Анализатор, Дубликаты, APK, Плеер, Читалка

### Блокировка приложения
- PIN-код (4–8 цифр) с автовводом
- Биометрия (отпечаток пальца)
- Комбинированный режим: биометрия + PIN
- Таймаут блокировки (30 мин)
- Секретный вопрос для восстановления PIN
- Защита от скриншотов при блокировке

### Защищённая папка
- Шифрование AES-256-GCM (Android Keystore)
- Файлы полностью невидимы без щита
- Кнопка щита требует PIN
- Долгий тап на щит → вход в защищённые файлы
- Тап на щит в режиме защиты → выход
- Виртуальная директория всех защищённых файлов
- Копирование расшифровывает, перемещение расшифровывает + снимает защиту

### Глобальный поиск
- Долгий тап на поиск → вход в глобальный поиск
- Поиск по имени по всему устройству
- Фильтры: категория, размер, дата
- Поиск по содержимому: текст, DOCX, ODT, PDF
- Поиск по метаданным аудио/видео
- ML Kit распознавание объектов на фото
- OCR на фотографиях (распознавание текста)
- Поиск по архивам (имена файлов внутри)
- Индексирование: базовое (текст, метаданные, архивы), медиа (аудио/видео теги), визуальное (ML + OCR)
- Фоновое обновление индекса при изменении файлов и включении экрана (раз в 30 мин)

### Локальный поиск (в панели)
- Поиск по имени: фильтрует файлы в текущей папке по названию
- Поиск по содержимому: ищет текст внутри файлов (TXT, DOCX, ODT, PDF и др.)
- При первом поиске по содержимому папка индексируется, повторный поиск — мгновенный
- Автосброс при смене папки

### Интернет-поиск ⚠️ не проверено
- Поиск и скачивание файлов из интернета (второй таб в глобальном поиске)
- Четыре источника: Open Directory, Internet Archive, Library Genesis, торренты (нулевая раздача)
- Введи "книга", "музыка", "фильм" — поиск сразу по всем форматам этого типа
- Введи несколько типов сразу: "книга аудиокнига" — ищет и книги, и аудиокниги
- Автоопределение типа контента по ключевым словам (книга/музыка/фильм/видео/...)
- Library Genesis — миллионы книг на всех языках: PDF, EPUB, FB2, DJVU
- Тап на результат — скачивание в Downloads/Haron/
- Долгий тап — копировать ссылку
- Web Navigator: кнопка "Обзор" у найденных директорий → просматривай ссылки на странице, навигируй вглубь, тап на файл = скачивание ⚠️ не проверено

### Передача файлов
- Wi-Fi Direct + обнаружение в сети (NSD)
- Автовыбор лучшего протокола
- HTTP-сервер с QR-кодом для любых устройств
- Двухшаговый QR: сначала Wi-Fi подключение, потом скачивание (с блюром неактивного)
- Ручная настройка точки доступа (SSID/пароль) для Wi-Fi QR — сохраняется после первого ввода
- Определение корпоративных сетей (CGNAT) — подсказка включить точку доступа
- Счётчик отправленных файлов и байтов при скачивании целевым устройством

### Облачные хранилища
- Google Drive, Dropbox, Яндекс Диск
- Безопасная OAuth2 авторизация с PKCE
- Просмотр облачных файлов в двухпанельном режиме (как локальные)
- Кликабельные хлебные крошки для навигации по облачным папкам
- Счётчик файлов в облачных папках
- Быстрый просмотр облачных картинок с большими миниатюрами
- Облачная галерея: полноэкранный просмотр со свайпом между фото
- Стриминг видео и аудио из облака (играет сразу, без полной загрузки)
- Открытие и редактирование текстовых файлов из облака
- Сохранение в облако, локально или и туда, и туда
- Скачивание облачных файлов в локальное хранилище
- Загрузка локальных файлов в облако (файлы до 500MB+)
- Надёжная загрузка: чанки по 8-10MB с повторной отправкой при обрыве сети
- Переименование файлов и папок в облаке
- Удаление из облака (в том числе из быстрого просмотра)
- Создание папок в облаке
- Перетаскивание файлов между локальным хранилищем и облаком
- Параллельные трансферы с индивидуальной отменой каждого

### Сканер штрихкодов
- Долгий тап на кнопку QR → открывается камера-сканер
- Распознаёт QR-коды и штрихкоды (как стандартный сканер)
- Голосовая команда "Сканер" / "Штрихкод" / "QR"

### Трансляция на ТВ
- Chromecast, Miracast, DLNA
- Режимы: медиа, слайд-шоу, PDF-презентация, информация о файле, зеркало экрана (через браузер)
- Пульт: воспроизведение/пауза, перемотка, громкость
- Транскодирование: AVI, MKV, WMV, MOV, FLV, 3GP, TS → MP4 для Chromecast
- Кеширование транскодов — повторный каст мгновенный
- Автоматический fallback при неподдерживаемом аудио (AC3/DTS → без аудио)

### Сеть и SMB
- Автообнаружение устройств в сети
- SMB-браузер с двухпанельным режимом
- Загрузка/скачивание с прогрессом
- Сохранённые подключения (зашифрованные)

### Быстрая отправка
- Долгий тап на файл → кружки устройств → перетащи для отправки
- Доверенные устройства принимают автоматически
- Переименование устройств (псевдонимы)

### USB OTG
- Автоопределение USB-накопителей
- Навигация, безопасное извлечение
- Индикатор свободного/общего места

### Жесты
- 4 настраиваемых свайпа от краёв экрана
- 14 действий на выбор
- Настройка в Настройках

### Голосовые команды
- 35+ команд на русском и английском языках
- Тап на микрофон — разовая команда. Повторный тап — отключение
- Перетаскивание кнопки микрофона в любое место экрана
- Долгий тап на микрофон — открыть список всех команд

#### Активация голосом «Харон»
- Включается в настройках → Жесты и голос
- Скажи «Харон [команда]» — выполнится сразу (например «Харон настройки»)
- Или просто «Харон» — приложение активируется и ждёт команду
- Распознаёт даже если Google слышит «Харун», «Хорон» и другие варианты
- Проверяет все варианты распознавания, не только первый
- Микрофон автоматически останавливается при сворачивании и включается при возврате

#### Управление панелями голосом
- Добавь к любой команде слово для выбора панели:
  - Верхняя: «вверху», «верхняя», «первая» (например «назад вверху»)
  - Нижняя: «внизу», «нижняя», «вторая» (например «открой загрузки внизу»)
- Без модификатора — команда выполняется в активной панели

#### Навигация в папки голосом
- «Открой [папку]», «перейди в [папку]», «зайди в [папку]»
- Понимает русские названия для английских папок: камера→Camera, загрузки→Download, документы→Documents, музыка→Music, картинки→Pictures, видео→Movies и др.
- Понимает все падежи: «открой камеру», «перейди в камере», «зайди в загрузки»
- Ищет по всему хранилищу (до 3 уровней вложенности), не только в текущей папке
- Нечёткий поиск: находит папку даже при неточном произношении
- Кэш папок: первый поиск сканирует хранилище, повторные — мгновенные
- Корневые папки приоритетнее вложенных (Music в корне, а не TwinApps/Music)
- «Обнови кэш» — пересканировать хранилище если появились новые папки

#### Поведение
- Открытый диалог, меню или полка автоматически закрывается при новой команде
- Быстрый отклик: сокращённые таймауты тишины для мгновенной реакции

#### Все фразы (РУ / EN)
- Меню: меню, открой меню / menu, open menu
- Полка: полка, полку, буфер / shelf, clipboard
- Скрытые: скрытые, показать скрытые, спрятанные / hidden, show hidden
- Создать: создать, создай, новый, новая / create, new
- Поиск: поиск, найти, найди / search, find
- Терминал: терминал, консоль, командная строка / terminal, console
- Выделить все: выделить все, выдели все / select all
- Обновить: обновить, обнови / refresh, reload
- Домой: домой, на главную, корень / home, root, go home
- Сортировка: сортировка [имя/размер/дата/тип] [вверх/вниз] / sort [name/size/date/type] [asc/desc]
- Настройки: настройки, параметры / settings
- Передача: передача, передать, отправить / transfer, send
- Корзина: корзина, мусор / trash
- Анализ: анализ, память, хранилище / storage, analysis
- Дубликаты: дубликаты, копии / duplicates
- Приложения: приложения, апк / apps, apk
- Сканер: сканер, скан, штрихкод, qr / scanner, scan, barcode, qr
- Логи: логи / logs
- Логи пауза: логи стоп, логи пауза / logs stop, logs pause
- Логи продолжить: логи старт, логи продолжить / logs start, logs resume
- Назад: назад / back, go back
- Вперёд: вперёд / forward, go forward
- Наверх: наверх, родительская / up, go up
- Удалить: удалить, удали / delete, remove
- Копировать: копировать, копируй, скопируй / copy
- Переместить: переместить, перемести, перенести, перенеси / move
- Переименовать: переименуй [имя], переименовать в [имя] / rename [name], rename to [name]
- Архив: архивировать, заархивируй, запаковать, запакуй / archive, zip
- Распаковать: распаковать, распакуй, извлечь, разархивируй / extract, unpack, unzip
- Свойства: свойства, информация / properties, info
- Снять выделение: снять выделение, сними выделение / deselect
- Перейти: открой [папку], перейди в [папку], зайди в [папку] / open [folder], go to [folder]
- Обнови кэш: обнови кэш, обнови голосовой кэш / refresh cache
- Защищённые: защищённые, защита, сейф / secure, protected
- Альтернативные фразы для каждой команды отображаются в настройках

### Терминал
- Выполнение команд оболочки
- Цветной вывод (16/256/RGB)
- Автодополнение путей по Tab
- Кликабельные пути в выводе
- История команд (200)
- SSH-подключение к удалённым серверам (`ssh user@host [-p port]`)
- Интерактивный shell через SSH (cd, env, aliases сохраняются)
- Диалог пароля с toggle-видимостью и сохранением (AES-256-GCM)
- Бейдж SSH и кнопка отключения в шапке
- Обработка ошибок: неверный пароль, таймаут, отказ, DNS
- `exit` / `disconnect` в SSH — возврат в локальный режим

### Открывалка по умолчанию
- Haron зарегистрирован как обработчик файлов в системе
- Открытие из любого приложения через «Открыть с помощью»
- Приём файлов через «Поделиться»

### Сравнение файлов и папок
- Текстовый diff: два столбца с синхронной прокруткой
- Цветовая кодировка: зелёный/красный/жёлтый
- Сравнение папок
- Автоопределение режима

### Оформление
- Тема: системная / светлая / тёмная
- Material Design 3

---

## Релиз 2 (versionCode=3, versionName=1.4) — Открытые вопросы

> Всё что требует доработки, проверки или решения во втором релизе.

### Есть вопросы (от пользователя)

- **Поиск по содержимому в папке** — есть вопросы по логике поиска внутри файлов. Уточнить при тестировании.

### Не проверено

- **Batch 70** — DnD Copy/Move полоска на дивайдере (выбор операции при перетаскивании между панелями)
- **Batch 38** — Стеганография (скрыта до отдельного релиза)
- **UI-фиксы** — Терминал (капитализация после пробела) + Текстовый редактор (полоса между текстом и клавиатурой)
- **libaums + NTFS-предупреждение** — ❌ регрессия: на Sony пропал доступ к большинству не-NTFS флешек. NTFS на Sony определяется верно. NTFS на ASUS работает (ASUS монтирует нативно).

### TODO

- **Проверка SD-карты** — перемещение между хранилищами (внутренняя ↔ SD) использует copy+delete. Протестировать на устройстве с SD-картой.
- **Батч 54** — История сравнений с кешем результатов (запланирован, не реализован)

---

## Заметки

> Свободная секция для идей, вопросов, решений которые ещё не оформились.
