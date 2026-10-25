package com.danielportelabyrne.cinema.platform

/** The subset of Android's SharedPreferences that the shared code calls. */
interface Preferences {
    fun getString(key: String, defValue: String?): String?
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getLong(key: String, defValue: Long): Long
    fun getInt(key: String, defValue: Int): Int
    fun contains(key: String): Boolean
    val all: Map<String, *>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putLong(key: String, value: Long): Editor
        fun putInt(key: String, value: Int): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}
