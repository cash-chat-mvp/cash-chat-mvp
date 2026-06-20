# iOS Slice 0 — 기반(Koin/Flow 브리지) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** iOS(Swift)에서 브랜치의 `shared/commonMain` 데이터 레이어(Koin 그래프 + Kotlin Flow)에 접근할 수 있는 다리를 놓아, 이후 모든 iOS 기능 슬라이스가 그 위에서 동작하게 한다.

**Architecture:** `shared/iosMain`에 iOS용 Koin 부트스트랩(`doInitKoin`)과 Swift↔Flow 브리지(`KoinHelper`, `FlowCollector`)를 추가한다. iOS는 `KeychainTokenProvider`(브랜치 `TokenProvider` 구현)를 주입하고 앱 시작 시 Koin을 1회 초기화한다. 기존 iOS UI 골격/네비게이션은 변경하지 않는다(회귀 0).

**Tech Stack:** Kotlin Multiplatform, Koin(koin-core, commonMain 경유 iosMain 사용 가능), Ktor Darwin, SwiftUI, Security(Keychain).

---

## 배경/제약 (반드시 숙지)

- **dev 머지 금지.** 브랜치가 source of truth. dev의 `sharedModule(baseUrl, tokenProvider, engineProvider)`/`KoinIos`/`IosBridges`를 그대로 가져오지 말 것 — 시그니처가 다르다.
- 브랜치 DI 사실:
  - `sharedDataModule(rawBaseUrl: String)` 는 소비측이 먼저 `single<TokenProvider> { ... }` 를 등록한다는 전제로 동작한다. (`apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`)
  - HTTP 엔진은 expect/actual(`http1ClientEngine()`)로 iOS는 이미 Darwin 배선됨. **engineProvider 파라미터 없음.**
  - Koin singles(이미 존재): `ChatStore`, `AttendanceStore`, `HudStore`, `EvolutionStore`, `AdRewardStore`, `single<PointsRepository> { LocalPointsRepository() }`.
- 브랜치 `TokenProvider` 인터페이스 (`.../core/network/TokenProvider.kt`):
  ```kotlin
  interface TokenProvider {
      suspend fun accessToken(): String?
      suspend fun refresh(): Boolean
  }
  ```
- 토큰 재발급 계약(백엔드 `AuthController`): `POST /api/auth/refresh`, body `{"refreshToken": "..."}`, 응답 `AuthResponse { accessToken: String, refreshToken: String? , ... }`.
- iOS Keychain 키(기존 `ContentView.swift` AppState): access=`"access_token"`, refresh=`"refresh_token"`, role=`"role"`. 저장/조회는 `KeychainHelper.get/set/remove(forKey:)`.
- 빌드 시 `JAVA_HOME`은 JDK 21이어야 한다(프로젝트 CLAUDE.md):
  ```bash
  export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  ```

## File Structure

- Create: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt` — iOS Koin 부트스트랩(`doInitKoin`).
- Create: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt` — `KoinHelper`(store getter) + `FlowCollector`(Flow→Swift 콜백).
- Create: `apps/frontend/CashChatIOS/CashChatIOS/KeychainTokenProvider.swift` — 브랜치 `TokenProvider` 구현.
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift` — 앱 시작 시 `doInitKoin` 호출.

검증은 KMM 컴파일 + iOS 빌드 + 런타임 스모크로 한다(iosMain Koin/Darwin 코드는 commonTest 단위테스트 대상이 아님 — 정직하게 빌드/스모크로 검증).

---

### Task 1: iOS Koin 부트스트랩 `doInitKoin` 추가

**Files:**
- Create: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt`

- [ ] **Step 1: 파일 생성**

```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.core.network.TokenProvider
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * iOS 앱 시작 시 1회 호출. Swift 에서 KoinIosKt.doInitKoin(baseUrl:tokenProvider:) 로 접근.
 *
 * 브랜치 DI 규약: sharedDataModule(baseUrl) 는 소비측이 먼저 TokenProvider 를 등록한다는
 * 전제로 동작한다. dev 버전과 달리 engineProvider 파라미터가 없다(Darwin 은 actual 로 배선됨).
 */
fun doInitKoin(baseUrl: String, tokenProvider: TokenProvider) {
    startKoin {
        modules(
            module { single<TokenProvider> { tokenProvider } },
            sharedDataModule(baseUrl),
        )
    }
}
```

- [ ] **Step 2: KMM 컴파일로 검증**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL (미해결 참조 없음 — `sharedDataModule`, `TokenProvider`, `startKoin`, `module` 모두 해석됨).

---

### Task 2: Swift↔Flow 브리지 `KoinHelper` / `FlowCollector` 추가

**Files:**
- Create: `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: 파일 생성**

```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.ads.AdRewardStore
import com.nomadclub.cashchat.shared.attendance.AttendanceStore
import com.nomadclub.cashchat.shared.attendance.AttendanceUiState
import com.nomadclub.cashchat.shared.attendance.CheckInRewardEvent
import com.nomadclub.cashchat.shared.chat.ChatStore
import com.nomadclub.cashchat.shared.evolution.EvolutionStore
import com.nomadclub.cashchat.shared.hud.HudStore
import com.nomadclub.cashchat.shared.points.PointsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Swift 에서 Koin 그래프의 store 인스턴스에 접근하기 위한 헬퍼. */
class KoinHelper : KoinComponent {
    private val chat: ChatStore by inject()
    private val attendance: AttendanceStore by inject()
    private val points: PointsRepository by inject()
    private val hud: HudStore by inject()
    private val evolution: EvolutionStore by inject()
    private val adReward: AdRewardStore by inject()

    fun chatStore(): ChatStore = chat
    fun attendanceStore(): AttendanceStore = attendance
    fun pointsRepository(): PointsRepository = points
    fun hudStore(): HudStore = hud
    fun evolutionStore(): EvolutionStore = evolution
    fun adRewardStore(): AdRewardStore = adReward
}

/**
 * StateFlow/SharedFlow 를 Swift 콜백으로 브리지한다.
 * Swift 는 Flow 를 직접 구독하기 어렵기 때문에 메인 디스패처 스코프에서 collect 후 콜백을 호출한다.
 * Swift ViewModel 의 deinit 에서 cancel() 을 호출하지 않으면 무한 collect 코루틴이 살아남아
 * 메모리 누수가 발생하므로 반드시 취소해야 한다.
 */
class FlowCollector {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun collectAttendance(store: AttendanceStore, onEach: (AttendanceUiState) -> Unit) {
        scope.launch { store.state.collect { onEach(it) } }
    }

    fun collectRewards(store: AttendanceStore, onEach: (CheckInRewardEvent) -> Unit) {
        scope.launch { store.rewardEvents.collect { onEach(it) } }
    }

    fun collectBalance(repo: PointsRepository, onEach: (Long) -> Unit) {
        scope.launch { repo.balance.collect { onEach(it) } }
    }

    fun cancel() {
        scope.cancel()
    }
}
```

> 참고: chat/hud/evolution 의 Flow 구독 메서드는 각 기능 슬라이스에서 해당 store 의 실제
> state 타입을 확인한 뒤 `FlowCollector` 에 추가한다(지금은 attendance/points 만 — Slice 0 검증 범위).

- [ ] **Step 2: KMM 컴파일로 검증**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:compileKotlinIosSimulatorArm64
```
Expected: BUILD SUCCESSFUL. `ChatStore`/`HudStore`/`EvolutionStore`/`AdRewardStore`/`AttendanceStore`/`PointsRepository`/`AttendanceUiState`/`CheckInRewardEvent` 모두 해석됨. (해석 실패 시 해당 store 의 실제 패키지/타입명을 `git grep` 으로 확인해 import 수정.)

---

### Task 3: shared 프레임워크 빌드로 Swift 노출 확인

**Files:** (없음 — 빌드 검증 태스크)

- [ ] **Step 1: shared 디버그 빌드**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL. `KoinHelper`, `FlowCollector`, `KoinIosKt.doInitKoin` 가 `CashChatShared` 프레임워크 헤더에 노출된다(Obj-C 인터롭 대상).

- [ ] **Step 2: 커밋**

```bash
git add apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt \
        apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt
git commit -m "feat(ios): shared iosMain Koin 부트스트랩 및 Flow 브리지 추가"
```

---

### Task 4: `KeychainTokenProvider.swift` 추가 (브랜치 TokenProvider 구현)

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/KeychainTokenProvider.swift`

- [ ] **Step 1: 파일 생성**

`TokenProvider` 는 `CashChatShared` 프레임워크에서 노출되는 Kotlin 인터페이스다. Swift 에서
이를 구현한다. `accessToken()`/`refresh()` 는 Kotlin suspend 함수지만, 값을 반환만 하고 예외를
던지지 않으므로 Swift 에서는 async 완료 콜백(컴플리션) 형태로 노출된다. KMM 은 suspend 함수를
Swift 의 `completionHandler` 클로저로 브리지하므로 아래처럼 구현한다.

```swift
import Foundation
import CashChatShared

/// 브랜치 shared `TokenProvider` 의 iOS 구현.
/// 토큰 저장/조회는 기존 KeychainHelper, 재발급은 POST /api/auth/refresh 를 사용한다.
final class KeychainTokenProvider: NSObject, TokenProvider {
    private let baseUrl: String

    init(baseUrl: String = AppConfig.apiBaseUrl) {
        self.baseUrl = baseUrl.hasSuffix("/") ? String(baseUrl.dropLast()) : baseUrl
    }

    // Kotlin: suspend fun accessToken(): String?
    func accessToken() async throws -> String? {
        KeychainHelper.get(forKey: "access_token")
    }

    // Kotlin: suspend fun refresh(): Boolean
    func refresh() async throws -> KotlinBoolean {
        guard let refreshToken = KeychainHelper.get(forKey: "refresh_token") else {
            return KotlinBoolean(bool: false)
        }
        guard let url = URL(string: "\(baseUrl)/api/auth/refresh") else {
            return KotlinBoolean(bool: false)
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: ["refreshToken": refreshToken])

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                return KotlinBoolean(bool: false)
            }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let newAccess = json["accessToken"] as? String else {
                return KotlinBoolean(bool: false)
            }
            KeychainHelper.set(newAccess, forKey: "access_token")
            if let newRefresh = json["refreshToken"] as? String {
                KeychainHelper.set(newRefresh, forKey: "refresh_token")
            }
            return KotlinBoolean(bool: true)
        } catch {
            return KotlinBoolean(bool: false)
        }
    }
}
```

> 주의: Swift 에서 Kotlin suspend 함수를 구현할 때 반환 타입은 KMM 브리지 규약을 따른다.
> `Boolean` 은 `KotlinBoolean` 으로, nullable `String?` 은 `String?` 로 매핑된다. 실제 생성된
> 프레임워크 헤더(`CashChatShared.h` 또는 Xcode 자동완성)에서 `TokenProvider` 프로토콜의 정확한
> 시그니처를 확인하고, 다르면 그에 맞춰 시그니처를 조정한다(예: completionHandler 형태일 경우
> async 대신 `completionHandler:` 클로저로 구현).

- [ ] **Step 2: 컴파일 검증은 Task 6(Xcode 빌드)에서 수행**

(이 파일 단독으로는 빌드할 수 없고 프레임워크 임베드가 필요하므로 Task 6에서 일괄 검증.)

---

### Task 5: 앱 시작 시 Koin 초기화 연결

**Files:**
- Modify: `apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift`

- [ ] **Step 1: 현재 내용 확인**

```bash
cat apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift
```
Expected: `@main struct CashChatIOSApp: App { ... }` 형태. `init()` 유무 확인.

- [ ] **Step 2: `init()` 에서 doInitKoin 호출 추가**

`import CashChatShared` 를 추가하고, `App` 의 `init()` 에서(없으면 추가) 다음을 호출한다.
중복 초기화(startKoin 두 번 호출 시 크래시)를 막기 위해 1회만 실행되도록 가드한다.

```swift
import SwiftUI
import CashChatShared

@main
struct CashChatIOSApp: App {
    init() {
        KoinIosKt.doInitKoin(
            baseUrl: AppConfig.apiBaseUrl,
            tokenProvider: KeychainTokenProvider()
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

> 기존 `body`/기타 설정이 있으면 보존하고 `init()` 만 추가한다. SwiftUI `App` 의 `init()` 은
> 앱 생명주기당 1회 호출되므로 별도 가드 불필요(단, 프리뷰/테스트에서 중복 호출되지 않는지 확인).

---

### Task 6: 프레임워크 임베드 + Xcode 빌드 + 런타임 스모크

**Files:** (없음 — 통합 검증 태스크)

- [ ] **Step 1: shared 프레임워크 임베드/사인**

```bash
cd apps/frontend && export JAVA_HOME="$(/usr/libexec/java_home -v 21)" && ./gradlew :shared:embedAndSignAppleFrameworkForXcode
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Xcode 빌드**

`apps/frontend/CashChatIOS/CashChatIOS.xcodeproj` 를 Xcode 에서 열고 시뮬레이터 타겟으로 빌드(⌘B).
Expected: 빌드 성공. `KeychainTokenProvider` 가 `TokenProvider` 를 만족하고 `KoinIosKt.doInitKoin` 가 해석됨.

- [ ] **Step 3: 런타임 스모크 — Koin 그래프 접근 확인**

`ContentView.swift` 의 `AppState.restoreSession()` 끝 또는 `ContentView.task` 안에 임시 디버그 로그를 넣어 store 인스턴스를 얻는지 확인한다(검증 후 제거).

```swift
#if DEBUG
let _store = KoinHelper().attendanceStore()
print("✅ Koin 그래프 접근 OK: \(_store)")
#endif
```
앱 실행 → Xcode 콘솔에 `✅ Koin 그래프 접근 OK:` 출력 확인. startKoin 크래시 없음.
확인 후 임시 로그 제거.

- [ ] **Step 4: 회귀 확인**

앱을 실행해 기존 동작이 유지되는지 확인:
- 로그인 화면(`OnboardingView`) 정상 표시 / 로그인 플로우 동작
- 로그인 후 4탭(채팅·리워드·상점·마이페이지) 네비게이션 그대로 동작(목업 상태 유지)

- [ ] **Step 5: 커밋**

```bash
git add apps/frontend/CashChatIOS/CashChatIOS/KeychainTokenProvider.swift \
        apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift
git commit -m "feat(ios): KeychainTokenProvider 추가 및 앱 시작 시 Koin 초기화 연결"
```

---

## Self-Review (작성자 점검 완료)

- **Spec 커버리지:** 본 플랜은 spec 문서 `2026-06-18-ios-feature-parity-design.md` 의 **Slice 0** 전체를 구현한다(KoinIos.doInitKoin / IosBridges KoinHelper·FlowCollector / KeychainTokenProvider / 앱 부트스트랩 / 빌드·스모크 검증). Slice 1(채팅) 이후는 별도 플랜 대상.
- **플레이스홀더:** 없음. 모든 코드 단계에 완전한 코드 포함. "보류 사항"(로그아웃 토큰 캐시 정리)은 spec에 명시된 의도적 범위 제외이며 본 플랜 작업 항목 아님.
- **타입 일관성:** `TokenProvider.accessToken()/refresh()`, `KoinHelper().attendanceStore()`, `KoinIosKt.doInitKoin(baseUrl:tokenProvider:)`, Keychain 키(`access_token`/`refresh_token`) 가 전 태스크에서 일관됨.
- **알려진 리스크:** KMM→Swift suspend 브리지 시그니처(`KotlinBoolean` vs `completionHandler`)는 실제 생성 헤더에 따라 달라질 수 있어 Task 4/6에서 헤더 확인 후 조정하도록 명시함.
