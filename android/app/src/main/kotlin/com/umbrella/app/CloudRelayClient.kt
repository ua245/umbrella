package com.umbrella.app

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// ─── Request / Response Models ──────────────────────────────────────────────

data class EvaluateRequest(
    val family_id: String,
    val screen_text: String,
    val active_package: String
)

data class EvaluateResponse(
    val verdict: String,       // "BLOCK" or "ALLOW"
    val reason: String?        // Human-readable reason from Gemma 4
)

data class RulesResponse(
    val rules: Map<String, Any>?,  // Structured JSON rule dict from Firestore
    val blocked_domains: List<String>?
)

// ─── Retrofit Interface ──────────────────────────────────────────────────────

interface UmbrellaApi {
    @POST("/api/evaluate")
    suspend fun evaluate(@Body request: EvaluateRequest): EvaluateResponse

    @GET("/api/rules/{family_id}")
    suspend fun fetchRules(@Path("family_id") familyId: String): RulesResponse
}

// ─── Singleton Client ────────────────────────────────────────────────────────

object CloudRelayClient {

    // Replace with your deployed Cloud Run URL
    private const val BASE_URL = "https://umbrella-relay-XXXXXXXX-uc.a.run.app/"

    val api: UmbrellaApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UmbrellaApi::class.java)
    }
}
