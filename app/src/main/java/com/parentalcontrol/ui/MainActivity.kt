package com.parentalcontrol.ui

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.ImageView
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
}
