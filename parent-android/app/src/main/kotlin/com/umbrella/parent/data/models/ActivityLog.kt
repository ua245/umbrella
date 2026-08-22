package com.umbrella.parent.data.models

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ViolationSeverity {
    INFO,
    WARNING,
    BLOCKED
}

/**
 * Activity log document received from Firestore path: families/{family_id}/logs
 */
data class ActivityLog(
    val id: String = "",
    val appName: String = "Unknown Application",
    val packageName: String? = null,
    val severity: ViolationSeverity = ViolationSeverity.INFO,
    val category: String = "GENERAL",
    val screenSnippet: String? = null,
    val aiReasoning: String? = null,
    val actionTaken: String = "MONITORED",
    val rawEncryptedData: String? = null,
    val timestamp: Timestamp? = null
) {
    fun getFormattedTime(): String {
        if (timestamp == null) return "Just now"
        val date: Date = timestamp.toDate()
        val diff = System.currentTimeMillis() - date.time
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
        }
    }
}

