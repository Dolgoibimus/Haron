package com.vamp.haron.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.vamp.haron.domain.model.GestureAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun startListening() {
        if (!isAvailable) {
            _state.value = VoiceState.ERROR
            return
        }
        stop()
        _state.value = VoiceState.LISTENING
        _lastResult.value = null

        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = VoiceState.PROCESSING
                }
                override fun onError(error: Int) {
                    // Destroy finished recognizer immediately to avoid race on next start
                    destroyRecognizer()
                    _state.value = VoiceState.IDLE
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val action = matches?.firstNotNullOfOrNull { matchPhrase(it) }
                    _lastResult.value = action
                    // Destroy finished recognizer immediately to avoid race on next start
                    destroyRecognizer()
                    _state.value = VoiceState.IDLE
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Accept both Russian and English
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
        }

        recognizer?.startListening(intent)
    }

    fun stop() {
        destroyRecognizer()
        if (_state.value == VoiceState.LISTENING || _state.value == VoiceState.PROCESSING) {
            _state.value = VoiceState.IDLE
        }
    }

    private fun destroyRecognizer() {
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {}
        recognizer = null
    }

    fun consumeResult() {
        _lastResult.value = null
    }

    private fun matchPhrase(text: String): GestureAction? {
        val lower = text.lowercase().trim()
        return PHRASE_MAP.entries.firstOrNull { (phrases, _) ->
            phrases.any { phrase -> lower.contains(phrase) }
        }?.value
    }

    companion object {
        /**
         * Map of trigger phrases (Russian + English) to actions.
         * Phrases are checked with `contains` so partial matches work.
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
            // Sort
            listOf("сортировка", "сортировку", "sort", "отсортируй") to GestureAction.SORT_CYCLE,
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
            listOf("приложения", "apps", "applications", "менеджер приложений", "app manager") to GestureAction.OPEN_APPS
        )
    }
}
