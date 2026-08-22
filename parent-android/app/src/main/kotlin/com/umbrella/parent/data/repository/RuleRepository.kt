package com.umbrella.parent.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.umbrella.parent.data.models.GenerateRulesRequest
import com.umbrella.parent.data.models.GenerateRulesResponse
import com.umbrella.parent.data.models.RuleDict
import com.umbrella.parent.data.models.SyncRulesRequest
import com.umbrella.parent.data.remote.ApiClient
import com.umbrella.parent.data.remote.UmbrellaApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class RuleRepository(
    private val apiService: UmbrellaApiService = ApiClient.apiService,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val gson: Gson = Gson()
) {
    private val _activeRules = MutableStateFlow<RuleDict?>(null)
    val activeRules: StateFlow<RuleDict?> = _activeRules.asStateFlow()

    /**
     * Sends the parent's natural language command to Cloud Run /api/generate-rules.
     * The backend queries Gemma 4 to parse the command into a structured RuleDict.
     */
    suspend fun generateRulesFromPrompt(
        familyId: String,
        prompt: String
    ): Result<GenerateRulesResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.generateRules(
                GenerateRulesRequest(
                    familyId = familyId,
                    prompt = prompt,
                    existingRules = null
                )
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Server returned error: ${response.code()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deploys and enforces the generated RuleDict.
     * Saves to Firestore families/{familyId}/rules/latest and notifies kid's device via FCM.
     */
    suspend fun deployAndSyncRules(
        familyId: String,
        ruleDict: RuleDict,
        kidFcmToken: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val jsonString = gson.toJson(ruleDict)

            // 1. Direct Firestore write for instant offline synchronization
            firestore.collection("families")
                .document(familyId)
                .collection("rules")
                .document("latest")
                .set(mapOf(
                    "data" to jsonString,
                    "raw_dict" to ruleDict,
                    "updated_at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
                .await()

            // 2. Call backend /sync/rules to trigger FCM silent push wake-up to kid's device
            try {
                apiService.syncRules(
                    SyncRulesRequest(
                        familyId = familyId,
                        encryptedRules = jsonString,
                        kidFcmToken = kidFcmToken
                    )
                )
            } catch (ignored: Exception) {
                // Non-fatal if backend wake-up fails; Firestore document listener is primary
            }

            _activeRules.value = ruleDict
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches current active rules from Firestore.
     */
    suspend fun fetchActiveRules(familyId: String): Result<RuleDict?> = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("families")
                .document(familyId)
                .collection("rules")
                .document("latest")
                .get()
                .await()

            if (doc.exists()) {
                val dataStr = doc.getString("data")
                if (!dataStr.isNullOrEmpty()) {
                    val ruleDict = gson.fromJson(dataStr, RuleDict::class.java)
                    _activeRules.value = ruleDict
                    return@withContext Result.success(ruleDict)
                }
            }
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

