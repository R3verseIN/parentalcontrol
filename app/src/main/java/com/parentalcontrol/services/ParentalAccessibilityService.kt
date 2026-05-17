package com.parentalcontrol.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.parentalcontrol.ui.PinActivity

class ParentalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ParentalAccessService"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Monitor window state changes (e.g. settings apps being navigated by the child)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d(TAG, "Foreground App Shift: $packageName")
            
            // Secure Settings Safeguard Block
            if (packageName == "com.android.settings") {
                // Only block if Device Admin is ALREADY active!
                val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val cn = android.content.ComponentName(this, com.parentalcontrol.security.ParentalDeviceAdminReceiver::class.java)
                if (dpm.isAdminActive(cn)) {
                    val rootNode = rootInActiveWindow ?: event.source
                    if (rootNode != null) {
                        val textList = mutableListOf<String>()
                        findTextNodes(rootNode, textList)
                        rootNode.recycle()
                        
                        val hasParentalControl = textList.any { it.contains("Parental Control", ignoreCase = true) }
                        val hasDeactivationAction = textList.any { 
                            it.contains("Uninstall", ignoreCase = true) || 
                            it.contains("Force stop", ignoreCase = true) || 
                            it.contains("Disable", ignoreCase = true) ||
                            it.contains("Device admin", ignoreCase = true) ||
                            it.contains("Deactivate", ignoreCase = true)
                        }

                        if (hasParentalControl && hasDeactivationAction) {
                            Log.w(TAG, "Blocked attempt to bypass or uninstall the application. Launching lock screen.")
                            launchLockGatekeeper()
                        }
                    }
                }
            }
        }
    }

    private fun findTextNodes(node: AccessibilityNodeInfo, list: MutableList<String>) {
        if (node.text != null) {
            list.add(node.text.toString())
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                findTextNodes(child, list)
            }
        }
    }

    private fun launchLockGatekeeper() {
        val intent = Intent(this, PinActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_UNLOCK)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected Successfully!")
    }
}
