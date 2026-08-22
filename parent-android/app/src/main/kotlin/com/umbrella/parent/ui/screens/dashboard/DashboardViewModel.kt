package com.umbrella.parent.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umbrella.parent.data.models.ActivityLog
import com.umbrella.parent.data.models.ViolationSeverity
import com.umbrella.parent.data.repository.DashboardRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class LogFilter {
    ALL,
    BLOCKED,
    WARNINGS,
    SAFE
}

data class DashboardUiState(
    val familyId: String = "",
    val logs: List<ActivityLog> = emptyList(),
    val filteredLogs: List<ActivityLog> = emptyList(),
    val activeFilter: LogFilter = LogFilter.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val showPairingDialog: Boolean = false,
    val totalViolationsToday: Int = 0,
    val totalWarningsToday: Int = 0,
    val totalScannedToday: Int = 0
)

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository = DashboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(familyId = dashboardRepository.getFamilyId())
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        startListeningToLogs()
    }

    private fun startListeningToLogs() {
        val familyId = _uiState.value.familyId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            dashboardRepository.listenToActivityLogs(familyId)
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to stream logs: ${error.localizedMessage}"
                        )
                    }
                }
                .collect { logList ->
                    val blocks = logList.count { it.severity == ViolationSeverity.BLOCKED }
                    val warnings = logList.count { it.severity == ViolationSeverity.WARNING }
                    val total = logList.size

                    _uiState.update { state ->
                        state.copy(
                            logs = logList,
                            isLoading = false,
                            totalViolationsToday = blocks,
                            totalWarningsToday = warnings,
                            totalScannedToday = total,
                            filteredLogs = applyFilter(logList, state.activeFilter, state.searchQuery)
                        )
                    }
                }
        }
    }

    fun setFilter(filter: LogFilter) {
        _uiState.update { state ->
            state.copy(
                activeFilter = filter,
                filteredLogs = applyFilter(state.logs, filter, state.searchQuery)
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredLogs = applyFilter(state.logs, state.activeFilter, query)
            )
        }
    }

    fun showPairingDialog() {
        _uiState.update { it.copy(showPairingDialog = true) }
    }

    fun dismissPairingDialog() {
        _uiState.update { it.copy(showPairingDialog = false) }
    }

    fun updateFamilyId(newId: String) {
        dashboardRepository.setFamilyId(newId)
        _uiState.update { it.copy(familyId = newId) }
        startListeningToLogs()
    }

    private fun applyFilter(
        logs: List<ActivityLog>,
        filter: LogFilter,
        query: String
    ): List<ActivityLog> {
        return logs.filter { log ->
            val matchesFilter = when (filter) {
                LogFilter.ALL -> true
                LogFilter.BLOCKED -> log.severity == ViolationSeverity.BLOCKED
                LogFilter.WARNINGS -> log.severity == ViolationSeverity.WARNING
                LogFilter.SAFE -> log.severity == ViolationSeverity.INFO
            }

            val matchesQuery = query.isBlank() ||
                    log.appName.contains(query, ignoreCase = true) ||
                    log.category.contains(query, ignoreCase = true) ||
                    (log.aiReasoning?.contains(query, ignoreCase = true) == true)

            matchesFilter && matchesQuery
        }
    }
}

