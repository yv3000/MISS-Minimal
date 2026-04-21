package com.minimalist.launcher

import android.content.Context
import android.content.SharedPreferences

object PrefsManager {
    private const val PREFS_NAME = "MinimalistPrefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getHomeApp(slot: Int): String? = prefs.getString("home_app_$slot", null)
    fun setHomeApp(slot: Int, packageName: String) = prefs.edit().putString("home_app_$slot", packageName).apply()

    fun getCustomName(packageName: String): String? = prefs.getString("custom_name_$packageName", null)
    fun setCustomName(packageName: String, name: String) = prefs.edit().putString("custom_name_$packageName", name).apply()

    fun getLimitMs(packageName: String): Long = prefs.getLong("limit_ms_$packageName", 0L)
    fun setLimitMs(packageName: String, limitMs: Long) = prefs.edit().putLong("limit_ms_$packageName", limitMs).apply()

    fun getLimitStart(packageName: String): Long = prefs.getLong("limit_start_$packageName", 0L)
    fun setLimitStart(packageName: String, start: Long) = prefs.edit().putLong("limit_start_$packageName", start).apply()
    
    fun clearLimit(packageName: String) = prefs.edit().remove("limit_ms_$packageName").remove("limit_start_$packageName").apply()

    fun getFontSize(context: Context): Float = prefs.getFloat("font_size", 12f)
    fun setFontSize(context: Context, size: Float) = prefs.edit().putFloat("font_size", size).apply()
}
