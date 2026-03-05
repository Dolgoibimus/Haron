package com.vamp.haron.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.domain.model.GestureAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceCommand"

enum class VoiceState {
    IDLE, LISTENING, PROCESSING, ERROR
}

@Singleton
class VoiceCommandManager @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val _state = MutableStateFlow(VoiceState.IDLE)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _lastResult = MutableStateFlow<GestureAction?>(null)
    val lastResult: StateFlow<GestureAction?> = _lastResult.asStateFlow()

    /** Pending sort params set when SORT_SPECIFIC is matched. */
    var pendingSortField: SortField? = null
        private set
    var pendingSortDirection: SortDirection? = null
        private set

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var busyRetryCount = 0

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (recognizer != null) return recognizer
        EcosystemLogger.d(TAG, "Creating SpeechRecognizer")
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)?.apply {
            setRecognitionListener(listener)
        }
        return recognizer
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            EcosystemLogger.d(TAG, "onReadyForSpeech")
            busyRetryCount = 0
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            _state.value = VoiceState.PROCESSING
        }
        override fun onError(error: Int) {
            val errorName = when (error) {
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
                SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
                SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
                SpeechRecognizer.ERROR_SERVER -> "SERVER"
                SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
                SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
                else -> "UNKNOWN($error)"
            }
            EcosystemLogger.d(TAG, "onError: $errorName")

            // Retry on BUSY — recognizer may still be shutting down from previous session
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && busyRetryCount < 3) {
                busyRetryCount++
                EcosystemLogger.d(TAG, "BUSY retry #$busyRetryCount in 300ms")
                mainHandler.postDelayed({ doStartListening() }, 300)
                return
            }

            // On CLIENT error — destroy and recreate (recognizer in bad state)
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                destroyRecognizer()
            }

            _state.value = VoiceState.IDLE
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            EcosystemLogger.d(TAG, "onResults: ${matches?.take(3)}")
            val action = matches?.firstNotNullOfOrNull { matchPhrase(it) }
            _lastResult.value = action
            _state.value = VoiceState.IDLE
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        if (!isAvailable) {
            _state.value = VoiceState.ERROR
            return
        }
        // Cancel previous listening without destroying recognizer
        try { recognizer?.cancel() } catch (_: Exception) {}
        _state.value = VoiceState.LISTENING
        _lastResult.value = null
        busyRetryCount = 0
        doStartListening()
    }

    private fun doStartListening() {
        val rec = ensureRecognizer()
        if (rec == null) {
            EcosystemLogger.d(TAG, "Failed to create SpeechRecognizer")
            _state.value = VoiceState.ERROR
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Accept both Russian and English
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
            // Extend silence timeout — give user more time to speak
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        }

        rec.startListening(intent)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel() } catch (_: Exception) {}
        if (_state.value == VoiceState.LISTENING || _state.value == VoiceState.PROCESSING) {
            _state.value = VoiceState.IDLE
        }
    }

    /** Full destroy — call only on Activity destroy or critical error */
    fun destroy() {
        stop()
        destroyRecognizer()
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    fun consumeResult() {
        _lastResult.value = null
    }

    fun consumeSortParams() {
        pendingSortField = null
        pendingSortDirection = null
    }

    private fun matchPhrase(text: String): GestureAction? {
        val lower = text.lowercase().trim()

        // Special sort handling: "сортировка имя вниз" etc.
        val sortAction = tryMatchSort(lower)
        if (sortAction != null) return sortAction

        // Logs pause/resume (check before general "логи" to avoid false match)
        val logsAction = tryMatchLogs(lower)
        if (logsAction != null) return logsAction

        // General phrase matching
        return PHRASE_MAP.entries.firstOrNull { (phrases, _) ->
            phrases.any { phrase -> lower.contains(phrase) }
        }?.value
    }

    private fun tryMatchLogs(lower: String): GestureAction? {
        val logTriggers = listOf("лог", "logs")
        if (logTriggers.none { lower.contains(it) }) return null
        val stopWords = listOf("стоп", "stop", "пауза", "pause", "останов", "выключ")
        val startWords = listOf("начать", "start", "resume", "продолж", "включ", "запуст")
        return when {
            stopWords.any { lower.contains(it) } -> GestureAction.LOGS_PAUSE
            startWords.any { lower.contains(it) } -> GestureAction.LOGS_RESUME
            else -> null // fall through to PHRASE_MAP for plain "логи" → OPEN_LOGS
        }
    }

    private fun tryMatchSort(lower: String): GestureAction? {
        val sortTriggers = listOf("сортировка", "сортировку", "sort", "отсортируй", "сортируй")
        if (sortTriggers.none { lower.contains(it) }) return null

        // Parse sort field
        val field = when {
            SORT_FIELD_NAME.any { lower.contains(it) } -> SortField.NAME
            SORT_FIELD_SIZE.any { lower.contains(it) } -> SortField.SIZE
            SORT_FIELD_DATE.any { lower.contains(it) } -> SortField.DATE
            SORT_FIELD_EXT.any { lower.contains(it) } -> SortField.EXTENSION
            else -> null
        }

        // Parse sort direction
        val direction = when {
            SORT_DIR_ASC.any { lower.contains(it) } -> SortDirection.ASCENDING
            SORT_DIR_DESC.any { lower.contains(it) } -> SortDirection.DESCENDING
            else -> null
        }

        if (field != null) {
            pendingSortField = field
            pendingSortDirection = direction
            return GestureAction.SORT_SPECIFIC
        }

        // Plain "сортировка" without params → cycle
        return GestureAction.SORT_CYCLE
    }

    companion object {
        // Sort field keywords
        private val SORT_FIELD_NAME = listOf("имя", "имени", "name", "названи")
        private val SORT_FIELD_SIZE = listOf("размер", "size", "вес")
        private val SORT_FIELD_DATE = listOf("дат", "date", "время", "time")
        private val SORT_FIELD_EXT = listOf("тип", "расширени", "extension", "type", "формат")

        // Sort direction keywords
        private val SORT_DIR_ASC = listOf("вверх", "возрастани", "asc", "ascending", "up")
        private val SORT_DIR_DESC = listOf("вниз", "убывани", "desc", "descending", "down")

        /**
         * Map of trigger phrases (Russian + English) to actions.
         * Phrases are checked with `contains` so partial matches work.
         * Sort is handled separately in tryMatchSort().
         */
        private val PHRASE_MAP: Map<List<String>, GestureAction> = mapOf(
            // Menu / Drawer
            listOf("меню", "menu", "открой меню", "open menu") to GestureAction.OPEN_DRAWER,
            // Shelf
            listOf("полка", "полку", "shelf", "буфер", "clipboard") to GestureAction.OPEN_SHELF,
            // Hidden files
            listOf("скрытые", "hidden", "показать скрытые", "show hidden", "спрятанные") to GestureAction.TOGGLE_HIDDEN,
            // Create
            listOf("создать", "создай", "новый", "новая", "create", "new") to GestureAction.CREATE_NEW,
            // Search
            listOf("поиск", "найти", "найди", "search", "find") to GestureAction.GLOBAL_SEARCH,
            // Terminal
            listOf("терминал", "terminal", "консоль", "console", "командная строка") to GestureAction.OPEN_TERMINAL,
            // Select all
            listOf("выделить все", "выдели все", "select all") to GestureAction.SELECT_ALL,
            // Refresh
            listOf("обновить", "обнови", "refresh", "reload") to GestureAction.REFRESH,
            // Home / Root
            listOf("домой", "на главную", "корень", "home", "root", "go home") to GestureAction.GO_HOME,
            // Settings
            listOf("настройки", "settings", "параметры") to GestureAction.OPEN_SETTINGS,
            // Transfer
            listOf("передача", "передать", "отправить", "transfer", "send") to GestureAction.OPEN_TRANSFER,
            // Trash
            listOf("корзин", "trash", "мусор") to GestureAction.OPEN_TRASH,
            // Storage analysis
            listOf("анализ", "память", "storage", "analysis", "хранилище") to GestureAction.OPEN_STORAGE,
            // Duplicate detector
            listOf("дубликат", "duplicate", "duplicates", "копии") to GestureAction.OPEN_DUPLICATES,
            // App manager
            listOf("приложени", "apps", "applications", "менеджер приложений", "app manager", "апк", "apk") to GestureAction.OPEN_APPS,
            // Scanner
            listOf("сканер", "скан", "scanner", "scan", "штрихкод", "barcode", "qr") to GestureAction.OPEN_SCANNER,
            // Logs
            listOf("логи", "logs", "лог") to GestureAction.OPEN_LOGS
        )
    }
}
