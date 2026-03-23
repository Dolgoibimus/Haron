package com.vamp.haron.presentation.search

import androidx.lifecycle.ViewModel
import com.vamp.haron.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class IndexNotificationViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {
    val showNotification: StateFlow<Boolean> = searchRepository.indexCompleted
    fun dismiss() = searchRepository.dismissIndexCompleted()
}
