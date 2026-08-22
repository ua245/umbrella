package com.umbrella.parent.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.umbrella.parent.data.models.ActivityLog
import com.umbrella.parent.data.models.ViolationSeverity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class DashboardRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    // In-memory or persisted family ID
    private var cachedFamilyId: String = "umbrella-fam-7729"

    fun getFamilyId(): String = cachedFamilyId

    fun setFamilyId(id: String) {
        cachedFamilyId = id
    }

    fun generateNewFamilyId(): String {
        cachedFamilyId = "umbrella-" + UUID.randomUUID().toString().take(8)
        return cachedFamilyId
    }

    /**
     * Subscribes to real-time activity logs from Firestore:
     * families/{family_id}/logs
     * Ordered by timestamp descending (newest incidents first).
     */
    fun listenToActivityLogs(familyId: String): Flow<List<ActivityLog>> = callbackFlow {
        val collectionRef = firestore.collection("families")
            .document(familyId)
            .collection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)

        val registration = collectionRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                // If index is missing or offline, emit empty or propagate
                close(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val logs = snapshot.documents.mapNotNull { doc ->
                    try {
                        val severityStr = doc.getString("severity") ?: "INFO"
                        val severity = when (severityStr.uppercase()) {
                            "BLOCKED", "CRITICAL" -> ViolationSeverity.BLOCKED
                            "WARNING", "WARN" -> ViolationSeverity.WARNING
                            else -> ViolationSeverity.INFO
                        }

                        ActivityLog(
                            id = doc.id,
                            appName = doc.getString("app_name") ?: "Unknown App",
                            packageName = doc.getString("package_name"),
                            severity = severity,
                            category = doc.getString("category") ?: "GENERAL",
                            screenSnippet = doc.getString("screen_snippet"),
                            aiReasoning = doc.getString("ai_reasoning"),
                            actionTaken = doc.getString("action_taken") ?: "MONITORED",
                            rawEncryptedData = doc.getString("data"),
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                trySend(logs)
            }
        }

        awaitClose {
            registration.remove()
        }
    }
}

