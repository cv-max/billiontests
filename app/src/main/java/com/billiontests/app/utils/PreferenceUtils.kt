package com.billiontests.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object PreferenceUtils {

    const val PREFERENCE_NAME = "app_preference"
    const val IS_FIRST_TIME_USER = "is_first_time_user"
    const val IS_SIGNED_UP = "is_signed_up"
    const val USER_NAME = "user_name"
    const val AGE = "age"
    const val GENDER = "gender"
    const val LAST_TEST_TIME = "last_test_time"
    const val PHONE_NUMBER = "phone_number"

    const val SELECTED_ROACH = "selected_roach"

    const val LAST_FOLDER_NAME = "last_folder_name"

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)
    }

    fun putString(context: Context, key: String, value: String) {
        getSharedPreferences(context).edit { putString(key, value) }
    }

    fun getString(context: Context, key: String, defaultValue: String = ""): String {
        return getSharedPreferences(context).getString(key, defaultValue) ?: defaultValue
    }

    fun putInt(context: Context, key: String, value: Int) {
        getSharedPreferences(context).edit { putInt(key, value) }
    }

    fun getInt(context: Context, key: String, defaultValue: Int = 0): Int {
        return getSharedPreferences(context).getInt(key, defaultValue)
    }

    fun putBoolean(context: Context, key: String, value: Boolean) {
        getSharedPreferences(context).edit { putBoolean(key, value) }
    }

    fun getBoolean(context: Context, key: String, defaultValue: Boolean = false): Boolean {
        return getSharedPreferences(context).getBoolean(key, defaultValue)
    }

    fun putLong(context: Context, key: String, value: Long) {
        getSharedPreferences(context).edit { putLong(key, value) }
    }

    fun getLong(context: Context, key: String, defaultValue: Long = 0L): Long {
        return getSharedPreferences(context).getLong(key, defaultValue)
    }

    fun putFloat(context: Context, key: String, value: Float) {
        getSharedPreferences(context).edit { putFloat(key, value) }
    }

    fun getFloat(context: Context, key: String, defaultValue: Float = 0f): Float {
        return getSharedPreferences(context).getFloat(key, defaultValue)
    }

    fun remove(context: Context, key: String) {
        getSharedPreferences(context).edit { remove(key) }
    }

    fun clear(context: Context) {
        getSharedPreferences(context).edit { clear() }
    }
}