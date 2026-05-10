package com.nomadclub.cashchat.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "CashChatAuth"

/**
 * 인증 UI 상태.
 *
 * - Loading   : 세션 확인 또는 게스트 로그인 진행 중
 * - Authenticated: 유효한 세션 보유 (userId, role 포함)
 * - Error     : API 호출 실패 또는 네트워크 오류
 */
sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val userId: Long, val role: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * 앱 시작 시 인증을 처리하는 ViewModel (Koin 주입).
 *
 * init 블록에서 자동으로 세션을 초기화합니다:
 *   - 기존 accessToken이 있으면 → Authenticated 상태로 즉시 전환
 *   - 없으면 → loginAsGuest() 호출 후 Authenticated 전환
 *
 * 화면 회전 등 Composable 재생성 시에도 ViewModel은 유지되므로
 * API 중복 호출이 발생하지 않습니다.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        initSession()
    }

    /**
     * 세션 초기화.
     * 저장된 토큰이 있으면 바로 Authenticated, 없으면 게스트 로그인 수행.
     */
    private fun initSession() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val token = authRepository.getAccessToken()
                if (token != null) {
                    // 기존 세션 재사용
                    val role = authRepository.getUserRole() ?: "GUEST"
                    val userId = authRepository.getUserId() ?: 0L
                    Log.d(TAG, "♻️ 기존 세션 재사용 | userId=$userId, role=$role")
                    _authState.value = AuthState.Authenticated(userId, role)
                } else {
                    // 세션 없음 → 게스트 자동 로그인 (CC-154 UUID 생성 포함)
                    Log.d(TAG, "⚡ 세션 없음 → 게스트 로그인 시작")
                    val response = authRepository.loginAsGuest().getOrThrow()
                    _authState.value = AuthState.Authenticated(
                        userId = response.userId,
                        role = response.role
                    )
                }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(
                    e.message ?: "인증에 실패했습니다. 네트워크를 확인해주세요."
                )
            }
        }
    }

    /** 오류 후 재시도 */
    fun retry() = initSession()

    /**
     * Google serverAuthCode로 Member 로그인.
     * 성공 시 Authenticated(role="MEMBER")로 상태 전환.
     * 실패 시 Error 상태 — onError 콜백으로 상위에 전달 (스낵바 등).
     */
    fun loginWithGoogle(serverAuthCode: String, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            try {
                val response = authRepository.loginWithGoogle(serverAuthCode).getOrThrow()
                Log.d(TAG, "✅ Google 로그인 완료 | userId=${response.userId}, role=${response.role}")
                _authState.value = AuthState.Authenticated(response.userId, response.role)
            } catch (e: Exception) {
                val msg = e.message ?: "Google 로그인에 실패했습니다."
                Log.e(TAG, "❌ Google 로그인 실패: $msg")
                onError(msg)
                // 로그인 실패 시 기존 게스트 세션으로 복귀
                initSession()
            }
        }
    }

    /**
     * 로그아웃.
     * 세션을 초기화하고 다시 게스트 로그인을 수행합니다.
     * DeviceToken은 유지되므로 동일 UUID로 새 Guest 세션을 발급받습니다.
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            initSession()
        }
    }
}
