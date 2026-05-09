package com.nomadclub.cashchat.core.network

import com.google.gson.Gson
import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.data.model.AuthResponse
import com.nomadclub.cashchat.data.model.TokenRefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import android.util.Log

class TokenAuthenticator(
    private val tokenDataStore: TokenDataStore,
    private val baseUrl: String
) : Authenticator {

    // OkHttpClient는 스레드풀·커넥션풀을 공유하므로 인스턴스 하나만 유지
    private val refreshClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        // 갱신 엔드포인트 자체가 401 → 무한 루프 방지
        // google callback은 일회용 code라 재시도 불가
        if (path.contains("auth/refresh") ||
            path.contains("auth/guest") ||
            path.contains("auth/callback/google") ||
            responseCount(response) >= 2
        ) return null

        return synchronized(this) {
            val role = tokenDataStore.getRoleBlocking()
            when {
                role == "GUEST" -> refreshGuestToken(response.request)
                role == "MEMBER" || role == "ADMIN" -> refreshMemberToken(response.request)
                else -> null
            }
        }
    }

    private fun refreshGuestToken(originalRequest: Request): Request? {
        // 신규 설치 직후에도 deviceToken이 없는 경우를 방어하기 위해 getOrCreate 사용
        val deviceToken = tokenDataStore.getOrCreateDeviceTokenBlocking()
        return runBlocking {
            try {
                val req = Request.Builder()
                    .url("${baseUrl}api/auth/guest?deviceToken=$deviceToken")
                    .post(ByteArray(0).toRequestBody())
                    .build()
                val resp = refreshClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@runBlocking null
                    val authResponse = Gson().fromJson(body, AuthResponse::class.java)
                    tokenDataStore.saveAuthResponse(authResponse)
                    originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${authResponse.accessToken}")
                        .build()
                } else null
            } catch (e: Exception) {
                Log.e("TokenAuthenticator", "게스트 토큰 갱신 실패: ${e.message}", e)
                null
            }
        }
    }

    /** prior response 체인을 따라 실제 재시도 횟수를 반환 */
    private fun responseCount(response: Response): Int {
        var count = 1
        var current = response.priorResponse
        while (current != null) {
            count++
            current = current.priorResponse
        }
        return count
    }

    private fun refreshMemberToken(originalRequest: Request): Request? {
        val refreshToken = tokenDataStore.getRefreshTokenBlocking() ?: return null
        return runBlocking {
            try {
                val json = Gson().toJson(TokenRefreshRequest(refreshToken))
                val body = json.toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("${baseUrl}api/auth/refresh")
                    .post(body)
                    .build()
                val resp = refreshClient.newCall(req).execute()
                if (resp.isSuccessful) {
                    val respBody = resp.body?.string() ?: return@runBlocking null
                    val authResponse = Gson().fromJson(respBody, AuthResponse::class.java)
                    tokenDataStore.saveAuthResponse(authResponse)
                    originalRequest.newBuilder()
                        .header("Authorization", "Bearer ${authResponse.accessToken}")
                        .build()
                } else {
                    // Refresh Token 만료 → 저장된 토큰 삭제 → null 반환 시 AuthRepository가 재로그인 유도
                    tokenDataStore.clearTokens()
                    null
                }
            } catch (e: Exception) {
                Log.e("TokenAuthenticator", "멤버 토큰 갱신 실패: ${e.message}", e)
                null
            }
        }
    }
}
