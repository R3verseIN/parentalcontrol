package com.parentalcontrol.security

import android.content.Context
import java.security.MessageDigest

class PinManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences("parental_control_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PIN_HASH = "parental_pin_hash"
    }

    /**
     * Checks if a parental control PIN has been configured.
     */
    fun isPinSet(): Boolean {
        return sharedPreferences.contains(KEY_PIN_HASH)
    }

    /**
     * Hashes the provided PIN using SHA-256 and stores it in SharedPreferences.
     */
    fun savePin(pin: String): Boolean {
        if (pin.length < 6) return false
        val hashed = hashPin(pin) ?: return false
        return sharedPreferences.edit().putString(KEY_PIN_HASH, hashed).commit()
    }

    /**
     * Hashes the input PIN and verifies it against the stored hash.
     */
    fun verifyPin(input: String): Boolean {
        val storedHash = sharedPreferences.getString(KEY_PIN_HASH, null) ?: return false
        val inputHash = hashPin(input)
        return storedHash == inputHash
    }

    /**
     * Generates a SHA-256 hash string for security.
     */
    private fun hashPin(pin: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(pin.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
