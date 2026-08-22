package com.umbrella.parent.data.models

import com.google.gson.annotations.SerializedName

data class GenerateRulesRequest(
    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("prompt")
    val prompt: String,

    @SerializedName("existing_rules")
    val existingRules: Map<String, Any>? = null
)

data class GenerateRulesResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("rule_dict")
    val ruleDict: RuleDict,

    @SerializedName("explanation")
    val explanation: String,

    @SerializedName("raw_json")
    val rawJson: String
)

data class SyncRulesRequest(
    @SerializedName("family_id")
    val familyId: String,

    @SerializedName("encrypted_rules")
    val encryptedRules: String,

    @SerializedName("kid_fcm_token")
    val kidFcmToken: String? = null
)

data class SyncRulesResponse(
    @SerializedName("status")
    val status: String,

    @SerializedName("message")
    val message: String
)

