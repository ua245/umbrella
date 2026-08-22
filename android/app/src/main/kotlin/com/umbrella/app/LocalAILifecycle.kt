package com.umbrella.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*

/**
 * Listens for screen ON/OFF broadcasts and manages the 5-minute evaluation loop.
 * When the screen turns on, starts polling screen text against the Cloud Relay AI.
 * When the screen turns off, cancels all evaluation to conserve battery.
 */
class LocalAILifecycle : BroadcastReceiver() {

    companion object {
        private const val TAG = "UmbrellaLifecycle"
        private const val EVAL_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private var evaluationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.i(TAG, "Screen ON — starting evaluation loop.")
                startEvaluationLoop(context)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.i(TAG, "Screen OFF — pausing evaluation loop to conserve battery.")
                evaluationJob?.cancel()
                evaluationJob = null
            }
        }
    }

    private fun startEvaluationLoop(context: Context) {
        // Prevent duplicate loops
        evaluationJob?.cancel()

        evaluationJob = scope.launch {
            while (isActive) {
                delay(EVAL_INTERVAL_MS)

                val screenText = UmbrellaAccessibilityService.latestScreenText
                val activeApp = UmbrellaAccessibilityService.activePackageName

                if (screenText.isBlank()) {
                    Log.d(TAG, "Screen text empty — skipping evaluation.")
                    continue
                }

                Log.d(TAG, "Evaluating screen: pkg=$activeApp text=${screenText.take(80)}...")

                try {
                    val familyId = getFamilyId(context) ?: run {
                        Log.w(TAG, "No family_id found — device not yet paired.")
                        continue
                    }

                    val response = CloudRelayClient.api.evaluate(
                        EvaluateRequest(
                            family_id = familyId,
                            screen_text = screenText,
                            active_package = activeApp
                        )
                    )

                    Log.i(TAG, "Verdict: ${response.verdict} — ${response.reason}")

                    if (response.verdict == "BLOCK") {
                        // Draw un-closeable overlay via AccessibilityService
                        UmbrellaAccessibilityService.instance?.showBlockOverlay(
                            reason = response.reason ?: "Content blocked by parental rules."
                        )
                    }

                } catch (e: CancellationException) {
                    throw e // Let coroutine cancel cleanly
                } catch (e: Exception) {
                    Log.e(TAG, "Evaluation failed: ${e.message}")
                }
            }
        }
    }

    private fun getFamilyId(context: Context): String? {
        val prefs = context.getSharedPreferences("umbrella_prefs", Context.MODE_PRIVATE)
        return prefs.getString("family_id", null)
    }
}
