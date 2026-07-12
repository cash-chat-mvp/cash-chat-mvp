# 온디바이스 Gemma 로컬 채팅 모드 — 상세 설계

- 작성일: 2026-06-26
- 대상: `apps/frontend` (Android + iOS, KMM)
- 상태: 설계 승인됨 → 구현 플랜 작성 대기

## 1. 목적

채팅에 **온디바이스 Gemma 모델을 선택해 대화**할 수 있는 새 모드를 추가한다.
기존 백엔드 어시스턴트(Cash AI, Gemini SSE)와 나란히, 사용자가 채팅 화면 상단에서
모델을 전환해 **로컬·무료·오프라인** 대화를 할 수 있게 한다.

`Powered by Gemma` 표기를 사용하며, 모델 가중치는 Gemma 4 E2B(LiteRT-LM `.litertlm`
포맷, Apache 2.0)를 기본으로 한다.

## 2. 확정된 제품 결정 (브레인스토밍 결과)

| 항목 | 결정 |
| --- | --- |
| 플랫폼 | Android + iOS 모두 **실동작** 온디바이스 추론 |
| 모델 배포 | **첫 사용 시 온디맨드 다운로드** (앱 샌드박스 저장) |
| 경제 시스템 통합 | **별도 무료 로컬 모드** — 에너지·코인보상·광고·상품카드 전부 미적용 |
| 미지원 기기 | **능력 게이팅 + 안내만** (원격 폴백 없음) |
| 진입점 | **채팅 화면 상단 모델 스위처** (Cash AI ↔ Gemma) |

## 3. 검증된 기술 사실 (2026-06 기준)

- **Gemma 4** 실재: 모바일 타깃은 E2B(2.3B effective) / E4B(4.5B effective), 텍스트·이미지·오디오,
  128K 컨텍스트. **라이선스 Apache 2.0**.
- **LiteRT-LM v0.12.0** 실재:
  - Android: Kotlin SDK 정식 — `com.google.ai.edge.litertlm:litertlm-android`,
    패키지 `com.google.ai.edge.litertlm` (`Engine`, `EngineConfig`, `Conversation`,
    `ConversationConfig`, `SamplerConfig`, `Backend.CPU()/GPU()/NPU()`).
  - iOS: **Swift API (Early Preview)**, Metal GPU 가속. Kotlin/Native에서 직접 호출 불가.
  - 모델 배포: HuggingFace / Google AI Edge Gallery, `.litertlm` 포맷.
- 비고: 참고로 받은 `headline-duel-kmp`는 실제로는 클라우드 DistilBERT API 예제이므로
  온디바이스 브릿징 레퍼런스로 사용하지 않는다. 공식 LiteRT-LM Kotlin/Swift API로 설계한다.

## 4. 기존 코드베이스 연계 지점

- `shared` 모듈은 **static framework**(`CashChatShared`, `isStatic=true`, CocoaPods 아님).
- iOS는 Kotlin 인터페이스를 Swift 구현체로 주입하는 패턴을 이미 사용:
  `TokenProvider`, `AdChatIntervalProvider`를 `doInitKoin(baseUrl, tokenProvider, adChatInterval)`로 주입.
  → **LiteRT-LM iOS Swift 엔진도 동일 패턴으로 주입**한다.
- 기존 `ChatStore`는 에너지 게이트·광고·코인 보상·상품 카드가 깊게 얽혀 있다.
  → Gemma는 **별도 `LocalChatStore`**로 분리해 기존 코드를 건드리지 않는다.
- `ChatItem`(`UserMessage`/`AssistantMessage`)·`ChatStreamEvent`·`FlowCollector`(Swift Flow 브릿지)는 재사용.
- SQLDelight는 의존성만 존재하고 스키마 미활성(E-4 예약) → **MVP에서 건드리지 않는다.**
- Koin DI: `sharedDataModule(baseUrl)`에 로컬 LLM 관련 single 등록.

## 5. 아키텍처

```
commonMain (com.nomadclub.cashchat.shared.localllm)
├── LocalLlmEngine        ← 인터페이스 (플랫폼 구현 Koin 주입)
├── EngineState / SamplingParameters / GemmaModelSpec
├── ModelDownloader       ← 인터페이스 (진행률 Flow, 재개, sha256 검증)
├── ModelDownloadStore    ← 상태머신
├── DeviceCapability      ← canRunGemma(spec)
├── LocalChatStore        ← ChatItem/스트리밍 상태 (economy 없음)
├── ChatModelMode         ← { CASH_AI, GEMMA_LOCAL } 선택 상태
└── platform/ (expect)    ← localModelsDir(), totalRamBytes(), availableStorageBytes()

androidMain
├── LiteRtLlmEngine       ← litertlm-android SDK 래퍼
└── *.android.kt          ← expect 구현 (ActivityManager.MemoryInfo, filesDir 등)

iosMain
└── *.ios.kt              ← expect 구현 (Foundation/UIKit: RAM·경로·저장공간)

iOS 앱 (Swift)
└── LocalLlmEngine 구현   ← LiteRT-LM Swift API(Metal) 래핑 → doInitKoin으로 주입
```

### 왜 expect/actual `class`가 아니라 인터페이스 + 주입인가

iOS LiteRT-LM은 Swift 프레임워크라 Kotlin/Native가 직접 호출할 수 없다.
expect/actual `class`는 actual을 iosMain(Kotlin)에 둬야 하므로 부적합.
대신 commonMain `interface LocalLlmEngine`을 두고 Android는 Kotlin 구현,
iOS는 Swift 구현을 주입한다(코드베이스의 `TokenProvider` 패턴과 동일).
단, 순수 플랫폼 유틸(`localModelsDir`, `totalRamBytes`, `availableStorageBytes`)은
Kotlin/Native가 자체 구현 가능하므로 expect/actual `fun`을 사용한다.

## 6. 핵심 컴포넌트

### 6.1 `LocalLlmEngine` (commonMain 인터페이스)

```kotlin
enum class EngineState { UNINITIALIZED, LOADING, READY, GENERATING, ERROR }

data class SamplingParameters(
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 64,
    val maxTokens: Int = 2048,
)

interface LocalLlmEngine {
    val state: StateFlow<EngineState>
    @Throws(Exception::class) suspend fun load(modelPath: String, params: SamplingParameters = SamplingParameters())
    fun generate(prompt: String): Flow<String>   // 토큰 스트림
    fun resetSession()
    fun release()                                  // 가중치 RAM 해제
}
```

- **Android `LiteRtLlmEngine`**: `Engine(EngineConfig(modelPath, Backend.GPU()))` 초기화 →
  `Conversation`(SamplerConfig 적용) 생성 → `conversation.sendMessageAsync(prompt).map { it.text }`.
  GPU 실패 시 `Backend.CPU()` 폴백.
- **iOS (Swift)**: LiteRT-LM Swift API 래핑. 토큰 콜백/AsyncStream을 Kotlin `Flow`로 브릿지
  (`FlowCollector` 역방향 — Swift→Kotlin 콜백을 `callbackFlow`로 감싸 노출).
  `@Throws` 누락 시 iOS 크래시(메모리 노트 reference-kmm-suspend-throws) → suspend는 `@Throws(Exception::class)` 필수.

### 6.2 `LocalChatStore` (commonMain)

- 상태: `items: StateFlow<List<ChatItem>>`, `isStreaming: StateFlow<Boolean>`, `engineState`(LocalLlmEngine.state 위임).
- `sendMessage(text)`:
  1. `UserMessage(CONFIRMED)` + `AssistantMessage(isStreaming=true, "")` 추가
  2. 엔진 미로드면 `ensureLoaded()` (LOADING 표시, 최초 ~10초)
  3. `engine.generate(prompt).collect { token -> updateAssistant { it.copy(text = it.text + token) } }`
  4. 완료 시 `isStreaming=false`. `CancellationException` 전파, 그 외 예외는 `AssistantMessage(isError=true)`.
- **에너지/광고/상품/보상 분기 전혀 없음** → `ChatStore`보다 단순.
- 멀티턴: LiteRT-LM `Conversation` 세션이 직전 맥락 유지. 시스템 프롬프트 1개 고정.
- 테스트 대체: `FakeLocalLlmEngine`(commonTest)로 토큰 스트림/취소/에러 주입.

### 6.3 모델 다운로드 파이프라인

```kotlin
data class GemmaModelSpec(
    val variantId: String,      // "gemma4-e2b-int4"
    val fileName: String,       // "*.litertlm"
    val url: String,            // HuggingFace/CDN
    val sha256: String,
    val sizeBytes: Long,        // ~2.5GB
    val minRamBytes: Long,
)

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data object Verifying : ModelDownloadState
    data class Ready(val localPath: String) : ModelDownloadState
    data class Failed(val reason: String) : ModelDownloadState
}
```

- `ModelDownloader`: Ktor로 스트리밍 다운로드, 진행률 Flow, 중단 재개, 완료 후 **sha256 검증**.
  저장 위치는 `expect localModelsDir()`.
- `ModelDownloadStore`: 위 상태머신을 들고 UI에 노출. 검증 실패 시 파일 삭제 후 `Failed`.
- 모델 메타(버전·sha256·완료 여부)는 작은 설정 파일로 기록해 재실행 시 재다운로드 방지.

### 6.4 능력 게이팅 `DeviceCapability`

```kotlin
sealed interface CapabilityResult {
    data object Ok : CapabilityResult
    data class Insufficient(val reason: String) : CapabilityResult  // RAM 부족 / 저장공간 부족
}
fun canRunGemma(spec: GemmaModelSpec): CapabilityResult
```

- `expect totalRamBytes()`, `expect availableStorageBytes()` 기반 판정.
- 미달 시 모델 스위처에서 Gemma를 **비활성 + 사유 안내**.

### 6.5 모델 스위처 / 모드 상태

- `ChatModelMode { CASH_AI, GEMMA_LOCAL }`.
- Android `ChatViewModel`(또는 신규 경량 holder)·iOS Swift VM이 모드에 따라
  `ChatStore` vs `LocalChatStore`를 바인딩. economy UI는 `GEMMA_LOCAL`일 때 숨김.

## 7. 데이터 플로우 (Gemma 전송)

```
입력 → LocalChatStore.sendMessage(text)
     → items += UserMessage(CONFIRMED) + AssistantMessage(streaming)
     → ensureLoaded(): EngineState LOADING → READY (최초 ~10초, 진행표시)
     → engine.generate(prompt).collect { token → assistant.text += token }
     → 완료: isStreaming=false
     → 취소/에러: CancellationException 전파 / AssistantMessage(isError=true)
```

## 8. 영속성 (MVP 최소)

- Gemma 대화는 **단일 로컬 대화 1개**를 `kotlinx.serialization` JSON으로 앱 파일에 저장/복원
  (파일 경로는 `expect`). 다중 스레드/목록은 범위 외.
- SQLDelight 스키마는 **활성화하지 않는다**(E-4 예약). E-4 도입 시 이관 가능한 형태로 둔다.

## 9. 메모리 생명주기 (안정성 핵심)

- **모드 이탈/화면 종료** → `engine.release()`로 가중치 RAM 해제.
- **OS 메모리 압박**: Android `onTrimMemory`, iOS `didReceiveMemoryWarning` → `release()`.
  재진입 시 lazy 재로드.
- KV-cache 직렬화 기반 콜드스타트 제거는 **범위 외** — 단순 release/재로드만.

## 10. 빌드 설정

### Android
- `libs.versions.toml`에 `litertlm-android` 추가, `shared/build.gradle.kts` androidMain 의존성 등록.
- GPU 네이티브 라이브러리 로더 매니페스트 등록.
- LiteRT-LM의 minSdk 요구를 플랜 단계에서 검증(현재 앱 minSdk 24).

### iOS
- LiteRT-LM Swift 패키지(SPM, Early Preview) 추가.
- **Entitlements 2종 필수**:
  - `com.apple.developer.kernel.increased-memory-limit`
  - `com.apple.developer.kernel.extended-virtual-addressing`
- `doInitKoin` 시그니처에 `gemmaEngine: LocalLlmEngine` 파라미터 추가(Swift 구현 주입).
- iOS 검증은 **clean build**(메모리 노트 feedback-ios-verify-clean-build).

## 11. MVP 범위 제외 (명시적 디퍼)

- 멀티모달(이미지·오디오) — 텍스트 전용
- 원격 Ktor 하이브리드 폴백
- KV-cache 세션 직렬화/콜드스타트 제거
- Reasoning `<think>` 모드, MTP 스펙큘레이티브 디코딩
- Gemma 대화 다중 스레드/목록 관리

## 12. 테스트 전략

- **commonTest**: `LocalChatStore`(FakeLocalLlmEngine — 토큰 스트림·취소·에러),
  `ModelDownloadStore` 상태머신, `DeviceCapability` 판정 로직.
- 엔진 actual 구현체(Android Kotlin / iOS Swift)는 얇은 래퍼 → **실기기 수동/계측 검증**.

## 13. 라이선스 컴플라이언스 (Apache 2.0)

- 앱 내 '오픈소스 라이선스'에 Gemma / LiteRT-LM Apache 2.0 전문 추가.
- `Powered by Gemma` 표기.
- Gemma 금지된 사용 정책을 서비스 약관에 반영(흐름형 규제 의무).
- 별도 작은 작업으로 구현 플랜에 포함.

## 14. 미해결/플랜 단계 검증 항목

- Gemma 4 E2B `.litertlm` 정확한 파일 크기·sha256·배포 URL(HuggingFace) 확정.
- LiteRT-LM minSdk / iOS 최소 버전 요구 확인.
- iOS Swift API(Early Preview)의 스트리밍 콜백 정확한 시그니처 확인 후 Flow 브릿지 확정.
