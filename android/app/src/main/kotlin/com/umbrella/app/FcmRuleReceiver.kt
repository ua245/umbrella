package com.umbrella.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

/**
 * Receives silent FCM push notifications from the Cloud Relay.
 * When the parent updates rules, the Cloud Relay sends a "FETCH_RULES" data message
 * which wakes up the kid's app to refresh its rule cache from Firestore.
 */
class FcmRuleReceiver : FirebaseMessagingService() {

    companion object {
        private const val TAG = "UmbrellaFCM"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val action = remoteMessage.data["action"] ?: return
        Log.i(TAG, "FCM received: action=$action")

        when (action) {
            "FETCH_RULES" -> handleFetchRules()
            "SHOW_BLOCK"  -> {
                val reason = remoteMessage.data["reason"] ?: "Blocked by parent."
                UmbrellaAccessibilityService.instance?.showBlockOverlay(reason)
            }
        }
    }

    private fun handleFetchRules() {
        scope.launch {
            try {
                val prefs = getSharedPreferences("umbrella_prefs", Context.MODE_PRIVATE)
                val familyId = prefs.getString("family_id", null) ?: run {
                    Log.w(TAG, "No family_id — device not paired yet.")
                    return@launch
                }

                Log.d(TAG, "Fetching fresh rules for family=$familyId")
                val rulesResponse = CloudRelayClient.api.fetchRules(familyId)

                // Persist new blocked domains to local blocklist in DnsVpnService
                rulesResponse.blocked_domains?.let { domains ->
                    DnsVpnService.BLOCKED_DOMAINS.clear()
                    DnsVpnService.BLOCKED_DOMAINS.addAll(domains)
                    Log.i(TAG, "Updated DNS blocklist: ${domains.size} domains.")
                }

                // Persist rule JSON for LocalAILifecycle evaluation prompts
                rulesResponse.rules?.let { rules ->
                    prefs.edit()
                        .putString("active_rules_json", rules.toString())
                        .apply()
                    Log.i(TAG, "Active rules updated from parent.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch rules: ${e.message}")
            }
        }
    }

    override fun onNewToken(token: String) {
        // In a full implementation, POST this token to the Cloud Relay
        // so the parent app can trigger FCM messages to this device.
        Log.i(TAG, "New FCM token: $token")
        val prefs = getSharedPreferences("umbrella_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
