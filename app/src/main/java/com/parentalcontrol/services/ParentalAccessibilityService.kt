package com.parentalcontrol.services

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.parentalcontrol.R
import com.parentalcontrol.security.BlocklistManager
import com.parentalcontrol.security.ParentalDeviceAdminReceiver
import com.parentalcontrol.security.PinManager

class ParentalAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ParentalAccessService"

        @JvmStatic
        var bypassSafeguardUntil: Long = 0

        @JvmStatic
        val unlockedPackages = mutableSetOf<String>()
    }

    private var screenOffReceiver: android.content.BroadcastReceiver? = null
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private lateinit var pinManager: PinManager
    private val inputPin = StringBuilder()
    private var dots: List<View> = emptyList()
    private var currentlyBlockingPackage: String? = null

    // Anti-Bruteforce State
    private val attemptTimestamps = mutableListOf<Long>()
    private var lockoutEndTime = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        BlocklistManager.init(this)
        pinManager = PinManager(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Pre-inflate the overlay to ensure zero latency drawing
        preInflateOverlay()

        screenOffReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                Log.d(TAG, "Screen lock/sleep registered. Auto-relocking apps.")
                unlockedPackages.clear()
                hideOverlay()
            }
        }
        val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, filter)

        Log.d(TAG, "Accessibility Service Connected Successfully!")
    }

    private fun preInflateOverlay() {
        try {
            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.activity_pin, null)

            val tvTitle: TextView = overlayView!!.findViewById(R.id.tvPinTitle)
            val tvSubtitle: TextView = overlayView!!.findViewById(R.id.tvPinSubtitle)
            
            tvTitle.text = "Device Locked"
            tvSubtitle.text = "Enter your parental control security PIN"

            dots = listOf(
                overlayView!!.findViewById(R.id.dot1),
                overlayView!!.findViewById(R.id.dot2),
                overlayView!!.findViewById(R.id.dot3),
                overlayView!!.findViewById(R.id.dot4),
                overlayView!!.findViewById(R.id.dot5),
                overlayView!!.findViewById(R.id.dot6)
            )

            setupNumpad(overlayView!!, tvSubtitle)

            // Handle Back Button gracefully
            overlayView!!.isFocusableInTouchMode = true
            overlayView!!.setOnKeyListener { _, keyCode, keyEvent ->
                if (keyCode == KeyEvent.KEYCODE_BACK && keyEvent.action == KeyEvent.ACTION_UP) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    hideOverlay()
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pre-inflate overlay: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_CLICKED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // Auto-relock when navigating away
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName != currentlyBlockingPackage && !packageName.startsWith("com.parentalcontrol")) {
                if (unlockedPackages.isNotEmpty()) {
                    unlockedPackages.clear()
                }
            }
        }

        val isPermanentlyBlocked = BlocklistManager.isBlocked(packageName)
        val isScheduledBlocked = BlocklistManager.isCurrentlyInBlockedSchedule(packageName)
        val isSettingsOrInstaller = packageName == "com.android.settings" || packageName.contains("packageinstaller")

        if (isPermanentlyBlocked || isScheduledBlocked || isSettingsOrInstaller) {
            
            if (isSettingsOrInstaller && System.currentTimeMillis() < bypassSafeguardUntil) {
                return
            }

            if (unlockedPackages.contains(packageName)) {
                return
            }

            currentlyBlockingPackage = packageName
            handleBlockedAppAttempt()
        }
    }

    private fun handleBlockedAppAttempt() {
        val now = System.currentTimeMillis()

        if (now < lockoutEndTime) {
            // Hard Lockout Active - Destroy the screen with BACK
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // Check if overlay is already attached
        if (overlayView?.parent != null) {
            return
        }

        // Track attempts for brute-force defense
        attemptTimestamps.removeAll { now - it > 10000 } // Clear attempts older than 10s
        attemptTimestamps.add(now)

        if (attemptTimestamps.size >= 3) {
            // 3-Strike Rule Tripped -> Activate 30-second Lockout
            lockoutEndTime = now + 30000
            Toast.makeText(this, "Too many rapid attempts. Settings locked for 30s.", Toast.LENGTH_LONG).show()
            
            // Unwind navigation stack aggressively
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_BACK)
            hideOverlay()
            return
        }

        showOverlay()
    }

    private fun showOverlay() {
        if (overlayView == null) preInflateOverlay()
        if (overlayView?.parent != null) return

        try {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            windowManager?.addView(overlayView, params)
            overlayView!!.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay: ${e.message}")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun hideOverlay() {
        if (overlayView?.parent != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay: ${e.message}")
            }
        }
        inputPin.clear()
        updateDots()
    }

    private fun setupNumpad(view: View, subtitleView: TextView) {
        val numButtons = listOf<Button>(
            view.findViewById(R.id.btn0), view.findViewById(R.id.btn1), view.findViewById(R.id.btn2),
            view.findViewById(R.id.btn3), view.findViewById(R.id.btn4), view.findViewById(R.id.btn5),
            view.findViewById(R.id.btn6), view.findViewById(R.id.btn7), view.findViewById(R.id.btn8),
            view.findViewById(R.id.btn9)
        )

        for (button in numButtons) {
            button.setOnClickListener {
                if (inputPin.length < 6) {
                    inputPin.append(button.text)
                    updateDots()
                    if (inputPin.length == 6) {
                        view.postDelayed({
                            processPinEntry(subtitleView)
                        }, 150)
                    }
                }
            }
        }

        view.findViewById<ImageButton>(R.id.btnDelete).setOnClickListener {
            if (inputPin.isNotEmpty()) {
                inputPin.deleteCharAt(inputPin.length - 1)
                updateDots()
            }
        }

        view.findViewById<ImageButton>(R.id.btnDone).setOnClickListener {
            if (inputPin.length == 6) {
                processPinEntry(subtitleView)
            } else {
                Toast.makeText(this, "Please enter a 6-digit PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processPinEntry(subtitleView: TextView) {
        val enteredPin = inputPin.toString()
        inputPin.clear()
        updateDots()

        if (pinManager.verifyPin(enteredPin)) {
            val blockedPkg = currentlyBlockingPackage
            if (blockedPkg != null) {
                unlockedPackages.add(blockedPkg)
            }
            bypassSafeguardUntil = System.currentTimeMillis() + 15000
            
            // Clear brute-force trackers upon successful entry
            attemptTimestamps.clear()
            
            hideOverlay()
        } else {
            subtitleView.text = "Incorrect PIN. Please try again."
        }
    }

    private fun updateDots() {
        val length = inputPin.length
        for (i in dots.indices) {
            if (i < length) {
                dots[i].setBackgroundResource(R.drawable.pin_dot_on)
            } else {
                dots[i].setBackgroundResource(R.drawable.pin_dot_off)
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
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
}
