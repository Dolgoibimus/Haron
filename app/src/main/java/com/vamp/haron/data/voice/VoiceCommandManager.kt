package com.vamp.haron.data.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
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
import com.vamp.haron.domain.model.PanelId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "VoiceCommand"
private val WAKE_WORDS = listOf(
    "харон", "харун", "хорон", "хару", "haron",
    "арон", "хрон", "хором", "хорош"
)

enum class VoiceState {
    IDLE, LISTENING, PROCESSING, ERROR,
    /** Wake word mode — listening for "Харон". */
    WAKE_LISTENING,
    /** Wake word heard, listening for command. */
    WAKE_ACTIVATED
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

    /** Pending folder query for NAVIGATE_TO_FOLDER. */
    var pendingFolderQuery: String? = null
        private set

    /** Pending rename name for RENAME (e.g. "переименуй в тест"). */
    var pendingRenameName: String? = null
        private set

    /** Panel override for "назад вверху" / "копировать внизу". */
    var pendingPanelOverride: PanelId? = null
        private set

    private var recognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var busyRetryCount = 0
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var beepMuted = false

    /** Wake word mode flag — when true, auto-restarts recognizer after each result/error. */
    private val _wakeWordEnabled = MutableStateFlow(false)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    /** True when "Харон" was heard, waiting for command. */
    private var wakeActivated = false

    /** True while manual one-shot listening (FAB tap) is active — overrides wake restart. */
    private var manualMode = false

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    private var lastRecognizerCreateTime = 0L

    /** Mute recognition beep in wake mode to avoid constant sounds. */
    private fun muteBeep() {
        if (beepMuted) return
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
            beepMuted = true
        } catch (_: Exception) {}
    }

    private fun unmuteBeep() {
        if (!beepMuted) return
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
            beepMuted = false
        } catch (_: Exception) {}
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (recognizer != null) return recognizer
        val now = System.currentTimeMillis()
        val sinceLast = now - lastRecognizerCreateTime
        EcosystemLogger.d(TAG, "Creating SpeechRecognizer (since last: ${sinceLast}ms)")
        lastRecognizerCreateTime = now
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)?.apply {
            setRecognitionListener(listener)
        }
        return recognizer
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            EcosystemLogger.d(TAG, "onReadyForSpeech (wake=${_wakeWordEnabled.value}, activated=$wakeActivated, manual=$manualMode)")
            busyRetryCount = 0
            // In wake mode keep muted the entire time — no unmute between restarts.
            // Only unmute for manual mode (FAB tap) after beep window passes.
            if (beepMuted && manualMode) {
                mainHandler.postDelayed({ unmuteBeep() }, 500)
            }
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            // In wake mode keep WAKE_LISTENING to avoid visual flicker on FAB
            if (_wakeWordEnabled.value && !manualMode) return
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

            // Wake word mode: auto-restart on timeout/no-match/network errors
            if (_wakeWordEnabled.value && !manualMode) {
                wakeActivated = false
                val delay = if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || error == SpeechRecognizer.ERROR_NO_MATCH) 100L else 1000L
                _state.value = VoiceState.WAKE_LISTENING
                mainHandler.postDelayed({ restartWakeListening() }, delay)
                return
            }

            _state.value = VoiceState.IDLE
        }
        override fun onResults(results: Bundle?) {
            val t0 = System.nanoTime()
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            EcosystemLogger.d(TAG, "onResults: ${matches?.take(3)} (wake=${_wakeWordEnabled.value}, activated=$wakeActivated, manual=$manualMode)")

            if (_wakeWordEnabled.value && !manualMode) {
                handleWakeResults(matches)
                val elapsedMs = (System.nanoTime() - t0) / 1_000_000
                if (elapsedMs > 2) EcosystemLogger.d("Perf", "onResults(wake): ${elapsedMs}ms")
                return
            }

            // Normal (manual) mode
            val action = matches?.firstNotNullOfOrNull { matchPhrase(it) }
            _lastResult.value = action
            manualMode = false
            // After manual command, return to wake listening if enabled
            if (_wakeWordEnabled.value) {
                _state.value = VoiceState.WAKE_LISTENING
                mainHandler.postDelayed({ restartWakeListening() }, 300)
            } else {
                _state.value = VoiceState.IDLE
            }
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // --- Wake word result handling ---

    private fun handleWakeResults(matches: java.util.ArrayList<String>?) {
        if (matches.isNullOrEmpty()) {
            _state.value = VoiceState.WAKE_LISTENING
            mainHandler.postDelayed({ restartWakeListening() }, 200)
            return
        }

        if (wakeActivated) {
            // Already heard "Харон", this is the command — try all alternatives
            EcosystemLogger.d(TAG, "Wake command alternatives: ${matches.take(5)}")
            val action = matches.firstNotNullOfOrNull { matchPhrase(it) }
            _lastResult.value = action
            wakeActivated = false
            _state.value = VoiceState.WAKE_LISTENING
            mainHandler.postDelayed({ restartWakeListening() }, 300)
            return
        }

        // Check ALL alternatives for wake word
        for (alt in matches) {
            val lower = alt.lowercase().trim()
            val wakeMatch = findWakeWord(lower) ?: continue

            val afterWake = lower.substring(wakeMatch.endIndex).trim()
            if (afterWake.isNotEmpty()) {
                // "Харон открой загрузки" — try matching command from this alternative
                val action = matchPhrase(afterWake)
                if (action != null) {
                    EcosystemLogger.d(TAG, "Wake + command (alt): '$afterWake' from '$lower'")
                    _lastResult.value = action
                    _state.value = VoiceState.WAKE_LISTENING
                    mainHandler.postDelayed({ restartWakeListening() }, 300)
                    return
                }
            } else {
                // Just "Харон" — activate, wait for command
                EcosystemLogger.d(TAG, "Wake word activated from: '$lower'")
                wakeActivated = true
                _state.value = VoiceState.WAKE_ACTIVATED
                mainHandler.postDelayed({ restartWakeListening() }, 100)
                return
            }
        }

        // Also try: maybe Google split the command weirdly — extract text after any wake word
        // and try matchPhrase on ALL alternatives' afterWake parts
        val afterWakeCandidates = matches.mapNotNull { alt ->
            val lower = alt.lowercase().trim()
            val wm = findWakeWord(lower) ?: return@mapNotNull null
            lower.substring(wm.endIndex).trim().takeIf { it.isNotEmpty() }
        }
        if (afterWakeCandidates.isNotEmpty()) {
            val action = afterWakeCandidates.firstNotNullOfOrNull { matchPhrase(it) }
            if (action != null) {
                EcosystemLogger.d(TAG, "Wake + command (fallback): $afterWakeCandidates")
                _lastResult.value = action
                _state.value = VoiceState.WAKE_LISTENING
                mainHandler.postDelayed({ restartWakeListening() }, 300)
                return
            }
        }

        // No wake word found — ignore, restart
        _state.value = VoiceState.WAKE_LISTENING
        mainHandler.postDelayed({ restartWakeListening() }, 200)
    }

    /** Find wake word in text, return match with end index. */
    private data class WakeMatch(val word: String, val endIndex: Int)

    private fun findWakeWord(lower: String): WakeMatch? {
        for (w in WAKE_WORDS) {
            val idx = lower.indexOf(w)
            if (idx >= 0) {
                // Advance past the end of the word containing the wake word
                // e.g. "харона открой" → skip past "харона" (to the space), not just "харон"
                var endIdx = idx + w.length
                while (endIdx < lower.length && lower[endIdx] != ' ') endIdx++
                return WakeMatch(w, endIdx)
            }
        }
        return null
    }

    private fun restartWakeListening() {
        if (!_wakeWordEnabled.value) return
        doStartListening()
    }

    // --- Public API ---

    /** Manual one-shot listening (FAB tap). */
    fun startListening() {
        if (!isAvailable) {
            _state.value = VoiceState.ERROR
            return
        }
        unmuteBeep() // manual mode — let the user hear the beep
        // Cancel previous listening without destroying recognizer
        try { recognizer?.cancel() } catch (_: Exception) {}
        mainHandler.removeCallbacksAndMessages(null)
        manualMode = true
        wakeActivated = false
        _state.value = VoiceState.LISTENING
        _lastResult.value = null
        busyRetryCount = 0
        doStartListening()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
        if (enabled) {
            if (_state.value == VoiceState.IDLE) {
                manualMode = false
                wakeActivated = false
                _state.value = VoiceState.WAKE_LISTENING
                doStartListening()
            }
        } else {
            if (!manualMode) {
                mainHandler.removeCallbacksAndMessages(null)
                try { recognizer?.cancel() } catch (_: Exception) {}
                wakeActivated = false
                unmuteBeep()
                _state.value = VoiceState.IDLE
            }
        }
    }

    private fun doStartListening() {
        val rec = ensureRecognizer()
        if (rec == null) {
            EcosystemLogger.d(TAG, "Failed to create SpeechRecognizer")
            _state.value = if (_wakeWordEnabled.value && !manualMode) VoiceState.WAKE_LISTENING else VoiceState.ERROR
            // Retry wake mode after delay
            if (_wakeWordEnabled.value && !manualMode) {
                mainHandler.postDelayed({ restartWakeListening() }, 2000)
            }
            return
        }

        val isWake = _wakeWordEnabled.value && !manualMode
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Accept both Russian and English
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
            if (isWake && !wakeActivated) {
                // Wake mode: longer silence timeout (wait for user to speak)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            } else {
                // Manual mode or wake-activated: shorter timeout for faster response
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
            }
        }

        // Mute beep sound in wake mode to avoid constant pinging
        if (isWake) muteBeep()

        rec.startListening(intent)
    }

    fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        manualMode = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        if (_state.value == VoiceState.LISTENING || _state.value == VoiceState.PROCESSING) {
            // After manual stop, return to wake if enabled
            if (_wakeWordEnabled.value) {
                _state.value = VoiceState.WAKE_LISTENING
                mainHandler.postDelayed({ restartWakeListening() }, 300)
            } else {
                _state.value = VoiceState.IDLE
            }
        }
    }

    /** Pause wake word when app goes to background. */
    fun pause() {
        if (_wakeWordEnabled.value && !manualMode) {
            mainHandler.removeCallbacksAndMessages(null)
            try { recognizer?.cancel() } catch (_: Exception) {}
            wakeActivated = false
            unmuteBeep()
            _state.value = VoiceState.WAKE_LISTENING // keep state so UI shows it's "enabled but paused"
            EcosystemLogger.d(TAG, "Paused (app backgrounded)")
        }
    }

    /** Resume wake word when app comes back to foreground. */
    fun resume() {
        if (_wakeWordEnabled.value && !manualMode) {
            EcosystemLogger.d(TAG, "Resumed (app foregrounded)")
            restartWakeListening()
        }
    }

    /** Full destroy — call only on Activity destroy or critical error */
    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        manualMode = false
        wakeActivated = false
        unmuteBeep()
        try { recognizer?.cancel() } catch (_: Exception) {}
        destroyRecognizer()
        _state.value = VoiceState.IDLE
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

    fun consumeFolderQuery(): String? {
        val q = pendingFolderQuery
        pendingFolderQuery = null
        return q
    }

    fun consumeRenameName(): String? {
        val n = pendingRenameName
        pendingRenameName = null
        return n
    }

    fun consumePanelOverride(): PanelId? {
        val p = pendingPanelOverride
        pendingPanelOverride = null
        return p
    }

    // --- Phrase matching ---

    /** Pre-sorted flat list: (phrase, action) sorted by phrase length descending.
     *  First match = longest match, no need to scan the whole map. */
    private val sortedPhrases: List<Pair<String, GestureAction>> by lazy {
        PHRASE_MAP.flatMap { (phrases, action) ->
            phrases.map { phrase -> phrase to action }
        }.sortedByDescending { it.first.length }
    }

    private fun matchPhrase(text: String): GestureAction? {
        val t0 = System.nanoTime()
        val lower = text.lowercase().trim()

        // Detect panel modifier: "вверху"/"внизу" → override target panel
        pendingPanelOverride = detectPanelOverride(lower)

        // Special sort handling: "сортировка имя вниз" etc.
        val sortAction = tryMatchSort(lower)
        if (sortAction != null) { logPerfMatch(t0); return sortAction }

        // Logs pause/resume (check before general "логи" to avoid false match)
        val logsAction = tryMatchLogs(lower)
        if (logsAction != null) { logPerfMatch(t0); return logsAction }

        // Rename: "переименуй в тест" / "rename to test"
        val renameAction = tryMatchRename(lower)
        if (renameAction != null) { logPerfMatch(t0); return renameAction }

        // Navigation: "открой загрузки" / "go to downloads"
        val navAction = tryMatchNavigation(lower)
        if (navAction != null) { logPerfMatch(t0); return navAction }

        // General phrase matching — sorted by length desc, first match = longest
        val result = sortedPhrases.firstOrNull { lower.contains(it.first) }?.second
        logPerfMatch(t0)
        return result
    }

    private fun logPerfMatch(startNanos: Long) {
        val elapsedUs = (System.nanoTime() - startNanos) / 1000
        if (elapsedUs > 500) { // log only if > 0.5ms
            EcosystemLogger.d("Perf", "matchPhrase: ${elapsedUs}μs")
        }
    }

    /** Detect panel modifier in phrase: "вверху"/"внизу"/"верхн"/"нижн". */
    private fun detectPanelOverride(lower: String): PanelId? {
        val topWords = listOf("вверху", "верхн", "первая", "первой", "top panel")
        val bottomWords = listOf("внизу", "нижн", "вторая", "второй", "bottom panel")
        if (topWords.any { lower.contains(it) }) return PanelId.TOP
        if (bottomWords.any { lower.contains(it) }) return PanelId.BOTTOM
        return null
    }

    private fun tryMatchRename(lower: String): GestureAction? {
        val renameTriggers = listOf("переименуй в ", "переименовать в ", "rename to ")
        for (trigger in renameTriggers) {
            val idx = lower.indexOf(trigger)
            if (idx >= 0) {
                val name = lower.substring(idx + trigger.length).trim()
                pendingRenameName = name.ifEmpty { null }
                return GestureAction.RENAME
            }
        }
        // Simple "переименуй" / "rename" without a name — handled by PHRASE_MAP
        return null
    }

    private fun tryMatchNavigation(lower: String): GestureAction? {
        val navPrefixes = listOf(
            "открой папку ", "открыть папку ", "откройте папку ",
            "открой ", "открыть ", "откройте ",
            "перейди в ", "перейди к ", "зайди в ",
            "go to ", "open folder ", "open ", "navigate to "
        )
        for (prefix in navPrefixes) {
            val idx = lower.indexOf(prefix)
            if (idx < 0) continue
            var query = lower.substring(idx + prefix.length).trim()
            if (query.isEmpty()) continue
            // Strip panel modifiers from query so they don't break folder matching
            query = stripPanelWords(query)
            if (query.isEmpty()) continue
            // If the remaining query matches an existing PHRASE_MAP command — skip
            if (isExistingCommand(query)) return null
            pendingFolderQuery = query
            EcosystemLogger.d(TAG, "Navigation query: $query")
            return GestureAction.NAVIGATE_TO_FOLDER
        }
        return null
    }

    /** Remove panel modifier words from text: "картинки внизу" → "картинки". */
    private fun stripPanelWords(text: String): String {
        val panelWords = listOf("вверху", "внизу", "верхн", "нижн", "первая", "первой",
            "вторая", "второй", "top panel", "bottom panel", "наверху", "снизу",
            "в верху", "во вторую", "в первую")
        var result = text
        for (w in panelWords) {
            result = result.replace(w, "").trim()
        }
        return result
    }

    /** Check if query matches any phrase in PHRASE_MAP (to avoid "открой терминал" → navigation). */
    private fun isExistingCommand(query: String): Boolean {
        return sortedPhrases.any { (phrase, _) ->
            query.contains(phrase) || phrase.contains(query)
        }
    }

    private fun tryMatchLogs(lower: String): GestureAction? {
        val logTriggers = listOf("лог", "logs")
        if (logTriggers.none { lower.contains(it) }) return null
        val stopWords = listOf("стоп", "stop", "пауза", "pause", "останов", "выключ")
        val startWords = listOf("начать", "start", "старт", "resume", "продолж", "включ", "запуст")
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
        private val SORT_DIR_ASC = listOf("возрастани", "asc", "ascending")
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
            // Terminal
            listOf("терминал", "terminal", "консоль", "console", "командная строка") to GestureAction.OPEN_TERMINAL,
            // Select all
            listOf("выделить все", "выдели все", "выделить всё", "выдели всё", "select all") to GestureAction.SELECT_ALL,
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
            listOf("логи", "logs", "лог") to GestureAction.OPEN_LOGS,
            // --- Level 1: new commands ---
            // Back
            listOf("назад", "back", "go back") to GestureAction.NAVIGATE_BACK,
            // Forward
            listOf("вперед", "вперёд", "forward", "go forward") to GestureAction.NAVIGATE_FORWARD,
            // Up
            listOf("наверх", "up", "go up", "родительская") to GestureAction.NAVIGATE_UP,
            // Delete
            listOf("удалить", "удали", "delete", "remove") to GestureAction.DELETE_SELECTED,
            // Copy
            listOf("копировать", "копируй", "скопируй", "copy") to GestureAction.COPY_SELECTED,
            // Move
            listOf("переместить", "перемести", "перенести", "перенеси", "move") to GestureAction.MOVE_SELECTED,
            // Rename (without "в name" — just opens rename dialog)
            listOf("переименовать", "переименуй", "rename") to GestureAction.RENAME,
            // Archive
            listOf("архивировать", "архивируй", "заархивируй", "запаковать", "запакуй", "archive", "zip") to GestureAction.CREATE_ARCHIVE,
            // Extract
            listOf("распаковать", "распакуй", "извлечь", "извлеки", "разархивируй", "extract", "unpack", "unzip") to GestureAction.EXTRACT_ARCHIVE,
            // Properties
            listOf("свойства", "свойство", "информация", "properties", "info") to GestureAction.FILE_PROPERTIES,
            // Deselect
            listOf("снять выделение", "сними выделение", "убрать выделение", "deselect", "deselect all") to GestureAction.DESELECT_ALL,
            // Refresh folder cache
            listOf("обнови кэш", "обновить кэш", "обнови кеш", "обновить кеш", "refresh cache", "обнови голосовой кэш") to GestureAction.REFRESH_FOLDER_CACHE,
            // Secure folder
            listOf("защищённые", "защищенные", "защита", "сейф", "secure", "protected") to GestureAction.OPEN_SECURE_FOLDER
        )
    }
}
