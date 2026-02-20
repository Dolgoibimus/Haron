package com.vamp.haron.presentation.permission

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vamp.haron.common.util.hasStoragePermission
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _isGranted = MutableStateFlow(hasStoragePermission(application))
    val isGranted: StateFlow<Boolean> = _isGranted.asStateFlow()

    fun recheckPermission() {
        _isGranted.value = hasStoragePermission(getApplication())
    }
}
