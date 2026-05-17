package com.parentalcontrol.security

import android.content.Context
import android.content.SharedPreferences

object BlocklistManager {

    private const val PREFS_NAME = "app_blocking_prefs"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"

    private lateinit var prefs: SharedPreferences

    /**
     * Initialize the BlocklistManager with the application Context.
     */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if SharedPreferences has been initialized.
     */
    private fun checkInit() {
        if (!::prefs.isInitialized) {
            throw IllegalStateException("BlocklistManager is not initialized. Call init(context) first.")
        }
    }

    /**
     * Returns the set of currently blocked package names.
     */
    fun getBlockedPackages(): Set<String> {
        checkInit()
        return prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
    }

    /**
     * Checks if a specific package is blocked.
     */
    fun isBlocked(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        return getBlockedPackages().contains(packageName)
    }

    /**
     * Add a package name to the blocklist.
     */
    fun blockPackage(packageName: String) {
        checkInit()
        val currentSet = getBlockedPackages().toMutableSet()
        if (currentSet.add(packageName)) {
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, currentSet).apply()
        }
    }

    /**
     * Remove a package name from the blocklist.
     */
    fun unblockPackage(packageName: String) {
        checkInit()
        val currentSet = getBlockedPackages().toMutableSet()
        if (currentSet.remove(packageName)) {
            prefs.edit().putStringSet(KEY_BLOCKED_PACKAGES, currentSet).apply()
        }
    }
}
