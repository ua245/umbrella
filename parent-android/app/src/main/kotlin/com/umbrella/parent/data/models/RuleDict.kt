package com.umbrella.parent.data.models

import com.google.gson.annotations.SerializedName

/**
 * Structured Rule dictionary produced by Gemma 4 Cloud Inference
 * and enforced across the kid's device (VPN + Accessibility Service).
 */
data class RuleDict(
    @SerializedName("blocked_categories")
    val blockedCategories: List<String> = emptyList(),

    @SerializedName("blocked_keywords")
    val blockedKeywords: List<String> = emptyList(),

    @SerializedName("app_rules")
    val appRules: List<AppRule> = emptyList(),

    @SerializedName("bedtime_start")
    val bedtimeStart: String? = null,

    @SerializedName("bedtime_end")
    val bedtimeEnd: String? = null,

    @SerializedName("school_hours_start")
    val schoolHoursStart: String? = null,

    @SerializedName("school_hours_end")
    val schoolHoursEnd: String? = null,

    @SerializedName("safe_search_forced")
    val safeSearchForced: Boolean = true,

    @SerializedName("raw_intent")
    val rawIntent: String = "",

    @SerializedName("explanation")
    val explanation: String = ""
)

data class AppRule(
    @SerializedName("package_name")
    val packageName: String,

    @SerializedName("app_name")
    val appName: String,

    @SerializedName("is_blocked")
    val isBlocked: Boolean = false,

    @SerializedName("daily_limit_minutes")
    val dailyLimitMinutes: Int? = null
)

