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

    /**
     * Retrieve the list of scheduled block ranges for an app.
     */
    fun getBlockedSchedules(packageName: String): Set<String> {
        checkInit()
        return prefs.getStringSet("schedule_$packageName", emptySet()) ?: emptySet()
    }

    /**
     * Save the list of scheduled block ranges for an app.
     */
    fun saveBlockedSchedules(packageName: String, schedules: Set<String>) {
        checkInit()
        prefs.edit().putStringSet("schedule_$packageName", schedules).apply()
    }

    /**
     * Mathematically checks if the current time falls inside any scheduled block window.
     */
    fun isCurrentlyInBlockedSchedule(packageName: String?): Boolean {
        if (packageName.isNullOrEmpty()) return false
        val schedules = getBlockedSchedules(packageName)
        if (schedules.isEmpty()) return false

        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

        for (range in schedules) {
            val parts = range.split("-")
            if (parts.size != 2) continue
            
            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")
            if (startParts.size != 2 || endParts.size != 2) continue

            val startMin = startParts[0].toIntOrNull() ?: continue
            val startMinuteVal = startParts[1].toIntOrNull() ?: continue
            val endMin = endParts[0].toIntOrNull() ?: continue
            val endMinuteVal = endParts[1].toIntOrNull() ?: continue

            val startTotalMinutes = startMin * 60 + startMinuteVal
            val endTotalMinutes = endMin * 60 + endMinuteVal

            if (startTotalMinutes <= endTotalMinutes) {
                // Standard range, e.g. 08:00 - 14:00
                if (currentMinutes in startTotalMinutes..endTotalMinutes) {
                    return true
                }
            } else {
                // Overnight range, e.g. 21:00 - 07:00
                if (currentMinutes >= startTotalMinutes || currentMinutes <= endTotalMinutes) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Get the count of persistently blocked applications.
     */
    fun getBlockedAppsCount(): Int {
        return getBlockedPackages().size
    }

    /**
     * Get the count of applications that have active custom schedules.
     */
    fun getActiveSchedulesCount(): Int {
        checkInit()
        var count = 0
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith("schedule_")) {
                val scheduleSet = value as? Set<*>
                if (!scheduleSet.isNullOrEmpty()) {
                    count++
                }
            }
        }
        return count
    }
}
