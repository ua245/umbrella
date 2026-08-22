package com.umbrella.parent.data.models

import java.util.UUID

enum class MessageSender {
    USER,
    AI,
    SYSTEM
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: MessageSender,
    val timestamp: Long = System.currentTimeMillis(),
    val ruleDict: RuleDict? = null,
    val rawJson: String? = null,
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val isEnforced: Boolean = false
)

