package com.vamp.haron.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.backup.BackupManager
import com.vamp.haron.domain.model.SecureFileEntry
import com.vamp.haron.domain.model.backup.BackupInfo
import com.vamp.haron.domain.model.backup.BackupResult
import com.vamp.haron.domain.model.backup.BackupSection
import com.vamp.haron.domain.usecase.backup.CreateBackupUseCase
import com.vamp.haron.domain.usecase.backup.DeleteBackupUseCase
import com.vamp.haron.domain.usecase.backup.ListBackupsUseCase
import com.vamp.haron.domain.usecase.backup.RestoreBackupUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val backups: List<BackupInfo> = emptyList(),
    val isCreating: Boolean = false,
    val isRestoring: Boolean = false,
    val selectedSections: Set<BackupSection> = BackupSection.entries.toSet() - BackupSection.SECURE_FOLDER,
    val restoreResult: BackupResult? = null,
    val error: String? = null,
    val successMessage: String? = null,
    /** Файлы защищённой папки (для пофайлового выбора) */
    val secureFiles: List<SecureFileEntry> = emptyList(),
    /** ID выбранных файлов защищённой папки */
    val selectedSecureFileIds: Set<String> = emptySet(),
    /** Прогресс бэкапа secure folder: current/total */
    val secureProgress: Pair<Int, Int>? = null,
    /** Прогресс восстановления: (percent, stepName) */
    val restoreProgress: Pair<Int, String>? = null,
    /** Путь к последнему восстановленному бэкапу (неактивен для повторного восстановления) */
    val restoredFilePath: String? = null,
    /** Общий прогресс создания бэкапа 0..100 */
    val createProgressPercent: Int = 0
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val createBackupUseCase: CreateBackupUseCase,
    private val restoreBackupUseCase: RestoreBackupUseCase,
    private val listBackupsUseCase: ListBackupsUseCase,
    private val deleteBackupUseCase: DeleteBackupUseCase,
    private val backupManager: BackupManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    companion object {
        private const val TAG = "${HaronConstants.TAG}/Backup"
        private const val PREFS_NAME = "haron_backup_state"
        private const val KEY_RESTORED_BACKUP = "last_restored_backup_path"
    }

    private val backupPrefs = appContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()

    init {
        val savedPath = backupPrefs.getString(KEY_RESTORED_BACKUP, null)
        if (savedPath != null) {
            _state.update { it.copy(restoredFilePath = savedPath) }
        }
        refreshBackups()
        loadSecureFiles()
    }

    fun refreshBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = listBackupsUseCase()
            _state.update { it.copy(backups = list) }
        }
    }

    /** Загрузка списка файлов защищённой папки для UI. */
    private fun loadSecureFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = backupManager.getSecureFolderEntries()
            _state.update { s ->
                s.copy(
                    secureFiles = entries,
                    selectedSecureFileIds = entries.map { it.id }.toSet() // все выбраны по умолчанию
                )
            }
        }
    }

    fun toggleSection(section: BackupSection) {
        _state.update { s ->
            val new = if (section in s.selectedSections) {
                s.selectedSections - section
            } else {
                s.selectedSections + section
            }
            s.copy(selectedSections = new)
        }
    }

    /** Тоггл одного файла защищённой папки. */
    fun toggleSecureFile(id: String) {
        _state.update { s ->
            val new = if (id in s.selectedSecureFileIds) {
                s.selectedSecureFileIds - id
            } else {
                s.selectedSecureFileIds + id
            }
            s.copy(selectedSecureFileIds = new)
        }
    }

    /** Выбрать/снять все файлы защищённой папки. */
    fun toggleAllSecureFiles() {
        _state.update { s ->
            if (s.selectedSecureFileIds.size == s.secureFiles.size) {
                s.copy(selectedSecureFileIds = emptySet())
            } else {
                s.copy(selectedSecureFileIds = s.secureFiles.map { it.id }.toSet())
            }
        }
    }

    fun createBackup(password: String?) {
        val state = _state.value
        val sections = state.selectedSections
        if (sections.isEmpty()) {
            _state.update { it.copy(error = "Выберите хотя бы одну секцию") }
            return
        }

        val secureIds = if (BackupSection.SECURE_FOLDER in sections) {
            state.selectedSecureFileIds
        } else null

        _state.update { it.copy(isCreating = true, error = null, successMessage = null, secureProgress = null, createProgressPercent = 0) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = createBackupUseCase(
                sections = sections,
                password = password?.ifBlank { null },
                secureFileIds = secureIds,
                onProgress = { current, total ->
                    val percent = if (total > 0) (current * 100 / total) else 0
                    _state.update { it.copy(createProgressPercent = percent) }
                }
            )
            result.fold(
                onSuccess = { file ->
                    EcosystemLogger.i(TAG, "Backup created: ${file.name}")
                    _state.update {
                        it.copy(
                            isCreating = false,
                            secureProgress = null,
                            successMessage = "Бэкап создан: ${file.name}"
                        )
                    }
                    refreshBackups()
                },
                onFailure = { e ->
                    EcosystemLogger.e(TAG, "Backup failed: ${e.message}")
                    _state.update {
                        it.copy(isCreating = false, secureProgress = null, error = "Ошибка: ${e.message}")
                    }
                }
            )
        }
    }

    fun restoreBackup(file: File, password: String?) {
        _state.update { it.copy(isRestoring = true, error = null, restoreResult = null, restoreProgress = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                restoreBackupUseCase(
                    backupFile = file,
                    password = password?.ifBlank { null },
                    onProgress = { current, total, stepName ->
                        // total=100 means raw percent from BackupManager (decrypt/read phases)
                        // otherwise scale to 10-100 range (0-10 reserved for decrypt/read)
                        val percent = if (total == 100) current
                            else if (total > 0) 10 + (current * 90 / total) else 0
                        _state.update { it.copy(restoreProgress = percent.coerceIn(0, 100) to stepName) }
                    }
                )
            }
            EcosystemLogger.i(TAG, "Restore result: $result")
            if (result !is BackupResult.Error) {
                backupPrefs.edit().putString(KEY_RESTORED_BACKUP, file.absolutePath).apply()
            }
            _state.update {
                it.copy(
                    isRestoring = false,
                    restoreResult = result,
                    restoreProgress = null,
                    restoredFilePath = if (result !is BackupResult.Error) file.absolutePath else it.restoredFilePath
                )
            }
        }
    }

    fun deleteBackup(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteBackupUseCase(file)
            refreshBackups()
        }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, successMessage = null, restoreResult = null) }
    }
}
