package com.nomadclub.cashchat.shared.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginStore {
    private val _emailState = MutableStateFlow("")
    val emailState: StateFlow<String> = _emailState.asStateFlow()

    fun updateEmail(email: String) {
        _emailState.value = email
    }

    fun login() {
        // TODO: Implement login logic
    }
}
