# 혜택존(Benefit Zone) Phase F+1 진행 로그

## Task 0: 의존성 추가
- 상태: ✅ 완료
- 변경 파일:
  - `apps/frontend/gradle/libs.versions.toml`
  - `apps/frontend/shared/build.gradle.kts`
- 검증:
  - `./gradlew :shared:dependencies --configuration commonMainImplementation -q | grep -i ktor` → `io.ktor:ktor-client-auth:2.3.12 (n)` 확인됨
  - `./gradlew :shared:dependencies --configuration commonTestImplementation -q | grep -iE "ktor|coroutines-test|kotlin-test"` → `kotlin-test`, `ktor-client-mock:2.3.12`, `kotlinx-coroutines-test:1.9.0` 모두 확인됨 (coroutines-core와 동일 버전 1.9.0)
  - `./gradlew :shared:compileKotlinMetadata -q` → 성공 (출력 없음, 에러 없음)
- 인계 메모:
  - 기존 coroutines 버전 키 이름은 `kotlinxCoroutines` (값 `1.9.0`)이며, 일반적인 `coroutines` 키는 toml에 없음. 플랜 문서의 `coroutines = "1.8.1"` 안내는 이 저장소 상황과 다르므로 **새 키를 만들지 않고 기존 `kotlinxCoroutines` ref를 재사용**함 — `kotlinx-coroutines-test`도 `version.ref = "kotlinxCoroutines"`로 선언됨.
  - ktor 버전 키 이름은 `ktor` (값 `2.3.12`).
  - `commonTest` 블록은 기존 파일 스타일(`commonMain.dependencies {}` 축약형)에 맞춰 `commonTest.dependencies {}`로 추가함.

## Task 1: TokenProvider + ApiConfig
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/TokenProvider.kt` (신규)
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/ApiConfig.kt` (신규)
- 검증:
  - `./gradlew :shared:compileKotlinMetadata -q` → 성공 (출력 없음, 에러 없음)
- 인계 메모:
  - 시그니처는 플랜 명세 그대로 구현(메서드명/파라미터명 변경 없음). `core/network` 패키지 신규 생성.
  - 후속 Task(AuthenticatedApiClient, Android DataStoreTokenProvider, iOS KeychainTokenProvider, Koin 모듈)는 이 인터페이스에 의존 가능.

## Task 2-3: AuthenticatedApiClient (TDD)
- 상태: ✅
- 변경 파일:
  - shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClientTest.kt (신규)
  - shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/core/network/AuthenticatedApiClient.kt (신규)
- 검증: `./gradlew :shared:testDebugUnitTest --tests "*AuthenticatedApiClientTest*"` → 테스트 2개 PASS (BUILD SUCCESSFUL)
  - Task 2 단계: AuthenticatedApiClient 미존재로 컴파일 에러 FAIL 확인 후 진행
  - Task 3 구현 1차 시도에서 `request.url.encodedPath` 가 Unresolved reference 컴파일 에러 발생
    → `encodedPath` 는 `io.ktor.http` 의 URLBuilder 확장 프로퍼티로, 명시적 import(`io.ktor.http.encodedPath`) 필요. import 추가 후 컴파일/테스트 모두 통과.
- 인계 메모: AuthenticatedApiClient(config, tokenProvider, engine) 시그니처와 `.httpClient` 프로퍼티는 스펙대로 유지됨 — 후속 Koin 모듈에서 `AuthenticatedApiClient(get(), get(), engineProvider())`, `get<AuthenticatedApiClient>().httpClient` 형태로 바로 사용 가능.

## Task 4: Android TokenProvider 어댑터
- 상태: ✅
- 변경 파일:
  - `apps/frontend/app/src/main/java/com/nomadclub/cashchat/core/data/DataStoreTokenProvider.kt` (신규)
  - `apps/frontend/app/src/main/java/com/nomadclub/cashchat/core/data/TokenDataStore.kt` (수정: `updateTokensBlocking` 추가)
- 검증: `./gradlew :app:compileDebugKotlin -q` → 성공 (출력 없음, 에러 없음)
  - 참고: 이 모듈에는 dev/prod flavor가 없어 `compileDevDebugKotlin` 태스크가 존재하지 않음 → `compileDebugKotlin` 사용
- 인계 메모:
  - 실제 키 상수명은 `KEY_ACCESS_TOKEN`, `KEY_REFRESH_TOKEN` (둘 다 `stringPreferencesKey`, private companion object) — 스펙과 일치.
  - `getOrCreateDeviceTokenBlocking()`이 실제로 존재함 (스펙 그대로 사용).
  - `androidx.datastore.preferences.core.edit`는 이미 import 되어 있어 추가 import 불필요. `runBlocking`도 기존 import 재사용.
  - `updateTokensBlocking`은 기존 `*Blocking` 함수들 바로 아래(파일 끝)에 추가함.

## Task 5: 출석 DTO 모델
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/model/AttendanceModels.kt` (신규)
- 검증:
  - `./gradlew :shared:compileKotlinMetadata -q` → 성공 (출력 없음, 에러 없음)
- 인계 메모:
  - `BonusItem`, `RewardPreview`, `MonthlyAttendance`, `CheckInResult` 4개 데이터 클래스 모두 `@Serializable` 적용, 필드명/타입 스펙과 정확히 일치(coin/awardedCoin: Long, dayCount/year/month/currentStreak/streakDayCount/quantity: Int, todayChecked: Boolean).
  - 기존 빈 디렉터리 `attendance/`가 이미 존재했으나 그 하위 `model/` 패키지 및 파일은 신규 생성.
  - 후속 Task(AttendanceApiService, AttendanceStore)는 이 패키지(`com.nomadclub.cashchat.shared.attendance.model`)에서 바로 import 가능.

## Task 6-7: AttendanceApiService (TDD)
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceApiServiceTest.kt` (신규, Task 6)
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceApiService.kt` (신규, Task 7)
- 검증:
  - RED: `./gradlew :shared:testDebugUnitTest --tests "*AttendanceApiServiceTest*"` → 컴파일 실패 (`Unresolved reference 'AttendanceApiService'`, `'itemCode'`) — 예상대로 실패 확인
  - GREEN: 동일 명령 재실행 → BUILD SUCCESSFUL, 테스트 결과 XML에서 `tests="2" failures="0" errors="0"` 확인 (getMonthly/checkIn 둘 다 PASS)
- 인계 메모:
  - 시그니처 `AttendanceApiService(config: ApiConfig, httpClient: HttpClient)`로 스펙과 일치 — 후속 Koin 모듈에서 `AttendanceApiService(get(), get<AuthenticatedApiClient>().httpClient)` 형태로 바로 사용 가능.
  - `getMonthly`/`checkIn` 모두 `@Throws(CancellationException::class, Exception::class)` 적용 (iOS 크래시 방지).
  - `getMonthly(year, month)`는 둘 다 nullable이며 null이면 쿼리 파라미터를 생략 — MockEngine 테스트에서는 항상 값이 전달되므로 경로(`/api/attendance/me`)만 검증.

## Task 8: PointsRepository 격리
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/points/PointsRepository.kt` (신규)
- 검증: `./gradlew :shared:compileKotlinMetadata -q` → 성공 (출력 없음, 에러 없음)
- 인계 메모: GET /api/points/me BE 준비 시 RemotePointsRepository로 교체

## Task 9-10: AttendanceStore (TDD)
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceStoreTest.kt` (신규, Task 9)
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/attendance/AttendanceStore.kt` (신규, Task 10)
- 검증:
  - RED: `./gradlew :shared:testDebugUnitTest --tests "*AttendanceStoreTest*"` → 컴파일 실패 (`Unresolved reference 'AttendanceStore'` 등) — 예상대로 실패 확인
  - GREEN: 동일 명령 재실행 → BUILD SUCCESSFUL, 테스트 결과 XML `tests="2" failures="0" errors="0"` (loadMonthly/checkIn 둘 다 PASS)
  - 전체: `./gradlew :shared:cleanTestDebugUnitTest :shared:testDebugUnitTest` → BUILD SUCCESSFUL, 3개 테스트 클래스(AttendanceApiServiceTest, AttendanceStoreTest, AuthenticatedApiClientTest) 총 6개 테스트 전부 PASS, failures=0 errors=0
- 인계 메모:
  - **runTest hang 이슈 발견 및 해결**: `loadMonthly`/`checkIn` 최초 구현에서 `_state.update { it.copy(isLoading = true, ...) }`를 `scope.launch` 블록 *내부* 맨 앞에 두었더니, `state.first { !it.isLoading }`가 (StateFlow 초기값이 이미 `isLoading=false`이므로) launch된 코루틴이 실행되기 전에 즉시 매칭되어 `loadMonthly` 결과 반영 전 상태를 반환 → 1번째 테스트는 `expected:[1,2] but was:[]`로 즉시 실패, 2번째 테스트는 `checkIn()`이 (이전 `loadMonthly`의 `todayChecked` 갱신을 못 보고) 거듭 호출되며 `UncompletedCoroutinesError`(1분 타임아웃)로 행(hang) 발생.
  - **해결**: `isLoading = true` / `isCheckingIn = true` 설정을 `scope.launch {}` 호출 *직전*, 즉 동기 컨텍스트에서 수행하도록 변경(네트워크 호출과 결과 반영만 launch 내부에 유지). 이렇게 하면 `loadMonthly()`/`checkIn()` 호출 시점에 StateFlow가 즉시 로딩 상태로 전이되어 `first { !it.isLoading }`/순차 호출이 올바른 순서로 동기화됨. 테스트 단언/시그니처는 변경하지 않음.
  - `checkIn`은 `todayChecked || isCheckingIn` 가드로 중복 출석 방지.
  - 시그니처 `AttendanceStore(service, pointsRepository, scope)`, 노출 `state: StateFlow<AttendanceUiState>`, `rewardEvents: SharedFlow<CheckInRewardEvent>`, `loadMonthly(year?, month?)`, `checkIn()` — 후속 Koin(`AttendanceStore(get(), get(), get())`)/UI(`store.state`, `store.rewardEvents`, `store.loadMonthly()`, `store::checkIn`) 패턴과 일치.

## Task 11: Koin shared 모듈
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt` (신규)
- 검증: `./gradlew :shared:compileKotlinMetadata -q` → 성공(출력 없음, 에러 없음). `./gradlew :shared:compileKotlinIosSimulatorArm64 -q`(JAVA_HOME=Android Studio JBR 21 사용) → 성공(출력 없음, 에러 없음). `single { tokenProvider }`처럼 외부 캡처 인스턴스를 직접 등록하는 패턴도 문제없이 컴파일됨.
- 인계 메모: 플랫폼이 baseUrl/TokenProvider/engineProvider 주입 필요 (Android Task 12, iOS Task 17)

## Task 12: Android DI 배선
- 상태: ✅
- 변경 파일:
  - app/src/main/java/com/nomadclub/cashchat/CashChatApplication.kt (sharedModule을 startKoin modules에 결합)
  - app/src/main/java/com/nomadclub/cashchat/di/AppModule.kt (DataStoreTokenProvider single 추가)
  - app/build.gradle.kts (implementation(libs.ktor.client.okhttp) 추가)
- 검증: `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL (app-debug.apk 생성 확인)
- 인계 메모:
  - Application 클래스: com.nomadclub.cashchat.CashChatApplication (단일 startKoin 호출 지점)
  - 실제 빌드 태스크명: :app:assembleDebug (product flavor 없음, compileDebugKotlin 경유)
  - ktor-client-okhttp: app/build.gradle.kts에 신규 추가함 (기존엔 없었음, OkHttp.create() 사용 위해 필요)

## Task 13: AttendanceWidget Composable
- 상태: ✅
- 변경 파일:
  - apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/rewards/AttendanceWidget.kt (신규)
- 검증: `./gradlew :app:compileDebugKotlin -q` → BUILD SUCCESSFUL (출력 없음, 에러 없음)
- 인계 메모:
  - 시그니처 `AttendanceWidget(state: AttendanceUiState, onCheckIn: () -> Unit, modifier: Modifier = Modifier)` — Task 14(BenefitZoneScreen `AttendanceWidget(state = state, onCheckIn = store::checkIn)`)와 일치.
  - 컬러: 완료 #5C6BFA(Primary), 오늘 #FFB800(Accent), 미출석 #E0DCEF(Unchecked), 배경 그라데이션 #E8E1FF→#FAFBFF, 버튼 48dp/Primary.
  - import는 스펙의 `androidx.compose.foundation.layout.*` 와일드카드 대신 명시적 import(Box, Column, Spacer, Arrangement, fillMaxWidth, height, padding, size)로 작성 — 동일하게 컴파일 성공.

## Task 14: BenefitZoneScreen
- 상태: ✅
- 변경 파일: app/src/main/java/com/nomadclub/cashchat/feature/rewards/BenefitZoneScreen.kt (신규 생성)
- 검증: `./gradlew :app:compileDebugKotlin -q` → BUILD SUCCESSFUL
- 인계 메모: koinInject는 별도 의존성 추가 없이 해결됨. `koin-androidx-compose:3.5.6`이 `koin-compose:1.1.5`를 transitively 가져오며 여기에 `org.koin.compose.koinInject`가 포함되어 있음(koin-compose 아티팩트는 3.x가 아닌 1.x 버전 체계 사용 — `version.ref = "koinCompose"`(=3.5.6)로 alias를 추가했더니 `Could not find io.insert-koin:koin-compose:3.5.6` 에러 발생, 즉시 롤백). gradle/libs.versions.toml과 app/build.gradle.kts는 변경 없이 원상 유지. `BenefitZoneScreen()` 무인자 호출 가능(Task 15 라우팅 대비), AttendanceWidget 연결·rewardEvents 토스트("출석 완료! 🪙+{amount}")·balance 표시 모두 스펙대로 구현.

## Task 15: MainScreen 라우팅 교체
- 상태: ✅
- 변경 파일: apps/frontend/app/src/main/java/com/nomadclub/cashchat/feature/main/MainScreen.kt (import RewardsScreen → BenefitZoneScreen 교체, REWARDS 탭 composable 블록을 `BenefitZoneScreen()` 무인자 호출로 교체)
- 검증: `./gradlew :app:compileDebugKotlin -q` → BUILD SUCCESSFUL(출력 없음). `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL (app-debug.apk 생성 확인, app/build/outputs/apk/debug/app-debug.apk)
- 인계 메모: 수동 런타임 검증 PENDING(기기 필요) — 로그인→혜택존 탭→출석 도장→토스트/코인/버튼 비활성 확인 필요. RewardsScreen.kt 파일은 보존됨(grep 결과 MainScreen.kt 외 다른 곳에서 사용되지 않아 import만 교체, 파일 삭제 안 함).

## Task 17/18 (iOS Kotlin 브리지): KoinIos.kt + IosBridges.kt
- 상태: ✅
- 변경 파일:
  - `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt`
  - `apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`
- 검증: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:compileKotlinIosSimulatorArm64 -q` → BUILD SUCCESSFUL (출력 없음, 에러 없음)
- 인계 메모: Swift에서 `KoinIosKt.doInitKoin(baseUrl:tokenProvider:)`, `KoinHelper().attendanceStore()/pointsRepository()`, `FlowCollector().collectAttendance/collectRewards/collectBalance`로 사용 가능. `Dispatchers.Main`은 iOS 네이티브 타깃에서 컴파일/링크 문제 없이 그대로 사용됨(별도 대응 불필요).

## Task 16 + 18 (iOS Swift): KeychainTokenProvider + 출석 ViewModel + RewardsView 연동
- 상태: ⚠️ (빌드 미검증 — 사용자 Xcode 필요)
- 변경 파일:
  - `apps/frontend/CashChatIOS/CashChatIOS/KeychainTokenProvider.swift` (신규)
  - `apps/frontend/CashChatIOS/CashChatIOS/BenefitZone/AttendanceViewModel.swift` (신규, `AttendanceViewModel` + `AttendanceWidgetView`)
  - `apps/frontend/CashChatIOS/CashChatIOS/CashChatIOSApp.swift` (수정: `import CashChatShared` 추가, `init()`에서 `KoinIosKt.doInitKoin(baseUrl:tokenProvider:)` 1회 호출)
  - `apps/frontend/CashChatIOS/CashChatIOS/ContentView.swift` (수정: `RewardsView`에 `@StateObject private var attendanceVM = AttendanceViewModel()` 추가, ScrollView > VStack(spacing:14) 최상단에 `AttendanceWidgetView(vm: attendanceVM).padding(.horizontal, 20)` 삽입, 기존 `.onAppear` 애니메이션 블록 안에 `attendanceVM.load()` 한 줄 추가. mock 미션 카드는 그대로 유지)
- 검증: 빌드 불가 환경 — 문법/시그니처/타입변환만 신중히 재검토함.
  - `IosBridges.kt`/`AttendanceStore.kt`/`AttendanceModels.kt`/`PointsRepository.kt`/`KoinIos.kt`/`TokenProvider.kt` 실제 소스를 직접 읽고 대조함 (추측 금지).
  - **중요 발견**: 작업 지시서의 KMM 타입 변환 가정과 실제 소스가 다름 — `AttendanceUiState.month: Int`(→ Swift `Int32`, non-optional), `checkedDays: List<Int>`(→ `[KotlinInt]`, `.intValue` 필요), `RewardPreview.coin: Long`(→ `Int64` non-optional, `KotlinLong` 아님), `CheckInRewardEvent.awardedCoin: Long`(→ `Int64`), `PointsRepository.balance: StateFlow<Long>`(→ FlowCollector 콜백 파라미터 `Int64` non-optional). `FlowCollector`의 콜백 시그니처(`(AttendanceUiState) -> Unit` 등)도 모두 non-null이므로 `guard let ... else` 옵셔널 언래핑이 불필요·컴파일 에러 유발 — 제거하고 `.coin ?? 0`(RewardPreview? 만 옵셔널), `value`(Int64 그대로), `Int(s.month)`(Int32→Int)로 직접 사용하도록 작성함.
  - `KeychainTokenProvider`는 `TokenProvider` 인터페이스(`fun accessToken(): String?` 등 비-suspend 4메서드 + `updateTokens`)와 정확히 일치. `KoinIosKt.doInitKoin(baseUrl:tokenProvider:)` 시그니처도 `KoinIos.kt`와 일치 확인.
- 인계 메모 (사용자 Xcode 필요 작업):
  1. 신규 파일 2개(`KeychainTokenProvider.swift`, `BenefitZone/AttendanceViewModel.swift`)를 Xcode 타깃 멤버십에 추가 (File > Add Files to "CashChatIOS", Target Membership 체크) — pbxproj 미반영 시 컴파일 안 됨. `.xcodeproj/project.pbxproj`는 의도적으로 수정하지 않음.
  2. `JAVA_HOME=$(/usr/libexec/java_home -v 21)` 설정 후 `:shared:embedAndSignAppleFrameworkForXcode` 실행 → Xcode 빌드.
  3. 런타임: 로그인 → 리워드 탭 진입 → 출석 위젯이 ScrollView 최상단에 렌더되는지 → "출석 도장 찍기" 탭 → 토스트("출석 완료! 🪙+N") / 잔액 / 버튼 비활성("오늘 출석 완료")로 전환 확인.
