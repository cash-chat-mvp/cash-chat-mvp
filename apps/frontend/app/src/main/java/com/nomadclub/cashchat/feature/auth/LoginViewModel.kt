package com.nomadclub.cashchat.feature.auth

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 로그인 화면의 상태와 로직을 담당하는 ViewModel.
 * - ViewModel: 화면 회전 등으로 Composable이 다시 그려져도 데이터가 유지됩니다.
 * - MutableStateFlow: UI가 구독할 수 있는 "값이 바뀌는 상태". asStateFlow()로 읽기 전용으로 노출합니다.
 */
class LoginViewModel : ViewModel() {
    // 내부에서는 수정 가능(_emailState), 외부에는 읽기 전용(emailState)으로 노출
    private val _emailState = MutableStateFlow("")
    val emailState = _emailState.asStateFlow()

    /** 입력된 이메일을 상태에 반영 (추후 이메일 로그인 시 사용) */
    fun updateEmail(email: String) {
        _emailState.value = email
    }

    /** 카카오/Apple 로그인 버튼 클릭 시 호출. 실제 로그인 API 연동은 TODO */
    fun login() {
        // TODO: Implement login logic
    }
}