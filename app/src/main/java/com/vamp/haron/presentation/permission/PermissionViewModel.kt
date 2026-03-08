package com.vamp.haron.presentation.permission

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
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
        val granted = hasStoragePermission(getApplication())
        EcosystemLogger.d(HaronConstants.TAG, "PermissionVM: recheckPermission storage=$granted")
        _isGranted.value = granted
    }
}
