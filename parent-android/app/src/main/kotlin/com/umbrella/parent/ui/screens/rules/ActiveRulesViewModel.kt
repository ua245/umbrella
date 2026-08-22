package com.umbrella.parent.ui.screens.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umbrella.parent.data.models.RuleDict
import com.umbrella.parent.data.repository.DashboardRepository
import com.umbrella.parent.data.repository.RuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActiveRulesUiState(
    val familyId: String = "",
    val activeRuleDict: RuleDict? = null,
    val isLoading: Boolean = false,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class ActiveRulesViewModel(
    private val ruleRepository: RuleRepository = RuleRepository(),
    private val dashboardRepository: DashboardRepository = DashboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ActiveRulesUiState(familyId = dashboardRepository.getFamilyId())
    )
    val uiState: StateFlow<ActiveRulesUiState> = _uiState.asStateFlow()

    init {
        loadActiveRules()
    }

    fun loadActiveRules() {
        val familyId = _uiState.value.familyId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = ruleRepository.fetchActiveRules(familyId)
            result.fold(
                onSuccess = { rules ->
                    _uiState.update {
                        it.copy(
                            activeRuleDict = rules,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load active rules: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun toggleSafeSearch(enabled: Boolean) {
        val current = _uiState.value.activeRuleDict ?: return
        val updated = current.copy(safeSearchForced = enabled)
        updateRules(updated)
    }

    private fun updateRules(updatedRules: RuleDict) {
        val familyId = _uiState.value.familyId
        _uiState.update { it.copy(isUpdating = true) }

        viewModelScope.launch {
            val result = ruleRepository.deployAndSyncRules(familyId, updatedRules)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            activeRuleDict = updatedRules,
                            isUpdating = false,
                            statusMessage = "Rule updated and synced successfully!"
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isUpdating = false,
                            errorMessage = "Failed to update rules: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun dismissStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}

