# 육성형 AI 챗봇 FE 구현 계획 (CC-348)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** BE 연동가이드(CC-311) 기준으로 채팅(SSE)·밥(에너지)·진화·광고 보상을 KMM shared 데이터 레이어 + Android Compose UI로 구현한다.

**Architecture:** shared(commonMain)에 Ktor 기반 API 클라이언트·SSE 파서·Store(상태 홀더)를 구현하고, Android는 ViewModel이 shared Store를 구독해 화면을 그린다. 기존 auth Retrofit/TokenDataStore는 그대로 두고 `TokenProvider` 인터페이스로 연결한다.

**Tech Stack:** Kotlin 2.0.21 KMM, Ktor 2.3.12(OkHttp/Darwin), kotlinx.serialization, Koin, Jetpack Compose(M3), AdMob Rewarded(SSV)

**스펙:** `docs/superpowers/specs/2026-06-10-ai-chatbot-fe-design.md`

**전제/주의:**
- 모든 작업은 `apps/frontend/` 에서 수행. 빌드: `cd apps/frontend && ./gradlew ...`
- shared 테스트 실행: `./gradlew :shared:testDebugUnitTest`
- **BE 의존성(미해결)**: 포인트 잔액 조회 API가 백엔드에 없다(`UserResponse`에 points 없음). HUD 코인 칩은 `points: Long? = null`이면 숨기는 구조로 구현하고, BE에 잔액 API를 요청한다(별도 Jira 티켓).
- iOS에서 호출될 shared suspend 함수에는 반드시 `@Throws(Exception::class)` 부착.
- 커밋 메시지는 한국어, Conventional Commits.

---

## 파일 구조 (전체 맵)

```
apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/
  core/network/ApiException.kt          공통 에러 (code 기반)
  core/network/TokenProvider.kt         토큰 공급 인터페이스
  core/network/HttpClientFactory.kt     Ktor HttpClient 팩토리 (Auth 플러그인 포함)
  core/network/SseParser.kt             SSE 라인 파서 (순수 로직)
  chat/ChatApi.kt                       대화방 CRUD + stream(SSE)
  chat/model/ChatDtos.kt                Conversation/Message DTO
  chat/model/ChatItem.kt                UI용 메시지 모델 (sealed, 확장 포인트)
  chat/ChatStore.kt                     메시지 상태머신 (기존 mock 대체)
  energy/EnergyApi.kt                   GET /api/energy/me
  evolution/EvolutionApi.kt             상태 조회 + 시도
  evolution/EvolutionStore.kt           idempotencyKey 관리 포함
  ads/AdsApi.kt                         quota + issue-nonce
  ads/AdRewardStore.kt                  쿼터 + 적립 폴링
  hud/HudStore.kt                       level·points·energy 통합 HUD 상태
  di/SharedModule.kt                    shared Koin 모듈

apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/
  core/network/SseParserTest.kt
  core/network/ApiErrorTest.kt
  chat/ChatStoreTest.kt
  hud/HudStoreTest.kt
  ads/AdRewardStoreTest.kt

apps/frontend/app/src/main/java/com/nomadclub/cashchat/
  core/network/DataStoreTokenProvider.kt   TokenProvider Android 구현
  di/AppModule.kt                          (수정) shared 모듈 배선
  ads/RewardedAdManager.kt                 (수정) SSV nonce 추가
  feature/chat/ChatViewModel.kt            (전면 교체) shared Store 구독
  feature/chat/ChatScreen.kt               (전면 교체) 슬림 톱바 + 스트리밍 UI
  feature/chat/ConversationListScreen.kt   (신규)
  feature/chat/EnergyGateBottomSheet.kt    (신규)
  feature/chat/components/ChatComponents.kt (신규) 버블·칩·게이지
  feature/chat/evolution/EvolutionScreen.kt (신규) 차지&플래시 연출
  feature/main/MainScreen.kt               (수정) 라우트 추가
삭제: shared/.../chat/model/AdInfo.kt, feature/chat/models/ (mock 모델)
```

---

### Task 1: shared 테스트 인프라 + ApiException/에러 파싱

**Files:**
- Modify: `apps/frontend/gradle/libs.versions.toml`
- Modify: `apps/frontend/shared/build.gradle.kts`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/ApiException.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/ApiErrorTest.kt`

- [ ] **Step 1: 테스트 의존성 추가**

`libs.versions.toml`의 `[libraries]` 끝(`sentry-kmp` 아래)에 추가:

```toml
ktor-client-mock = { group = "io.ktor", name = "ktor-client-mock", version.ref = "ktor" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
```

`shared/build.gradle.kts`의 `sourceSets` 블록 안(`iosMain.dependencies` 아래)에 추가:

```kotlin
commonTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.ktor.client.mock)
    implementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 2: 실패하는 테스트 작성** — `ApiErrorTest.kt`

```kotlin
package com.nomadclub.cashchat.shared.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorTest {

    @Test
    fun `에러 본문을 ApiException으로 파싱한다`() {
        val exception = parseApiError(
            httpStatus = 409,
            body = """{ "code": "INSUFFICIENT_ENERGY", "message": "에너지가 부족합니다." }""",
        )
        assertEquals("INSUFFICIENT_ENERGY", exception.code)
        assertEquals(409, exception.httpStatus)
        assertEquals("에너지가 부족합니다.", exception.message)
    }

    @Test
    fun `본문이 JSON이 아니면 UNKNOWN 코드로 폴백한다`() {
        val exception = parseApiError(httpStatus = 500, body = "Internal Server Error")
        assertEquals("UNKNOWN", exception.code)
        assertEquals(500, exception.httpStatus)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.core.network.ApiErrorTest"`
Expected: FAIL (컴파일 에러 — `parseApiError` 미정의)

- [ ] **Step 4: 구현** — `ApiException.kt`

```kotlin
package com.nomadclub.cashchat.shared.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 백엔드 공통 에러 `{ code, message }`. 화면 분기는 HTTP status가 아닌 code로 한다. */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
) : Exception(message) {
    companion object {
        const val INSUFFICIENT_ENERGY = "INSUFFICIENT_ENERGY"
        const val INSUFFICIENT_POINTS = "INSUFFICIENT_POINTS"
        const val ALREADY_MAX_LEVEL = "ALREADY_MAX_LEVEL"
        const val CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND"
        const val UNKNOWN = "UNKNOWN"
    }
}

@Serializable
private data class ErrorBody(val code: String? = null, val message: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true }

fun parseApiError(httpStatus: Int, body: String): ApiException {
    val parsed = runCatching { errorJson.decodeFromString<ErrorBody>(body) }.getOrNull()
    return ApiException(
        code = parsed?.code ?: ApiException.UNKNOWN,
        message = parsed?.message ?: "요청에 실패했어요 ($httpStatus)",
        httpStatus = httpStatus,
    )
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.core.network.ApiErrorTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add apps/frontend/gradle/libs.versions.toml apps/frontend/shared/build.gradle.kts apps/frontend/shared/src
git commit -m "feat(shared): 공통 ApiException 및 에러 파싱 추가"
```

---

### Task 2: SSE 라인 파서

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/SseParser.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/SseParserTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성** — `SseParserTest.kt`

서버 포맷(BE 가이드): `event: message\ndata: 안녕\n\n`, 실패 시 `event: error\ndata: stream failed\n\n`.

```kotlin
package com.nomadclub.cashchat.shared.core.network

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {

    private fun parseAll(raw: String): List<SseEvent> {
        val parser = SseParser()
        return raw.lines().mapNotNull(parser::feed)
    }

    @Test
    fun `message 이벤트의 data를 토큰으로 반환한다`() {
        val events = parseAll("event: message\ndata: 안녕\n\nevent: message\ndata: 하세요\n\n")
        assertEquals(listOf(SseEvent("message", "안녕"), SseEvent("message", "하세요")), events)
    }

    @Test
    fun `error 이벤트를 그대로 반환한다`() {
        val events = parseAll("event: error\ndata: stream failed\n\n")
        assertEquals(listOf(SseEvent("error", "stream failed")), events)
    }

    @Test
    fun `event 라인이 없으면 기본 message 타입으로 처리한다`() {
        val events = parseAll("data: hello\n\n")
        assertEquals(listOf(SseEvent("message", "hello")), events)
    }

    @Test
    fun `data에 콜론 공백 없이 와도 파싱한다`() {
        val events = parseAll("event:message\ndata:hi\n\n")
        assertEquals(listOf(SseEvent("message", "hi")), events)
    }

    @Test
    fun `빈 data는 빈 문자열 토큰으로 유지한다`() {
        val events = parseAll("event: message\ndata: \n\n")
        assertEquals(listOf(SseEvent("message", "")), events)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.core.network.SseParserTest"`
Expected: FAIL (컴파일 에러 — `SseParser` 미정의)

- [ ] **Step 3: 구현** — `SseParser.kt`

```kotlin
package com.nomadclub.cashchat.shared.core.network

/** 파싱된 SSE 이벤트 한 건. [event]는 "message" 또는 "error". */
data class SseEvent(val event: String, val data: String)

/**
 * SSE 텍스트 스트림을 라인 단위로 받아 이벤트를 조립한다.
 * 빈 줄이 이벤트 경계. event 라인이 없으면 SSE 표준대로 "message"로 간주.
 */
class SseParser {
    private var eventType: String? = null
    private var data: String? = null

    /** 라인 1개를 소비하고, 이벤트가 완성되면 반환(아니면 null). */
    fun feed(line: String): SseEvent? {
        return when {
            line.startsWith("event:") -> {
                eventType = line.removePrefix("event:").trim()
                null
            }
            line.startsWith("data:") -> {
                data = line.removePrefix("data:").removePrefix(" ")
                null
            }
            line.isBlank() -> {
                val completed = data?.let { SseEvent(eventType ?: "message", it) }
                eventType = null
                data = null
                completed
            }
            else -> null // comment 등 무시
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.core.network.SseParserTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src
git commit -m "feat(shared): SSE 라인 파서 구현"
```

---

### Task 3: TokenProvider + HttpClient 팩토리

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/TokenProvider.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/HttpClientFactory.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/HttpClientFactoryTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성** — `HttpClientFactoryTest.kt`

```kotlin
package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
private data class Pong(val ok: Boolean)

private class FakeTokenProvider(var token: String? = "abc") : TokenProvider {
    var refreshCalled = 0
    override suspend fun accessToken(): String? = token
    override suspend fun refresh(): Boolean { refreshCalled++; token = "refreshed"; return true }
}

class HttpClientFactoryTest {

    @Test
    fun `Authorization 헤더에 Bearer 토큰을 붙인다`() = runTest {
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = createCashChatHttpClient("https://api.test", FakeTokenProvider(), engine)
        val pong: Pong = client.get("https://api.test/ping").body()
        assertEquals(true, pong.ok)
        assertEquals("Bearer abc", seenAuth)
    }

    @Test
    fun `에러 상태코드는 ApiException으로 변환된다`() = runTest {
        val engine = MockEngine {
            respond(
                """{"code":"INSUFFICIENT_ENERGY","message":"에너지 부족"}""",
                HttpStatusCode.Conflict,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = createCashChatHttpClient("https://api.test", FakeTokenProvider(), engine)
        val exception = assertFailsWith<ApiException> { client.get("https://api.test/energy").body<Pong>() }
        assertEquals("INSUFFICIENT_ENERGY", exception.code)
        assertEquals(409, exception.httpStatus)
    }

    @Test
    fun `401이면 refresh 후 1회 재시도한다`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            if (request.headers[HttpHeaders.Authorization] == "Bearer refreshed") {
                respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                respond("""{"code":"UNAUTHORIZED","message":"x"}""", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val provider = FakeTokenProvider()
        val client = createCashChatHttpClient("https://api.test", provider, engine)
        val pong: Pong = client.get("https://api.test/me").body()
        assertEquals(true, pong.ok)
        assertEquals(1, provider.refreshCalled)
        assertEquals(2, calls)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.core.network.HttpClientFactoryTest"`
Expected: FAIL (컴파일 에러)

- [ ] **Step 3: 구현**

`TokenProvider.kt`:

```kotlin
package com.nomadclub.cashchat.shared.core.network

/** 플랫폼(Android/iOS)이 구현하는 토큰 공급자. shared는 저장 방식을 모른다. */
interface TokenProvider {
    suspend fun accessToken(): String?
    /** 401 수신 시 호출. 갱신 성공 여부 반환. 실패 시 호출측은 로그아웃 플로우로 보낸다. */
    suspend fun refresh(): Boolean
}
```

`HttpClientFactory.kt`:

```kotlin
package com.nomadclub.cashchat.shared.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * CashChat 공통 HttpClient.
 * - Bearer 토큰 자동 첨부, 401 시 1회 refresh 후 재시도
 * - 4xx/5xx 응답을 [ApiException]으로 변환 (SSE 스트림 요청은 호출부에서 별도 처리)
 * - [engine]은 테스트(MockEngine)용. null이면 플랫폼 기본 엔진.
 */
fun createCashChatHttpClient(
    baseUrl: String,
    tokenProvider: TokenProvider,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }
    val client = if (engine != null) HttpClient(engine, config) else HttpClient(config)

    client.plugin(HttpSend).intercept { request ->
        tokenProvider.accessToken()?.let { request.bearerAuth(it) }
        var call = execute(request)
        if (call.response.status == HttpStatusCode.Unauthorized && tokenProvider.refresh()) {
            request.headers.remove(io.ktor.http.HttpHeaders.Authorization)
            tokenProvider.accessToken()?.let { request.bearerAuth(it) }
            call = execute(request)
        }
        val status = call.response.status
        if (status.value >= 400) {
            throw parseApiError(status.value, call.response.bodyAsText())
        }
        call
    }
    return client
}
```

> `bearerAuth`는 기존 Authorization 헤더를 덮어쓰지 않고 추가하므로, 재시도 전 반드시 `headers.remove(HttpHeaders.Authorization)` 후 새 토큰을 붙인다 — 위 스니펫에 반영되어 있다. (테스트가 검증한다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.core.network.HttpClientFactoryTest"`
Expected: PASS (3 tests). 401 재시도 테스트가 실패하면 위 노트의 헤더 remove 처리를 확인.

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src
git commit -m "feat(shared): TokenProvider 및 Ktor HttpClient 팩토리 구현"
```

---

### Task 4: API DTO + Api 클래스 (chat·energy·evolution·ads·user)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/model/ChatDtos.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatApi.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/energy/EnergyApi.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionApi.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdsApi.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatApiTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성** — `ChatApiTest.kt`

```kotlin
package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private object NoAuth : TokenProvider {
    override suspend fun accessToken(): String? = null
    override suspend fun refresh(): Boolean = false
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

class ChatApiTest {

    @Test
    fun `대화방을 생성한다`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/v1/chat/conversations", request.url.encodedPath)
            respond(
                """{"conversationId":7,"title":"영어 공부 팁","createdAt":"2026-06-10T00:00:00Z","updatedAt":"2026-06-10T00:00:00Z"}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val conversation = api.createConversation("영어 공부 팁")
        assertEquals(7L, conversation.conversationId)
    }

    @Test
    fun `스트림은 message 토큰을 Flow로 흘린다`() = runTest {
        val sse = "event: message\ndata: 안녕\n\nevent: message\ndata: 하세요\n\n"
        val engine = MockEngine {
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val events = api.streamMessage(conversationId = 7, message = "hi").toList()
        assertEquals(listOf<ChatStreamEvent>(ChatStreamEvent.Token("안녕"), ChatStreamEvent.Token("하세요"), ChatStreamEvent.Done), events)
    }

    @Test
    fun `스트림 시작 전 409는 ApiException으로 던진다`() = runTest {
        val engine = MockEngine {
            respond("""{"code":"INSUFFICIENT_ENERGY","message":"x"}""", HttpStatusCode.Conflict, jsonHeaders)
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val exception = assertFailsWith<ApiException> { api.streamMessage(7, "hi").toList() }
        assertEquals(ApiException.INSUFFICIENT_ENERGY, exception.code)
    }

    @Test
    fun `error 이벤트는 StreamError로 매핑된다`() = runTest {
        val sse = "event: message\ndata: 부분\n\nevent: error\ndata: stream failed\n\n"
        val engine = MockEngine {
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        }
        val api = ChatApi(createCashChatHttpClient("https://api.test", NoAuth, engine), "https://api.test")
        val events = api.streamMessage(7, "hi").toList()
        assertTrue(events.contains(ChatStreamEvent.StreamError("stream failed")))
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.chat.ChatApiTest"`
Expected: FAIL (컴파일 에러)

- [ ] **Step 3: DTO 구현** — `ChatDtos.kt`

백엔드 응답 필드명과 1:1 일치(타임스탬프는 ISO-8601 String으로 수신).

```kotlin
package com.nomadclub.cashchat.shared.chat.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationDto(
    val conversationId: Long,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ConversationSummaryDto(
    val conversationId: Long,
    val title: String,
    val lastMessage: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ChatMessageDto(
    val messageId: Long,
    val role: String,      // "USER" | "ASSISTANT"
    val content: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class CreateConversationRequest(val title: String? = null)

@Serializable
data class ChatStreamRequest(val conversationId: Long, val message: String)
```

- [ ] **Step 4: ChatApi 구현** — `ChatApi.kt`

```kotlin
package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.ChatMessageDto
import com.nomadclub.cashchat.shared.chat.model.ChatStreamRequest
import com.nomadclub.cashchat.shared.chat.model.ConversationDto
import com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto
import com.nomadclub.cashchat.shared.chat.model.CreateConversationRequest
import com.nomadclub.cashchat.shared.core.network.SseParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** SSE 스트림 이벤트. 시작 전 HTTP 에러는 Flow가 아니라 ApiException으로 전파된다. */
sealed interface ChatStreamEvent {
    data class Token(val text: String) : ChatStreamEvent
    data class StreamError(val message: String) : ChatStreamEvent
    data object Done : ChatStreamEvent
}

class ChatApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    @Throws(Exception::class)
    suspend fun createConversation(title: String? = null): ConversationDto =
        client.post("$baseUrl/api/v1/chat/conversations") {
            contentType(ContentType.Application.Json)
            setBody(CreateConversationRequest(title))
        }.body()

    @Throws(Exception::class)
    suspend fun listConversations(): List<ConversationSummaryDto> =
        client.get("$baseUrl/api/v1/chat/conversations").body()

    @Throws(Exception::class)
    suspend fun getMessages(conversationId: Long): List<ChatMessageDto> =
        client.get("$baseUrl/api/v1/chat/conversations/$conversationId/messages").body()

    /** SSE 스트림. message 토큰 → Token, error 이벤트 → StreamError, 정상 종료 → Done. */
    fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent> = flow {
        client.preparePost("$baseUrl/api/v1/chat/stream") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Text.EventStream)
            setBody(ChatStreamRequest(conversationId, message))
        }.execute { response ->
            val channel = response.bodyAsChannel()
            val parser = SseParser()
            var errored = false
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                val event = parser.feed(line) ?: continue
                when (event.event) {
                    "error" -> { errored = true; emit(ChatStreamEvent.StreamError(event.data)) }
                    else -> emit(ChatStreamEvent.Token(event.data))
                }
            }
            if (!errored) emit(ChatStreamEvent.Done)
        }
    }
}
```

- [ ] **Step 5: 나머지 Api 구현** (단순 GET/POST이므로 테스트는 ChatApiTest 패턴으로 충분 — DTO 직렬화는 통합에서 검증)

`energy/EnergyApi.kt`:

```kotlin
package com.nomadclub.cashchat.shared.energy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class EnergyDto(val energy: Int, val maxEnergy: Int)

class EnergyApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getMyEnergy(): EnergyDto = client.get("$baseUrl/api/energy/me").body()
}
```

`evolution/EvolutionApi.kt`:

```kotlin
package com.nomadclub.cashchat.shared.evolution

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class EvolutionStateDto(
    val level: Int,
    val isMaxLevel: Boolean,
    val nextAttemptCost: Long? = null,
    val nextSuccessRate: Double? = null,
)

@Serializable
data class EvolutionAttemptDto(
    val success: Boolean,
    val fromLevel: Int,
    val resultLevel: Int,
    val cost: Long,
)

@Serializable
private data class EvolutionAttemptRequest(val idempotencyKey: String)

class EvolutionApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getState(): EvolutionStateDto = client.get("$baseUrl/api/evolution/me").body()

    /** 버튼 1탭 = 새 idempotencyKey. 같은 탭의 네트워크 재시도는 같은 키 재사용(서버 멱등). */
    @Throws(Exception::class)
    suspend fun attempt(idempotencyKey: String): EvolutionAttemptDto =
        client.post("$baseUrl/api/evolution/attempt") {
            contentType(ContentType.Application.Json)
            setBody(EvolutionAttemptRequest(idempotencyKey))
        }.body()
}
```

`ads/AdsApi.kt`:

```kotlin
package com.nomadclub.cashchat.shared.ads

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@Serializable
data class AdRewardQuotaDto(
    val usedToday: Int,
    val dailyLimit: Int,
    val remaining: Int,
    val resetAtKst: String,
)

@Serializable
data class IssueNonceDto(val nonce: String, val expiresAt: String)

class AdsApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getQuota(): AdRewardQuotaDto = client.get("$baseUrl/api/ads/reward/quota").body()

    @Throws(Exception::class)
    suspend fun issueNonce(): IssueNonceDto = client.post("$baseUrl/api/ads/reward/issue-nonce").body()
}
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.chat.ChatApiTest"`
Expected: PASS (4 tests)

- [ ] **Step 7: 커밋**

```bash
git add apps/frontend/shared/src
git commit -m "feat(shared): 채팅 SSE·에너지·진화·광고 API 클라이언트 구현"
```

---

### Task 5: ChatItem 모델 + ChatStore 상태머신 (mock 대체)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/model/ChatItem.kt`
- Rewrite: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatStore.kt` (기존 mock 전면 교체)
- Delete: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/model/AdInfo.kt`, 기존 `chat/model/ChatMessage.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatStoreTest.kt`

- [ ] **Step 1: ChatItem 모델 작성** — `ChatItem.kt`

```kotlin
package com.nomadclub.cashchat.shared.chat.model

/** 채팅 화면에 그려지는 항목. 쿠팡 카드·Ad Gate 등은 추후 타입 추가로 확장(스펙 §1.1). */
sealed interface ChatItem {
    val id: String

    enum class SendStatus { PENDING, CONFIRMED, BLOCKED }

    data class UserMessage(
        override val id: String,
        val text: String,
        val status: SendStatus,
    ) : ChatItem

    data class AssistantMessage(
        override val id: String,
        val text: String,
        val isStreaming: Boolean,
        val isError: Boolean = false,
    ) : ChatItem
}
```

- [ ] **Step 2: 실패하는 테스트 작성** — `ChatStoreTest.kt`

```kotlin
package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.core.network.ApiException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** ChatApi와 같은 시그니처의 가짜. ChatStore는 이 인터페이스에 의존한다. */
private class FakeChatGateway : ChatGateway {
    var streamResult: (suspend () -> Flow<ChatStreamEvent>)? = null
    var createdConversations = 0

    override suspend fun createConversation(title: String?) =
        com.nomadclub.cashchat.shared.chat.model.ConversationDto(
            conversationId = (++createdConversations).toLong(), title = title ?: "새 대화",
            createdAt = "2026-06-10T00:00:00Z", updatedAt = "2026-06-10T00:00:00Z",
        )

    override suspend fun listConversations() = emptyList<com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto>()
    override suspend fun getMessages(conversationId: Long) = emptyList<com.nomadclub.cashchat.shared.chat.model.ChatMessageDto>()
    override fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent> =
        flow { streamResult!!.invoke().collect { emit(it) } }
}

class ChatStoreTest {

    @Test
    fun `전송 성공 - pending이 confirmed되고 assistant 텍스트가 누적된다`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = {
            flow {
                emit(ChatStreamEvent.Token("안녕"))
                emit(ChatStreamEvent.Token("하세요"))
                emit(ChatStreamEvent.Done)
            }
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()

        val items = store.items.value
        val user = items.filterIsInstance<ChatItem.UserMessage>().last()
        val assistant = items.filterIsInstance<ChatItem.AssistantMessage>().last()
        assertEquals(ChatItem.SendStatus.CONFIRMED, user.status)
        assertEquals("안녕하세요", assistant.text)
        assertEquals(false, assistant.isStreaming)
    }

    @Test
    fun `대화방 없으면 첫 전송 전에 자동 생성한다`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = { flow { emit(ChatStreamEvent.Done) } }
        val store = ChatStore(gateway, this)
        store.sendMessage("처음 인사")
        testScheduler.advanceUntilIdle()
        assertEquals(1, gateway.createdConversations)
    }

    @Test
    fun `에너지 부족 - pending 유지 + 게이트 이벤트 발행, 확정 저장 안 함`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = {
            throw ApiException(ApiException.INSUFFICIENT_ENERGY, "에너지 부족", 409)
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()

        val user = store.items.value.filterIsInstance<ChatItem.UserMessage>().last()
        assertEquals(ChatItem.SendStatus.BLOCKED, user.status)
        assertEquals(true, store.energyGateVisible.value)
        assertTrue(store.items.value.filterIsInstance<ChatItem.AssistantMessage>().isEmpty())
    }

    @Test
    fun `retryBlocked - 막혔던 메시지를 같은 대화방으로 재전송한다`() = runTest {
        val gateway = FakeChatGateway()
        var attempts = 0
        gateway.streamResult = {
            attempts++
            if (attempts == 1) throw ApiException(ApiException.INSUFFICIENT_ENERGY, "x", 409)
            flow { emit(ChatStreamEvent.Token("응답")); emit(ChatStreamEvent.Done) }
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()
        store.retryBlocked()
        testScheduler.advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(1, gateway.createdConversations) // 대화방 재사용
        val user = store.items.value.filterIsInstance<ChatItem.UserMessage>().last()
        assertEquals(ChatItem.SendStatus.CONFIRMED, user.status)
    }

    @Test
    fun `스트림 도중 error 이벤트 - 부분 텍스트 유지 + isError 표시`() = runTest {
        val gateway = FakeChatGateway()
        gateway.streamResult = {
            flow { emit(ChatStreamEvent.Token("부분")); emit(ChatStreamEvent.StreamError("stream failed")) }
        }
        val store = ChatStore(gateway, this)
        store.sendMessage("hi")
        testScheduler.advanceUntilIdle()

        val assistant = store.items.value.filterIsInstance<ChatItem.AssistantMessage>().last()
        assertEquals("부분", assistant.text)
        assertIs<ChatItem.AssistantMessage>(assistant)
        assertEquals(true, assistant.isError)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.chat.ChatStoreTest"`
Expected: FAIL (컴파일 에러 — `ChatGateway`, 새 `ChatStore` 미정의)

- [ ] **Step 4: ChatStore 구현** (기존 파일 전면 교체, `AdInfo.kt`·구 `ChatMessage.kt` 삭제)

```kotlin
package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.chat.model.ChatMessageDto
import com.nomadclub.cashchat.shared.chat.model.ConversationDto
import com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ChatApi 추상화 — 테스트 대체용. 프로덕션 구현은 ChatApi 위임. */
interface ChatGateway {
    suspend fun createConversation(title: String? = null): ConversationDto
    suspend fun listConversations(): List<ConversationSummaryDto>
    suspend fun getMessages(conversationId: Long): List<ChatMessageDto>
    fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent>
}

class ApiChatGateway(private val api: ChatApi) : ChatGateway {
    override suspend fun createConversation(title: String?) = api.createConversation(title)
    override suspend fun listConversations() = api.listConversations()
    override suspend fun getMessages(conversationId: Long) = api.getMessages(conversationId)
    override fun streamMessage(conversationId: Long, message: String) = api.streamMessage(conversationId, message)
}

/**
 * 채팅 메시지 상태머신 (스펙 §3.1).
 * pending → confirmed(스트림 시작) / blocked(409 에너지 부족 → 게이트).
 */
class ChatStore(
    private val gateway: ChatGateway,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<ChatItem>>(emptyList())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _energyGateVisible = MutableStateFlow(false)
    val energyGateVisible: StateFlow<Boolean> = _energyGateVisible.asStateFlow()

    /** 스트림 정상 종료 시 1 증가 — HUD가 energy 재조회 트리거로 사용. */
    private val _streamCompletedCount = MutableStateFlow(0)
    val streamCompletedCount: StateFlow<Int> = _streamCompletedCount.asStateFlow()

    var conversationId: Long? = null
        private set

    private var blockedMessageId: String? = null

    @Throws(Exception::class)
    suspend fun openConversation(id: Long) {
        conversationId = id
        val history = gateway.getMessages(id).map { dto ->
            if (dto.role == "USER") {
                ChatItem.UserMessage(id = "m${dto.messageId}", text = dto.content, status = ChatItem.SendStatus.CONFIRMED)
            } else {
                ChatItem.AssistantMessage(id = "m${dto.messageId}", text = dto.content, isStreaming = false)
            }
        }
        _items.value = history
        blockedMessageId = null
        _energyGateVisible.value = false
    }

    fun startNewConversation() {
        conversationId = null
        _items.value = emptyList()
        blockedMessageId = null
        _energyGateVisible.value = false
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return
        val messageId = "u${currentTimeMillis()}"
        _items.update { it + ChatItem.UserMessage(messageId, trimmed, ChatItem.SendStatus.PENDING) }
        scope.launch { stream(messageId, trimmed) }
    }

    /** 게이트에서 충전 완료 후 호출 — 막힌 메시지를 같은 대화방으로 재전송. */
    fun retryBlocked() {
        val id = blockedMessageId ?: return
        val message = _items.value.filterIsInstance<ChatItem.UserMessage>().firstOrNull { it.id == id } ?: return
        blockedMessageId = null
        _energyGateVisible.value = false
        updateUser(id) { it.copy(status = ChatItem.SendStatus.PENDING) }
        scope.launch { stream(id, message.text) }
    }

    fun dismissEnergyGate() { _energyGateVisible.value = false }

    /** 스트림 단절 후 재시도 — 마지막 user 메시지를 재전송. */
    fun retryLastMessage() {
        val last = _items.value.filterIsInstance<ChatItem.UserMessage>().lastOrNull() ?: return
        _items.update { items -> items.filterNot { it is ChatItem.AssistantMessage && it.isError } }
        scope.launch { stream(last.id, last.text) }
    }

    private suspend fun stream(messageId: String, text: String) {
        _isStreaming.value = true
        // catch 블록에서 기존 스트리밍 메시지를 마감할 수 있도록 try 밖에 선언
        val assistantId = "a${currentTimeMillis()}"
        var assistantAdded = false
        try {
            val convId = conversationId ?: gateway.createConversation(text.take(20)).conversationId.also { conversationId = it }
            var errored = false
            gateway.streamMessage(convId, text).collect { event ->
                when (event) {
                    is ChatStreamEvent.Token -> {
                        if (!assistantAdded) {
                            updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                            _items.update { it + ChatItem.AssistantMessage(assistantId, event.text, isStreaming = true) }
                            assistantAdded = true
                        } else {
                            updateAssistant(assistantId) { it.copy(text = it.text + event.text) }
                        }
                    }
                    is ChatStreamEvent.StreamError -> {
                        errored = true
                        updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                        if (assistantAdded) {
                            updateAssistant(assistantId) { it.copy(isStreaming = false, isError = true) }
                        } else {
                            _items.update { it + ChatItem.AssistantMessage(assistantId, "", isStreaming = false, isError = true) }
                        }
                    }
                    ChatStreamEvent.Done -> {
                        updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                        if (assistantAdded) updateAssistant(assistantId) { it.copy(isStreaming = false) }
                        _streamCompletedCount.update { it + 1 }
                    }
                }
            }
            if (errored) Unit // 부분 텍스트 유지, 재시도는 retryLastMessage()
        } catch (e: ApiException) {
            if (e.code == ApiException.INSUFFICIENT_ENERGY) {
                blockedMessageId = messageId
                updateUser(messageId) { it.copy(status = ChatItem.SendStatus.BLOCKED) }
                _energyGateVisible.value = true
            } else if (e.code == ApiException.CONVERSATION_NOT_FOUND) {
                conversationId = null
                updateUser(messageId) { it.copy(status = ChatItem.SendStatus.BLOCKED) }
            } else {
                updateUser(messageId) { it.copy(status = ChatItem.SendStatus.BLOCKED) }
            }
        } catch (e: Exception) {
            // 네트워크 단절 등 — 부분 응답 유지 + 기존 메시지를 에러 상태로 마감 (중복 추가 금지)
            if (assistantAdded) {
                updateAssistant(assistantId) { it.copy(isStreaming = false, isError = true) }
            } else {
                _items.update { it + ChatItem.AssistantMessage(assistantId, "", isStreaming = false, isError = true) }
            }
        } finally {
            _isStreaming.value = false
        }
    }

    private fun updateUser(id: String, transform: (ChatItem.UserMessage) -> ChatItem.UserMessage) {
        _items.update { items -> items.map { if (it is ChatItem.UserMessage && it.id == id) transform(it) else it } }
    }

    private fun updateAssistant(id: String, transform: (ChatItem.AssistantMessage) -> ChatItem.AssistantMessage) {
        _items.update { items -> items.map { if (it is ChatItem.AssistantMessage && it.id == id) transform(it) else it } }
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.chat.ChatStoreTest"`
Expected: PASS (5 tests)

> 기존 mock(`AdInfo.kt`, 구 `chat/model/ChatMessage.kt`) 삭제로 androidApp 컴파일이 깨질 수 있다 — Task 8에서 Android 쪽을 교체하므로, 이 시점에는 `:shared:testDebugUnitTest`만 통과하면 된다.

- [ ] **Step 6: 커밋**

```bash
git add -A apps/frontend/shared/src
git commit -m "feat(shared): ChatStore 상태머신 구현 및 mock 제거"
```

---

### Task 6: HudStore + EvolutionStore + AdRewardStore

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/hud/HudStore.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/evolution/EvolutionStore.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdRewardStore.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/ads/AdRewardStoreTest.kt`

- [ ] **Step 1: 실패하는 테스트 작성** — `AdRewardStoreTest.kt` (폴링이 핵심 로직)

```kotlin
package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.energy.EnergyDto
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AdRewardStoreTest {

    @Test
    fun `폴링 - 에너지가 증가하면 즉시 성공으로 끝난다`() = runTest {
        var calls = 0
        val store = AdRewardStore(
            fetchQuota = { AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            fetchEnergy = {
                calls++
                if (calls >= 2) EnergyDto(10, 50) else EnergyDto(0, 50)
            },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val rewarded = store.awaitRewardApplied(baselineEnergy = 0)
        assertEquals(true, rewarded)
        assertEquals(2, calls)
    }

    @Test
    fun `폴링 - 백오프 전 횟수(6회) 모두 변동 없으면 false를 반환한다`() = runTest {
        var calls = 0
        val store = AdRewardStore(
            fetchQuota = { AdRewardQuotaDto(3, 10, 7, "2026-06-11T00:00:00+09:00") },
            issueNonce = { IssueNonceDto("n", "x") },
            fetchEnergy = { calls++; EnergyDto(0, 50) },
            scope = this,
            pollDelaysMillis = List(5) { 0L },
        )
        val rewarded = store.awaitRewardApplied(baselineEnergy = 0)
        assertEquals(false, rewarded)
        assertEquals(6, calls)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.nomadclub.cashchat.shared.ads.AdRewardStoreTest"`
Expected: FAIL (컴파일 에러)

- [ ] **Step 3: AdRewardStore 구현**

```kotlin
package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.energy.EnergyDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 광고 보상 플로우 (스펙 §3.2):
 * quota 확인 → nonce 발급 → (UI가 AdMob 표시) → 적립 폴링.
 * AdMob SSV 콜백은 10초 이상 지연이 흔하므로 지수 백오프(2·3·5·8·12초, 총 30초)로 폴링한다.
 * 적립은 서버 재조회 결과로만 반영한다 — 로컬 가산 금지.
 */
class AdRewardStore(
    private val fetchQuota: suspend () -> AdRewardQuotaDto,
    private val issueNonce: suspend () -> IssueNonceDto,
    private val fetchEnergy: suspend () -> EnergyDto,
    private val scope: CoroutineScope,
    private val pollDelaysMillis: List<Long> = listOf(2_000, 3_000, 5_000, 8_000, 12_000),
) {
    private val _quota = MutableStateFlow<AdRewardQuotaDto?>(null)
    val quota: StateFlow<AdRewardQuotaDto?> = _quota.asStateFlow()

    @Throws(Exception::class)
    suspend fun refreshQuota(): AdRewardQuotaDto = fetchQuota().also { _quota.value = it }

    @Throws(Exception::class)
    suspend fun requestNonce(): String = issueNonce().nonce

    /**
     * 광고 닫힌 뒤 호출. baseline 대비 에너지 증가가 관측되면 true.
     * 즉시 1회 + 백오프 간격마다 1회(총 6회) 조회, 끝까지 변동 없으면 false → UI는 "보상 확인 중" + 수동 새로고침 안내.
     */
    @Throws(Exception::class)
    suspend fun awaitRewardApplied(baselineEnergy: Int): Boolean {
        repeat(pollDelaysMillis.size + 1) { attempt ->
            if (attempt > 0) delay(pollDelaysMillis[attempt - 1])
            val energy = fetchEnergy()
            if (energy.energy > baselineEnergy) return true
        }
        return false
    }
}
```

> 테스트 기준은 "호출 횟수": 즉시 1회 + 백오프 횟수만큼 `fetchEnergy` 호출(기본 6회). 지연은 루프 시작 시점에만 1회 적용 — 이중 delay 금지.

- [ ] **Step 4: HudStore 구현** (단순 조합 — 테스트는 통합에서)

```kotlin
package com.nomadclub.cashchat.shared.hud

import com.nomadclub.cashchat.shared.energy.EnergyApi
import com.nomadclub.cashchat.shared.evolution.EvolutionApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 채팅 톱바(HUD) 상태 (스펙 §2.4).
 * points는 BE에 잔액 조회 API가 없어 현재 null 고정 — UI는 null이면 코인 칩을 숨긴다.
 * TODO(BE): 포인트 잔액 API 추가되면 연결.
 */
data class HudState(
    val level: Int = 1,
    val isMaxLevel: Boolean = false,
    val energy: Int = 0,
    val maxEnergy: Int = 0,
    val points: Long? = null,
    val isLoaded: Boolean = false,
)

class HudStore(
    private val energyApi: EnergyApi,
    private val evolutionApi: EvolutionApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HudState())
    val state: StateFlow<HudState> = _state.asStateFlow()

    fun refresh() {
        scope.launch { runCatching { refreshNow() } }
    }

    @Throws(Exception::class)
    suspend fun refreshNow() = coroutineScope {
        val energyDeferred = async { energyApi.getMyEnergy() }
        val evolutionDeferred = async { evolutionApi.getState() }
        val energy = energyDeferred.await()
        val evolution = evolutionDeferred.await()
        _state.value = HudState(
            level = evolution.level,
            isMaxLevel = evolution.isMaxLevel,
            energy = energy.energy,
            maxEnergy = energy.maxEnergy,
            points = null,
            isLoaded = true,
        )
    }

    @Throws(Exception::class)
    suspend fun refreshEnergyOnly() {
        val energy = energyApi.getMyEnergy()
        _state.value = _state.value.copy(energy = energy.energy, maxEnergy = energy.maxEnergy, isLoaded = true)
    }
}
```

- [ ] **Step 5: EvolutionStore 구현**

```kotlin
package com.nomadclub.cashchat.shared.evolution

import com.nomadclub.cashchat.shared.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** 진화 상태 + 시도 (스펙 §3.3). 버튼 1탭 = 새 idempotencyKey, 재시도는 같은 키. */
class EvolutionStore(private val api: EvolutionApi) {

    private val _state = MutableStateFlow<EvolutionStateDto?>(null)
    val state: StateFlow<EvolutionStateDto?> = _state.asStateFlow()

    private var currentAttemptKey: String? = null

    @Throws(Exception::class)
    suspend fun refresh(): EvolutionStateDto = api.getState().also { _state.value = it }

    /** 새 시도 시작 — 새 idempotencyKey 발급 후 호출. */
    @Throws(Exception::class)
    suspend fun attempt(): EvolutionAttemptDto {
        val key = newUuidLike().also { currentAttemptKey = it }
        return api.attempt(key)
    }

    /** 직전 시도의 네트워크 재시도 — 같은 키 재사용(서버 멱등 보장). */
    @Throws(Exception::class)
    suspend fun retryLastAttempt(): EvolutionAttemptDto {
        val key = currentAttemptKey ?: return attempt()
        return api.attempt(key)
    }

    // commonMain에는 UUID API가 없어 시간+난수 조합으로 충분한 유일성 확보(서버 max 255자)
    private fun newUuidLike(): String =
        "${currentTimeMillis()}-${Random.nextLong().toULong().toString(16)}-${Random.nextLong().toULong().toString(16)}"
}
```

- [ ] **Step 6: 테스트 통과 확인 + 전체 shared 테스트**

Run: `./gradlew :shared:testDebugUnitTest`
Expected: PASS (Task 1~6 전체)

- [ ] **Step 7: 커밋**

```bash
git add apps/frontend/shared/src
git commit -m "feat(shared): HUD·진화·광고 보상 Store 구현"
```

---

### Task 7: shared Koin 모듈 + Android 배선 (TokenProvider 구현)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/core/network/DataStoreTokenProvider.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`

- [ ] **Step 1: SharedModule 작성**

```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.ads.AdsApi
import com.nomadclub.cashchat.shared.chat.ApiChatGateway
import com.nomadclub.cashchat.shared.chat.ChatApi
import com.nomadclub.cashchat.shared.chat.ChatGateway
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.core.network.TokenProvider
import com.nomadclub.cashchat.shared.core.network.createCashChatHttpClient
import com.nomadclub.cashchat.shared.energy.EnergyApi
import com.nomadclub.cashchat.shared.evolution.EvolutionApi
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module

/**
 * shared 데이터 레이어 Koin 모듈.
 * 사용처(Android/iOS)는 baseUrl과 TokenProvider 구현을 먼저 등록해야 한다.
 */
fun sharedDataModule(baseUrl: String) = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createCashChatHttpClient(baseUrl, get<TokenProvider>()) }

    single { ChatApi(get(), baseUrl) }
    single { EnergyApi(get(), baseUrl) }
    single { EvolutionApi(get(), baseUrl) }
    single { AdsApi(get(), baseUrl) }

    single<ChatGateway> { ApiChatGateway(get()) }
    single { ChatStore(get(), get()) }
    single { HudStore(get(), get(), get()) }
    single { EvolutionStore(get()) }
    single {
        val adsApi = get<AdsApi>()
        val energyApi = get<EnergyApi>()
        AdRewardStore(
            fetchQuota = { adsApi.getQuota() },
            issueNonce = { adsApi.issueNonce() },
            fetchEnergy = { energyApi.getMyEnergy() },
            scope = get(),
        )
    }
}
```

- [ ] **Step 2: Android TokenProvider 구현** — `DataStoreTokenProvider.kt`

```kotlin
package com.nomadclub.cashchat.core.network

import com.nomadclub.cashchat.core.data.TokenDataStore
import com.nomadclub.cashchat.data.repository.AuthRepository
import com.nomadclub.cashchat.shared.core.network.TokenProvider

/**
 * 기존 TokenDataStore/AuthRepository를 shared TokenProvider로 노출.
 * refresh는 기존 Retrofit refresh 플로우를 재사용한다 — 정책 한 곳 유지.
 */
class DataStoreTokenProvider(
    private val tokenDataStore: TokenDataStore,
    private val authRepository: AuthRepository,
) : TokenProvider {
    override suspend fun accessToken(): String? = tokenDataStore.getAccessTokenBlocking()
    override suspend fun refresh(): Boolean =
        runCatching { authRepository.refreshTokens() }.getOrDefault(false)
}
```

> `AuthRepository`에 refresh 메서드가 없거나 시그니처가 다르면(파일 확인: `data/repository/AuthRepository.kt`) 기존 `TokenAuthenticator`가 쓰는 refresh 경로를 호출하는 `suspend fun refreshTokens(): Boolean`을 AuthRepository에 추가한다. 핵심: **refresh 로직을 복제하지 말고 한 곳을 호출**할 것.

- [ ] **Step 3: AppModule 배선** — `di/AppModule.kt` 수정

import 추가 후 `appModule` 마지막에 추가:

```kotlin
// shared 데이터 레이어 (CC-348)
single<TokenProvider> { DataStoreTokenProvider(get(), get()) }
```

그리고 Koin startKoin 위치(Application 클래스)에서 모듈 등록에 `sharedDataModule(BuildConfig.BASE_URL)` 추가:

```kotlin
modules(appModule, sharedDataModule(BuildConfig.BASE_URL))
```

(Application 클래스는 `app/src/main/java/com/nomadclub/cashchat/` 아래 `*App*.kt` — `grep -rn "startKoin" app/src/main`으로 찾는다.)

- [ ] **Step 4: 빌드 확인**

Run: `./gradlew :app:assembleDebug -x lint`
Expected: 이 시점엔 기존 ChatViewModel/ChatScreen이 삭제된 shared mock을 참조해 **컴파일 실패할 수 있음**. 실패하는 파일이 `feature/chat/*`뿐인지 확인하고 다음 Task에서 해결. `:shared:assembleDebug`는 성공해야 한다:

Run: `./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/shared/src apps/frontend/app/src
git commit -m "feat(di): shared 데이터 레이어 Koin 배선 및 TokenProvider 연결"
```

---

### Task 8: ChatViewModel + 채팅 화면 컴포넌트 교체

**Files:**
- Rewrite: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatViewModel.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/ChatComponents.kt`
- Rewrite: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt`
- Delete: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/models/` (mock 모델)
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt` (viewModel 등록)
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/main/MainScreen.kt`

- [ ] **Step 1: ChatViewModel 교체**

```kotlin
package com.nomadclub.cashchat.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    val chatStore: ChatStore,
    val hudStore: HudStore,
    val adRewardStore: AdRewardStore,
) : ViewModel() {

    /** 게이트 시트의 광고 보상 단계 표시용 */
    enum class RewardPhase { IDLE, SHOWING_AD, POLLING, FAILED }

    private val _rewardPhase = MutableStateFlow(RewardPhase.IDLE)
    val rewardPhase = _rewardPhase.asStateFlow()

    init {
        hudStore.refresh()
        viewModelScope.launch {
            chatStore.streamCompletedCount.collect { if (it > 0) runCatching { hudStore.refreshEnergyOnly() } }
        }
        viewModelScope.launch {
            chatStore.energyGateVisible.collect { visible ->
                if (visible) runCatching { adRewardStore.refreshQuota() }
            }
        }
    }

    fun send(text: String) = chatStore.sendMessage(text)

    fun openConversation(id: Long) {
        viewModelScope.launch { runCatching { chatStore.openConversation(id) } }
    }

    /** 게이트 CTA: nonce 발급 → 광고 표시 콜백 → 폴링 → 재전송 */
    fun startAdReward(showAd: suspend (nonce: String) -> Boolean) {
        viewModelScope.launch {
            _rewardPhase.value = RewardPhase.SHOWING_AD
            val baseline = hudStore.state.value.energy
            val result = runCatching {
                val nonce = adRewardStore.requestNonce()
                if (!showAd(nonce)) return@runCatching false
                _rewardPhase.value = RewardPhase.POLLING
                adRewardStore.awaitRewardApplied(baseline)
            }.getOrDefault(false)

            runCatching { hudStore.refreshEnergyOnly() }
            runCatching { adRewardStore.refreshQuota() }
            if (result) {
                _rewardPhase.value = RewardPhase.IDLE
                chatStore.retryBlocked()
            } else {
                _rewardPhase.value = RewardPhase.FAILED
            }
        }
    }

    fun dismissGate() {
        _rewardPhase.value = RewardPhase.IDLE
        chatStore.dismissEnergyGate()
    }
}
```

AppModule에 등록(기존 `viewModel { SettingsViewModel(get()) }` 아래):

```kotlin
viewModel { com.nomadclub.cashchat.feature.chat.ChatViewModel(get(), get(), get()) }
```

- [ ] **Step 2: 공용 컴포넌트 작성** — `components/ChatComponents.kt`

```kotlin
package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.model.ChatItem

/** 코인/밥 공용 칩 */
@Composable
fun StatChip(emoji: String, text: String, warning: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(emoji, style = MaterialTheme.typography.labelMedium)
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 밥 게이지 — 잔량 비율 따라 색 전환, 변화 시 부드럽게 차오름 */
@Composable
fun EnergyGauge(energy: Int, maxEnergy: Int, modifier: Modifier = Modifier) {
    val ratio = if (maxEnergy > 0) energy.toFloat() / maxEnergy else 0f
    val animated by animateFloatAsState(ratio, animationSpec = tween(600), label = "energy")
    val barColor = if (ratio <= 0.2f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier.height(5.dp).clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier.fillMaxWidth(animated).height(5.dp)
                .clip(RoundedCornerShape(3.dp)).background(barColor),
        )
    }
}

/** 메시지 버블 */
@Composable
fun MessageBubble(item: ChatItem) {
    when (item) {
        is ChatItem.UserMessage -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(
                    alpha = if (item.status == ChatItem.SendStatus.PENDING || item.status == ChatItem.SendStatus.BLOCKED) 0.55f else 1f,
                ),
            ) {
                Text(
                    item.text,
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 280.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        is ChatItem.AssistantMessage -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(
                shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 300.dp).animateContentSize()) {
                    if (item.text.isNotEmpty()) {
                        Text(
                            item.text + if (item.isStreaming) " ▍" else "",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (item.isError) {
                        Text(
                            "응답이 끊겼어요. 다시 시도해주세요.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** 타이핑 인디케이터 (점 3개) */
@Composable
fun TypingIndicator() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(8.dp)) {
        repeat(3) { index ->
            val alpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
                .animateFloat(
                    initialValue = 0.2f, targetValue = 1f,
                    animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                        animation = tween(500, delayMillis = index * 150),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                    ), label = "dot$index",
                )
            Box(
                Modifier.size(7.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}
```

- [ ] **Step 3: ChatScreen 교체**

기존 `ChatScreen.kt`를 전면 재작성. 시그니처가 MainScreen과 결합되어 있으므로 함께 수정한다.

```kotlin
package com.nomadclub.cashchat.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.feature.chat.components.EnergyGauge
import com.nomadclub.cashchat.feature.chat.components.MessageBubble
import com.nomadclub.cashchat.feature.chat.components.StatChip
import com.nomadclub.cashchat.feature.chat.components.TypingIndicator
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import org.koin.androidx.compose.koinViewModel

private val suggestedQuestions = listOf("오늘 저녁 뭐 먹을까?", "가성비 이어폰 추천해줘", "영어 공부 팁 알려줘")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenConversations: () -> Unit,
    onOpenEvolution: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val items by viewModel.chatStore.items.collectAsState()
    val isStreaming by viewModel.chatStore.isStreaming.collectAsState()
    val gateVisible by viewModel.chatStore.energyGateVisible.collectAsState()
    val hud by viewModel.hudStore.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(items.size, (items.lastOrNull() as? ChatItem.AssistantMessage)?.text?.length) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        // ── 슬림 톱바
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onOpenConversations) {
                Icon(Icons.Filled.Forum, contentDescription = "대화 목록")
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.clickable(onClick = onOpenEvolution),
            ) {
                Text("🐣", Modifier.padding(6.dp))
            }
            Column(Modifier.clickable(onClick = onOpenEvolution)) {
                Text("미래", style = MaterialTheme.typography.titleSmall)
                if (hud.isLoaded) {
                    Text("Lv.${hud.level}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.weight(1f))
            // 포인트 잔액 API 부재(BE 의존성) — points가 null이면 칩 숨김
            hud.points?.let { StatChip("🪙", "%,d".format(it)) }
            if (hud.isLoaded) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatChip("⚡", "${hud.energy}/${hud.maxEnergy}", warning = hud.energy == 0)
                    EnergyGauge(hud.energy, hud.maxEnergy, Modifier.width(56.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // ── 메시지 리스트
        Box(Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🐣", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("안녕! 뭐든 물어봐요", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    suggestedQuestions.forEach { question ->
                        SuggestionChip(
                            onClick = { viewModel.send(question) },
                            label = { Text(question) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        MessageBubble(item)
                        if (item is ChatItem.AssistantMessage && item.isError) {
                            TextButton(onClick = { viewModel.chatStore.retryLastMessage() }) {
                                Text("다시 시도")
                            }
                        }
                    }
                    if (isStreaming && items.lastOrNull() !is ChatItem.AssistantMessage) {
                        item { TypingIndicator() }
                    }
                }
            }
        }

        // ── 입력바
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("메시지를 입력하세요...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )
            FilledIconButton(
                onClick = { viewModel.send(input); input = "" },
                enabled = input.isNotBlank() && !isStreaming,
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "전송")
            }
        }
    }

    if (gateVisible) {
        EnergyGateBottomSheet(viewModel = viewModel) // Task 10에서 구현 — 이 Task에서는 임시 스텁
    }
}
```

이 Task에서는 임시 스텁을 같은 파일 하단에 추가(Task 10에서 본 구현으로 교체):

```kotlin
@Composable
fun EnergyGateBottomSheet(viewModel: ChatViewModel) {
    // Task 10에서 본 구현으로 교체
}
```

- [ ] **Step 4: MainScreen 수정**

`composable(MainTab.CHAT.route)` 블록을 다음으로 교체(기존 points/messageCount props 제거):

```kotlin
composable(MainTab.CHAT.route) {
    ChatScreen(
        onOpenConversations = { navController.navigate("chat/conversations") },
        onOpenEvolution = { navController.navigate("evolution") },
    )
}
```

> 기존 `ChatScreen`이 받던 `points`, `addPoints`, `incrementMessageCount`, `onNavigateTab`은 mock 보상 로직이므로 제거. `MainScreen`의 해당 상태가 Rewards/Shop 탭에서만 쓰이면 그대로 두고 Chat 연결만 끊는다.

또한 NavHost에 라우트 2개 추가(Task 9·11에서 화면 구현):

```kotlin
composable("chat/conversations") { /* Task 9 */ }
composable("evolution") { /* Task 11 */ }
```

- [ ] **Step 5: mock 모델 삭제 + 빌드 확인**

```bash
rm -rf apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/models
```

Run: `./gradlew :app:assembleDebug -x lint`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 수동 확인 (에뮬레이터)**

dev flavor로 설치 후: 채팅 진입 → 톱바에 Lv/밥 게이지 표시 → 메시지 전송 → 스트리밍 누적 확인.

- [ ] **Step 7: 커밋**

```bash
git add -A apps/frontend/app/src
git commit -m "feat(chat): 실서버 연동 채팅 화면 및 슬림 톱바 구현"
```

---

### Task 9: 대화방 목록 화면

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ConversationListScreen.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/main/MainScreen.kt`

- [ ] **Step 1: 화면 구현**

```kotlin
package com.nomadclub.cashchat.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.ChatApi
import com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewConversation: () -> Unit,
    chatApi: ChatApi = koinInject(),
) {
    var conversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { chatApi.listConversations() }
            .onSuccess { conversations = it }
            .onFailure { loadFailed = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("대화") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewConversation) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("새 대화")
            }
        },
    ) { padding ->
        when {
            loadFailed -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("목록을 불러오지 못했어요")
            }
            conversations == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            conversations!!.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("🐣", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Text("첫 대화를 시작해보세요")
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(conversations!!, key = { it.conversationId }) { conversation ->
                    ListItem(
                        headlineContent = { Text(conversation.title) },
                        supportingContent = {
                            conversation.lastMessage?.let { Text(it, maxLines = 1) }
                        },
                        modifier = Modifier.clickable { onOpenConversation(conversation.conversationId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 2: MainScreen 라우트 연결**

```kotlin
composable("chat/conversations") {
    ConversationListScreen(
        onBack = { navController.popBackStack() },
        onOpenConversation = { id ->
            chatViewModel.openConversation(id)   // koinViewModel을 MainScreen 레벨에서 공유
            navController.popBackStack()
        },
        onNewConversation = {
            chatViewModel.chatStore.startNewConversation()
            navController.popBackStack()
        },
    )
}
```

> ChatViewModel을 MainScreen에서 `val chatViewModel: ChatViewModel = koinViewModel()`로 한 번 얻어 CHAT 라우트와 목록 라우트에 같은 인스턴스를 넘긴다(Koin 기본 viewModel 스코프가 라우트별로 갈리지 않도록). `ChatScreen(viewModel = chatViewModel, ...)` 형태로 전달.

- [ ] **Step 3: 빌드 + 수동 확인**

Run: `./gradlew :app:assembleDebug -x lint`
Expected: BUILD SUCCESSFUL. 에뮬레이터에서 목록 → 대화 선택 → 메시지 복원 확인.

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/app/src
git commit -m "feat(chat): 대화방 목록 화면 추가"
```

---

### Task 10: 밥 충전 게이트 바텀시트 + AdMob SSV nonce

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/RewardedAdManager.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/EnergyGateBottomSheet.kt` (Task 8 스텁 교체)

- [ ] **Step 1: RewardedAdManager에 SSV nonce 추가**

`show` 함수에 `nonce: String?` 파라미터 추가, `ad.show` 직전에 삽입:

```kotlin
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions

fun show(
    activity: Activity,
    nonce: String? = null,
    onRewarded: (amount: Int) -> Unit,
    onDismissed: () -> Unit,
    onNotReady: () -> Unit = {},
) {
    val ad = rewardedAd
    if (ad == null) { onNotReady(); return }
    nonce?.let {
        ad.setServerSideVerificationOptions(
            ServerSideVerificationOptions.Builder().setCustomData(it).build()
        )
    }
    // ...기존 fullScreenContentCallback + ad.show(...) 로직 유지
}
```

기존 `show` 호출처가 있으면(`grep -rn "rewardedAdManager.show\|RewardedAdManager" app/src/main`) named argument로 호환 유지.

- [ ] **Step 2: EnergyGateBottomSheet 본 구현** (Task 8의 스텁 함수 교체 — ChatScreen.kt에서 스텁 제거)

```kotlin
package com.nomadclub.cashchat.feature.chat

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.ads.RewardedAdManager
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnergyGateBottomSheet(
    viewModel: ChatViewModel,
    adManager: RewardedAdManager = koinInject(),
) {
    val quota by viewModel.adRewardStore.quota.collectAsState()
    val phase by viewModel.rewardPhase.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) { adManager.preload(context) }

    ModalBottomSheet(
        onDismissRequest = { viewModel.dismissGate() },
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🍚", style = MaterialTheme.typography.displayMedium)
            Text("밥이 떨어졌어요!", style = MaterialTheme.typography.titleLarge)
            Text(
                "광고 보고 밥을 채우면 바로 이어서 대화해요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            quota?.let {
                Text(
                    if (it.remaining > 0) "오늘 ${it.remaining}회 남음 · 자정에 리셋"
                    else "오늘 광고 한도에 도달했어요 · 내일 다시 만나요",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))

            when (phase) {
                ChatViewModel.RewardPhase.POLLING -> {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text("보상 확인 중...", style = MaterialTheme.typography.labelMedium)
                }
                ChatViewModel.RewardPhase.FAILED -> {
                    Text("보상 확인이 지연되고 있어요", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = {
                        viewModel.startAdReward { _ -> true } // 폴링만 재시도 (광고 재시청 없이)
                    }) { Text("다시 확인") }
                }
                else -> {
                    Button(
                        onClick = {
                            val activity = context as? Activity ?: return@Button
                            viewModel.startAdReward { nonce ->
                                suspendCancellableCoroutine { continuation ->
                                    adManager.show(
                                        activity = activity,
                                        nonce = nonce,
                                        onRewarded = { },
                                        onDismissed = { continuation.resume(true) },
                                        onNotReady = { continuation.resume(false) },
                                    )
                                }
                            }
                        },
                        enabled = (quota?.remaining ?: 0) > 0 && phase == ChatViewModel.RewardPhase.IDLE,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("▶  광고 보고 밥 채우기") }

                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("🪙 포인트로 충전 (준비 중)")
                    }
                }
            }
        }
    }
}
```

`RewardedAdManager`가 Koin에 등록되어 있는지 확인(`grep -n "RewardedAdManager" app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`) — 없으면 `single { RewardedAdManager(get()) }` 추가 (AppConfig가 Koin에 있어야 함; 기존 패턴 확인).

- [ ] **Step 3: 빌드 + 수동 확인**

Run: `./gradlew :app:assembleDebug -x lint`
Expected: BUILD SUCCESSFUL.
수동: 밥 0 상태에서 전송 → 시트 표시 → 테스트 광고 시청 → 게이지 갱신 + 자동 재전송. (dev 환경 AdMob 테스트 유닛 ID 사용)

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/app/src
git commit -m "feat(energy): 밥 충전 게이트 바텀시트 및 SSV nonce 연동"
```

---

### Task 11: 진화 스테이지 풀스크린 + 차지&플래시 연출

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionViewModel.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionScreen.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/main/MainScreen.kt`

- [ ] **Step 1: EvolutionViewModel**

연출 단계는 enum 상태머신으로 — UI는 상태만 그린다 (스펙 §1.4).

```kotlin
package com.nomadclub.cashchat.feature.chat.evolution

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nomadclub.cashchat.shared.core.network.ApiException
import com.nomadclub.cashchat.shared.evolution.EvolutionAttemptDto
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EvolutionViewModel(
    val evolutionStore: EvolutionStore,
    private val hudStore: HudStore,
) : ViewModel() {

    /** 연출 단계: IDLE → CHARGING(0.8s) → SURGING(1.2s) → REVEAL_SUCCESS/REVEAL_FAIL */
    enum class Phase { IDLE, CHARGING, SURGING, REVEAL_SUCCESS, REVEAL_FAIL }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase = _phase.asStateFlow()

    private val _lastResult = MutableStateFlow<EvolutionAttemptDto?>(null)
    val lastResult = _lastResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /** 2회차부터 연출 스킵 허용 */
    var attemptCount = 0
        private set
    private var skipRequested = false

    init { viewModelScope.launch { runCatching { evolutionStore.refresh() } } }

    fun requestSkip() { skipRequested = true }

    fun attempt() {
        if (_phase.value != Phase.IDLE) return
        attemptCount++
        skipRequested = false
        viewModelScope.launch {
            _phase.value = Phase.CHARGING
            val result = try {
                evolutionStore.attempt()
            } catch (e: ApiException) {
                _phase.value = Phase.IDLE
                _errorMessage.value = when (e.code) {
                    ApiException.INSUFFICIENT_POINTS -> "포인트가 부족해요. 광고로 모아볼까요?"
                    ApiException.ALREADY_MAX_LEVEL -> "이미 최고 레벨이에요!"
                    else -> e.message
                }
                runCatching { evolutionStore.refresh() }
                return@launch
            } catch (e: Exception) {
                _phase.value = Phase.IDLE
                _errorMessage.value = "네트워크 오류 — 다시 시도해주세요"
                return@launch
            }
            _lastResult.value = result

            // 연출 타임라인 (스킵 시 즉시 결과)
            if (!skipRequested) delay(800)            // CHARGING
            if (!skipRequested) { _phase.value = Phase.SURGING; delay(1200) }
            _phase.value = if (result.success) Phase.REVEAL_SUCCESS else Phase.REVEAL_FAIL

            // 성공 시 레벨·밥 보너스 반영 (스펙 §3.3)
            runCatching { evolutionStore.refresh() }
            hudStore.refresh()
        }
    }

    fun dismissResult() { _phase.value = Phase.IDLE; _lastResult.value = null }
    fun clearError() { _errorMessage.value = null }
}
```

AppModule에 추가:

```kotlin
viewModel { com.nomadclub.cashchat.feature.chat.evolution.EvolutionViewModel(get(), get()) }
```

- [ ] **Step 2: EvolutionScreen 구현 (연출 포함)**

```kotlin
package com.nomadclub.cashchat.feature.chat.evolution

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val levelEmojis = mapOf(1 to "🥚", 2 to "🐣", 3 to "🐤", 4 to "🦅", 5 to "🐲")
private val levelNames = mapOf(1 to "알", 2 to "부화", 3 to "유년", 4 to "성장", 5 to "궁극")

@Composable
fun EvolutionScreen(
    onClose: () -> Unit,
    viewModel: EvolutionViewModel = koinViewModel(),
) {
    val state by viewModel.evolutionStore.state.collectAsState()
    val phase by viewModel.phase.collectAsState()
    val result by viewModel.lastResult.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val haptic = LocalHapticFeedback.current

    // ── 연출 값들
    val scale by animateFloatAsState(
        targetValue = when (phase) {
            EvolutionViewModel.Phase.CHARGING -> 0.92f
            EvolutionViewModel.Phase.SURGING -> 1.08f
            EvolutionViewModel.Phase.REVEAL_SUCCESS -> 1.2f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale",
    )
    val glow by animateFloatAsState(
        targetValue = if (phase == EvolutionViewModel.Phase.SURGING) 1f else 0f,
        animationSpec = tween(900), label = "glow",
    )
    // 화이트 플래시: REVEAL_SUCCESS 진입 순간 1f → 0f
    val flash = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        when (phase) {
            EvolutionViewModel.Phase.SURGING -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            EvolutionViewModel.Phase.REVEAL_SUCCESS -> {
                flash.snapTo(1f)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                flash.animateTo(0f, tween(600))
            }
            EvolutionViewModel.Phase.REVEAL_FAIL -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            else -> Unit
        }
    }
    // 실패 쉐이크
    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(phase) {
        if (phase == EvolutionViewModel.Phase.REVEAL_FAIL) {
            repeat(4) {
                shakeOffset.animateTo(12f, tween(40)); shakeOffset.animateTo(-12f, tween(40))
            }
            shakeOffset.animateTo(0f, tween(40))
        }
    }

    val displayLevel = if (phase == EvolutionViewModel.Phase.REVEAL_SUCCESS) {
        result?.resultLevel ?: state?.level ?: 1
    } else state?.level ?: 1

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f + glow * 0.25f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .clickable(enabled = phase == EvolutionViewModel.Phase.CHARGING || phase == EvolutionViewModel.Phase.SURGING) {
                if (viewModel.attemptCount >= 2) viewModel.requestSkip()
            },
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "닫기") }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    levelEmojis[displayLevel] ?: "🐣",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier
                        .scale(scale)
                        .graphicsLayer { translationX = shakeOffset.value },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "미래 · Lv.$displayLevel ${levelNames[displayLevel] ?: ""}",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                StepIndicator(current = displayLevel, total = 5)
            }

            Spacer(Modifier.weight(1f))

            state?.let { evolution ->
                if (evolution.isMaxLevel) {
                    AssistChip(onClick = {}, label = { Text("🏆 최고 레벨 달성!") })
                } else {
                    StatRow("다음 진화 비용", "🪙 %,d".format(evolution.nextAttemptCost ?: 0))
                    Spacer(Modifier.height(6.dp))
                    StatRow("성공 확률", "${((evolution.nextSuccessRate ?: 0.0) * 100).toInt()}%")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "성공하면 밥도 보너스 충전! ⚡",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.attempt() },
                        enabled = phase == EvolutionViewModel.Phase.IDLE,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (phase == EvolutionViewModel.Phase.IDLE) "🎰 진화 시도하기" else "두근두근...") }
                }
            } ?: CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }

        // 성공 파티클
        if (phase == EvolutionViewModel.Phase.REVEAL_SUCCESS) SuccessParticles()

        // 화이트 플래시 오버레이
        if (flash.value > 0f) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = flash.value)))
        }

        // 결과 카드
        result?.let { attemptResult ->
            if (phase == EvolutionViewModel.Phase.REVEAL_SUCCESS || phase == EvolutionViewModel.Phase.REVEAL_FAIL) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissResult() },
                    title = {
                        Text(if (attemptResult.success) "🎉 Lv.${attemptResult.resultLevel} 달성!" else "아깝다!")
                    },
                    text = {
                        Text(
                            if (attemptResult.success) "진화 성공! 밥도 보너스로 충전됐어요 ⚡"
                            else "이번엔 실패했어요 (-%,d 코인). 다시 도전해볼까요?".format(attemptResult.cost),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val retry = !attemptResult.success
                            viewModel.dismissResult()
                            if (retry) viewModel.attempt()
                        }) { Text(if (attemptResult.success) "좋아!" else "다시 도전") }
                    },
                    dismissButton = if (!attemptResult.success) {
                        { TextButton(onClick = { viewModel.dismissResult() }) { Text("다음에") } }
                    } else null,
                )
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("진화 실패") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("확인") } },
        )
    }
}

@Composable
private fun StepIndicator(current: Int, total: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        (1..total).forEach { step ->
            val isDone = step <= current
            Box(
                Modifier.size(if (step == current) 14.dp else 10.dp).clip(CircleShape)
                    .background(if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
            if (step < total) {
                Box(
                    Modifier.width(22.dp).height(2.dp)
                        .background(if (step < current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 성공 파티클 — 단일 progress로 N개의 입자를 방사형으로 흩뿌린다 */
@Composable
private fun SuccessParticles(count: Int = 80) {
    val particles = remember {
        List(count) {
            Triple(
                Random.nextFloat() * 2f * Math.PI.toFloat(),  // 각도
                0.4f + Random.nextFloat() * 0.6f,              // 속도 계수
                Random.nextFloat(),                            // 크기 시드
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing)) }
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height * 0.4f)
        particles.forEach { (angle, speed, seed) ->
            val distance = progress.value * speed * size.minDimension * 0.5f
            drawCircle(
                color = color.copy(alpha = (1f - progress.value).coerceIn(0f, 1f)),
                radius = 3.dp.toPx() + seed * 4.dp.toPx(),
                center = center + Offset(cos(angle) * distance, sin(angle) * distance),
            )
        }
    }
}
```

- [ ] **Step 3: MainScreen 라우트 연결**

```kotlin
composable("evolution") {
    EvolutionScreen(onClose = { navController.popBackStack() })
}
```

- [ ] **Step 4: 빌드 + 수동 확인**

Run: `./gradlew :app:assembleDebug -x lint`
Expected: BUILD SUCCESSFUL.
수동: 톱바 아바타 탭 → 진화 화면 → 시도 → 차지/서지/플래시(or 쉐이크) → 결과 다이얼로그 → 레벨·밥 갱신 확인. 402(포인트 부족) 시 에러 다이얼로그 확인.

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/app/src
git commit -m "feat(evolution): 진화 스테이지 풀스크린 및 차지&플래시 연출 구현"
```

---

### Task 12: 통합 검증 + 마무리

**Files:** (수정 없음 — 검증 전용)

- [ ] **Step 1: 전체 테스트 + 빌드**

```bash
./gradlew :shared:testDebugUnitTest :app:assembleDebug
```
Expected: BUILD SUCCESSFUL, 모든 shared 테스트 PASS

- [ ] **Step 2: 수동 시나리오 체크리스트** (dev 서버 연결, 에뮬레이터)

1. 채팅 진입 → 톱바 Lv/밥 게이지 로드
2. 새 대화 전송 → 대화방 자동 생성 + SSE 스트리밍 누적
3. 대화방 목록 → 기존 대화 선택 → 메시지 복원
4. 밥 0까지 소진 → 409 게이트 시트 → 테스트 광고 → 게이지 차오름 → 자동 재전송
5. 진화 화면 → 시도(성공/실패 연출) → 레벨·밥·확률 갱신
6. 다크모드 전환 후 1~5 재확인
7. 기내 모드로 스트리밍 끊기 → 부분 텍스트 + "다시 시도" 동작

- [ ] **Step 3: 커밋 & 후속 작업 기록**

남은 BE 의존성을 Jira에 기록(또는 팀 공유):
- 포인트 잔액 조회 API 부재 → HUD 코인 칩 비표시 상태. API 추가 시 `HudStore.refreshNow()`에 연결.
- 포인트로 밥 충전 엔드포인트(예정) → 게이트 시트의 비활성 버튼에 연결.

---

# 확장 기능 태스크 (스펙 §6 — 2026-06-11 추가)

> 방침: 미구현 BE 기능은 UI 선구현 + `FeatureFlags`로 진입점 차단. API 스펙은 `docs/planning/be-api-requests-cc348.md` 참조. BE가 준비되면 플래그 활성 + API 연결만 한다.

### Task 13: FeatureFlags + 포인트 잔액 칩 연결 준비 (P1-1)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/config/FeatureFlags.kt`
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/wallet/PointsApi.kt`
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/hud/HudStore.kt`
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`

- [ ] **Step 1: FeatureFlags 작성**

```kotlin
package com.nomadclub.cashchat.shared.core.config

/** BE 미구현 기능의 진입점 차단 플래그. API가 배포되면 true로 전환 + 연결 확인. */
object FeatureFlags {
    const val POINT_BALANCE = false        // P1-1 GET /api/points/me
    const val POINT_TOPUP = false          // P1-2 POST /api/energy/topup
    const val ENERGY_RECOVERY = false      // P1-3 energy/me 확장
    const val CONVERSATION_EDIT = false    // P2-1 삭제·이름변경
    const val COUPANG_CARD = true          // P2-2 SSE product — 수신 시 자동 렌더(플래그는 UI 데모용)
    const val AD_GATE = true               // P2-3 SSE gate — 수신 시 자동 렌더
    const val EVOLUTION_HISTORY = false    // P3-1 attempts 조회
    const val SHARE_LINK = false           // P3-3 공개 공유
}
```

- [ ] **Step 2: PointsApi 작성** (BE 배포 전 — 호출부는 플래그로 차단)

```kotlin
package com.nomadclub.cashchat.shared.wallet

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

@Serializable
data class PointBalanceDto(val balance: Long)

/** P1-1 요청 API (docs/planning/be-api-requests-cc348.md). 배포 전까지 FeatureFlags.POINT_BALANCE로 차단. */
class PointsApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun getBalance(): PointBalanceDto = client.get("$baseUrl/api/points/me").body()
}
```

- [ ] **Step 3: HudStore에 포인트 연결**

`HudStore` 생성자에 `private val pointsApi: PointsApi` 추가, `refreshNow()`의 병렬 조회에 추가:

```kotlin
val pointsDeferred = if (FeatureFlags.POINT_BALANCE) async { runCatching { pointsApi.getBalance().balance }.getOrNull() } else null
// ...
points = pointsDeferred?.await(),
```

`SharedModule`에 `single { PointsApi(get(), baseUrl) }` 추가, `HudStore` 등록을 `HudStore(get(), get(), get(), get())`로 갱신. (HUD 코인 칩 UI는 Task 8에서 이미 `points != null`일 때만 표시.)

- [ ] **Step 4: 빌드 + 커밋**

Run: `./gradlew :shared:testDebugUnitTest :app:assembleDebug -x lint` → PASS/SUCCESS

```bash
git add -A apps/frontend
git commit -m "feat(shared): FeatureFlags 도입 및 포인트 잔액 API 연결 준비"
```

---

### Task 14: 출석 체크 (그룹 A — BE 구현 완료)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceApi.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/AttendanceSheet.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatViewModel.kt`, `ChatScreen.kt`, `di/SharedModule.kt`

- [ ] **Step 1: AttendanceApi 작성** (BE DTO와 1:1)

```kotlin
package com.nomadclub.cashchat.shared.attendance

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import kotlinx.serialization.Serializable

@Serializable
data class BonusItemDto(val itemCode: String, val quantity: Int)

@Serializable
data class RewardPreviewDto(val dayCount: Int, val coin: Long, val bonusItems: List<BonusItemDto>)

@Serializable
data class CheckInDto(
    val awardedCoin: Long,
    val streakDayCount: Int,
    val bonusItems: List<BonusItemDto>,
    val nextRewardPreview: RewardPreviewDto,
)

@Serializable
data class MonthlyAttendanceDto(
    val year: Int,
    val month: Int,
    val checkedDays: List<Int>,
    val currentStreak: Int,
    val todayChecked: Boolean,
    val nextRewardPreview: RewardPreviewDto,
)

class AttendanceApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun checkIn(): CheckInDto = client.post("$baseUrl/api/attendance/check-in").body()

    @Throws(Exception::class)
    suspend fun getMonthly(year: Int? = null, month: Int? = null): MonthlyAttendanceDto =
        client.get("$baseUrl/api/attendance/me") {
            year?.let { parameter("year", it) }
            month?.let { parameter("month", it) }
        }.body()
}
```

`SharedModule`에 `single { AttendanceApi(get(), baseUrl) }` 추가. 409 `ALREADY_CHECKED_IN`(중복 체크인)은 조용히 무시.

- [ ] **Step 2: ChatViewModel에 출석 로직 추가**

```kotlin
// ChatViewModel 생성자에 attendanceApi: AttendanceApi 추가 (AppModule viewModel 등록도 get() 하나 추가)
private val _attendance = MutableStateFlow<MonthlyAttendanceDto?>(null)
val attendance = _attendance.asStateFlow()
private val _checkInResult = MutableStateFlow<CheckInDto?>(null)
val checkInResult = _checkInResult.asStateFlow()

init {
    viewModelScope.launch {
        runCatching {
            val monthly = attendanceApi.getMonthly()
            _attendance.value = monthly
            if (!monthly.todayChecked) {
                _checkInResult.value = attendanceApi.checkIn()
                _attendance.value = attendanceApi.getMonthly()
            }
        }
    }
}
fun dismissCheckIn() { _checkInResult.value = null }
```

- [ ] **Step 3: AttendanceSheet UI** — 체크인 보상 모달 + 월 캘린더

```kotlin
package com.nomadclub.cashchat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.attendance.CheckInDto
import com.nomadclub.cashchat.shared.attendance.MonthlyAttendanceDto

/** 출석 보상 다이얼로그 — 채팅 진입 시 미출석이면 자동 표시 */
@Composable
fun CheckInRewardDialog(result: CheckInDto, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📅 ${result.streakDayCount}일째 출석!") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🪙 +%,d 코인".format(result.awardedCoin), style = MaterialTheme.typography.titleLarge)
                if (result.bonusItems.isNotEmpty()) {
                    Text("보너스: " + result.bonusItems.joinToString { "${it.itemCode} ×${it.quantity}" })
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "다음 보상: ${result.nextRewardPreview.dayCount}일차 🪙${result.nextRewardPreview.coin}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("좋아!") } },
    )
}

/** 월 캘린더 — 톱바 캘린더 아이콘으로 진입하는 바텀시트 내용물 */
@Composable
fun AttendanceCalendar(monthly: MonthlyAttendanceDto) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${monthly.year}년 ${monthly.month}월 출석", style = MaterialTheme.typography.titleMedium)
        Text("🔥 연속 ${monthly.currentStreak}일", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(220.dp)) {
            items((1..31).toList()) { day ->
                val checked = day in monthly.checkedDays
                Box(
                    Modifier.padding(3.dp).size(34.dp).clip(CircleShape)
                        .background(
                            if (checked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$day",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
```

ChatScreen 톱바에 캘린더 아이콘(`Icons.Filled.CalendarMonth`) 추가 → `ModalBottomSheet`로 `AttendanceCalendar` 표시. `checkInResult != null`이면 `CheckInRewardDialog` 표시.

- [ ] **Step 4: 빌드 + 수동 확인 + 커밋**

Run: `./gradlew :app:assembleDebug -x lint` → SUCCESS. 수동: 첫 진입 시 출석 다이얼로그 → 캘린더에 오늘 표시.

```bash
git add -A apps/frontend
git commit -m "feat(attendance): 출석 체크 모달 및 월 캘린더 구현"
```

---

### Task 15: 상점·인벤토리 실서버 연동 (그룹 A)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/shop/ShopApi.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/shop/ShopScreen.kt` (기존 mock 교체)
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/evolution/EvolutionScreen.kt`

- [ ] **Step 1: ShopApi 작성**

```kotlin
package com.nomadclub.cashchat.shared.shop

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class ShopCatalogDto(val category: String, val phase1Active: Boolean, val items: List<Item>) {
    @Serializable
    data class Item(
        val itemCode: String,
        val name: String,
        val priceCoin: Long,
        val effectSummary: String,
        val displayOrder: Int,
    )
}

@Serializable
data class PurchaseResultDto(
    val purchaseOrderId: Long,
    val status: String,
    val coinBalance: Long,
    val inventory: List<Item>,
) {
    @Serializable
    data class Item(val itemCode: String, val qty: Int)
}

@Serializable
data class InventoryDto(val items: List<Item>) {
    @Serializable
    data class Item(val itemCode: String, val qty: Int)
}

@Serializable
private data class PurchaseRequest(val itemCode: String, val qty: Int, val idempotencyKey: String)

class ShopApi(private val client: HttpClient, private val baseUrl: String) {
    /** category: BE ShopItemCategory enum 이름 (예: "EVOLUTION") — 잘못된 값은 400 */
    @Throws(Exception::class)
    suspend fun getItems(category: String): ShopCatalogDto =
        client.get("$baseUrl/api/shop/items") { parameter("category", category) }.body()

    /** idempotencyKey: UUID 형식 필수(서버 검증). 버튼 1탭 = 새 UUID, 재시도는 같은 키. */
    @Throws(Exception::class)
    suspend fun purchase(itemCode: String, qty: Int, idempotencyKey: String): PurchaseResultDto =
        client.post("$baseUrl/api/shop/purchase") {
            contentType(ContentType.Application.Json)
            setBody(PurchaseRequest(itemCode, qty, idempotencyKey))
        }.body()

    @Throws(Exception::class)
    suspend fun getInventory(): InventoryDto = client.get("$baseUrl/api/inventory/me").body()
}
```

`SharedModule` 등록. idempotencyKey는 Android에서 `java.util.UUID.randomUUID().toString()` 사용(서버가 UUID 형식 검증).

- [ ] **Step 2: ShopScreen 실연동**

기존 `ShopScreen`의 mock 아이템 리스트를 `ShopApi.getItems()` + `getInventory()` 결과로 교체. 구매 버튼 → 확인 다이얼로그 → `purchase()` → `coinBalance`로 토스트("구매 완료 · 잔액 🪙N") + 인벤토리 갱신. `INSUFFICIENT_COIN`(402) → "코인 부족" 안내. 카테고리는 BE enum 확인 후 탭 구성(`grep -n "enum class ShopItemCategory" apps/backend -r`).

- [ ] **Step 3: 진화 화면에 보유 아이템 표시**

`EvolutionScreen` 스탯 카드 아래에 인벤토리 칩 행 추가(예: "🧿 확률 부적 ×2"). 효과 적용 API는 BE 미구현이므로 **표시 전용** + "적용 기능 준비 중" 라벨.

- [ ] **Step 4: 빌드 + 수동 확인 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(shop): 상점·인벤토리 실서버 연동 및 진화 화면 아이템 표시"
```

---

### Task 16: 쿠팡 상품 카드 (P2-2 — UI 선구현 + SSE 확장 대비)

**Files:**
- Modify: `apps/frontend/shared/src/.../chat/model/ChatItem.kt`, `chat/ChatApi.kt`, `chat/ChatStore.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/ProductCard.kt`
- Test: `apps/frontend/shared/src/commonTest/.../chat/ProductEventTest.kt`

- [ ] **Step 1: 실패하는 테스트 — SSE `event: product` 파싱**

```kotlin
package com.nomadclub.cashchat.shared.chat

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProductEventTest {
    @Test
    fun `product 이벤트를 ProductCards로 파싱한다`() = runTest {
        val sse = "event: message\ndata: 추천드려요\n\n" +
            "event: product\ndata: {\"products\":[{\"title\":\"버즈3\",\"price\":149000,\"rating\":4.7,\"reviewCount\":32000,\"imageUrl\":\"https://i\",\"trackingUrl\":\"https://t\"}]}\n\n"
        // Task 4의 MockEngine 패턴 재사용
        val api = chatApiWithMockSse(sse)   // 테스트 헬퍼: ChatApiTest와 동일 구성 추출
        val events = api.streamMessage(7, "hi").toList()
        val productEvent = events.filterIsInstance<ChatStreamEvent.ProductCards>().single()
        assertEquals("버즈3", productEvent.products.single().title)
    }
}
```

- [ ] **Step 2: 모델·파싱 구현**

`ChatItem.kt`에 추가:

```kotlin
@kotlinx.serialization.Serializable
data class ProductDto(
    val title: String,
    val price: Long,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val imageUrl: String? = null,
    val trackingUrl: String,
)

// ChatItem 내부에 추가
data class ProductCards(override val id: String, val products: List<ProductDto>) : ChatItem
```

`ChatStreamEvent`에 `data class ProductCards(val products: List<ProductDto>) : ChatStreamEvent` 추가. `ChatApi.streamMessage`의 when에 `"product" -> emit(ChatStreamEvent.ProductCards(Json { ignoreUnknownKeys = true }.decodeFromString<ProductPayload>(event.data).products))` 분기 추가(`@Serializable private data class ProductPayload(val products: List<ProductDto>)`). `ChatStore.stream`의 collect when에 `is ChatStreamEvent.ProductCards -> _items.update { it + ChatItem.ProductCards("p${currentTimeMillis()}", event.products) }` 추가.

- [ ] **Step 3: ProductCard 컴포저블** — 파트너스 고지 필수(상세기획안 §3.3)

```kotlin
package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import com.nomadclub.cashchat.shared.chat.model.ProductDto

@Composable
fun ProductCardList(item: ChatItem.ProductCards) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item.products.forEach { product ->
            OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(product.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("₩%,d".format(product.price), style = MaterialTheme.typography.bodyMedium)
                        product.rating?.let {
                            Spacer(Modifier.width(8.dp))
                            Text("★$it (${product.reviewCount ?: 0})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { uriHandler.openUri(product.trackingUrl) },
                        contentPadding = PaddingValues(0.dp)) { Text("쿠팡에서 보기 →") }
                    Text(
                        "ⓘ 이 포스팅은 쿠팡 파트너스 활동의 일환으로, 이에 따른 일정액의 수수료를 제공받습니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProductCardPreview() {
    ProductCardList(
        ChatItem.ProductCards("p1", listOf(
            ProductDto("삼성 갤럭시 버즈3 Pro", 149000, 4.7, 32000, null, "https://link.coupang.com/x"),
        )),
    )
}
```

`MessageBubble`의 when(또는 ChatScreen items 분기)에 `is ChatItem.ProductCards -> ProductCardList(item)` 추가.

- [ ] **Step 4: 테스트 + 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(chat): 쿠팡 상품 카드 UI 및 SSE product 이벤트 대비"
```

---

### Task 17: Ad Gate UI (P2-3 — UI 선구현 + SSE 확장 대비)

**Files:**
- Modify: `apps/frontend/shared/src/.../chat/model/ChatItem.kt`, `chat/ChatApi.kt`, `chat/ChatStore.kt`
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/AdGateCard.kt`

- [ ] **Step 1: 모델 추가**

`ChatItem.AssistantMessage`에 `val gated: Boolean = false` 추가. `ChatStreamEvent`에 `data class Gate(val teaserChars: Int, val rewardCoin: Int) : ChatStreamEvent` 추가, `ChatApi`에 `"gate"` 분기(`@Serializable private data class GatePayload(val teaserChars: Int = 80, val rewardCoin: Int = 30)`). `ChatStore`에서 `Gate` 수신 시 현재 assistant 메시지에 `gated = true` 마킹 + `gateInfo`(teaserChars·rewardCoin)를 StateFlow로 보관.

- [ ] **Step 2: AdGateCard 컴포저블** — teaser + blur + CTA (상세기획안 §2.2 FE 방식)

```kotlin
package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 게이트된 응답: teaser는 노출, 본문은 blur. 광고 시청 완료 시 onUnlocked 콜백으로 해제. */
@Composable
fun AdGateCard(
    fullText: String,
    teaserChars: Int,
    rewardCoin: Int,
    onWatchAd: () -> Unit,
) {
    val teaser = fullText.take(teaserChars)
    val hidden = fullText.drop(teaserChars)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Text(teaser, style = MaterialTheme.typography.bodyMedium)
            if (hidden.isNotEmpty()) {
                Box {
                    Text(hidden, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.blur(16.dp))
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("🔓 답변 전체 보기", style = MaterialTheme.typography.titleSmall)
                        Text("광고 시청 후 🪙+$rewardCoin", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Button(onClick = onWatchAd, shape = RoundedCornerShape(20.dp)) { Text("▶ 광고 보기") }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdGatePreview() {
    AdGateCard(
        fullText = "7만원이면 QCY T13 ANC가 최고의 선택이에요! 노이즈캔슬링 성능이 가격 대비 뛰어나고 배터리도 30시간으로 넉넉합니다. 통화 품질도 이 가격대 최상위권이라...",
        teaserChars = 40, rewardCoin = 30, onWatchAd = {},
    )
}
```

ChatScreen에서 `gated == true`인 AssistantMessage는 `MessageBubble` 대신 `AdGateCard`로 렌더. `onWatchAd`는 게이트 시트와 동일한 광고 플로우(`viewModel.startAdReward`) 재사용 — 성공 시 해당 메시지 `gated = false`로 해제(blur 해제는 `animateContentSize` 효과로 자연 전환).

- [ ] **Step 3: 빌드 + Preview 확인 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(chat): Ad Gate 블라인드 카드 UI 및 SSE gate 이벤트 대비"
```

---

### Task 18: 포인트로 밥 충전 (P1-2 — 버튼 활성화 준비)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/energy/EnergyTopupApi.kt`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/EnergyGateBottomSheet.kt`

- [ ] **Step 1: API 작성**

```kotlin
package com.nomadclub.cashchat.shared.energy

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
data class EnergyTopupDto(val energy: Int, val maxEnergy: Int, val costPoints: Long, val pointBalance: Long)

@Serializable
private data class TopupRequest(val idempotencyKey: String)

/** P1-2 요청 API. FeatureFlags.POINT_TOPUP 활성 전까지 호출 금지. */
class EnergyTopupApi(private val client: HttpClient, private val baseUrl: String) {
    @Throws(Exception::class)
    suspend fun topup(idempotencyKey: String): EnergyTopupDto =
        client.post("$baseUrl/api/energy/topup") {
            contentType(ContentType.Application.Json)
            setBody(TopupRequest(idempotencyKey))
        }.body()
}
```

- [ ] **Step 2: 게이트 시트 버튼 연결**

기존 "준비 중" 버튼을:

```kotlin
OutlinedButton(
    onClick = { showTopupConfirm = true },
    enabled = FeatureFlags.POINT_TOPUP,
    modifier = Modifier.fillMaxWidth(),
) { Text(if (FeatureFlags.POINT_TOPUP) "🪙 포인트로 충전" else "🪙 포인트로 충전 (준비 중)") }
```

확인 다이얼로그 → `EnergyTopupApi.topup(UUID)` → `INSUFFICIENT_POINTS`(402)면 안내, 성공 시 HUD 갱신 + `retryBlocked()` — 광고 경로와 동일한 마무리.

- [ ] **Step 3: 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(energy): 포인트 밥 충전 UI 및 API 연결 준비"
```

---

### Task 19: 대화방 삭제·이름 변경 (P2-1)

**Files:**
- Modify: `apps/frontend/shared/src/.../chat/ChatApi.kt`, `ConversationListScreen.kt`

- [ ] **Step 1: API 메서드 추가** (FeatureFlags.CONVERSATION_EDIT 활성 전 호출 금지)

```kotlin
@Throws(Exception::class)
suspend fun deleteConversation(conversationId: Long) {
    client.delete("$baseUrl/api/v1/chat/conversations/$conversationId")
}

@Throws(Exception::class)
suspend fun renameConversation(conversationId: Long, title: String): ConversationDto =
    client.patch("$baseUrl/api/v1/chat/conversations/$conversationId") {
        contentType(ContentType.Application.Json)
        setBody(CreateConversationRequest(title))
    }.body()
```

- [ ] **Step 2: 목록 long-press 메뉴**

`ListItem`에 `combinedClickable(onClick=..., onLongClick = { menuFor = conversation })` 적용. `DropdownMenu`: "이름 변경"(텍스트 입력 다이얼로그 → rename → 목록 갱신), "삭제"(확인 다이얼로그 → optimistic 제거 → delete, 실패 시 복원+토스트). 메뉴 항목은 `FeatureFlags.CONVERSATION_EDIT` false면 비활성 + "준비 중" 라벨.

- [ ] **Step 3: 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(chat): 대화방 삭제·이름변경 메뉴 UI 추가"
```

---

### Task 20: 에너지 자동회복 카운트다운 (P1-3)

**Files:**
- Modify: `apps/frontend/shared/src/.../energy/EnergyApi.kt`, `hud/HudStore.kt`, `feature/chat/ChatScreen.kt`

- [ ] **Step 1: DTO 확장** — `EnergyDto`에 nullable 필드 추가(기존 응답과 호환):

```kotlin
@Serializable
data class EnergyDto(
    val energy: Int,
    val maxEnergy: Int,
    val nextRecoverAt: String? = null,   // P1-3 확장 — 배포 전엔 항상 null
    val recoverAmount: Int? = null,
)
```

`HudState`에 `nextRecoverAtEpochMillis: Long? = null` 추가(ISO-8601 파싱은 `kotlinx.datetime` 미도입 상태이므로 Android단에서 `java.time.Instant.parse` — `HudState`엔 원본 String 보관: `nextRecoverAt: String?`).

- [ ] **Step 2: 카운트다운 UI** — 밥 칩 아래, `FeatureFlags.ENERGY_RECOVERY && nextRecoverAt != null`일 때:

```kotlin
@Composable
fun RecoveryCountdown(nextRecoverAtIso: String) {
    var remainText by remember { mutableStateOf("") }
    LaunchedEffect(nextRecoverAtIso) {
        val target = java.time.Instant.parse(nextRecoverAtIso).toEpochMilli()
        while (true) {
            val remainSec = ((target - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            remainText = "%d:%02d 후 ⚡회복".format(remainSec / 60, remainSec % 60)
            if (remainSec == 0L) break
            kotlinx.coroutines.delay(1000)
        }
    }
    Text(remainText, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

0 도달 시 `hudStore.refreshEnergyOnly()` 호출(LaunchedEffect 루프 종료 후).

- [ ] **Step 3: 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(energy): 자동회복 카운트다운 UI 추가"
```

---

### Task 21: 진화 시도 기록 타임라인 (P3-1)

**Files:**
- Modify: `apps/frontend/shared/src/.../evolution/EvolutionApi.kt`, `evolution/EvolutionStore.kt`, `feature/chat/evolution/EvolutionScreen.kt`

- [ ] **Step 1: API·Store 확장**

```kotlin
@Serializable
data class EvolutionAttemptRecordDto(
    val success: Boolean, val fromLevel: Int, val resultLevel: Int,
    val cost: Long, val attemptedAt: String,
)

@Serializable
data class EvolutionAttemptsDto(val attempts: List<EvolutionAttemptRecordDto>)

// EvolutionApi에 추가 (FeatureFlags.EVOLUTION_HISTORY 활성 전 호출 금지)
@Throws(Exception::class)
suspend fun getAttempts(limit: Int = 20): EvolutionAttemptsDto =
    client.get("$baseUrl/api/evolution/attempts") { parameter("limit", limit) }.body()
```

`EvolutionStore`에 `val history = MutableStateFlow<List<EvolutionAttemptRecordDto>>(emptyList())` + `refreshHistory()`.

- [ ] **Step 2: 타임라인 UI** — 진화 화면 CTA 아래, 플래그 활성 시:

```kotlin
LazyColumn(Modifier.heightIn(max = 160.dp)) {
    items(history) { record ->
        ListItem(
            leadingContent = { Text(if (record.success) "✅" else "💨") },
            headlineContent = {
                Text(if (record.success) "Lv.${record.fromLevel}→${record.resultLevel} 성공!"
                     else "Lv.${record.fromLevel} 실패")
            },
            supportingContent = { Text("🪙${record.cost} · ${record.attemptedAt.take(10)}") },
        )
    }
}
```

- [ ] **Step 3: 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(evolution): 진화 시도 기록 타임라인 UI 추가"
```

---

### Task 22: 캐릭터 이름 짓기 (P3-2 — 로컬 우선)

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/core/data/CharacterPreferenceStore.kt`
- Modify: `feature/chat/ChatScreen.kt`, `feature/chat/evolution/EvolutionScreen.kt`, `di/AppModule.kt`

- [ ] **Step 1: 로컬 저장소** (기존 `ThemePreferenceStore` 패턴 동일 — DataStore preferences)

```kotlin
package com.nomadclub.cashchat.core.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.characterDataStore by preferencesDataStore(name = "character_prefs")

/** 캐릭터 닉네임 로컬 저장. BE PATCH /api/users/me/character-name 배포 시 동기화 추가(P3-2). */
class CharacterPreferenceStore(private val context: Context) {
    private val keyName = stringPreferencesKey("character_name")

    val name: Flow<String> = context.characterDataStore.data.map { it[keyName] ?: "미래" }

    suspend fun setName(value: String) {
        val trimmed = value.trim().take(10)
        if (trimmed.isEmpty()) return
        context.characterDataStore.edit { it[keyName] = trimmed }
    }
}
```

`AppModule`: `single { CharacterPreferenceStore(androidContext()) }`.

- [ ] **Step 2: UI 연결** — 진화 화면의 이름 텍스트 옆 ✏️ 아이콘 → `AlertDialog` + `OutlinedTextField`(1~10자) → 저장. ChatScreen 톱바·진화 화면의 하드코딩 "미래"를 `characterStore.name.collectAsState(initial = "미래")` 값으로 교체.

- [ ] **Step 3: 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(character): 캐릭터 이름 짓기(로컬 저장) 추가"
```

---

### Task 23: 대화 내보내기 (FE 단독) + 공유 링크 자리

**Files:**
- Modify: `feature/chat/ChatScreen.kt`

- [ ] **Step 1: OS 공유 시트로 텍스트 내보내기**

톱바 오버플로 메뉴(⋮)에 "대화 내보내기" 추가:

```kotlin
fun shareConversation(context: Context, items: List<ChatItem>, characterName: String) {
    val text = items.joinToString("\n") { item ->
        when (item) {
            is ChatItem.UserMessage -> "나: ${item.text}"
            is ChatItem.AssistantMessage -> "$characterName: ${item.text}"
            else -> ""
        }
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "대화 공유"))
}
```

같은 메뉴에 "공유 링크 만들기 (준비 중)" 항목을 `FeatureFlags.SHARE_LINK`로 비활성 추가(P3-3 대비).

- [ ] **Step 2: 빌드 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(chat): 대화 내보내기(OS 공유 시트) 추가"
```

---

### Task 24: 캐릭터 인터랙션 (그룹 C — FE 단독)

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/components/CharacterAvatar.kt`
- Modify: `feature/chat/ChatScreen.kt`, `feature/chat/evolution/EvolutionScreen.kt`

- [ ] **Step 1: 반응형 아바타 컴포저블**

```kotlin
package com.nomadclub.cashchat.feature.chat.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch

/** 에너지 비율별 표정 + 탭 시 통통 튀는 반응 (스펙 §6.3) */
@Composable
fun CharacterAvatar(
    level: Int,
    energyRatio: Float,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val base = mapOf(1 to "🥚", 2 to "🐣", 3 to "🐤", 4 to "🦅", 5 to "🐲")[level] ?: "🐣"
    val mood = when {
        energyRatio <= 0f -> "😵"
        energyRatio <= 0.2f -> "🥺"
        else -> ""
    }
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    Text(
        base + mood,
        style = style,
        modifier = modifier.scale(scale.value).clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            scope.launch {
                scale.animateTo(1.25f, spring(stiffness = Spring.StiffnessHigh))
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
        },
    )
}
```

- [ ] **Step 2: 적용** — ChatScreen 톱바 아바타(탭 시 통통 + 진화 화면 이동은 더블 액션 충돌 방지를 위해 long-press 대신: 통통 반응 후 진화 화면 이동 유지), 빈 화면의 🐣, 진화 화면 캐릭터를 `CharacterAvatar`로 교체.

- [ ] **Step 3: 빌드 + 수동 확인 + 커밋**

```bash
git add -A apps/frontend
git commit -m "feat(character): 아바타 탭 반응 및 에너지별 표정 추가"
```

---

### Task 25: 확장 통합 검증

- [ ] **Step 1: 전체 테스트 + 빌드**

```bash
./gradlew :shared:testDebugUnitTest :app:assembleDebug
```

- [ ] **Step 2: 수동 시나리오 (Task 12의 7개에 추가)**

8. 첫 진입 출석 다이얼로그 → 캘린더 확인
9. 상점에서 아이템 구매 → 잔액 토스트 + 인벤토리 반영 → 진화 화면 아이템 칩
10. ProductCard·AdGateCard Compose Preview 렌더 확인 (BE 미배포 — 데모)
11. 캐릭터 이름 변경 → 톱바·진화 화면 반영, 앱 재시작 후 유지
12. 대화 내보내기 → OS 공유 시트에 전체 대화 텍스트
13. 아바타 탭 → 통통 반응 + 햅틱, 밥 0일 때 표정 변화
14. FeatureFlags off 상태에서 "준비 중" 항목들이 모두 비활성인지 확인

- [ ] **Step 3: BE 요청 문서 공유**

`docs/planning/be-api-requests-cc348.md`를 Confluence/Jira로 백엔드에 전달.

---

## 셀프 리뷰 결과

- **스펙 커버리지**: 코어(§1~5)=Task 1~12. 확장(§6.1)=Task 14·15·23, (§6.2)=Task 13·16~22, (§6.3)=Task 24. BE API 요청 문서=`docs/planning/be-api-requests-cc348.md`(Task 25에서 전달).
- **타입 일관성**: `ChatStreamEvent`(Token/StreamError/Done/ProductCards/Gate), `ChatItem`(UserMessage/AssistantMessage(gated)/ProductCards), `FeatureFlags` 키, `EnergyDto` 확장 필드 — 전 Task 일치 확인.
- **플레이스홀더**: Task 8 게이트 스텁→Task 10 교체 명시. Task 15의 ShopItemCategory enum 값과 Task 7의 AuthRepository refresh 시그니처는 실행 시 grep으로 확인하도록 명령 포함(외부 코드 의존 — 의도된 확인 단계).
