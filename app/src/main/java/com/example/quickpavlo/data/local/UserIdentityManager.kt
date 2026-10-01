package com.example.quickpavlo.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserIdentityManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("quickpavlo_user_prefs", Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString().substring(0, 8)
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    fun getUserName(): String {
        return prefs.getString("user_name", "Usuario-QuickPavlo") ?: "Usuario-QuickPavlo"
    }

    fun setUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
    }
}
