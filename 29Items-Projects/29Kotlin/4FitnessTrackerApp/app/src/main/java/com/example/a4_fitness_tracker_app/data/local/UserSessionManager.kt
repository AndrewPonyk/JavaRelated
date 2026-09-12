package com.example.a4_fitness_tracker_app.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserSessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentNickname = MutableStateFlow<String?>(getActiveNickname())
    val currentNickname: StateFlow<String?> = _currentNickname.asStateFlow()

    fun getActiveNickname(): String? {
        return prefs.getString(KEY_ACTIVE_NICKNAME, null)
    }

    fun getActiveUserId(): String {
        val nickname = getActiveNickname() ?: "default_user"
        return nickname.lowercase().trim().replace(" ", "_")
    }

    fun setActiveNickname(nickname: String) {
        val cleanNickname = nickname.trim()
        prefs.edit().putString(KEY_ACTIVE_NICKNAME, cleanNickname).apply()
        _currentNickname.value = cleanNickname
        saveToRecentUsers(cleanNickname)
    }

    fun getRecentUsers(): List<String> {
        val set = prefs.getStringSet(KEY_SAVED_USERS, emptySet()) ?: emptySet()
        return set.toList().sorted()
    }

    private fun saveToRecentUsers(nickname: String) {
        val currentSet = prefs.getStringSet(KEY_SAVED_USERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(nickname)
        prefs.edit().putStringSet(KEY_SAVED_USERS, currentSet).apply()
    }

    fun clearSession() {
        prefs.edit().remove(KEY_ACTIVE_NICKNAME).apply()
        _currentNickname.value = null
    }

    companion object {
        private const val PREFS_NAME = "fitness_tracker_user_session"
        private const val KEY_ACTIVE_NICKNAME = "active_nickname"
        private const val KEY_SAVED_USERS = "saved_users_list"
    }
}
