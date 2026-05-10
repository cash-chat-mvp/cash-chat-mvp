package com.nomadclub.cashchat.shared.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val userId: Long, val role: String) : AuthState()
    object Unauthenticated : AuthState()
}

class LoginStore {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun setAuthenticated(userId: Long, role: String) {
        _authState.value = AuthState.Authenticated(userId, role)
    }

    fun logout() {
        _authState.value = AuthState.Unauthenticated
    }

    fun setLoading() {
        _authState.value = AuthState.Loading
    }
}
