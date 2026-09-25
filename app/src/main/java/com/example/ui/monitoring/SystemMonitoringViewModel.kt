package com.example.ui.monitoring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.SystemMonitoringAlert
import com.example.data.repository.SupabaseSystemMonitoringRepository
import com.example.data.repository.SystemMonitoringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SystemMonitoringUiState(
    val isLoading: Boolean = false,
    val alerts: List<SystemMonitoringAlert> = emptyList(),
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = !isLoading && errorMessage == null
    val isError: Boolean get() = !isLoading && errorMessage != null
    val isEmpty: Boolean get() = isSuccess && alerts.isEmpty()
}

class SystemMonitoringViewModel(
    private val repository: SystemMonitoringRepository = SupabaseSystemMonitoringRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemMonitoringUiState(isLoading = true))
    val uiState: StateFlow<SystemMonitoringUiState> = _uiState.asStateFlow()

    init {
        loadOpenAlerts()
    }

    fun loadOpenAlerts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val result = repository.getOpenAlerts()
                result.fold(
                    onSuccess = { alertsList ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                alerts = alertsList,
                                errorMessage = null
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.localizedMessage ?: "فشل تحميل تنبيهات المراقبة"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "حدث خطأ غير متوقع أثناء تحميل التنبيهات"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
