package com.parentalcontrol.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.parentalcontrol.R
import com.parentalcontrol.security.PinManager

class PinActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "extra_mode"
        const val MODE_SETUP = 1
        const val MODE_UNLOCK = 2
        const val MODE_CHANGE = 3
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var pinManager: PinManager

    private var currentMode = MODE_UNLOCK
    private val inputPin = StringBuilder()
    
    // Multi-stage states
    private var firstAttemptPin = ""
    private var isConfirming = false
    private var isVerifyingCurrent = false

    private lateinit var dots: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin)

        pinManager = PinManager(this)

        // Bind layout views
        tvTitle = findViewById(R.id.tvPinTitle)
        tvSubtitle = findViewById(R.id.tvPinSubtitle)

        dots = listOf(
            findViewById(R.id.dot1),
            findViewById(R.id.dot2),
            findViewById(R.id.dot3),
            findViewById(R.id.dot4),
            findViewById(R.id.dot5),
            findViewById(R.id.dot6)
        )

        // Auto-detect mode if not explicitly passed
        val passedMode = intent.getIntExtra(EXTRA_MODE, -1)
        currentMode = if (passedMode != -1) {
            passedMode
        } else {
            if (pinManager.isPinSet()) MODE_UNLOCK else MODE_SETUP
        }

        // Initialize state variables based on mode
        if (currentMode == MODE_CHANGE) {
            isVerifyingCurrent = true
        }

        setupNumpad()
        updateHeaderAndDots()
    }

    /**
     * Set click listeners on all the numpad buttons dynamically.
     */
    private fun setupNumpad() {
        val numButtons = listOf<Button>(
            findViewById(R.id.btn0),
            findViewById(R.id.btn1),
            findViewById(R.id.btn2),
            findViewById(R.id.btn3),
            findViewById(R.id.btn4),
            findViewById(R.id.btn5),
            findViewById(R.id.btn6),
            findViewById(R.id.btn7),
            findViewById(R.id.btn8),
            findViewById(R.id.btn9)
        )

        // Standard number input handler
        for (button in numButtons) {
            button.setOnClickListener {
                if (inputPin.length < 6) {
                    inputPin.append(button.text)
                    updateDots()
                    
                    // Auto-submit when 6 digits are entered
                    if (inputPin.length == 6) {
                        tvSubtitle.postDelayed({
                            processPinEntry()
                        }, 150)
                    }
                }
            }
        }

        // Delete button click handler
        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            if (inputPin.isNotEmpty()) {
                inputPin.deleteCharAt(inputPin.length - 1)
                updateDots()
            }
        }

        // Done button click handler
        findViewById<Button>(R.id.btnDone).setOnClickListener {
            if (inputPin.length == 6) {
                processPinEntry()
            } else {
                Toast.makeText(this, "Please enter a 6-digit PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Dynamic flow routing based on what task is being executed.
     */
    private fun processPinEntry() {
        val enteredPin = inputPin.toString()
        inputPin.clear()
        updateDots()

        when (currentMode) {
            MODE_SETUP -> handleSetupFlow(enteredPin)
            MODE_UNLOCK -> handleUnlockFlow(enteredPin)
            MODE_CHANGE -> handleChangeFlow(enteredPin)
        }
    }

    /**
     * Flow 1: Creates a new security PIN (includes confirmation checking).
     */
    private fun handleSetupFlow(enteredPin: String) {
        if (!isConfirming) {
            // Step 1: Cache first input, and ask to confirm
            firstAttemptPin = enteredPin
            isConfirming = true
            tvTitle.text = "Confirm PIN"
            tvSubtitle.text = "Re-enter your 6-digit parental security PIN"
        } else {
            // Step 2: Validate both entries
            if (enteredPin == firstAttemptPin) {
                val isSaved = pinManager.savePin(enteredPin)
                if (isSaved) {
                    Toast.makeText(this, "PIN Setup Successful!", Toast.LENGTH_SHORT).show()
                    navigateToMain()
                } else {
                    resetSetupState("Failed to save PIN. Try again.")
                }
            } else {
                resetSetupState("PINs did not match. Let's try again.")
            }
        }
    }

    private fun resetSetupState(errorMessage: String) {
        isConfirming = false
        firstAttemptPin = ""
        tvTitle.text = "Parental Control"
        tvSubtitle.text = errorMessage
    }

    /**
     * Flow 2: Validates PIN on app startup.
     */
    private fun handleUnlockFlow(enteredPin: String) {
        if (pinManager.verifyPin(enteredPin)) {
            val isInterception = com.parentalcontrol.services.ParentalAccessibilityService.isCurrentlyIntercepting
            if (isInterception) {
                // Consume the intercept state control flag
                com.parentalcontrol.services.ParentalAccessibilityService.isCurrentlyIntercepting = false
                
                // Enroll the currently blocked package in the session's active unlockedPackages set
                val blockedPkg = com.parentalcontrol.services.ParentalAccessibilityService.currentlyBlockingPackage
                if (blockedPkg != null) {
                    com.parentalcontrol.services.ParentalAccessibilityService.unlockedPackages.add(blockedPkg)
                }
                
                // Set the 15-second grace window to allow the parent to safely modify settings or uninstall the app
                com.parentalcontrol.services.ParentalAccessibilityService.bypassSafeguardUntil = System.currentTimeMillis() + 15000
                finish()
            } else {
                navigateToMain()
            }
        } else {
            tvSubtitle.text = "Incorrect PIN. Please try again."
        }
    }

    /**
     * Flow 3: Verify current PIN, then prompt to create a new one.
     */
    private fun handleChangeFlow(enteredPin: String) {
        if (isVerifyingCurrent) {
            // Phase 3.1: Confirm they know the current PIN before letting them change it
            if (pinManager.verifyPin(enteredPin)) {
                isVerifyingCurrent = false
                isConfirming = false
                tvTitle.text = "New PIN"
                tvSubtitle.text = "Enter a new 6-digit security PIN"
            } else {
                tvSubtitle.text = "Incorrect Current PIN. Verify credentials."
            }
        } else {
            // Phase 3.2: Re-use setup logic flow to establish the new PIN
            handleSetupFlow(enteredPin)
        }
    }

    override fun onBackPressed() {
        val isInterception = com.parentalcontrol.services.ParentalAccessibilityService.isCurrentlyIntercepting
        if (currentMode == MODE_UNLOCK && isInterception) {
            // Clear the intercept state control flag on exit
            com.parentalcontrol.services.ParentalAccessibilityService.isCurrentlyIntercepting = false
            
            // Redirect to Home screen instead of finishing and exposing the blocked app
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * Redirect to the Dashboard activity.
     */
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Sync visual UI headers based on the current mode and state variables.
     */
    private fun updateHeaderAndDots() {
        updateDots()
        when (currentMode) {
            MODE_SETUP -> {
                tvTitle.text = "Create PIN"
                tvSubtitle.text = "Create a security PIN to protect configurations"
            }
            MODE_UNLOCK -> {
                tvTitle.text = "Device Locked"
                tvSubtitle.text = "Enter your parental control security PIN"
            }
            MODE_CHANGE -> {
                tvTitle.text = "Verify Security"
                tvSubtitle.text = "Enter your current 6-digit PIN first"
            }
        }
    }

    /**
     * Sync state indicator dots (purple if entered, gray if blank).
     */
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
}
