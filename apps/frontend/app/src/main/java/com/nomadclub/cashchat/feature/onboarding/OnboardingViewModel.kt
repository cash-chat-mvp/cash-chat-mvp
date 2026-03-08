package com.nomadclub.cashchat.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 온보딩 화면용 ViewModel (현재는 로그인 연동 전이라 상태만 보유).
 * 필요 시 "다음" 페이지 인덱스, 스킵 여부 등을 여기서 관리할 수 있습니다.
 */
class OnboardingViewModel : ViewModel() {
    private val _emailState = MutableStateFlow("")
    val emailState = _emailState.asStateFlow()

    fun login() {
        // TODO: Implement login logic
    }
}