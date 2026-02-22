package com.vamp.haron.domain.usecase

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import com.vamp.haron.domain.model.ApkInstallInfo
import com.vamp.haron.domain.model.ApkPermissionInfo
import com.vamp.haron.domain.model.FileEntry
import com.vamp.haron.domain.model.SignatureStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class LoadApkInstallInfoUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Suppress("DEPRECATION")
    suspend operator fun invoke(entry: FileEntry): Result<ApkInstallInfo> = withContext(Dispatchers.IO) {
        val file = if (entry.isContentUri) copyToTemp(entry) else File(entry.path)
        try {
            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SIGNING_CERTIFICATES
            val info = pm.getPackageArchiveInfo(file.absolutePath, flags)
                ?: return@withContext Result.failure(Exception("Не удалось прочитать APK"))

            info.applicationInfo?.sourceDir = file.absolutePath
            info.applicationInfo?.publicSourceDir = file.absolutePath

            val appName = info.applicationInfo?.loadLabel(pm)?.toString()
            val icon = loadIcon(info, pm)
            val permissions = loadPermissions(info, pm)

            // Check if already installed
            val installedInfo = try {
                pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

            val isAlreadyInstalled = installedInfo != null
            val installedVersionName = installedInfo?.versionName
            val installedVersionCode = installedInfo?.longVersionCode
            val isUpgrade = isAlreadyInstalled && installedVersionCode != null && info.longVersionCode > installedVersionCode
            val isDowngrade = isAlreadyInstalled && installedVersionCode != null && info.longVersionCode < installedVersionCode

            val signatureStatus = if (!isAlreadyInstalled) {
                SignatureStatus.NOT_INSTALLED
            } else {
                compareSignatures(info, installedInfo!!)
            }

            Result.success(
                ApkInstallInfo(
                    appName = appName,
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = info.longVersionCode,
                    icon = icon,
                    fileSize = entry.size,
                    filePath = entry.path,
                    permissions = permissions,
                    isAlreadyInstalled = isAlreadyInstalled,
                    installedVersionName = installedVersionName,
                    installedVersionCode = installedVersionCode,
                    isUpgrade = isUpgrade,
                    isDowngrade = isDowngrade,
                    signatureStatus = signatureStatus
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            if (entry.isContentUri) file.delete()
        }
    }

    private fun loadIcon(info: PackageInfo, pm: PackageManager): Bitmap? {
        return try {
            info.applicationInfo?.loadIcon(pm)?.let { drawable ->
                val bmp = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun loadPermissions(info: PackageInfo, pm: PackageManager): List<ApkPermissionInfo> {
        val requested = info.requestedPermissions ?: return emptyList()
        return requested.mapNotNull { permName ->
            try {
                val permInfo = pm.getPermissionInfo(permName, 0)
                val label = permInfo.loadLabel(pm).toString()
                val isDangerous = permInfo.protection == PermissionInfo.PROTECTION_DANGEROUS
                val sysDesc = permInfo.loadDescription(pm)?.toString()
                val description = permissionDescriptions[permName]
                    ?: sysDesc?.takeIf { it.isNotBlank() && it != label }
                    ?: generateFallbackDescription(permName)
                ApkPermissionInfo(
                    name = permName,
                    label = label,
                    description = description,
                    isDangerous = isDangerous
                )
            } catch (_: PackageManager.NameNotFoundException) {
                ApkPermissionInfo(
                    name = permName,
                    label = permName.substringAfterLast('.'),
                    description = permissionDescriptions[permName]
                        ?: generateFallbackDescription(permName),
                    isDangerous = false
                )
            }
        }
    }

    private fun generateFallbackDescription(permName: String): String {
        val short = permName.substringAfterLast('.').lowercase()
        val keywords = mapOf(
            "location" to "Это разрешение связано с определением вашего местоположения. Приложение может отслеживать где вы находитесь.",
            "camera" to "Это разрешение даёт доступ к камере. Приложение сможет делать фото или снимать видео.",
            "audio" to "Это разрешение связано со звуком — запись с микрофона или доступ к аудиофайлам.",
            "sms" to "Это разрешение связано с SMS. Приложение может читать, отправлять или получать текстовые сообщения.",
            "phone" to "Это разрешение связано с телефоном — информация об устройстве, звонки или журнал вызовов.",
            "call" to "Это разрешение связано с телефонными звонками — совершение вызовов или доступ к истории.",
            "contact" to "Это разрешение даёт доступ к вашим контактам — чтение или изменение списка.",
            "storage" to "Это разрешение даёт доступ к файлам в памяти устройства — чтение или запись.",
            "media" to "Это разрешение даёт доступ к медиафайлам — фото, видео или музыке на устройстве.",
            "bluetooth" to "Это разрешение связано с Bluetooth — подключение к беспроводным устройствам.",
            "wifi" to "Это разрешение связано с Wi-Fi — информация о сети или подключение.",
            "network" to "Это разрешение связано с сетью — проверка подключения к интернету.",
            "calendar" to "Это разрешение даёт доступ к вашему календарю — чтение или добавление событий.",
            "sensor" to "Это разрешение даёт доступ к датчикам устройства — акселерометр, гироскоп или датчики здоровья.",
            "notification" to "Это разрешение связано с уведомлениями — показ или управление уведомлениями.",
            "alarm" to "Это разрешение связано с будильниками и таймерами.",
            "biometric" to "Это разрешение связано с биометрией — вход по отпечатку пальца или лицу.",
            "nfc" to "Это разрешение связано с NFC — бесконтактная передача данных.",
            "install" to "Это разрешение позволяет устанавливать приложения.",
            "billing" to "Это разрешение связано с покупками внутри приложения.",
            "vibrat" to "Это разрешение позволяет использовать вибрацию устройства.",
            "boot" to "Это разрешение позволяет приложению запускаться автоматически после включения устройства.",
            "wake" to "Это разрешение не даёт экрану или процессору уходить в сон, пока приложение работает.",
            "foreground" to "Это разрешение позволяет приложению работать в фоне и показывать уведомление об этом.",
            "overlay" to "Это разрешение позволяет рисовать окна поверх других приложений.",
            "shortcut" to "Это разрешение позволяет создавать ярлыки на рабочем столе.",
            "fingerprint" to "Это разрешение даёт доступ к сканеру отпечатков пальцев для входа или подтверждения."
        )
        for ((key, desc) in keywords) {
            if (short.contains(key)) return desc
        }
        return "Техническое разрешение, которое приложение запрашивает для своей работы. Обычно не влияет на вашу конфиденциальность."
    }

    private val permissionDescriptions = mapOf(
        // --- Интернет и сеть ---
        "android.permission.INTERNET" to "Приложение сможет выходить в интернет — загружать данные, обновляться, " +
                "показывать рекламу и отправлять информацию на свои серверы. Есть почти у всех приложений.",
        "android.permission.ACCESS_NETWORK_STATE" to "Приложение будет знать, подключены ли вы к интернету " +
                "и через что — Wi-Fi или мобильную сеть. Используется чтобы не тратить мобильный трафик.",
        "android.permission.ACCESS_WIFI_STATE" to "Приложение увидит название вашей Wi-Fi сети и статус подключения. " +
                "Часто нужно чтобы скачивать обновления и большие файлы только по Wi-Fi.",
        "android.permission.CHANGE_WIFI_STATE" to "Приложение сможет включать и выключать Wi-Fi на устройстве. " +
                "Используется для автоматического подключения к сетям.",
        "android.permission.CHANGE_NETWORK_STATE" to "Приложение сможет менять настройки сети — например, " +
                "переключаться между Wi-Fi и мобильным интернетом.",
        "android.permission.NEARBY_WIFI_DEVICES" to "Приложение сможет искать Wi-Fi устройства поблизости. " +
                "Нужно для передачи файлов между устройствами по Wi-Fi Direct.",

        // --- Камера и микрофон ---
        "android.permission.CAMERA" to "Приложение получит доступ к камере вашего телефона. Сможет снимать фото и видео, " +
                "сканировать QR-коды или проводить видеозвонки. Камера работает только когда приложение открыто.",
        "android.permission.RECORD_AUDIO" to "Приложение сможет записывать звук через микрофон. Используется для голосовых " +
                "сообщений, видеозвонков, диктофона или голосового поиска. Стоит обращать внимание — микрофон может " +
                "записывать всё вокруг.",

        // --- Контакты ---
        "android.permission.READ_CONTACTS" to "Приложение увидит все ваши контакты — имена, номера телефонов, email. " +
                "Обычно нужно для поиска друзей в приложении, но может использоваться для сбора данных.",
        "android.permission.WRITE_CONTACTS" to "Приложение сможет добавлять новые контакты и менять существующие " +
                "в вашей адресной книге.",
        "android.permission.GET_ACCOUNTS" to "Приложение увидит список аккаунтов на устройстве — Google, почта и другие. " +
                "Используется для входа в приложение через существующий аккаунт.",

        // --- Телефон и звонки ---
        "android.permission.READ_PHONE_STATE" to "Приложение получит информацию о вашем телефоне — модель, оператор, " +
                "номер телефона. Часто нужно для идентификации устройства, но может использоваться для отслеживания.",
        "android.permission.READ_PHONE_NUMBERS" to "Приложение увидит ваш номер телефона. Обычно нужно для автоматической " +
                "регистрации по номеру без ручного ввода.",
        "android.permission.CALL_PHONE" to "Приложение сможет звонить на любой номер без вашего подтверждения! " +
                "Будьте осторожны — это может привести к платным вызовам.",
        "android.permission.ANSWER_PHONE_CALLS" to "Приложение сможет автоматически принимать входящие звонки. " +
                "Используется в приложениях для звонков и автоответчиках.",
        "android.permission.READ_CALL_LOG" to "Приложение увидит всю историю ваших звонков — кому звонили, " +
                "кто звонил вам, когда и сколько длился разговор.",
        "android.permission.WRITE_CALL_LOG" to "Приложение сможет добавлять и удалять записи из журнала звонков. " +
                "Нужно для приложений интернет-телефонии.",
        "android.permission.PROCESS_OUTGOING_CALLS" to "Приложение будет перехватывать исходящие звонки. " +
                "Используется для блокировки спама или переадресации.",

        // --- SMS ---
        "android.permission.SEND_SMS" to "Приложение сможет отправлять SMS от вашего имени. Будьте осторожны — " +
                "отправка SMS на платные номера может стоить денег!",
        "android.permission.READ_SMS" to "Приложение прочитает все ваши SMS-сообщения. Обычно нужно для автоматического " +
                "считывания кодов подтверждения, но даёт доступ ко всей переписке.",
        "android.permission.RECEIVE_SMS" to "Приложение будет получать входящие SMS в реальном времени. Нужно чтобы " +
                "автоматически подставить код подтверждения при регистрации.",
        "android.permission.RECEIVE_MMS" to "Приложение будет получать входящие MMS — сообщения с картинками и видео.",

        // --- Местоположение ---
        "android.permission.ACCESS_FINE_LOCATION" to "Приложение будет знать ваше точное местоположение с точностью до " +
                "нескольких метров (через GPS). Нужно для навигации, карт и доставки. Одно из самых чувствительных разрешений.",
        "android.permission.ACCESS_COARSE_LOCATION" to "Приложение будет знать ваше приблизительное местоположение — " +
                "город или район. Менее точное чем GPS, но достаточно для погоды и новостей.",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "Приложение будет отслеживать ваше местоположение даже когда " +
                "вы его закрыли! Нужно для навигаторов и фитнес-трекеров, но может разряжать батарею и следить за вами.",

        // --- Хранилище и файлы ---
        "android.permission.READ_EXTERNAL_STORAGE" to "Приложение сможет читать все файлы в памяти телефона — фото, " +
                "документы, загрузки. На старых версиях Android даёт широкий доступ.",
        "android.permission.WRITE_EXTERNAL_STORAGE" to "Приложение сможет сохранять и изменять файлы в памяти телефона. " +
                "Нужно для скачивания файлов, сохранения фото и документов.",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "Полный доступ ко ВСЕМ файлам на устройстве без ограничений! " +
                "Такое нужно только файловым менеджерам, антивирусам и приложениям для бэкапа. " +
                "Если это обычное приложение — это подозрительно.",
        "android.permission.READ_MEDIA_IMAGES" to "Приложение получит доступ к вашим фотографиям и картинкам. " +
                "Нужно для выбора фото профиля, отправки в чат или обработки.",
        "android.permission.READ_MEDIA_VIDEO" to "Приложение получит доступ к вашим видеозаписям. " +
                "Нужно для просмотра, отправки или редактирования видео.",
        "android.permission.READ_MEDIA_AUDIO" to "Приложение получит доступ к вашей музыке и аудиозаписям. " +
                "Нужно для музыкальных плееров и редакторов.",

        // --- Уведомления ---
        "android.permission.POST_NOTIFICATIONS" to "Приложение сможет показывать вам уведомления — " +
                "сообщения, напоминания, новости, рекламу. Вы сможете отключить их в настройках в любой момент.",
        "android.permission.ACCESS_NOTIFICATION_POLICY" to "Приложение сможет управлять режимом «Не беспокоить». " +
                "Нужно для приложений-будильников и менеджеров уведомлений.",

        // --- Bluetooth ---
        "android.permission.BLUETOOTH" to "Приложение сможет подключаться к Bluetooth-устройствам — " +
                "наушникам, колонкам, умным часам, принтерам.",
        "android.permission.BLUETOOTH_ADMIN" to "Приложение сможет управлять Bluetooth — включать, выключать " +
                "и настраивать подключения к устройствам.",
        "android.permission.BLUETOOTH_CONNECT" to "Приложение сможет подключаться к уже знакомым Bluetooth-устройствам — " +
                "наушникам, колонкам, часам, которые вы ранее сопрягали.",
        "android.permission.BLUETOOTH_SCAN" to "Приложение будет искать Bluetooth-устройства поблизости. " +
                "Нужно при первом подключении нового устройства.",
        "android.permission.BLUETOOTH_ADVERTISE" to "Приложение сможет делать ваше устройство видимым для других " +
                "по Bluetooth. Нужно для передачи файлов между телефонами.",

        // --- Календарь ---
        "android.permission.READ_CALENDAR" to "Приложение увидит все ваши события в календаре — встречи, " +
                "дни рождения, напоминания. Может использоваться для планирования.",
        "android.permission.WRITE_CALENDAR" to "Приложение сможет добавлять события в ваш календарь и менять " +
                "существующие. Нужно для планировщиков и приложений с расписанием.",

        // --- Датчики и активность ---
        "android.permission.BODY_SENSORS" to "Приложение получит данные с датчиков здоровья — пульс, " +
                "давление, уровень кислорода. Нужно для фитнес-приложений и мониторинга здоровья.",
        "android.permission.BODY_SENSORS_BACKGROUND" to "Приложение будет считывать датчики здоровья даже в фоне. " +
                "Нужно для постоянного мониторинга пульса или сна.",
        "android.permission.ACTIVITY_RECOGNITION" to "Приложение будет определять чем вы занимаетесь — идёте, " +
                "бежите, едете на велосипеде или в машине. Нужно для фитнес-трекеров и шагомеров.",
        "android.permission.HIGH_SAMPLING_RATE_SENSORS" to "Приложение получит ускоренные данные с датчиков " +
                "движения. Нужно для спортивных трекеров и игр с управлением наклоном.",

        // --- Биометрия ---
        "android.permission.USE_BIOMETRIC" to "Приложение предложит войти по отпечатку пальца или распознаванию лица " +
                "вместо ввода пароля. Ваши биометрические данные не передаются приложению — проверку делает система.",
        "android.permission.USE_FINGERPRINT" to "Приложение предложит подтвердить действие отпечатком пальца. " +
                "Ваш отпечаток хранится только в защищённой памяти устройства.",

        // --- Системное ---
        "android.permission.VIBRATE" to "Приложение сможет включать вибрацию — для уведомлений, " +
                "будильников или обратной связи при нажатиях. Безобидное разрешение.",
        "android.permission.WAKE_LOCK" to "Приложение не даст телефону уйти в сон пока выполняет задачу — " +
                "скачивание файла, воспроизведение музыки, навигация. Может влиять на расход батареи.",
        "android.permission.RECEIVE_BOOT_COMPLETED" to "Приложение будет запускаться автоматически после " +
                "перезагрузки телефона. Нужно для будильников, мессенджеров и мониторинга. " +
                "Много таких приложений замедляют загрузку устройства.",
        "android.permission.FOREGROUND_SERVICE" to "Приложение сможет работать в фоне и показывать значок " +
                "в шторке уведомлений. Нужно для музыкальных плееров, загрузчиков и навигаторов.",
        "android.permission.FOREGROUND_SERVICE_DATA_SYNC" to "Приложение сможет синхронизировать данные в фоне — " +
                "загружать или отправлять файлы, даже когда вы его свернули.",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" to "Приложение сможет играть музыку или видео в фоне — " +
                "когда экран выключен или открыто другое приложение.",
        "android.permission.FOREGROUND_SERVICE_LOCATION" to "Приложение сможет отслеживать местоположение в фоне " +
                "с уведомлением. Нужно для навигаторов и фитнес-трекеров.",
        "android.permission.FOREGROUND_SERVICE_CAMERA" to "Приложение сможет использовать камеру в фоне. " +
                "Нужно для видеозвонков и приложений видеонаблюдения.",
        "android.permission.FOREGROUND_SERVICE_MICROPHONE" to "Приложение сможет записывать звук в фоне. " +
                "Нужно для записи звонков, диктофона или голосовых помощников.",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "Приложение сможет предлагать установить другие APK-файлы. " +
                "Нужно для магазинов приложений и автообновления. Если это не магазин — будьте осторожны.",
        "android.permission.REQUEST_DELETE_PACKAGES" to "Приложение сможет предлагать удалить другие приложения. " +
                "Нужно для антивирусов и менеджеров приложений.",
        "android.permission.SYSTEM_ALERT_WINDOW" to "Приложение сможет показывать окна поверх всех приложений! " +
                "Нужно для плавающих плееров, экрана звонка и чат-пузырей. " +
                "Мошенники могут использовать для подделки интерфейса.",
        "android.permission.QUERY_ALL_PACKAGES" to "Приложение увидит список всех установленных приложений на устройстве. " +
                "Нужно для антивирусов и лаунчеров, но может использоваться для анализа ваших предпочтений.",
        "android.permission.SCHEDULE_EXACT_ALARM" to "Приложение сможет ставить будильники и напоминания " +
                "на точное время. Нужно для будильников, планировщиков и приложений с расписанием.",
        "android.permission.USE_EXACT_ALARM" to "Приложение сможет срабатывать точно в заданное время, " +
                "даже если телефон в режиме энергосбережения. Нужно для будильников.",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to "Приложение попросит отключить для него " +
                "экономию батареи. Нужно для мессенджеров и приложений с уведомлениями, " +
                "но увеличит расход заряда.",
        "android.permission.PACKAGE_USAGE_STATS" to "Приложение увидит статистику использования других приложений — " +
                "какие вы открываете и сколько времени проводите. Нужно для контроля экранного времени.",

        // --- NFC ---
        "android.permission.NFC" to "Приложение сможет обмениваться данными через NFC — бесконтактная оплата, " +
                "считывание меток, проездных билетов или пропусков.",
        "android.permission.NFC_TRANSACTION_EVENT" to "Приложение будет получать уведомления о бесконтактных NFC-платежах.",

        // --- Ярлыки и интерфейс ---
        "android.permission.INSTALL_SHORTCUT" to "Приложение сможет создать ярлык на вашем рабочем столе " +
                "для быстрого доступа. Безобидное разрешение.",

        // --- Покупки ---
        "com.android.vending.BILLING" to "Приложение поддерживает покупки через Google Play — подписки, " +
                "разблокировка функций, удаление рекламы или виртуальные товары.",
        "com.android.vending.CHECK_LICENSE" to "Приложение проверяет лицензию через Google Play — " +
                "подтверждает что вы его законно купили/скачали."
    )

    private fun compareSignatures(apkInfo: PackageInfo, installedInfo: PackageInfo): SignatureStatus {
        return try {
            val apkSigningInfo = apkInfo.signingInfo
            val installedSigningInfo = installedInfo.signingInfo
            if (apkSigningInfo == null || installedSigningInfo == null) {
                return SignatureStatus.UNKNOWN
            }

            val apkSignatures = if (apkSigningInfo.hasMultipleSigners()) {
                apkSigningInfo.apkContentsSigners
            } else {
                apkSigningInfo.signingCertificateHistory
            }

            val installedSignatures = if (installedSigningInfo.hasMultipleSigners()) {
                installedSigningInfo.apkContentsSigners
            } else {
                installedSigningInfo.signingCertificateHistory
            }

            if (apkSignatures == null || installedSignatures == null) {
                return SignatureStatus.UNKNOWN
            }

            val apkSet = apkSignatures.map { it.toByteArray().contentHashCode() }.toSet()
            val installedSet = installedSignatures.map { it.toByteArray().contentHashCode() }.toSet()

            if (apkSet == installedSet) SignatureStatus.MATCH else SignatureStatus.MISMATCH
        } catch (_: Exception) {
            SignatureStatus.UNKNOWN
        }
    }

    private fun copyToTemp(entry: FileEntry): File {
        val tempFile = File(context.cacheDir, "apk_install_${System.currentTimeMillis()}.apk")
        context.contentResolver.openInputStream(Uri.parse(entry.path))?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open content URI")
        return tempFile
    }
}
