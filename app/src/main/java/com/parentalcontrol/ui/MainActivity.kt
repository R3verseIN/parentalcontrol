package com.parentalcontrol.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.app.admin.DevicePolicyManager
import com.parentalcontrol.security.ParentalDeviceAdminReceiver
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.card.MaterialCardView
import com.parentalcontrol.R
import com.parentalcontrol.services.ParentalAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var viewGlobalStatusDot: View
    private lateinit var tvGlobalStatusLabel: TextView

    private lateinit var badgeAccessibility: MaterialCardView
    private lateinit var viewAccessDot: View
    private lateinit var tvAccessStatus: TextView

    private lateinit var badgeDeviceAdmin: MaterialCardView
    private lateinit var viewAdminDot: View
    private lateinit var tvAdminStatus: TextView

    private lateinit var rowAppLimits: LinearLayout
    private lateinit var tvAppGuardStats: TextView
    private lateinit var rowProtectionShield: LinearLayout
    private lateinit var switchProtection: SwitchCompat
    private lateinit var tvShieldStatus: TextView
    private lateinit var rowAccessPin: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind layout views
        viewGlobalStatusDot = findViewById(R.id.viewGlobalStatusDot)
        tvGlobalStatusLabel = findViewById(R.id.tvGlobalStatusLabel)

        badgeAccessibility = findViewById(R.id.badgeAccessibility)
        viewAccessDot = findViewById(R.id.viewAccessDot)
        tvAccessStatus = findViewById(R.id.tvAccessStatus)

        badgeDeviceAdmin = findViewById(R.id.badgeDeviceAdmin)
        viewAdminDot = findViewById(R.id.viewAdminDot)
        tvAdminStatus = findViewById(R.id.tvAdminStatus)

        rowAppLimits = findViewById(R.id.rowAppLimits)
        tvAppGuardStats = findViewById(R.id.tvAppGuardStats)
        rowProtectionShield = findViewById(R.id.rowProtectionShield)
        switchProtection = findViewById(R.id.switchProtection)
        tvShieldStatus = findViewById(R.id.tvShieldStatus)
        rowAccessPin = findViewById(R.id.rowAccessPin)

        // Initialize persistent App Blocker memory
        com.parentalcontrol.security.BlocklistManager.init(this)

        // Launch App Blocker settings on row click
        rowAppLimits.setOnClickListener {
            val intent = Intent(this, AppBlockerActivity::class.java)
            startActivity(intent)
        }

        // Protection Shield toggle
        val prefs = getSharedPreferences("parental_control_prefs", Context.MODE_PRIVATE)
        switchProtection.isChecked = prefs.getBoolean("protection_enabled", true)
        updateShieldStatusText(switchProtection.isChecked)

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("protection_enabled", isChecked).apply()
            updateShieldStatusText(isChecked)
            if (isChecked) {
                Toast.makeText(this, "Protection Shield enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Protection Shield disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Clicking the row also toggles the switch
        rowProtectionShield.setOnClickListener {
            switchProtection.isChecked = !switchProtection.isChecked
        }

        // Request Device Admin anti-uninstall prompt on health badge click
        badgeDeviceAdmin.setOnClickListener {
            val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(this, ParentalDeviceAdminReceiver::class.java)
            if (devicePolicyManager.isAdminActive(componentName)) {
                Toast.makeText(this, "Uninstall protection is active.", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the parental app from unauthorized uninstallation.")
                }
                startActivity(intent)
            }
        }

        // Request System settings mapping on health badge click
        badgeAccessibility.setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Accessibility Service is active and securing background windows.", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "Find 'Parental Control' under installed apps & enable it.", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open settings automatically.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Change PIN on row click
        rowAccessPin.setOnClickListener {
            val intent = Intent(this, PinActivity::class.java).apply {
                putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboardHealth()
        val prefs = getSharedPreferences("parental_control_prefs", Context.MODE_PRIVATE)
        switchProtection.isChecked = prefs.getBoolean("protection_enabled", true)
        updateShieldStatusText(switchProtection.isChecked)
    }

    private fun updateShieldStatusText(enabled: Boolean) {
        if (enabled) {
            tvShieldStatus.text = "Block Settings & restricted apps"
            tvShieldStatus.setTextColor(Color.parseColor("#30D158"))
        } else {
            tvShieldStatus.text = "Protection is OFF"
            tvShieldStatus.setTextColor(Color.parseColor("#FF453A"))
        }
    }

    /**
     * Check if our ParentalAccessibilityService is currently turned on in Android settings.
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = ComponentName(this, ParentalAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    /**
     * Consolidated function to update all dynamic status and chip visuals inside parent dashboard in one go.
     */
    private fun updateDashboardHealth() {
        val isAccessActive = isAccessibilityServiceEnabled()
        
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, ParentalDeviceAdminReceiver::class.java)
        val isAdminActive = devicePolicyManager.isAdminActive(componentName)

        // 1. Update Accessibility Badge
        if (isAccessActive) {
            viewAccessDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#34C759")) // Neon Mint Green
            tvAccessStatus.text = "Active"
            tvAccessStatus.setTextColor(Color.parseColor("#34C759"))
            badgeAccessibility.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#1D1D21")))
        } else {
            viewAccessDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF453A")) // Neon Red
            tvAccessStatus.text = "Disabled (Tap)"
            tvAccessStatus.setTextColor(Color.parseColor("#FF453A"))
            badgeAccessibility.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#26FF453A"))) // Translucent rose highlights on error state!
        }

        // 2. Update Device Admin Badge
        if (isAdminActive) {
            viewAdminDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#34C759"))
            tvAdminStatus.text = "Locked"
            tvAdminStatus.setTextColor(Color.parseColor("#34C759"))
            badgeDeviceAdmin.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#1D1D21")))
        } else {
            viewAdminDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFCC00")) // Amber
            tvAdminStatus.text = "Unprotected (Tap)"
            tvAdminStatus.setTextColor(Color.parseColor("#FFCC00"))
            badgeDeviceAdmin.setCardBackgroundColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#26FFCC00"))) // Translucent amber highlights on warning state!
        }

        // 3. Update Dynamic Global Security status
        if (isAccessActive && isAdminActive) {
            viewGlobalStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#34C759"))
            tvGlobalStatusLabel.text = "System Secure"
            tvGlobalStatusLabel.setTextColor(Color.parseColor("#34C759"))
        } else if (!isAccessActive && !isAdminActive) {
            viewGlobalStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF453A"))
            tvGlobalStatusLabel.text = "System Unsecured"
            tvGlobalStatusLabel.setTextColor(Color.parseColor("#FF453A"))
        } else {
            viewGlobalStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFCC00"))
            tvGlobalStatusLabel.text = "Action Recommended"
            tvGlobalStatusLabel.setTextColor(Color.parseColor("#FFCC00"))
        }

        // 4. Update Dynamic Statistics
        val blockedCount = com.parentalcontrol.security.BlocklistManager.getBlockedAppsCount()
        val schedulesCount = com.parentalcontrol.security.BlocklistManager.getActiveSchedulesCount()
        tvAppGuardStats.text = "$blockedCount apps restricted | $schedulesCount active schedules"
    }
}
