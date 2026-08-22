package com.umbrella.app

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView

class UmbrellaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UmbrellaA11y"

        // Shared with LocalAILifecycle for the evaluation loop
        @Volatile var latestScreenText: String = ""
        @Volatile var activePackageName: String = ""

        // Called from LocalAILifecycle when a BLOCK verdict is received
        var instance: UmbrellaAccessibilityService? = null
    }

    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Umbrella Accessibility Service connected.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: ""
        activePackageName = pkg

        val isContentChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        val isStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (isContentChange || isStateChange) {
            val rootNode = rootInActiveWindow ?: return
            val extractedText = buildString {
                extractTextFromNode(rootNode, this)
            }
            latestScreenText = extractedText

            // --- Anti-Tamper: Detect attempts to disable VPN or Accessibility in Settings ---
            if (pkg == "com.android.settings") {
                val textLower = extractedText.lowercase()
                val isTamperAttempt = textLower.contains("umbrella") ||
                    textLower.contains("vpn") && textLower.contains("disconnect") ||
                    textLower.contains("accessibility") && textLower.contains("turn off")

                if (isTamperAttempt) {
                    Log.w(TAG, "Tamper attempt detected in Settings. Forcing back.")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    showBlockOverlay(
                        reason = "Settings access restricted. A Parent PIN is required to change Umbrella settings."
                    )
                }
            }
        }
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(' ') }
        node.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                extractTextFromNode(child, sb)
                child.recycle()
            }
        }
    }

    /**
     * Draws an un-closeable full-screen overlay using WindowManager.
     * Called by LocalAILifecycle when the Cloud AI returns a BLOCK verdict.
     */
    fun showBlockOverlay(reason: String = "Content blocked by Umbrella parental controls.") {
        mainHandler.post {
            if (overlayView != null) return@post // Already showing

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            val view = View(this).apply {
                setBackgroundColor(Color.argb(230, 20, 20, 40)) // dark navy overlay
            }

            // Compose the overlay layout programmatically
            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.argb(240, 15, 15, 35))
                setPadding(64, 64, 64, 64)
            }

            val iconText = TextView(this).apply {
                text = "☂️"
                textSize = 64f
                gravity = Gravity.CENTER
            }

            val titleText = TextView(this).apply {
                text = "Content Blocked"
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val reasonText = TextView(this).apply {
                text = reason
                textSize = 16f
                setTextColor(Color.argb(200, 200, 200, 220))
                gravity = Gravity.CENTER
                setPadding(0, 32, 0, 48)
            }

            val dismissBtn = Button(this).apply {
                text = "Enter Parent PIN to Continue"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(255, 63, 81, 181)) // indigo
                // In a real app, tapping this would show a PIN dialog
                // and call dismissBlockOverlay() on success
                setOnClickListener {
                    Log.d(TAG, "Parent PIN entry requested.")
                    // TODO: launch PinEntryActivity
                }
            }

            container.addView(iconText)
            container.addView(titleText)
            container.addView(reasonText)
            container.addView(dismissBtn)

            try {
                wm.addView(container, params)
                overlayView = container
                Log.i(TAG, "Block overlay displayed. Reason: $reason")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay: ${e.message}")
            }
        }
    }

    fun dismissBlockOverlay() {
        mainHandler.post {
            overlayView?.let { v ->
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                try {
                    wm.removeView(v)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove overlay: ${e.message}")
                }
                overlayView = null
            }
        }
    }

    override fun onInterrupt() {
        Log.e(TAG, "Service interrupted.")
        dismissBlockOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        dismissBlockOverlay()
    }
}
