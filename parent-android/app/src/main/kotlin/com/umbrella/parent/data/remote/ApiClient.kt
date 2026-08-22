package com.umbrella.parent.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // Default Cloud Run URL - can be overridden via updateBaseUrl(...)
    // When testing on Android Emulator: "http://10.0.2.2:8000/" or live Cloud Run endpoint
    private var currentBaseUrl = "https://umbrella-cloud-relay-dot-project.run.app/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private var retrofit: Retrofit = buildRetrofit(currentBaseUrl)

    private fun buildRetrofit(url: String): Retrofit {
        val sanitized = if (url.endsWith("/")) url else "$url/"
        return Retrofit.Builder()
            .baseUrl(sanitized)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun setBaseUrl(newUrl: String) {
        currentBaseUrl = newUrl
        retrofit = buildRetrofit(newUrl)
        apiService = retrofit.create(UmbrellaApiService::class.java)
    }

    fun getBaseUrl(): String = currentBaseUrl

    var apiService: UmbrellaApiService = retrofit.create(UmbrellaApiService::class.java)
        private set
}

