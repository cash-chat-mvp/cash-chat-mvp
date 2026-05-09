package com.nomadclub.cashchat.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class OnboardingViewModel : ViewModel(), KoinComponent {

    private val authRepository: AuthRepository by inject()

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        object Success : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun loginAsGuest() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.loginAsGuest()
                .onSuccess { _uiState.value = UiState.Success }
                .onFailure { _uiState.value = UiState.Error(it.message ?: "게스트 로그인 실패") }
        }
    }

    fun loginWithGoogle(authCode: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            authRepository.loginWithGoogle(authCode)
                .onSuccess { _uiState.value = UiState.Success }
                .onFailure { _uiState.value = UiState.Error(it.message ?: "Google 로그인 실패") }
        }
    }

    fun clearError() {
        _uiState.value = UiState.Idle
    }
}
