# 채팅 인라인 네이티브 광고 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 응답이 `ad_chat_interval`회 누적될 때마다 채팅 리스트에 메시지 버블 스타일 AdMob 네이티브 광고를 삽입한다 (Android + iOS).

**Architecture:** 삽입 타이밍/위치는 shared `ChatStore`가 결정해 `ChatItem.NativeAd` placeholder만 리스트에 넣고, 실제 광고 로딩·렌더링은 각 플랫폼 UI가 placeholder를 만나 수행한다. 빈도 값(`ad_chat_interval`)은 Remote Config에서 읽어 플랫폼별로 shared에 주입한다.

**Tech Stack:** Kotlin Multiplatform, Koin DI, Jetpack Compose, `play-services-ads` NativeAd (Android), SwiftUI + GoogleMobileAds `NativeAd`/`AdLoader` (iOS).

**Spec:** [docs/superpowers/specs/2026-06-23-chat-inline-native-ad-design.md](../specs/2026-06-23-chat-inline-native-ad-design.md)

---

## File Structure

**shared (`:shared`)**
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/model/ChatItem.kt` — `NativeAd` 타입 추가
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdChatIntervalProvider.kt` — 빈도 값 주입 인터페이스
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatStore.kt` — 카운팅·삽입 로직
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt` — ChatStore 주입 갱신
- Modify: `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt` — `doInitKoin`에 interval 파라미터
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatStoreNativeAdTest.kt`

**Android (`:app`)**
- Modify: `app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt` — `AdChatIntervalProvider` 등록
- Create: `app/src/main/java/com/nomadclub/cashchat/ads/NativeAdManager.kt` — NativeAd 로딩
- Create: `app/src/main/java/com/nomadclub/cashchat/ads/ChatNativeAdView.kt` — 버블 Composable
- Modify: `app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt` — `NativeAd` 분기 렌더

**iOS (`CashChatIOS`)**
- Modify: `CashChatIOS/CashChatIOS/CashChatIOSApp.swift` — `doInitKoin`에 interval 전달
- Create: `CashChatIOS/CashChatIOS/Ads/ChatNativeAdManager.swift` — NativeAd 로딩
- Create: `CashChatIOS/CashChatIOS/Ads/ChatNativeAdView.swift` — 버블 뷰
- Modify: `CashChatIOS/CashChatIOS/ChatScreen.swift` — `ChatItemNativeAd` 분기 렌더

> **AppConfig는 이미 `admobNativeAdUnitId`(Android/iOS)와 `adChatInterval`을 보유**한다. 광고 단위 ID·빈도 값 추가 작업은 불필요하다. Google 테스트 네이티브 ID: Android `ca-app-pub-3940256099942544/2247696110`, iOS `ca-app-pub-3940256099942544/3986624511`.

---

## Task 1: shared — `ChatItem.NativeAd` 타입 추가

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/model/ChatItem.kt`

- [ ] **Step 1: `NativeAd` 데이터 클래스 추가**

`ChatItem.kt`의 `ProductCards` 선언 바로 아래(닫는 `}` 직전)에 추가:

```kotlin
    data class ProductCards(override val id: String, val products: List<ProductDto>) : ChatItem

    /** 채팅 인라인 네이티브 광고 placeholder. 실제 광고는 플랫폼 UI가 로딩·렌더한다. */
    data class NativeAd(override val id: String) : ChatItem
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/model/ChatItem.kt
git commit -m "feat: ChatItem에 네이티브 광고 placeholder 타입 추가"
```

---

## Task 2: shared — `AdChatIntervalProvider` 인터페이스

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdChatIntervalProvider.kt`

- [ ] **Step 1: 파일 생성**

```kotlin
package com.nomadclub.cashchat.shared.ads

/**
 * 채팅 N회마다 네이티브 광고를 삽입할 때의 N(=ad_chat_interval).
 * 값은 플랫폼별 Remote Config(Android AppConfig / iOS AppConfig)에서 주입한다.
 * 0 이하이면 광고 비활성.
 */
fun interface AdChatIntervalProvider {
    fun get(): Long
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/AdChatIntervalProvider.kt
git commit -m "feat: 광고 삽입 빈도 주입용 AdChatIntervalProvider 추가"
```

---

## Task 3: shared — `ChatStore` 카운팅·삽입 로직 (TDD)

**Files:**
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatStoreNativeAdTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatStore.kt`

- [ ] **Step 1: 실패하는 테스트 작성**

새 파일 `ChatStoreNativeAdTest.kt` 생성. `FakeChatGateway`는 기존 `ChatStoreTest.kt`에 `private`로 있어 재사용 불가하므로 동일한 가짜를 이 파일에 다시 정의한다(테스트 격리).

```kotlin
package com.nomadclub.cashchat.shared.chat

import com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider
import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeGateway : ChatGateway {
    override suspend fun createConversation(title: String?) =
        com.nomadclub.cashchat.shared.chat.model.ConversationDto(
            conversationId = 1L, title = title ?: "새 대화",
            createdAt = "2026-06-23T00:00:00Z", updatedAt = "2026-06-23T00:00:00Z",
        )
    override suspend fun listConversations() = emptyList<com.nomadclub.cashchat.shared.chat.model.ConversationSummaryDto>()
    override suspend fun getMessages(conversationId: Long) = emptyList<com.nomadclub.cashchat.shared.chat.model.ChatMessageDto>()
    override fun streamMessage(conversationId: Long, message: String): Flow<ChatStreamEvent> =
        flow { emit(ChatStreamEvent.Token("응답")); emit(ChatStreamEvent.Done) }
}

class ChatStoreNativeAdTest {

    private fun store(interval: Long, scope: kotlinx.coroutines.CoroutineScope) =
        ChatStore(FakeGateway(), scope, AdChatIntervalProvider { interval })

    @Test
    fun `interval 3이면 3번째 응답 뒤에 네이티브 광고가 1개 삽입된다`() = runTest {
        val s = store(3, this)
        repeat(3) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        val ads = s.items.value.filterIsInstance<ChatItem.NativeAd>()
        assertEquals(1, ads.size)
        assertEquals(ChatItem.NativeAd::class, s.items.value.last()::class)
    }

    @Test
    fun `interval 3에서 2번째 응답까지는 광고가 없다`() = runTest {
        val s = store(3, this)
        repeat(2) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(0, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }

    @Test
    fun `interval 0이면 광고를 삽입하지 않는다`() = runTest {
        val s = store(0, this)
        repeat(5) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(0, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }

    @Test
    fun `interval 1이면 매 응답마다 광고가 삽입된다`() = runTest {
        val s = store(1, this)
        repeat(3) { s.sendMessage("q$it"); testScheduler.advanceUntilIdle() }
        assertEquals(3, s.items.value.filterIsInstance<ChatItem.NativeAd>().size)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:testDebugUnitTest --tests "*ChatStoreNativeAdTest*"`
Expected: 컴파일 실패 — `ChatStore` 생성자가 3번째 인자(`AdChatIntervalProvider`)를 받지 않음.

- [ ] **Step 3: `ChatStore` 생성자에 provider 추가**

`ChatStore.kt`의 클래스 헤더를 수정(기존 테스트 호환을 위해 기본값 부여):

```kotlin
class ChatStore(
    private val gateway: ChatGateway,
    private val scope: CoroutineScope,
    private val adIntervalProvider: com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider =
        com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider { 0L },
) {
```

- [ ] **Step 4: 응답 카운터 필드 추가**

`ChatStore.kt`에서 `private var streamJob: Job? = null` 아래에 추가:

```kotlin
    // 네이티브 광고 삽입용 — 정상 종료된 assistant 응답 누적 수. reset/대화전환 시 초기화.
    private var assistantResponseCount = 0
```

- [ ] **Step 5: `Done` 이벤트에서 삽입 호출**

`stream(...)`의 `ChatStreamEvent.Done` 분기를 다음으로 교체:

```kotlin
                    ChatStreamEvent.Done -> {
                        updateUser(messageId) { it.copy(status = ChatItem.SendStatus.CONFIRMED) }
                        if (assistantAdded) updateAssistant(assistantId) { it.copy(isStreaming = false) }
                        _streamCompletedCount.update { it + 1 }
                        if (assistantAdded) maybeInsertNativeAd()
                    }
```

`updateAssistant`/`updateUser` private 함수 아래에 삽입 로직 추가:

```kotlin
    /** 정상 응답마다 카운트하고, interval 배수에 도달하면 네이티브 광고 placeholder를 1개 덧붙인다. */
    private fun maybeInsertNativeAd() {
        val interval = adIntervalProvider.get()
        if (interval <= 0) return
        assistantResponseCount += 1
        if (assistantResponseCount % interval != 0L) return
        if (_items.value.lastOrNull() is ChatItem.NativeAd) return
        _items.update { it + ChatItem.NativeAd("ad${currentTimeMillis()}") }
    }
```

- [ ] **Step 6: 카운터 초기화 지점 추가**

`reset()`, `startNewConversation()`, `openConversation()` 각각에 `assistantResponseCount = 0`을 추가한다. 예) `reset()` 본문 끝(`_gateInfo.value = null` 아래), `startNewConversation()` 본문 끝(`_energyGateVisible.value = false` 아래), `openConversation()` 본문 끝(`_energyGateVisible.value = false` 아래)에 각각 한 줄:

```kotlin
        assistantResponseCount = 0
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:testDebugUnitTest --tests "*ChatStoreNativeAdTest*"`
Expected: PASS (4 tests)

- [ ] **Step 8: 기존 ChatStore 테스트 회귀 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:testDebugUnitTest --tests "*ChatStoreTest*"`
Expected: PASS (기존 5 tests — 기본값 덕분에 변경 없음)

- [ ] **Step 9: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/chat/ChatStore.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/chat/ChatStoreNativeAdTest.kt
git commit -m "feat: ChatStore가 응답 N회마다 네이티브 광고를 삽입"
```

---

## Task 4: DI 배선 — SharedModule + Android + iOS

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`
- Modify: `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt`
- Modify: `app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt`
- Modify: `CashChatIOS/CashChatIOS/CashChatIOSApp.swift`

> **설계 노트(중요):** `AdChatIntervalProvider`의 기본값을 `SharedModule`에 등록하지 **않는다**. Android는 `modules(appModule, sharedDataModule(...))` 순서로 로드하는데, 만약 `sharedDataModule`에 기본 `{ 0L }`을 등록하면 나중에 로드되는 `sharedDataModule`이 appModule의 실제 값을 덮어써 광고가 영영 비활성된다. 따라서 `SharedModule`은 등록하지 않고 ChatStore가 `get()`으로 요구만 하며, **각 플랫폼 모듈이 반드시 1회 등록**한다(Android appModule, iOS doInitKoin). 둘 다 등록하므로 Koin 충돌·override가 발생하지 않는다.

- [ ] **Step 1: SharedModule — ChatStore에 provider 주입(get)**

`SharedModule.kt`의 `single { ChatStore(get(), get()) }`를 다음으로 교체(기본 등록은 추가하지 않음):

```kotlin
    single { ChatStore(get(), get(), get()) }
```

- [ ] **Step 2: Android — AppConfig 기반 provider 등록**

`AppModule.kt`의 `single { com.nomadclub.cashchat.config.AppConfig.resolve(get()) }` 바로 아래에 추가:

```kotlin
    // 네이티브 광고 삽입 빈도(ad_chat_interval). ChatStore(shared)가 get()으로 요구한다.
    single<com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider> {
        com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider {
            get<com.nomadclub.cashchat.config.AppConfig>().adChatInterval
        }
    }
```

> `CashChatApplication.kt`는 수정하지 않는다(중복 등록이 없어 override 불필요).

- [ ] **Step 3: iOS — doInitKoin에 interval 파라미터 + provider 등록**

`KoinIos.kt`를 수정(기존 TokenProvider 등록 모듈에 provider를 함께 등록):

```kotlin
fun doInitKoin(baseUrl: String, tokenProvider: TokenProvider, adChatInterval: Long) {
    startKoin {
        modules(
            module {
                single<TokenProvider> { tokenProvider }
                single<com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider> {
                    com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider { adChatInterval }
                }
            },
            sharedDataModule(baseUrl),
        )
    }
}
```

- [ ] **Step 4: iOS Swift 호출부 갱신**

`CashChatIOSApp.swift`의 `doInitKoin(...)` 호출을 수정:

```swift
        KoinIosKt.doInitKoin(
            baseUrl: AppConfig.apiBaseUrl,
            tokenProvider: KeychainTokenProvider(),
            adChatInterval: Int64(AppConfig.adChatInterval)
        )
```

- [ ] **Step 5: shared 빌드 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Android 앱 빌드 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: 커밋**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt \
        apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt \
        apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt \
        apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift
git commit -m "feat: 네이티브 광고 빈도 provider를 Android/iOS Koin에 배선"
```

---

## Task 5: Android — `NativeAdManager`

**Files:**
- Create: `app/src/main/java/com/nomadclub/cashchat/ads/NativeAdManager.kt`

- [ ] **Step 1: 파일 생성**

기존 `RewardedAdManager`/`BannerAd` 패턴을 따른다. ad unit ID는 `AppConfig.admobNativeAdUnitId`(계층형 폴백 포함).

```kotlin
package com.nomadclub.cashchat.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.nomadclub.cashchat.config.AppConfig

/**
 * AdMob 네이티브 광고 1회성 로딩 헬퍼.
 * 채팅 리스트의 ChatItem.NativeAd placeholder 1개당 1회 호출해 광고를 받아온다.
 */
class NativeAdManager(
    private val appConfig: AppConfig,
) {
    companion object { private const val TAG = "NativeAdManager" }

    /**
     * 네이티브 광고를 로딩한다.
     * @param onLoaded 성공 시 NativeAd 전달. 호출자는 화면에서 사라질 때 [NativeAd.destroy] 책임.
     * @param onFailed 실패 시 errorCode 전달(빈 자리 처리 + 로깅용).
     */
    fun load(
        context: Context,
        onLoaded: (NativeAd) -> Unit,
        onFailed: (Int) -> Unit,
    ) {
        val loader = AdLoader.Builder(context, appConfig.admobNativeAdUnitId)
            .forNativeAd { ad -> onLoaded(ad) }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "네이티브 광고 로드 실패: ${error.message}")
                    onFailed(error.code)
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }
}
```

- [ ] **Step 2: Koin 등록**

`AppModule.kt`의 `single { com.nomadclub.cashchat.ads.RewardedAdManager(get()) }` 아래에 추가:

```kotlin
    single { com.nomadclub.cashchat.ads.NativeAdManager(get()) }
```

- [ ] **Step 3: 빌드 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/NativeAdManager.kt \
        apps/frontend/app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt
git commit -m "feat: Android NativeAdManager 추가"
```

---

## Task 6: Android — `ChatNativeAdView` 버블 Composable (시안 B)

**Files:**
- Create: `app/src/main/java/com/nomadclub/cashchat/ads/ChatNativeAdView.kt`

- [ ] **Step 1: 파일 생성**

시안 B(아이콘+헤드라인+광고주/별점+`Ad` 라벨 / 미디어 이미지 / 풀폭 CTA). AdMob 정책상 모든 에셋은 `NativeAdView`에 등록되어야 한다. `AndroidView`로 프로그래매틱 `NativeAdView`를 구성한다. 로딩 전/실패 시 아무것도 그리지 않는다.

```kotlin
package com.nomadclub.cashchat.ads

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.nomadclub.cashchat.config.AppConfig
import org.koin.compose.koinInject

/**
 * 채팅 리스트에 메시지 버블처럼 삽입되는 네이티브 광고(시안 B).
 * 로딩 성공 시에만 렌더하고, 실패/로딩 전에는 빈 자리(아무것도 안 그림)로 둔다.
 */
@Composable
fun ChatNativeAdView(
    modifier: Modifier = Modifier,
    nativeAdManager: NativeAdManager = koinInject(),
    appConfig: AppConfig = koinInject(),
) {
    if (!appConfig.adsEnabled) return

    val context = LocalContext.current
    var ad by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        nativeAdManager.load(
            context = context,
            onLoaded = { loaded -> ad = loaded },
            onFailed = { /* 빈 자리 유지 */ },
        )
        onDispose { ad?.destroy() }
    }

    val current = ad ?: return

    AndroidView(
        modifier = modifier.fillMaxWidth().padding(end = 48.dp),
        factory = { ctx ->
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()

            val headline = TextView(ctx).apply { textSize = 14f; maxLines = 2 }
            val advertiser = TextView(ctx).apply { textSize = 11f; alpha = 0.7f }
            val adBadge = TextView(ctx).apply {
                text = "Ad"; textSize = 10f
                setPadding(dp(4), dp(1), dp(4), dp(1))
            }
            val rating = RatingBar(ctx, null, android.R.attr.ratingBarStyleSmall).apply {
                numStars = 5; stepSize = 0.1f; isClickable = false
            }
            val mediaView = MediaView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(96),
                )
            }
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            }
            val cta = Button(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }

            val topRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(icon)
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(8), 0, dp(8), 0)
                    addView(headline)
                    addView(advertiser)
                    addView(rating)
                })
                addView(adBadge)
            }

            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(topRow)
                addView(mediaView)
                addView(cta)
            }

            NativeAdView(ctx).apply {
                this.headlineView = headline
                this.advertiserView = advertiser
                this.starRatingView = rating
                this.mediaView = mediaView
                this.iconView = icon
                this.callToActionView = cta
                addView(container)
            }
        },
        update = { adView ->
            (adView.headlineView as TextView).text = current.headline
            (adView.advertiserView as TextView).apply {
                text = current.advertiser ?: current.store ?: ""
                visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
            }
            (adView.starRatingView as RatingBar).apply {
                val r = current.starRating
                if (r != null) { rating = r.toFloat(); visibility = View.VISIBLE } else visibility = View.GONE
            }
            (adView.callToActionView as Button).text = current.callToAction ?: "자세히 보기"
            (adView.iconView as ImageView).apply {
                val drawable = current.icon?.drawable
                if (drawable != null) { setImageDrawable(drawable); visibility = View.VISIBLE } else visibility = View.GONE
            }
            adView.setNativeAd(current)
        },
    )
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/ChatNativeAdView.kt
git commit -m "feat: Android 채팅 네이티브 광고 버블 Composable 추가"
```

---

## Task 7: Android — `ChatScreen`에 연결

**Files:**
- Modify: `app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt`

- [ ] **Step 1: import 추가**

기존 import 블록에 추가:

```kotlin
import com.nomadclub.cashchat.ads.ChatNativeAdView
```

- [ ] **Step 2: items 분기에 NativeAd 케이스 추가**

`items(items, key = { it.id }) { item -> ... }` 블록에서, 기존 `if (item is ChatItem.AssistantMessage && item.gated ...) { AdGateCard(...) } else { MessageBubble(item) }` 구조의 가장 바깥 분기에 `NativeAd`를 먼저 처리하도록 수정한다. 구체적으로 해당 `if/else`를 다음으로 감싼다:

```kotlin
                    items(items, key = { it.id }) { item ->
                        if (item is ChatItem.NativeAd) {
                            ChatNativeAdView()
                        } else if (item is ChatItem.AssistantMessage && item.gated && !item.isStreaming) {
                            AdGateCard(
                                fullText = item.text,
                                teaserChars = gateInfo?.teaserChars ?: 80,
                                rewardCoin = gateInfo?.rewardCoin ?: 30,
                                onWatchAd = {
                                    val activity = context as? Activity ?: return@AdGateCard
                                    viewModel.startGateUnlock(item.id) { nonce ->
                                        suspendCancellableCoroutine { continuation ->
                                            var rewarded = false
                                            adManager.show(
                                                activity = activity,
                                                nonce = nonce,
                                                onRewarded = { rewarded = true },
                                                onDismissed = {
                                                    if (continuation.isActive) continuation.resume(rewarded)
                                                },
                                                onNotReady = {
                                                    if (continuation.isActive) continuation.resume(false)
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        } else {
                            MessageBubble(item)
                        }
                        if (item is ChatItem.AssistantMessage && item.isError) {
                            TextButton(onClick = { viewModel.chatStore.retryLastMessage() }) {
                                Text("다시 시도")
                            }
                        }
                    }
```

> 위 블록은 기존 코드(`ChatScreen.kt` 약 232–266행)를 그대로 두고 맨 앞에 `if (item is ChatItem.NativeAd) { ChatNativeAdView() } else if (...)`로 분기 하나만 추가한 형태다. 나머지 줄은 변경하지 않는다.

- [ ] **Step 3: 빌드 확인**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 에뮬레이터 수동 검증**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:installDebug`
확인: 채팅 3회(기본 `ad_chat_interval` 폴백/테스트 환경에선 RC 값) 후 메시지 사이에 `Ad` 라벨이 붙은 테스트 네이티브 광고 버블이 보이고, 흐름이 깨지지 않는다.

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt
git commit -m "feat: 채팅 리스트에 네이티브 광고 버블 연결(Android)"
```

---

## Task 8: iOS — `ChatNativeAdManager`

**Files:**
- Create: `CashChatIOS/CashChatIOS/Ads/ChatNativeAdManager.swift`

> **Xcode 통합**: 이 저장소는 새 `.swift` 파일을 동기화 그룹으로 자동 포함하는 설정이다([reference-ios-tnk-sdk-and-xcode-sync-groups] 메모 참고). `Ads/` 폴더에 파일을 추가하면 타깃에 자동 편입된다. 자동 포함이 안 되면 Xcode에서 타깃 멤버십을 수동 체크한다.

- [ ] **Step 1: 파일 생성**

`BannerAdView`/`RewardedAdManager`가 쓰는 신규 SDK 네이밍(`AdLoader`, `NativeAd`, `Request`)을 따른다. ad unit ID는 `AppConfig.admobNativeAdUnitId`.

```swift
import Foundation
import GoogleMobileAds

/// 채팅 인라인 네이티브 광고 1회성 로딩 헬퍼.
/// ChatItemNativeAd placeholder 1개당 ChatNativeAdLoader 1개가 생성되어 광고를 받아온다.
final class ChatNativeAdLoader: NSObject, ObservableObject, NativeAdLoaderDelegate {
    @Published var nativeAd: NativeAd?

    private var adLoader: AdLoader?

    func load() {
        guard AppConfig.adsEnabled else { return }
        guard let root = Self.rootViewController() else { return }
        let loader = AdLoader(
            adUnitID: AppConfig.admobNativeAdUnitId,
            rootViewController: root,
            adTypes: [.native],
            options: nil
        )
        loader.delegate = self
        loader.load(Request())
        self.adLoader = loader
    }

    func adLoader(_ adLoader: AdLoader, didReceive nativeAd: NativeAd) {
        self.nativeAd = nativeAd
    }

    func adLoader(_ adLoader: AdLoader, didFailToReceiveAdWithError error: Error) {
        print("네이티브 광고 로드 실패: \(error.localizedDescription)")
        // 빈 자리 유지
    }

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?.rootViewController
    }
}
```

- [ ] **Step 2: 빌드 확인**

Run:
```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode && \
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO | tail -5
```
Expected: BUILD SUCCEEDED

- [ ] **Step 3: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/Ads/ChatNativeAdManager.swift \
        apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj
git commit -m "feat: iOS 채팅 네이티브 광고 로더 추가"
```

---

## Task 9: iOS — `ChatNativeAdView` 버블 + ChatScreen 연결

**Files:**
- Create: `CashChatIOS/CashChatIOS/Ads/ChatNativeAdView.swift`
- Modify: `CashChatIOS/CashChatIOS/ChatScreen.swift`

- [ ] **Step 1: 버블 뷰 생성**

`NativeAdView`(UIKit)를 `UIViewRepresentable`로 래핑해 시안 B로 구성한다. 로딩 전/실패 시 `EmptyView`.

```swift
import SwiftUI
import GoogleMobileAds
import UIKit

/// 채팅 리스트에 메시지 버블처럼 삽입되는 네이티브 광고(시안 B).
struct ChatNativeAdView: View {
    @StateObject private var loader = ChatNativeAdLoader()

    var body: some View {
        Group {
            if let ad = loader.nativeAd {
                NativeAdContainer(nativeAd: ad)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.trailing, 48)
            } else {
                EmptyView()
            }
        }
        .onAppear { if loader.nativeAd == nil { loader.load() } }
    }
}

private struct NativeAdContainer: UIViewRepresentable {
    let nativeAd: NativeAd

    func makeUIView(context: Context) -> NativeAdView {
        let adView = NativeAdView()

        let icon = UIImageView()
        icon.translatesAutoresizingMaskIntoConstraints = false
        icon.widthAnchor.constraint(equalToConstant: 32).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 32).isActive = true

        let headline = UILabel()
        headline.font = .systemFont(ofSize: 14, weight: .semibold)
        headline.numberOfLines = 2

        let advertiser = UILabel()
        advertiser.font = .systemFont(ofSize: 11)
        advertiser.textColor = .secondaryLabel

        let badge = UILabel()
        badge.text = "Ad"
        badge.font = .systemFont(ofSize: 10)
        badge.textColor = .secondaryLabel

        let media = MediaView()
        media.translatesAutoresizingMaskIntoConstraints = false
        media.heightAnchor.constraint(equalToConstant: 96).isActive = true

        let cta = UIButton(type: .system)
        cta.isUserInteractionEnabled = false

        let textStack = UIStackView(arrangedSubviews: [headline, advertiser])
        textStack.axis = .vertical
        let topRow = UIStackView(arrangedSubviews: [icon, textStack, badge])
        topRow.axis = .horizontal
        topRow.spacing = 8
        topRow.alignment = .top

        let container = UIStackView(arrangedSubviews: [topRow, media, cta])
        container.axis = .vertical
        container.spacing = 8
        container.translatesAutoresizingMaskIntoConstraints = false
        container.isLayoutMarginsRelativeArrangement = true
        container.layoutMargins = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)

        adView.addSubview(container)
        NSLayoutConstraint.activate([
            container.topAnchor.constraint(equalTo: adView.topAnchor),
            container.bottomAnchor.constraint(equalTo: adView.bottomAnchor),
            container.leadingAnchor.constraint(equalTo: adView.leadingAnchor),
            container.trailingAnchor.constraint(equalTo: adView.trailingAnchor),
        ])

        adView.headlineView = headline
        adView.advertiserView = advertiser
        adView.iconView = icon
        adView.mediaView = media
        adView.callToActionView = cta

        adView.backgroundColor = .secondarySystemGroupedBackground
        adView.layer.cornerRadius = 14
        adView.clipsToBounds = true
        return adView
    }

    func updateUIView(_ adView: NativeAdView, context: Context) {
        (adView.headlineView as? UILabel)?.text = nativeAd.headline
        (adView.advertiserView as? UILabel)?.text = nativeAd.advertiser ?? nativeAd.store ?? ""
        (adView.iconView as? UIImageView)?.image = nativeAd.icon?.image
        (adView.iconView as? UIImageView)?.isHidden = nativeAd.icon?.image == nil
        (adView.callToActionView as? UIButton)?.setTitle(nativeAd.callToAction ?? "자세히 보기", for: .normal)
        adView.mediaView?.mediaContent = nativeAd.mediaContent
        adView.nativeAd = nativeAd
    }
}
```

- [ ] **Step 2: ChatScreen.row에 분기 추가**

`ChatScreen.swift`의 `row(for:)`에서 마지막 `else if let p = item as? ChatItemProductCards { ... }` 다음에 추가:

```swift
        } else if item is ChatItemNativeAd {
            HStack {
                ChatNativeAdView()
                Spacer(minLength: 0)
            }
        }
```

- [ ] **Step 3: 빌드 확인**

Run:
```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode && \
xcodebuild -project CashChatIOS/CashChatIOS.xcodeproj -scheme CashChatIOS -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO | tail -5
```
Expected: BUILD SUCCEEDED

- [ ] **Step 4: 시뮬레이터 수동 검증**

Xcode에서 실행 → 채팅 N회 후 메시지 사이에 `Ad` 라벨 네이티브 광고 버블이 좌측 정렬로 보이는지, 실패 시 빈 자리로 흐름이 유지되는지 확인.

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/Ads/ChatNativeAdView.swift \
        apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift \
        apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj
git commit -m "feat: 채팅 리스트에 네이티브 광고 버블 연결(iOS)"
```

---

## Task 10: 전체 검증 + 스펙 대조

- [ ] **Step 1: shared 전체 테스트**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :shared:testDebugUnitTest`
Expected: PASS (신규 ChatStoreNativeAdTest 포함, 기존 테스트 회귀 없음)

- [ ] **Step 2: Android 빌드 + 린트**

Run: `cd apps/frontend && JAVA_HOME="$(/usr/libexec/java_home -v 21)" ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: iOS 빌드**

Run: 위 Task 9 Step 3 명령
Expected: BUILD SUCCEEDED

- [ ] **Step 4: 스펙 체크리스트 확인**

스펙(`2026-06-23-chat-inline-native-ad-design.md`) 대조:
- D1/D2 빈도·카운팅 → Task 3 테스트로 검증됨
- D3 책임 분리(shared placeholder + 플랫폼 렌더) → Task 1·3·6·9
- D4 시안 B UI(아이콘·헤드라인·별점·미디어·CTA·`Ad` 라벨) → Task 6·9
- D5 Android+iOS → 모든 Task
- D6 실패 시 빈 자리 → Task 6·8·9 (onFailed/EmptyView)

- [ ] **Step 5: 마무리 — finishing-a-development-branch 스킬로 통합 옵션 결정**
