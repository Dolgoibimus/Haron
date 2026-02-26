package com.vamp.haron.presentation.steganography

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.R
import com.vamp.haron.domain.model.StegoDetectResult
import com.vamp.haron.domain.model.StegoHolder
import com.vamp.haron.domain.model.StegoPhase
import com.vamp.haron.domain.model.StegoProgress
import com.vamp.haron.domain.model.StegoResult
import com.vamp.haron.domain.repository.SteganographyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class StegoMode { HIDE, EXTRACT }

data class StegoUiState(
    val mode: StegoMode = StegoMode.HIDE,
    val carrierPath: String = "",
    val carrierName: String = "",
    val payloadPath: String = "",
    val payloadName: String = "",
    val detectResult: StegoDetectResult? = null,
    val progress: StegoProgress = StegoProgress(),
    val isProcessing: Boolean = false,
    val resultMessage: String? = null
)

@HiltViewModel
class SteganographyViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val stegoRepository: SteganographyRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StegoUiState())
    val state = _state.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    init {
        val carrier = StegoHolder.carrierPath
        val payload = StegoHolder.payloadPath
        if (carrier.isNotEmpty()) {
            _state.update {
                it.copy(
                    carrierPath = carrier,
                    carrierName = File(carrier).name
                )
            }
            detectHidden(carrier)
        }
        if (payload.isNotEmpty()) {
            _state.update {
                it.copy(
                    payloadPath = payload,
                    payloadName = File(payload).name
                )
            }
        }
    }

    fun setMode(mode: StegoMode) {
        _state.update { it.copy(mode = mode) }
    }

    fun setCarrier(path: String) {
        _state.update {
            it.copy(
                carrierPath = path,
                carrierName = File(path).name,
                detectResult = null
            )
        }
        detectHidden(path)
    }

    fun setPayload(path: String) {
        _state.update {
            it.copy(
                payloadPath = path,
                payloadName = File(path).name
            )
        }
    }

    fun hidePayload() {
        val s = _state.value
        if (s.carrierPath.isEmpty() || s.payloadPath.isEmpty()) return

        _state.update { it.copy(isProcessing = true) }

        viewModelScope.launch {
            val ext = s.carrierName.substringAfterLast('.', "")
            val baseName = s.carrierName.substringBeforeLast('.')
            val outputName = "${baseName}_stego.$ext"
            val outputDir = File(s.carrierPath).parent
                ?: Environment.getExternalStorageDirectory().absolutePath
            val outputPath = File(outputDir, outputName).absolutePath

            stegoRepository.hidePayload(s.carrierPath, s.payloadPath, outputPath)
                .collect { progress ->
                    _state.update { it.copy(progress = progress) }
                }

            val finalState = _state.value.progress
            _state.update { it.copy(isProcessing = false) }

            if (finalState.phase == StegoPhase.DONE) {
                _toastMessage.tryEmit(appContext.getString(R.string.stego_hidden_success))
                _state.update {
                    it.copy(resultMessage = appContext.getString(R.string.stego_output_file, outputPath))
                }
            } else if (finalState.phase == StegoPhase.ERROR) {
                _toastMessage.tryEmit(appContext.getString(R.string.stego_error, finalState.message))
            }
        }
    }

    fun extractPayload() {
        val s = _state.value
        if (s.carrierPath.isEmpty()) return

        _state.update { it.copy(isProcessing = true, progress = StegoProgress(StegoPhase.EXTRACTING)) }

        viewModelScope.launch {
            val outputDir = File(s.carrierPath).parent
                ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

            when (val result = stegoRepository.extractPayload(s.carrierPath, outputDir)) {
                is StegoResult.Extracted -> {
                    _toastMessage.tryEmit(appContext.getString(R.string.stego_extracted_success, result.payloadName))
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            progress = StegoProgress(StegoPhase.DONE),
                            resultMessage = appContext.getString(R.string.stego_output_file, result.outputPath)
                        )
                    }
                }
                is StegoResult.Error -> {
                    _toastMessage.tryEmit(appContext.getString(R.string.stego_error, result.message))
                    _state.update {
                        it.copy(
                            isProcessing = false,
                            progress = StegoProgress(StegoPhase.ERROR, message = result.message)
                        )
                    }
                }
                else -> {
                    _state.update { it.copy(isProcessing = false) }
                }
            }
        }
    }

    private fun detectHidden(path: String) {
        viewModelScope.launch {
            val result = stegoRepository.detectHiddenData(path)
            _state.update { it.copy(detectResult = result) }
        }
    }
}
