package com.lingsheng.slidercontrol.storage

import android.content.Context
import android.content.SharedPreferences

const val DEFAULT_STEPS_PER_MILLIMETRE = "198"

data class SavedSliderSettings(
    val bluetoothAddress: String? = null,
    val deviceAddressByte: String = "0",
    val speedMillimetresPerSecond: String = "5.0",
    val stepsPerMillimetre: String = DEFAULT_STEPS_PER_MILLIMETRE,
    val maximumTravelMillimetres: String = "",
)

class SliderPreferences(context: Context) {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): SavedSliderSettings = SavedSliderSettings(
        bluetoothAddress = preferences.getString(KEY_BLUETOOTH_ADDRESS, null),
        deviceAddressByte = preferences.getString(KEY_DEVICE_ADDRESS_BYTE, "0") ?: "0",
        speedMillimetresPerSecond = preferences.getString(KEY_SPEED, "5.0") ?: "5.0",
        stepsPerMillimetre = preferences.getString(KEY_STEPS_PER_MM, null)
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_STEPS_PER_MILLIMETRE,
        maximumTravelMillimetres = preferences.getString(KEY_MAX_TRAVEL, "") ?: "",
    )

    fun saveDeviceAddress(address: String?) {
        preferences.edit().putString(KEY_BLUETOOTH_ADDRESS, address).apply()
    }

    fun saveSpeed(speed: String) {
        preferences.edit().putString(KEY_SPEED, speed).apply()
    }

    fun saveDeviceAddressByte(address: String) {
        preferences.edit().putString(KEY_DEVICE_ADDRESS_BYTE, address).apply()
    }

    fun saveCalibration(stepsPerMillimetre: String, maximumTravelMillimetres: String) {
        preferences.edit()
            .putString(KEY_STEPS_PER_MM, stepsPerMillimetre)
            .putString(KEY_MAX_TRAVEL, maximumTravelMillimetres)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "slider_settings"
        const val KEY_BLUETOOTH_ADDRESS = "bluetooth_address"
        const val KEY_DEVICE_ADDRESS_BYTE = "device_address_byte"
        const val KEY_SPEED = "speed_mm_s"
        const val KEY_STEPS_PER_MM = "steps_per_mm"
        const val KEY_MAX_TRAVEL = "max_travel_mm"
    }
}
