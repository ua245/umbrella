package com.umbrella.parent.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.umbrella.parent.data.models.ChatMessage
import com.umbrella.parent.data.models.MessageSender
import com.umbrella.parent.data.models.RuleDict
import com.umbrella.parent.data.repository.DashboardRepository
import com.umbrella.parent.data.repository.RuleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val isApplying: Boolean = false,
    val currentPrompt: String = "",
    val errorMessage: String? = null,
    val appliedSuccessMessage: String? = null
)

class ChatRuleViewModel(
    private val ruleRepository: RuleRepository = RuleRepository(),
    private val dashboardRepository: DashboardRepository = DashboardRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Initial welcome message from Gemma 4 AI
        val welcomeMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = "👋 Hello! I am your Umbrella AI Assistant powered by Gemma 4.\n\n" +
                    "Tell me what protection rules you want to set for your child's device (e.g., 'Block gaming on school days', 'Limit TikTok to 30 mins', 'Set bedtime lock at 9 PM').",
            sender = MessageSender.AI,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { it.copy(messages = listOf(welcomeMessage)) }
    }

    fun onPromptChange(newPrompt: String) {
        _uiState.update { it.copy(currentPrompt = newPrompt) }
    }

    /**
     * Sends the natural language prompt to Cloud Run endpoint /api/generate-rules
     */
    fun sendPrompt(promptText: String = _uiState.value.currentPrompt) {
        val trimmed = promptText.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            sender = MessageSender.USER,
            timestamp = System.currentTimeMillis()
        )

        val loadingMessageId = UUID.randomUUID().toString()
        val loadingAiMessage = ChatMessage(
            id = loadingMessageId,
            text = "Gemma 4 is analyzing your command and generating structured rules...",
            sender = MessageSender.AI,
            isLoading = true,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + loadingAiMessage,
                currentPrompt = "",
                isGenerating = true,
                errorMessage = null
            )
        }

        val familyId = dashboardRepository.getFamilyId()

        viewModelScope.launch {
            val result = ruleRepository.generateRulesFromPrompt(
                familyId = familyId,
                prompt = trimmed
            )

            result.fold(
                onSuccess = { response ->
                    val completedAiMessage = ChatMessage(
                        id = loadingMessageId,
                        text = "I've structured your rules into an Umbrella RuleDict. Review the parameters below and confirm to enforce them.",
                        sender = MessageSender.AI,
                        ruleDict = response.ruleDict,
                        rawJson = response.rawJson,
                        isLoading = false,
                        timestamp = System.currentTimeMillis()
                    )

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == loadingMessageId) completedAiMessage else msg
                            },
                            isGenerating = false
                        )
                    }
                },
                onFailure = { error ->
                    val errorAiMessage = ChatMessage(
                        id = loadingMessageId,
                        text = "Sorry, I couldn't generate rules for that: ${error.localizedMessage ?: "Unknown error"}. Please try rephrasing.",
                        sender = MessageSender.AI,
                        isLoading = false,
                        isError = true,
                        timestamp = System.currentTimeMillis()
                    )

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == loadingMessageId) errorAiMessage else msg
                            },
                            isGenerating = false,
                            errorMessage = error.localizedMessage
                        )
                    }
                }
            )
        }
    }

    /**
     * Applies and synchronizes the RuleDict to the child's device.
     */
    fun applyRuleDict(ruleDict: RuleDict, messageId: String) {
        _uiState.update { it.copy(isApplying = true, errorMessage = null) }
        val familyId = dashboardRepository.getFamilyId()

        viewModelScope.launch {
            val result = ruleRepository.deployAndSyncRules(
                familyId = familyId,
                ruleDict = ruleDict
            )

            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isApplying = false,
                            appliedSuccessMessage = "Rules successfully deployed & synced with kid's device!",
                            messages = state.messages.map { msg ->
                                if (msg.ruleDict == ruleDict || msg.id == messageId) {
                                    msg.copy(isEnforced = true)
                                } else msg
                            }
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isApplying = false,
                            errorMessage = "Failed to sync rules: ${error.localizedMessage}"
                        )
                    }
                }
            )
        }
    }

    fun dismissSuccessMessage() {
        _uiState.update { it.copy(appliedSuccessMessage = null) }
    }

    fun dismissErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

