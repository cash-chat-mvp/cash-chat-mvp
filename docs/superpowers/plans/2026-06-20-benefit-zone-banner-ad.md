# 배너 광고 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AdMob 배너 광고를 채팅 화면 헤더 아래와 혜택존 출석 위젯 아래에 노출한다(Android + iOS).

**Architecture:** 광고 뷰는 플랫폼 UI 레이어에 둔다(Android Composable / iOS UIViewRepresentable). 슬롯 식별·노출 플래그만 KMM shared에 둔다. 광고 단위 ID는 기존 `AppConfig`(Android는 이미 존재, iOS는 신규 추가)에서 주입한다. 적립 흐름 없음(노출형 수익).

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose, AndroidView(`com.google.android.gms.ads.AdView`), SwiftUI `UIViewRepresentable`(`GADBannerView`), Koin, Kotest(commonTest).

**전제:** 개발 빌드는 Google 테스트 배너 ID 사용. 실 ID 전환은 릴리즈 영역(spec §7).

---

## File Structure

| 파일 | 책임 | 신규/수정 |
|---|---|---|
| `shared/.../shared/ads/BannerAdSlot.kt` | 배너 위치 enum (CHAT_TOP, BENEFIT_TOP) + 노출 여부 | 신규 |
| `shared/.../core/config/FeatureFlags.kt` | `BANNER_ADS` 플래그 추가 | 수정 |
| `shared/src/commonTest/.../ads/BannerAdSlotTest.kt` | enum·노출 분기 테스트 | 신규 |
| `app/.../ads/BannerAd.kt` | Android 배너 Composable (AdView 래핑) | 신규 |
| `app/.../feature/chat/ChatScreen.kt` | 헤더 divider 아래 배너 삽입 | 수정 |
| `app/.../feature/rewards/BenefitZoneScreen.kt` | 출석 위젯 아래 배너 삽입 | 수정 |
| `CashChatIOS/.../Ads/BannerAdView.swift` | iOS 배너 뷰 (GADBannerView 래핑) | 신규 |
| `CashChatIOS/.../AppConfig.swift` + `Secrets.swift` | `admobBannerAdUnitId` 키 추가 | 수정 |
| `CashChatIOS/.../ChatScreen.swift` | 헤더 아래 배너 삽입 | 수정 |
| `CashChatIOS/.../BenefitZoneScreen.swift` | 출석 아래 배너 삽입 | 수정 |

---

## Task 1: 슬롯 enum + 노출 플래그 (KMM shared, TDD)

**Files:**
- Create: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/BannerAdSlot.kt`
- Modify: `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/config/FeatureFlags.kt`
- Test: `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/ads/BannerAdSlotTest.kt`

- [ ] **Step 1: Write the failing test**

`apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/ads/BannerAdSlotTest.kt`:

```kotlin
package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.core.config.FeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BannerAdSlotTest {

    @Test
    fun `analyticsName 은 슬롯별 소문자 식별자를 반환한다`() {
        assertEquals("chat_top", BannerAdSlot.CHAT_TOP.analyticsName)
        assertEquals("benefit_top", BannerAdSlot.BENEFIT_TOP.analyticsName)
    }

    @Test
    fun `BANNER_ADS 플래그가 켜져 있으면 모든 슬롯이 노출 가능하다`() {
        // 현재 빌드 상수 기준: 플래그 on
        assertTrue(FeatureFlags.BANNER_ADS)
        BannerAdSlot.entries.forEach { slot ->
            assertEquals(FeatureFlags.BANNER_ADS, slot.isEnabled())
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*BannerAdSlotTest*"`
Expected: 컴파일 실패 (`BannerAdSlot` / `FeatureFlags.BANNER_ADS` 미정의)

- [ ] **Step 3: Add the flag**

`FeatureFlags.kt` 의 object 안에 한 줄 추가(기존 상수들 아래):

```kotlin
    const val BANNER_ADS = true            // 배너 광고 전역 on/off
```

- [ ] **Step 4: Create the enum**

`BannerAdSlot.kt`:

```kotlin
package com.nomadclub.cashchat.shared.ads

import com.nomadclub.cashchat.shared.core.config.FeatureFlags

/**
 * 배너 광고 노출 위치. 광고 단위 ID는 슬롯 공통(AppConfig.admobBannerAdUnitId)이며,
 * 슬롯은 Analytics 구분·위치별 제어 목적의 식별자다.
 */
enum class BannerAdSlot(val analyticsName: String) {
    CHAT_TOP("chat_top"),
    BENEFIT_TOP("benefit_top");

    /** 이 슬롯에 배너를 노출해도 되는지. 현재는 전역 플래그만 본다. */
    fun isEnabled(): Boolean = FeatureFlags.BANNER_ADS
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*BannerAdSlotTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/ads/BannerAdSlot.kt \
        apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/config/FeatureFlags.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/ads/BannerAdSlotTest.kt
git commit -m "feat(ads): 배너 슬롯 enum·노출 플래그 추가 (shared)"
```

---

## Task 2: Android 배너 Composable

**Files:**
- Create: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/BannerAd.kt`

배너는 광고 SDK 뷰라 단위 테스트가 어렵다 → 실행 검증(에뮬레이터)으로 확인한다.

- [ ] **Step 1: Create the Composable**

`apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/BannerAd.kt`:

```kotlin
package com.nomadclub.cashchat.ads

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.nomadclub.cashchat.config.AppConfig
import com.nomadclub.cashchat.shared.ads.BannerAdSlot
import org.koin.compose.koinInject

/**
 * AdMob 적응형(anchored adaptive) 배너.
 * - 로드 실패 시 슬롯을 숨긴다(높이 0) → 레이아웃 깨짐 방지.
 * - AdView 는 remember 로 보존하고 onDispose 에서 destroy 한다.
 */
@Composable
fun BannerAd(
    slot: BannerAdSlot,
    modifier: Modifier = Modifier,
    appConfig: AppConfig = koinInject(),
) {
    if (!slot.isEnabled()) return

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp

    // 로드 실패/미완료 시 숨기기 위한 상태
    var visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
    if (!visible) return

    val adView = remember(slot) {
        AdView(context).apply {
            adUnitId = appConfig.admobBannerAdUnitId
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp))
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("BannerAd", "배너 로드 실패(${slot.analyticsName}): ${error.message}")
                    visible = false
                }
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth(),
    )
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `cd apps/frontend && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

> 전제: `local.properties` 에 `ADMOB_BANNER_AD_UNIT_ID` 가 비어 있으면 `AdView` 가 빈 adUnitId 로 크래시한다.
> dev 는 Google 테스트 배너 ID `ca-app-pub-3940256099942544/9214589741` 를 넣어 둔다.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/ads/BannerAd.kt
git commit -m "feat(ads): android 적응형 배너 Composable 추가"
```

---

## Task 3: Android 배너 노출 위치 연동

**Files:**
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt:195`
- Modify: `apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt:111`

- [ ] **Step 1: 채팅 헤더 아래 배너 삽입**

`ChatScreen.kt` 의 `HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)` (195행) 바로 다음 줄에 추가:

```kotlin
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        com.nomadclub.cashchat.ads.BannerAd(
            slot = com.nomadclub.cashchat.shared.ads.BannerAdSlot.CHAT_TOP,
        )
```

- [ ] **Step 2: 혜택존 출석 위젯 아래 배너 삽입**

`BenefitZoneScreen.kt` 의 `item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }` (111행) 바로 다음에 새 item 추가:

```kotlin
            item { AttendanceWidget(state = state, onCheckIn = store::checkIn) }
            item {
                com.nomadclub.cashchat.ads.BannerAd(
                    slot = com.nomadclub.cashchat.shared.ads.BannerAdSlot.BENEFIT_TOP,
                )
            }
```

- [ ] **Step 3: Build**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 에뮬레이터 실행 검증**

에뮬레이터에서 디버그 APK 실행 → 채팅 화면 헤더 아래, 혜택존 출석 아래에 **"Test Ad" 라벨이 붙은 배너**가 보이는지 확인. 로그캣에 `BannerAd` 실패 경고가 없는지 확인.

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/chat/ChatScreen.kt \
        apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt
git commit -m "feat(ads): android 채팅·혜택존 배너 슬롯 연동"
```

---

## Task 4: iOS 배너 키 + 배너 뷰

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/Secrets.swift` (dev: 테스트 ID)
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift`
- Create: `apps/frontend/CashChatIOS/CashChatIOS/Ads/BannerAdView.swift`

- [ ] **Step 1: Secrets 에 배너 단위 ID 추가**

`Secrets.swift` 에 항목 추가 (dev 빌드는 Google 공식 테스트 배너 ID 사용):

```swift
    static let admobBannerAdUnitId = "ca-app-pub-3940256099942544/2934735716"  // Google 테스트 배너 ID
```

> 실 ID 는 릴리즈 시 교체(spec §7, 릴리즈 체크리스트).

- [ ] **Step 2: AppConfig 에 키 노출**

`AppConfig.swift` 의 `admobRewardedAdUnitId` 아래에 추가:

```swift
    static let admobBannerAdUnitId: String = required(Secrets.admobBannerAdUnitId, key: "admobBannerAdUnitId")
```

- [ ] **Step 3: 배너 뷰 생성**

`apps/frontend/CashChatIOS/CashChatIOS/Ads/BannerAdView.swift`:

```swift
import SwiftUI
import GoogleMobileAds
import UIKit

/// AdMob 적응형 배너. 로드 실패 시 높이 0으로 접어 레이아웃을 보존한다.
/// Android BannerAd Composable 과 동형(slot: chat_top / benefit_top).
struct BannerAdView: UIViewRepresentable {
    let slotName: String

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> BannerView {
        let width = UIScreen.main.bounds.width
        let banner = BannerView(adSize: currentOrientationAnchoredAdaptiveBanner(width: width))
        banner.adUnitID = AppConfig.admobBannerAdUnitId
        banner.delegate = context.coordinator
        if let root = Self.rootViewController() {
            banner.rootViewController = root
        }
        banner.load(Request())
        return banner
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?.rootViewController
    }

    final class Coordinator: NSObject, BannerViewDelegate {
        func bannerView(_ bannerView: BannerView, didFailToReceiveAdWithError error: Error) {
            print("배너 로드 실패: \(error.localizedDescription)")
            bannerView.isHidden = true
        }
    }
}
```

- [ ] **Step 4: shared framework 빌드 후 Xcode 빌드 확인**

Run:
```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
그다음 Xcode 에서 `CashChatIOS` 빌드(⌘B). 새 `.swift` 파일은 동기화 그룹으로 자동 포함됨.
Expected: 빌드 성공

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/Ads/BannerAdView.swift \
        apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift
git commit -m "feat(ads): ios 배너 단위 ID·BannerAdView 추가"
```

> 확인됨: `Secrets.swift` 는 .gitignore 대상(TRACKED 아님)이다. **커밋에서 제외**하고,
> 키는 로컬 `Secrets.swift` 에만 추가한다. 커밋 대상은 `AppConfig.swift`(+ Secrets 템플릿이 있으면 그쪽)뿐이다.

---

## Task 5: iOS 배너 노출 위치 연동

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift`
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift`

- [ ] **Step 1: 채팅 헤더 아래 배너 삽입**

`ChatScreen.swift` 에서 헤더(아바타·Lv·메뉴 행) 바로 아래, 메시지 리스트 위에 추가. `FeatureFlags.shared.BANNER_ADS` 가 shared 로 노출되지 않으면 Swift 상수로 분기하지 말고 항상 노출하되 실패 시 숨김 동작에 맡긴다:

```swift
BannerAdView(slotName: "chat_top")
    .frame(height: 50)
```

(헤더 컨테이너 VStack 안, Divider 다음 위치. 정확한 위치는 ChatScreen.swift 의 헤더 블록 직후.)

- [ ] **Step 2: 혜택존 출석 아래 배너 삽입**

`BenefitZoneScreen.swift` 에서 출석 위젯 뷰 바로 아래에 추가:

```swift
BannerAdView(slotName: "benefit_top")
    .frame(height: 50)
```

- [ ] **Step 3: shared framework + Xcode 빌드**

Run:
```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
Xcode 에서 빌드 + 시뮬레이터 실행 → 채팅 헤더 아래·혜택존 출석 아래 테스트 배너 노출 확인.

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/ChatScreen.swift \
        apps/frontend/CashChatIOS/CashChatIOS/BenefitZoneScreen.swift
git commit -m "feat(ads): ios 채팅·혜택존 배너 슬롯 연동"
```

---

## Self-Review 결과

- **Spec 커버리지:** §1 아키텍처(Task1·2·4), §2 동작/실패숨김(Task2·4), §3 위치(Task3·5), §4 플래그(Task1), §5 iOS(Task4·5), §6 테스트(Task1 + 실행검증). 전부 매핑됨.
- **플래그 정합성:** spec 수정으로 `FeatureFlags.BANNER_ADS`(컴파일타임) 사용 — RemoteConfig 아님. 일치.
- **iOS 키:** iOS `AppConfig` 에 배너 ID 없던 문제 → Task4 에서 추가.
- **타입 일관성:** `BannerAdSlot`/`analyticsName`/`isEnabled()`/`admobBannerAdUnitId` 명칭 전 태스크 일치.
- **주의:** flavor 명(`devDebug` 등)·iOS 헤더 정확한 삽입 지점·`Secrets.swift` gitignore 여부는 실행 시 확인 필요(각 태스크에 명시).
