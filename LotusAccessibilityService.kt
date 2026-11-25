
package com.personalvideoai

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.provider.Settings
import android.util.Log

class LotusAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // தற்போது திறந்திருக்கும் திரையின் தகவல்களைப் பெறலாம்.
        val root = rootInActiveWindow ?: return
        Log.d("LotusAccessibility", "Screen: ${root.packageName}")
    }

    override fun onInterrupt() {
        Log.d("LotusAccessibility", "Service interrupted")
    }

    fun getCurrentScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        return readNodeText(root)
    }

    private fun readNodeText(node: AccessibilityNodeInfo): String {
        val result = StringBuilder()
        node.text?.let { result.append(it).append("\n") }
        node.contentDescription?.let { result.append(it).append("\n") }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                result.append(readNodeText(it))
            }
        }
        return result.toString()
    }
}

