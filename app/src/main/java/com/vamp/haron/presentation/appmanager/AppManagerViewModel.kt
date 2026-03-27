package com.vamp.haron.presentation.appmanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.haron.domain.model.AppFilesInfo
import com.vamp.haron.domain.model.InstalledAppInfo
import com.vamp.haron.domain.usecase.ExtractApkUseCase
import com.vamp.haron.domain.usecase.GetAppFilesUseCase
import com.vamp.haron.domain.usecase.GetInstalledAppsUseCase
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppSortMode(val labelRes: Int) {
    NAME(R.string.name_label),
    SIZE(R.string.size_label),
    INSTALL_DATE(R.string.sort_by_install_date)
}

data class AppManagerUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val sortMode: AppSortMode = AppSortMode.NAME,
    val showSystemApps: Boolean = false,
    val selectedApp: InstalledAppInfo? = null,
    val uninstallingPackage: String? = null,
    val removingPackage: String? = null,
    val appFilesInfo: AppFilesInfo? = null,
    val isLoadingFiles: Boolean = false
)

@HiltViewModel
class AppManagerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val extractApkUseCase: ExtractApkUseCase,
    private val getAppFilesUseCase: GetAppFilesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppManagerUiState())
    val uiState = _uiState.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastMessage = _toastMessage.asSharedFlow()

    private var allApps: List<InstalledAppInfo> = emptyList()

    init {
        loadApps()
    }

    fun loadApps() {
        EcosystemLogger.d(HaronConstants.TAG, "AppManagerVM: loading installed apps")
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                getInstalledAppsUseCase().collect { apps ->
                    allApps = apps
                    EcosystemLogger.d(HaronConstants.TAG, "AppManagerVM: loaded ${apps.size} apps")
                    _uiState.update { it.copy(isLoading = false) }
                    applyFilters()
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "AppManagerVM: load apps failed: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun setSortMode(mode: AppSortMode) {
        _uiState.update { it.copy(sortMode = mode) }
        applyFilters()
    }

    fun toggleSystemApps() {
        _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
        applyFilters()
    }

    fun selectApp(app: InstalledAppInfo?) {
        _uiState.update { it.copy(selectedApp = app, appFilesInfo = null, isLoadingFiles = false) }
    }

    fun loadAppFiles(packageName: String) {
        _uiState.update { it.copy(isLoadingFiles = true) }
        viewModelScope.launch {
            try {
                val info = getAppFilesUseCase(packageName)
                _uiState.update { it.copy(appFilesInfo = info, isLoadingFiles = false) }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "AppManagerVM: loadAppFiles failed: ${e.message}")
                _uiState.update { it.copy(isLoadingFiles = false) }
            }
        }
    }

    fun extractApk(app: InstalledAppInfo) {
        EcosystemLogger.d(HaronConstants.TAG, "AppManagerVM: extract APK ${app.packageName}")
        viewModelScope.launch {
            extractApkUseCase(app)
                .onSuccess { path ->
                    val fileName = path.substringAfterLast('/')
                    EcosystemLogger.d(HaronConstants.TAG, "AppManagerVM: APK extracted to $fileName")
                    _toastMessage.tryEmit(appContext.getString(R.string.apk_extracted_format, fileName))
                }
                .onFailure { e ->
                    EcosystemLogger.e(HaronConstants.TAG, "AppManagerVM: APK extract failed: ${e.message}")
                    _toastMessage.tryEmit(appContext.getString(R.string.extraction_error_format, e.message ?: ""))
                }
        }
        _uiState.update { it.copy(selectedApp = null) }
    }

    fun markUninstalling(app: InstalledAppInfo) {
        EcosystemLogger.d(HaronConstants.TAG, "AppManagerVM: uninstall requested for ${app.packageName}")
        _uiState.update { it.copy(selectedApp = null, uninstallingPackage = app.packageName) }
    }

    fun onUninstallResult(resultOk: Boolean) {
        val pkg = _uiState.value.uninstallingPackage ?: return
        if (resultOk) {
            _uiState.update { it.copy(removingPackage = pkg, uninstallingPackage = null) }
        } else {
            // User cancelled or result unknown — double-check via PackageManager
            @Suppress("DEPRECATION")
            val gone = try { appContext.packageManager.getPackageInfo(pkg, 0); false }
                       catch (_: Exception) { true }
            if (gone) {
                _uiState.update { it.copy(removingPackage = pkg, uninstallingPackage = null) }
            } else {
                _uiState.update { it.copy(uninstallingPackage = null) }
            }
        }
    }

    fun onRemovalAnimationDone(packageName: String) {
        allApps = allApps.filter { it.packageName != packageName }
        _uiState.update { it.copy(removingPackage = null) }
        applyFilters()
    }

    fun openAppSettings(app: InstalledAppInfo) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "AppManagerVM: open settings failed for ${app.packageName}: ${e.message}")
            _toastMessage.tryEmit(appContext.getString(R.string.settings_open_failed))
        }
        _uiState.update { it.copy(selectedApp = null) }
    }

    private fun applyFilters() {
        val state = _uiState.value
        var filtered = allApps

        if (!state.showSystemApps) {
            filtered = filtered.filter { !it.isSystemApp }
        }

        if (state.searchQuery.isNotBlank()) {
            val q = state.searchQuery.lowercase()
            filtered = filtered.filter {
                it.appName.lowercase().contains(q) ||
                        it.packageName.lowercase().contains(q)
            }
        }

        filtered = when (state.sortMode) {
            AppSortMode.NAME -> filtered.sortedBy { it.appName.lowercase() }
            AppSortMode.SIZE -> filtered.sortedByDescending { it.apkSize }
            AppSortMode.INSTALL_DATE -> filtered.sortedByDescending { it.lastUpdateDate }
        }

        _uiState.update { it.copy(apps = filtered) }
    }
}
