package com.parentalcontrol.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.ImageView
import android.app.admin.DevicePolicyManager
import com.parentalcontrol.security.ParentalDeviceAdminReceiver
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.parentalcontrol.R
import com.parentalcontrol.services.ParentalAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var cardAccessibility: MaterialCardView
    private lateinit var ivIndicator: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvDesc: TextView
    private lateinit var btnEnableAccess: Button
    private lateinit var btnChangePin: Button

    private lateinit var cardUninstallProtection: MaterialCardView
    private lateinit var ivUninstallIndicator: ImageView
    private lateinit var tvUninstallTitle: TextView
    private lateinit var tvUninstallDesc: TextView
    private lateinit var btnEnableUninstallProtection: Button

    private lateinit var cardAppBlocker: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind layout views
        cardAccessibility = findViewById(R.id.cardAccessibility)
        ivIndicator = findViewById(R.id.ivAccessStatusIndicator)
        tvTitle = findViewById(R.id.tvAccessTitle)
        tvDesc = findViewById(R.id.tvAccessDesc)
        btnEnableAccess = findViewById(R.id.btnEnableAccess)
        btnChangePin = findViewById(R.id.btnChangePin)

        cardUninstallProtection = findViewById(R.id.cardUninstallProtection)
        ivUninstallIndicator = findViewById(R.id.ivUninstallIndicator)
        tvUninstallTitle = findViewById(R.id.tvUninstallTitle)
        tvUninstallDesc = findViewById(R.id.tvUninstallDesc)
        btnEnableUninstallProtection = findViewById(R.id.btnEnableUninstallProtection)

        cardAppBlocker = findViewById(R.id.cardAppBlocker)

        // Initialize persistent App Blocker memory
        com.parentalcontrol.security.BlocklistManager.init(this)

        // Launch App Blocker settings on card click
        cardAppBlocker.setOnClickListener {
            val intent = Intent(this, AppBlockerActivity::class.java)
            startActivity(intent)
        }

        // Request Device Admin anti-uninstall prompt
        btnEnableUninstallProtection.setOnClickListener {
            val componentName = ComponentName(this, ParentalDeviceAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects the parental app from unauthorized uninstallation.")
            }
            startActivity(intent)
        }

        // Request System settings mapping
        btnEnableAccess.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Find 'Parental Control' under installed apps & enable it.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open settings automatically.", Toast.LENGTH_SHORT).show()
            }
        }

        // Change PIN button mapping
        btnChangePin.setOnClickListener {
            val intent = Intent(this, PinActivity::class.java).apply {
                putExtra(PinActivity.EXTRA_MODE, PinActivity.MODE_CHANGE)
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
        checkDeviceAdminStatus()
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
     * Update the card and text color to match the system permission status.
     */
    private fun checkAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        if (isEnabled) {
            // Service Active State
            ivIndicator.setImageResource(R.drawable.ic_check_circle)
            ivIndicator.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#81C784"))
            tvTitle.text = "Parental Service Active"
            tvDesc.text = "System accessibility hooks are connected. App scanning active."
            tvDesc.setTextColor(Color.parseColor("#B3FFFFFF"))
            
            // Set card highlights to safe green color
            cardAccessibility.strokeColor = Color.parseColor("#81C784")
            
            // Modify button
            btnEnableAccess.text = "Service Running (Active)"
            btnEnableAccess.isEnabled = false
            btnEnableAccess.backgroundTintList = getColorStateList(android.R.color.darker_gray)
        } else {
            // Service Inactive State
            ivIndicator.setImageResource(R.drawable.ic_warning)
            ivIndicator.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF8A80"))
            tvTitle.text = "Accessibility Disabled"
            tvDesc.text = "Required to monitor window state changes & secure child activities."
            tvDesc.setTextColor(Color.parseColor("#FFCDD2"))
            
            // Set card highlight to warning color
            cardAccessibility.strokeColor = Color.parseColor("#FF8A80")
            
            // Restore button
            btnEnableAccess.text = "Enable Service"
            btnEnableAccess.isEnabled = true
            btnEnableAccess.backgroundTintList = getColorStateList(android.R.color.holo_red_light)
        }
    }

    /**
     * Update the anti-uninstall card status dynamically based on Device Admin settings.
     */
    private fun checkDeviceAdminStatus() {
        val devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, ParentalDeviceAdminReceiver::class.java)
        val isCustomAdminActive = devicePolicyManager.isAdminActive(componentName)

        if (isCustomAdminActive) {
            ivUninstallIndicator.setImageResource(R.drawable.ic_check_circle)
            ivUninstallIndicator.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#81C784"))
            tvUninstallTitle.text = "Uninstall Locked"
            tvUninstallDesc.text = "Device Administrator is active. Direct uninstallation disabled."
            tvUninstallDesc.setTextColor(Color.parseColor("#B3FFFFFF"))
            cardUninstallProtection.strokeColor = Color.parseColor("#81C784")

            btnEnableUninstallProtection.text = "Protection Active"
            btnEnableUninstallProtection.isEnabled = false
            btnEnableUninstallProtection.backgroundTintList = getColorStateList(android.R.color.darker_gray)
        } else {
            ivUninstallIndicator.setImageResource(R.drawable.ic_warning)
            ivUninstallIndicator.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF8A80"))
            tvUninstallTitle.text = "Uninstall Unprotected"
            tvUninstallDesc.text = "Activate Device Admin to lock settings and block uninstallation."
            tvUninstallDesc.setTextColor(Color.parseColor("#FFCDD2"))
            cardUninstallProtection.strokeColor = Color.parseColor("#FF8A80")

            btnEnableUninstallProtection.text = "Activate Protection"
            btnEnableUninstallProtection.isEnabled = true
            btnEnableUninstallProtection.backgroundTintList = getColorStateList(android.R.color.holo_red_light)
        }
    }
}
