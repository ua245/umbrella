package com.umbrella.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UmbrellaAccessibilityService : AccessibilityService() {
    
    companion object {
        var latestScreenText: String = ""
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val extractedText = StringBuilder()
                extractTextFromNode(rootNode, extractedText)
                latestScreenText = extractedText.toString()
                
                // Anti-tamper logic
                if (packageName == "com.android.settings" && (latestScreenText.contains("Umbrella", ignoreCase = true) || latestScreenText.contains("VPN", ignoreCase = true))) {
                    Log.w("UmbrellaA11y", "Tamper attempt detected. Forcing back.")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    // Show a quick blocking overlay (omitted exact view inflation for brevity, but WindowManager logic would go here)
                }
            }
        }
    }
    
    private fun extractTextFromNode(node: AccessibilityNodeInfo, sb: StringBuilder) {
        if (node.text != null) {
            sb.append(node.text).append(" ")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextFromNode(child, sb)
            }
        }
    }

    override fun onInterrupt() {
        Log.e("UmbrellaA11y", "Service interrupted")
    }
}
