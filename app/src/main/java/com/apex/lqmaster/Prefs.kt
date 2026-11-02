package com.apex.lqmaster

import android.content.Context

object Prefs {
    private const val PREF_NAME = "lq_master_prefs"
    private const val KEY_LAST_VERSION = "last_opt_version"

    // Kiểm tra xem bản game hiện tại đã được tối ưu chưa
    fun isOptimizedForCurrentVersion(context: Context, currentVersion: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_VERSION, "") == currentVersion
    }

    // Lưu lại version game sau khi tối ưu thành công
    fun saveOptimizedVersion(context: Context, version: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_VERSION, version).apply()
    }
}
