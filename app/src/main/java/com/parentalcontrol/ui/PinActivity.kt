package com.parentalcontrol.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
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

    private var currentMode = MODE_SETUP
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
            if (pinManager.isPinSet()) {
                MODE_UNLOCK
            } else {
                MODE_SETUP
            }
        }

        // Initialize state variables based on mode
        if (currentMode == MODE_CHANGE) {
            isVerifyingCurrent = true
        }

        setupNumpad()
        updateHeaderAndDots()
    }

    private fun setupNumpad() {
        val numButtons = listOf<Button>(
            findViewById(R.id.btn0), findViewById(R.id.btn1), findViewById(R.id.btn2),
            findViewById(R.id.btn3), findViewById(R.id.btn4), findViewById(R.id.btn5),
            findViewById(R.id.btn6), findViewById(R.id.btn7), findViewById(R.id.btn8),
            findViewById(R.id.btn9)
        )

        for (button in numButtons) {
            button.setOnClickListener {
                if (inputPin.length < 6) {
                    inputPin.append(button.text)
                    updateDots()
                    
                    if (inputPin.length == 6) {
                        tvSubtitle.postDelayed({
                            processPinEntry()
                        }, 150)
                    }
                }
            }
        }

        findViewById<ImageButton>(R.id.btnDelete).setOnClickListener {
            if (inputPin.isNotEmpty()) {
                inputPin.deleteCharAt(inputPin.length - 1)
                updateDots()
            }
        }

        findViewById<ImageButton>(R.id.btnDone).setOnClickListener {
            if (inputPin.length == 6) {
                processPinEntry()
            } else {
                Toast.makeText(this, "Please enter a 6-digit PIN", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

    private fun handleSetupFlow(enteredPin: String) {
        if (!isConfirming) {
            firstAttemptPin = enteredPin
            isConfirming = true
            tvTitle.text = "Confirm PIN"
            tvSubtitle.text = "Re-enter your 6-digit parental security PIN"
        } else {
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

    private fun handleUnlockFlow(enteredPin: String) {
        if (pinManager.verifyPin(enteredPin)) {
            Toast.makeText(this, "Unlocked!", Toast.LENGTH_SHORT).show()
            navigateToMain()
        } else {
            tvSubtitle.text = "Incorrect PIN. Please try again."
        }
    }

    private fun resetSetupState(errorMessage: String) {
        isConfirming = false
        firstAttemptPin = ""
        tvTitle.text = "Parental Control"
        tvSubtitle.text = errorMessage
    }

    private fun handleChangeFlow(enteredPin: String) {
        if (isVerifyingCurrent) {
            if (pinManager.verifyPin(enteredPin)) {
                isVerifyingCurrent = false
                isConfirming = false
                tvTitle.text = "New PIN"
                tvSubtitle.text = "Enter a new 6-digit security PIN"
            } else {
                tvSubtitle.text = "Incorrect Current PIN. Verify credentials."
            }
        } else {
            handleSetupFlow(enteredPin)
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

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
