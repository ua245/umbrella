package com.umbrella.parent.data.remote

import com.umbrella.parent.data.models.GenerateRulesRequest
import com.umbrella.parent.data.models.GenerateRulesResponse
import com.umbrella.parent.data.models.SyncRulesRequest
import com.umbrella.parent.data.models.SyncRulesResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface UmbrellaApiService {

    /**
     * Sends parent natural language input to Cloud Run backend,
     * where Gemma 4 translates the intent into a structured JSON RuleDict.
     */
    @POST("api/generate-rules")
    suspend fun generateRules(
        @Body request: GenerateRulesRequest
    ): Response<GenerateRulesResponse>

    /**
     * Deploys and pushes the active RuleDict to Firestore and notifies
     * the child's device via high-priority silent FCM push.
     */
    @POST("sync/rules")
    suspend fun syncRules(
        @Body request: SyncRulesRequest
    ): Response<SyncRulesResponse>
}

