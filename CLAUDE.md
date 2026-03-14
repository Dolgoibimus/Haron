# CLAUDE.md — Проводник

Этот файл для Claude Code (терминал). Читается автоматически при каждом запуске.
Для полного контекста проекта читай `AGENTS.md`.

---

## Сборка

```bash
./gradlew assembleDebug          # debug APK
./gradlew bundleRelease          # release AAB
./gradlew test                   # unit tests
./gradlew connectedAndroidTest   # instrumented tests
./gradlew lint
```

Сборку и синхронизацию Gradle выполняет пользователь вручную.
НЕ запускать `./gradlew` команды самостоятельно.

## Не читать / не трогать

`build/`, `app/build/`, `.idea/`, `.gradle/`, `*.apk`, `*.aab`, `*.jks`

## Стек

Kotlin + Jetpack Compose + Hilt + MVVM + Clean Architecture.
Package: `com.vamp.haron`. Min SDK 26, Target SDK 34.
Подключён модуль `ecosystem-core` — шина данных экосистемы.

## Критические места (Danger Zones)

- **MANAGE_EXTERNAL_STORAGE** — на Android 11+ требует отдельного Intent
  `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`. Не путать с обычным
  READ/WRITE_EXTERNAL_STORAGE — они не дают полного доступа.
- **Foreground Service для копирования** — тип `dataSync`, без него
  система убьёт операцию на большых файлах. START_STICKY обязателен.
- **FTS5 индекс** — не обновлять синхронно при каждом изменении файла.
  Только через Content Observer + фоновая очередь (WorkManager).
- **Drag-and-Drop** — `selectedIndices` и состояние выделения не трогать
  во время активной операции перетаскивания (isProcessing флаг).
- **HTTP-сервер** — порт 8080 может быть занят. Перебирать порты
  8080 → 8081 → 8082 при старте сервера.
- **Защищённая папка** — ключ шифрования AES-256 генерировать через
  Android Keystore, не хранить в SharedPreferences.
- **EcosystemLogger** — использовать вместо голого `Log.d` везде.
- **EcosystemPreferences** — не создавать свои SharedPreferences для темы и языка.
- **pdfbox-android** — обязательно `PDFBoxResourceLoader.init(context)` в
  `Application.onCreate()` до любого вызова PDFBox API. Иначе `ExceptionInInitializerError`.

## Защита от перезаписи

- **features.txt, strings.xml, AGENTS.md** — НИКОГДА не использовать Write (полная перезапись).
  Только **Edit** (точечная замена конкретного блока). После авто-сжатия контекста
  Write потеряет всё содержимое кроме того, что сессия "помнит".
- Перед любым изменением features.txt — сначала **Read**, убедиться что видишь полный файл.

## Правила сессии

- После реализации новой фичи → обновить `haron-agents.md`
  (перенести из TODO в Журнал решений, дописать changelog).
- После значимых изменений архитектуры → обновить `haron.md`.
- При приближении к лимиту токенов → сохранить прогресс в `haron-agents.md`
  секция "В работе" и напомнить про `/compact`.
- Напоминать про `/compact` при превышении ~15-20 сообщений.
  Не чаще раза в 15 сообщений.
