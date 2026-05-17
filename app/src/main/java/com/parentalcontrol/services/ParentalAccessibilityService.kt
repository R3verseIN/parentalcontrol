package com.parentalcontrol.services

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.parentalcontrol.security.BlocklistManager
import com.parentalcontrol.security.ParentalDeviceAdminReceiver
import com.parentalcontrol.ui.PinActivity

class ParentalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ParentalAccessService"

        // Timestamp to temporarily bypass settings blocking (15-second grace window for uninstallation)
        @JvmStatic
        var bypassSafeguardUntil: Long = 0

        // Global flag to track whether the active lock screen is an intercept overlay
        @JvmStatic
        var isCurrentlyIntercepting: Boolean = false

        // Tracks the package name currently awaiting PIN entry
        @JvmStatic
        var currentlyBlockingPackage: String? = null

        // Set of packages authorized to run during the current session
        @JvmStatic
        val unlockedPackages = mutableSetOf<String>()
    }

    private var screenOffReceiver: android.content.BroadcastReceiver? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Monitor window state changes (e.g. settings apps being navigated by the child)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            Log.d(TAG, "Foreground App Shift: $packageName")
            
            // Auto-relock: If the user navigated away from the unlocked app to a non-parental package, clear sessions
            if (packageName != null && packageName != currentlyBlockingPackage && !packageName.startsWith("com.parentalcontrol")) {
                if (unlockedPackages.isNotEmpty()) {
                    Log.d(TAG, "Navigated away from unlocked app. Clearing sessions.")
                    unlockedPackages.clear()
                }
            }

            // Check if the foreground application is blocked in parental settings (permanently or by schedule)
            val isPermanentlyBlocked = BlocklistManager.isBlocked(packageName)
            val isScheduledBlocked = BlocklistManager.isCurrentlyInBlockedSchedule(packageName)

            if (isPermanentlyBlocked || isScheduledBlocked) {
                // If it is in the unlocked session list, allow it to run
                if (unlockedPackages.contains(packageName)) {
                    return
                }

                Log.w(TAG, "Intercepted execution of blocked app: $packageName (Permanent: $isPermanentlyBlocked, Scheduled: $isScheduledBlocked). Launching lock screen overlay.")
                currentlyBlockingPackage = packageName // Cache package name
                launchLockGatekeeper()
                return
            }
            
            // Secure Settings Safeguard Block
            if (packageName == "com.android.settings") {
                if (System.currentTimeMillis() < bypassSafeguardUntil) {
                    Log.d(TAG, "Grace period active. Allowing settings deactivation.")
                    return
                }

                val rootNode = rootInActiveWindow ?: event.source
                if (rootNode == null) return
                val textList = mutableListOf<String>()
                findTextNodes(rootNode, textList)
                rootNode.recycle()
                val pageText = textList.joinToString(" ")

                if (pageText.contains("Accessibility", ignoreCase = true)) {
                    Log.w(TAG, "Accessibility settings detected. Launching lock screen.")
                    launchLockGatekeeper()
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
        isCurrentlyIntercepting = true // Activate state control
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

    override fun onDestroy() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val cn = ComponentName(this, ParentalDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(cn)) {
            dpm.lockNow()
        }
        super.onDestroy()
        if (screenOffReceiver != null) {
            unregisterReceiver(screenOffReceiver)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        BlocklistManager.init(this)

        // Register dynamic screen off receiver to relock session when screen sleeps
        screenOffReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                Log.d(TAG, "Screen lock/sleep registered. Auto-relocking blocked apps.")
                unlockedPackages.clear()
            }
        }
        val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        Log.d(TAG, "Accessibility Service Connected Successfully!")
    }
}
