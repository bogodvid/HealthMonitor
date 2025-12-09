package com.example.healthmonitor

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences("HealthMonitor", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_TOTAL_STEPS = "total_steps"
        private const val KEY_SESSION_STEPS = "session_steps"
        private const val KEY_LAST_BACKUP = "last_backup_date"
    }



    var userName: String?
        get() = sharedPrefs.getString(KEY_USER_NAME, null)
        set(value) = sharedPrefs.edit().putString(KEY_USER_NAME, value).apply()

    var isFirstLaunch: Boolean
        get() = sharedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = sharedPrefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()

    var totalSteps: Int
        get() = sharedPrefs.getInt(KEY_TOTAL_STEPS, 0)
        set(value) = sharedPrefs.edit().putInt(KEY_TOTAL_STEPS, value).apply()

    var sessionSteps: Int
        get() = sharedPrefs.getInt(KEY_SESSION_STEPS, 0)
        set(value) = sharedPrefs.edit().putInt(KEY_SESSION_STEPS, value).apply()

    var lastBackupDate: Long
        get() = sharedPrefs.getLong(KEY_LAST_BACKUP, 0)
        set(value) = sharedPrefs.edit().putLong(KEY_LAST_BACKUP, value).apply()
}