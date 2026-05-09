package com.nomadclub.cashchat.core.network

import com.nomadclub.cashchat.data.model.AuthResponse
import com.nomadclub.cashchat.data.model.TokenRefreshRequest
import com.nomadclub.cashchat.data.model.UserResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/guest")
    suspend fun loginAsGuest(
        @Query("deviceToken") deviceToken: String
    ): Response<AuthResponse>

    // TODO(CC-154): GET → POST 전환 필요 (보안 개선)
    //   현재 serverAuthCode가 URL 쿼리로 전달되어 프록시 로그·네트워크 인스펙터에 노출될 위험이 있음.
    //   백엔드 엔드포인트를 POST로 변경한 후 아래와 같이 수정:
    //
    //   data class GoogleLoginRequest(val code: String, val deviceToken: String?)
    //
    //   @POST("api/auth/callback/google")
    //   suspend fun loginWithGoogle(
    //       @Body request: GoogleLoginRequest
    //   ): Response<AuthResponse>
    //
    //   처리 순서: BE 엔드포인트 변경 → FE ApiService + AuthRepository.loginWithGoogle() 동시 배포
    @GET("api/auth/callback/google")
    suspend fun loginWithGoogle(
        @Query("code") code: String,
        @Query("deviceToken") deviceToken: String? = null
    ): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<AuthResponse>

    @GET("api/users/me")
    suspend fun getMe(): Response<UserResponse>
}
