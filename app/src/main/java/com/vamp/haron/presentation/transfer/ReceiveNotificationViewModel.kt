package com.vamp.haron.presentation.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.domain.repository.TransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReceiveNotificationViewModel @Inject constructor(
    private val transferRepository: TransferRepository
) : ViewModel() {

    /** Sender display name to show in the circle, or null if hidden */
    private val _senderName = MutableStateFlow<String?>(null)
    val senderName: StateFlow<String?> = _senderName.asStateFlow()

    val showNotification: Boolean get() = _senderName.value != null

    init {
        viewModelScope.launch {
            transferRepository.friendReceived.collect { name ->
                _senderName.value = name
            }
        }
    }

    fun dismiss() {
        _senderName.value = null
    }
}
