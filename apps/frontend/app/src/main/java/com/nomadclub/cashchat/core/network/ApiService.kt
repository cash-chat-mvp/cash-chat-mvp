package com.nomadclub.cashchat.core.network

import com.nomadclub.cashchat.shared.auth.model.AuthResponse
import com.nomadclub.cashchat.shared.auth.model.GoogleOAuthCallbackRequest
import com.nomadclub.cashchat.shared.auth.model.LogoutRequest
import com.nomadclub.cashchat.shared.auth.model.TokenRefreshRequest
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

    @POST("api/auth/callback/google")
    suspend fun loginWithGoogle(
        @Body request: GoogleOAuthCallbackRequest
    ): Response<AuthResponse>

    @POST("api/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<Unit>

    @POST("api/auth/refresh")
    suspend fun refreshToken(
        @Body request: TokenRefreshRequest
    ): Response<AuthResponse>

    @GET("api/users/me")
    suspend fun getMe(): Response<UserResponse>
}
