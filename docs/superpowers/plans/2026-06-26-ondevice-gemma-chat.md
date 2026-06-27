# 온디바이스 Gemma 로컬 채팅 모드 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채팅 화면 상단 모델 스위처로 Cash AI ↔ 온디바이스 Gemma(LiteRT-LM)를 전환해 로컬·무료·오프라인 대화를 할 수 있는 별도 모드를 Android/iOS에 추가한다.

**Architecture:** commonMain에 `LocalLlmEngine` 인터페이스 + `LocalChatStore`(economy 없음) + 모델 다운로드/능력 게이팅을 두고, Android는 `litertlm-android` Kotlin SDK 구현을, iOS는 LiteRT-LM Swift API 구현을 Koin으로 주입한다. 모델은 첫 사용 시 온디맨드 다운로드한다.

**Tech Stack:** Kotlin Multiplatform, Koin, Ktor(다운로드), kotlinx-serialization, LiteRT-LM v0.12.0(`com.google.ai.edge.litertlm`), Gemma 4 E2B(`.litertlm`), Jetpack Compose, SwiftUI.

**관련 스펙:** `docs/superpowers/specs/2026-06-26-ondevice-gemma-chat-design.md`

**테스트 규약(기존 코드 일치):** commonTest는 `kotlin.test`(`@Test`, `assertEquals`, `assertIs`) + `kotlinx.coroutines.test`(`runTest`, `TestScope`, `advanceUntilIdle`), 한국어 백틱 테스트명. 스토어는 `(deps, scope: CoroutineScope)` 시그니처. 실행: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest`. JBR 필요 시 메모리 노트 `reference-android-build-jlink-jbr` 참조.

**패키지 루트:** `com.nomadclub.cashchat.shared.localllm` (commonMain/androidMain/iosMain 공통)

---

## File Structure (생성/수정 대상)

**commonMain** (`apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/`)
- `localllm/EngineModels.kt` — `EngineState`, `SamplingParameters`, `LocalLlmEngine` 인터페이스
- `localllm/GemmaModelSpec.kt` — 모델 스펙 + 기본 카탈로그
- `localllm/LocalLlmPlatform.kt` — `expect` 플랫폼 함수(`localModelsDir`, `totalRamBytes`, `availableStorageBytes`)
- `localllm/DeviceCapability.kt` — `canRunGemma`
- `localllm/ModelDownload.kt` — `ModelDownloadState`, `ModelDownloader` 인터페이스
- `localllm/ModelDownloadStore.kt` — 다운로드 상태머신
- `localllm/KtorModelDownloader.kt` — Ktor 기반 다운로더 구현
- `localllm/LocalChatStore.kt` — 로컬 대화 스토어
- `localllm/LocalChatHistory.kt` — 단일 대화 JSON 영속화
- `localllm/ChatModeStore.kt` — `ChatModelMode` 선택 상태
- `di/SharedModule.kt` — (수정) 로컬 LLM single 등록

**androidMain**
- `localllm/LiteRtLlmEngine.kt` — litertlm-android SDK 래퍼
- `localllm/LocalLlmPlatform.android.kt` — actual 플랫폼 함수
- `localllm/AndroidLocalLlmContext.kt` — Context 홀더 + Koin 모듈

**iosMain**
- `localllm/LocalLlmPlatform.ios.kt` — actual 플랫폼 함수
- `di/KoinIos.kt` — (수정) `doInitKoin`에 `gemmaEngine` 주입

**commonTest**
- `localllm/FakeLocalLlmEngine.kt`, `DeviceCapabilityTest.kt`, `ModelDownloadStoreTest.kt`, `LocalChatStoreTest.kt`, `ChatModeStoreTest.kt`, `LocalChatHistoryTest.kt`

**app(Android UI)**
- `feature/chat/ChatScreen.kt`(수정), `feature/chat/components/ModelSwitcher.kt`(생성), `feature/chat/LocalChatViewModel.kt`(생성), `feature/chat/GemmaModelDownloadCard.kt`(생성)

**iOS(Swift)**
- `CashChatIOS/.../LocalLlmEngineImpl.swift`(생성), 채팅 화면 스위처(수정), `CashChatIOS.entitlements`(수정)

---

## Phase 0 — 의존성 & 스캐폴딩

### Task 0.1: LiteRT-LM Android 의존성 추가

**Files:**
- Modify: `apps/frontend/gradle/libs.versions.toml`
- Modify: `apps/frontend/shared/build.gradle.kts`

- [ ] **Step 1: 버전 카탈로그에 litertlm 추가**

`libs.versions.toml`의 `[versions]`에 추가:
```toml
litertlm = "0.12.0"
```
`[libraries]`에 추가:
```toml
litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
```

- [ ] **Step 2: shared androidMain 의존성 등록**

`shared/build.gradle.kts`의 `androidMain.dependencies { ... }` 안에 추가:
```kotlin
implementation(libs.litertlm.android)
```

- [ ] **Step 3: 빌드 동기화 확인**

Run: `cd apps/frontend && ./gradlew :shared:dependencies --configuration debugRuntimeClasspath | grep -i litertlm`
Expected: `com.google.ai.edge.litertlm:litertlm-android:0.12.0` 가 출력됨. (해석 실패 시 좌표/버전을 Maven에서 재확인 — 미해결 항목)

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/gradle/libs.versions.toml apps/frontend/shared/build.gradle.kts
git commit -m "chore(gemma): LiteRT-LM Android 의존성 추가"
```

---

## Phase 1 — 모델 스펙 & 능력 게이팅 (commonMain, TDD)

### Task 1.1: 모델 스펙 + 플랫폼 expect 함수

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/GemmaModelSpec.kt`
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.kt`

- [ ] **Step 1: GemmaModelSpec 작성**

`GemmaModelSpec.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

/** 온디바이스 Gemma 모델 1종의 배포·검증 메타데이터. */
data class GemmaModelSpec(
    val variantId: String,   // 예: "gemma4-e2b-int4"
    val displayName: String, // 사용자 노출명 (예: "Gemma (로컬)")
    val fileName: String,    // 저장 파일명, 예: "gemma4-e2b-int4.litertlm"
    val url: String,         // 다운로드 URL (HuggingFace/CDN)
    val sha256: String,      // 무결성 검증 해시(소문자 hex)
    val sizeBytes: Long,     // 다운로드 크기
    val minRamBytes: Long,   // 구동 권장 최소 물리 RAM
)

/**
 * MVP 기본 모델. url/sha256/sizeBytes 는 플랜 단계 미해결 항목으로,
 * 실제 배포 파일 확정 시 채운다. (스펙 §14)
 */
val DEFAULT_GEMMA_SPEC = GemmaModelSpec(
    variantId = "gemma4-e2b-int4",
    displayName = "Gemma (로컬)",
    fileName = "gemma4-e2b-int4.litertlm",
    url = "https://huggingface.co/REPLACE_WITH_REAL_URL/resolve/main/gemma4-e2b-int4.litertlm",
    sha256 = "REPLACE_WITH_REAL_SHA256",
    sizeBytes = 2_580_000_000L,
    minRamBytes = 4L * 1024 * 1024 * 1024, // 4GB
)
```

- [ ] **Step 2: 플랫폼 expect 함수 작성**

`LocalLlmPlatform.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

/** 모델 파일을 저장할 앱 전용 디렉터리 절대경로 (끝 슬래시 없음). */
expect fun localModelsDir(): String

/** 기기 물리 RAM 총량(바이트). 측정 불가 시 0. */
expect fun totalRamBytes(): Long

/** 모델 저장 위치의 가용 저장공간(바이트). 측정 불가 시 Long.MAX_VALUE. */
expect fun availableStorageBytes(): Long
```

- [ ] **Step 3: 컴파일 확인 (actual 미작성이라 commonMain만 검증은 불가 → Task 1.2 테스트와 함께 컴파일)**

이 단계에서는 파일만 생성한다. actual은 Phase 4/5에서 작성하며, 그 전까지 `:shared:testDebugUnitTest`는 androidMain actual 필요. 따라서 임시로 androidMain actual 스텁을 함께 생성한다:

Create `shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.android.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

// 정식 구현은 Task 4.2에서 Context 기반으로 교체한다.
actual fun localModelsDir(): String = AndroidLocalLlmContext.modelsDir()
actual fun totalRamBytes(): Long = AndroidLocalLlmContext.totalRamBytes()
actual fun availableStorageBytes(): Long = AndroidLocalLlmContext.availableStorageBytes()
```

Create `shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/localllm/AndroidLocalLlmContext.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/** 앱 시작 시 init(context) 1회 호출. 테스트(JVM)에서는 미초기화 시 안전한 기본값 반환. */
object AndroidLocalLlmContext {
    @Volatile private var appContext: Context? = null
    fun init(context: Context) { appContext = context.applicationContext }

    fun modelsDir(): String =
        (appContext?.filesDir?.absolutePath ?: "/tmp") + "/gemma-models"

    fun totalRamBytes(): Long {
        val ctx = appContext ?: return 0L
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem
    }

    fun availableStorageBytes(): Long {
        val ctx = appContext ?: return Long.MAX_VALUE
        val stat = StatFs(ctx.filesDir.absolutePath)
        return stat.availableBytes
    }
}
```

Create iosMain actual 스텁 `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.ios.kt` (정식은 Task 5.1):
```kotlin
package com.nomadclub.cashchat.shared.localllm

import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSProcessInfo

actual fun localModelsDir(): String {
    val docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: "."
    return "$docs/gemma-models"
}

actual fun totalRamBytes(): Long =
    NSProcessInfo.processInfo.physicalMemory.toLong()

actual fun availableStorageBytes(): Long = Long.MAX_VALUE // Task 5.1에서 정밀화
```

- [ ] **Step 4: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/GemmaModelSpec.kt \
        apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.kt \
        apps/frontend/shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/localllm/ \
        apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/localllm/
git commit -m "feat(gemma): 모델 스펙·플랫폼 expect 함수 및 actual 스텁 추가"
```

### Task 1.2: DeviceCapability.canRunGemma (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/DeviceCapability.kt`
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/DeviceCapabilityTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`DeviceCapabilityTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeviceCapabilityTest {
    private val spec = DEFAULT_GEMMA_SPEC.copy(
        sizeBytes = 1000, minRamBytes = 4000,
    )

    @Test
    fun `RAM과 저장공간이 충분하면 Ok`() {
        val result = canRunGemma(spec, ramBytes = 8000, freeStorageBytes = 5000)
        assertEquals(CapabilityResult.Ok, result)
    }

    @Test
    fun `RAM이 부족하면 Insufficient`() {
        val result = canRunGemma(spec, ramBytes = 2000, freeStorageBytes = 5000)
        assertIs<CapabilityResult.Insufficient>(result)
    }

    @Test
    fun `모델 크기보다 여유 저장공간이 적으면 Insufficient`() {
        // 다운로드엔 모델 크기 + 여유분이 필요하다(1.1배).
        val result = canRunGemma(spec, ramBytes = 8000, freeStorageBytes = 1050)
        assertIs<CapabilityResult.Insufficient>(result)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*DeviceCapabilityTest*"`
Expected: 컴파일 실패 (`canRunGemma`/`CapabilityResult` 미정의).

- [ ] **Step 3: 구현 작성**

`DeviceCapability.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

sealed interface CapabilityResult {
    data object Ok : CapabilityResult
    data class Insufficient(val reason: String) : CapabilityResult
}

/** 다운로드엔 모델 크기의 1.1배 여유 저장공간을 요구한다(임시 파일·검증 여유분). */
private const val STORAGE_HEADROOM = 1.1

fun canRunGemma(
    spec: GemmaModelSpec,
    ramBytes: Long = totalRamBytes(),
    freeStorageBytes: Long = availableStorageBytes(),
): CapabilityResult {
    if (ramBytes in 1 until spec.minRamBytes) {
        return CapabilityResult.Insufficient("RAM이 부족합니다 (필요 ${spec.minRamBytes / (1024 * 1024)}MB).")
    }
    val required = (spec.sizeBytes * STORAGE_HEADROOM).toLong()
    if (freeStorageBytes < required) {
        return CapabilityResult.Insufficient("저장공간이 부족합니다 (필요 ${required / (1024 * 1024)}MB).")
    }
    return CapabilityResult.Ok
}
```
> 주: `ramBytes == 0`(측정 불가)은 게이팅하지 않고 Ok로 통과시켜 오탐을 피한다.

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*DeviceCapabilityTest*"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/DeviceCapability.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/DeviceCapabilityTest.kt
git commit -m "feat(gemma): 기기 능력 게이팅(canRunGemma) 추가"
```

---

## Phase 2 — 모델 다운로드 (commonMain, TDD)

### Task 2.1: ModelDownloadStore 상태머신 (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/ModelDownload.kt`
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/ModelDownloadStore.kt`
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/ModelDownloadStoreTest.kt`

- [ ] **Step 1: 인터페이스/상태 타입 작성**

`ModelDownload.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.Flow

sealed interface ModelDownloadState {
    data object NotDownloaded : ModelDownloadState
    data class Downloading(val receivedBytes: Long, val totalBytes: Long) : ModelDownloadState
    data object Verifying : ModelDownloadState
    data class Ready(val localPath: String) : ModelDownloadState
    data class Failed(val reason: String) : ModelDownloadState
}

/** 다운로드 진행 단계. progress 는 누적 수신 바이트를 방출하고, 완료 시 정상 종료한다. */
sealed interface DownloadProgress {
    data class Bytes(val received: Long, val total: Long) : DownloadProgress
}

interface ModelDownloader {
    /** 이미 받은 유효 파일이 있으면 그 경로, 없으면 null. (sha256 검증 포함) */
    suspend fun existingValidFile(spec: GemmaModelSpec): String?
    /** 모델을 내려받아 progress 를 방출하고, 완료 시 검증된 로컬 경로를 반환한다. */
    fun download(spec: GemmaModelSpec): Flow<DownloadProgress>
    /** 다운로드 완료 후 호출 — sha256 검증 통과 시 최종 경로, 실패 시 null. */
    suspend fun verify(spec: GemmaModelSpec): String?
}
```

`ModelDownloadStore.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelDownloadStore(
    private val downloader: ModelDownloader,
    private val spec: GemmaModelSpec,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.NotDownloaded)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** 이미 받은 유효 파일이 있으면 즉시 Ready 로 만든다. */
    suspend fun refresh() {
        downloader.existingValidFile(spec)?.let { _state.value = ModelDownloadState.Ready(it) }
    }

    fun start() {
        if (_state.value is ModelDownloadState.Ready) return
        job?.cancel()
        job = scope.launch {
            try {
                downloader.download(spec).collect { p ->
                    when (p) {
                        is DownloadProgress.Bytes ->
                            _state.value = ModelDownloadState.Downloading(p.received, p.total)
                    }
                }
                _state.value = ModelDownloadState.Verifying
                val path = downloader.verify(spec)
                _state.value = if (path != null) ModelDownloadState.Ready(path)
                               else ModelDownloadState.Failed("무결성 검증 실패")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ModelDownloadState.Failed(e.message ?: "다운로드 실패")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        if (_state.value !is ModelDownloadState.Ready) {
            _state.value = ModelDownloadState.NotDownloaded
        }
    }
}
```

- [ ] **Step 2: 실패 테스트 작성**

`ModelDownloadStoreTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ModelDownloadStoreTest {
    private val spec = DEFAULT_GEMMA_SPEC

    private class FakeDownloader(
        val existing: String? = null,
        val verifyResult: String? = "/models/gemma.litertlm",
        val emitError: Boolean = false,
    ) : ModelDownloader {
        override suspend fun existingValidFile(spec: GemmaModelSpec) = existing
        override fun download(spec: GemmaModelSpec): Flow<DownloadProgress> = flow {
            emit(DownloadProgress.Bytes(50, 100))
            if (emitError) throw RuntimeException("네트워크 끊김")
            emit(DownloadProgress.Bytes(100, 100))
        }
        override suspend fun verify(spec: GemmaModelSpec) = verifyResult
    }

    @Test
    fun `다운로드 성공하면 Verifying 거쳐 Ready`() = runTest {
        val store = ModelDownloadStore(FakeDownloader(), spec, this)
        store.start()
        testScheduler.advanceUntilIdle()
        val ready = assertIs<ModelDownloadState.Ready>(store.state.value)
        assertEquals("/models/gemma.litertlm", ready.localPath)
    }

    @Test
    fun `다운로드 중 예외면 Failed`() = runTest {
        val store = ModelDownloadStore(FakeDownloader(emitError = true), spec, this)
        store.start()
        testScheduler.advanceUntilIdle()
        assertIs<ModelDownloadState.Failed>(store.state.value)
    }

    @Test
    fun `검증 실패하면 Failed`() = runTest {
        val store = ModelDownloadStore(FakeDownloader(verifyResult = null), spec, this)
        store.start()
        testScheduler.advanceUntilIdle()
        assertIs<ModelDownloadState.Failed>(store.state.value)
    }

    @Test
    fun `이미 받은 유효 파일이 있으면 refresh로 Ready`() = runTest {
        val store = ModelDownloadStore(FakeDownloader(existing = "/models/x.litertlm"), spec, this)
        store.refresh()
        val ready = assertIs<ModelDownloadState.Ready>(store.state.value)
        assertEquals("/models/x.litertlm", ready.localPath)
    }
}
```

- [ ] **Step 3: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ModelDownloadStoreTest*"`
Expected: 처음엔 컴파일/실행 통과해야 하나, 미작성 상태면 컴파일 실패. Step 1에서 구현을 이미 작성했으므로 PASS 가 정상. (TDD 순서상 store 코드는 Step 1에 포함됨 — 이 Task는 상태머신이 복잡해 인터페이스+구현을 함께 둔다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ModelDownloadStoreTest*"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/ModelDownload.kt \
        apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/ModelDownloadStore.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/ModelDownloadStoreTest.kt
git commit -m "feat(gemma): 모델 다운로드 상태머신(ModelDownloadStore) 추가"
```

### Task 2.2: KtorModelDownloader 구현

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/KtorModelDownloader.kt`

- [ ] **Step 1: 구현 작성**

`KtorModelDownloader.kt` — Ktor로 스트리밍 다운로드, 부분 파일 재개(Range), 완료 후 sha256 검증. 파일 IO는 플랫폼 공통 `expect`로 위임한다(아래 함수들은 `LocalLlmPlatform.kt`에 추가하고 actual을 Phase 4/5에서 구현).

`LocalLlmPlatform.kt`에 추가:
```kotlin
/** path 에 바이트를 append 모드로 기록한다(없으면 생성). */
expect fun appendBytesToFile(path: String, bytes: ByteArray)
/** path 파일의 현재 크기. 없으면 0. */
expect fun fileSize(path: String): Long
/** path 파일의 sha256(소문자 hex). 없으면 null. */
expect fun fileSha256(path: String): String?
/** path 파일을 삭제. */
expect fun deleteFile(path: String)
/** 디렉터리를 보장(생성). */
expect fun ensureDir(path: String)
```

`KtorModelDownloader.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.isEmpty
import io.ktor.utils.io.core.readBytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class KtorModelDownloader(
    private val client: HttpClient,
) : ModelDownloader {

    private fun targetPath(spec: GemmaModelSpec) = "${localModelsDir()}/${spec.fileName}"

    override suspend fun existingValidFile(spec: GemmaModelSpec): String? {
        val path = targetPath(spec)
        if (fileSize(path) != spec.sizeBytes) return null
        return if (fileSha256(path) == spec.sha256) path else null
    }

    override fun download(spec: GemmaModelSpec): Flow<DownloadProgress> = flow {
        ensureDir(localModelsDir())
        val path = targetPath(spec)
        var received = fileSize(path) // 재개 지원
        if (received >= spec.sizeBytes) { emit(DownloadProgress.Bytes(spec.sizeBytes, spec.sizeBytes)); return@flow }

        client.prepareGet(spec.url) {
            if (received > 0) headers { append(HttpHeaders.Range, "bytes=$received-") }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val packet = channel.readRemaining(64 * 1024)
                while (!packet.isEmpty) {
                    val chunk = packet.readBytes()
                    appendBytesToFile(path, chunk)
                    received += chunk.size
                    emit(DownloadProgress.Bytes(received, spec.sizeBytes))
                }
            }
        }
    }

    override suspend fun verify(spec: GemmaModelSpec): String? {
        val path = targetPath(spec)
        if (fileSha256(path) != spec.sha256) { deleteFile(path); return null }
        return path
    }
}
```
> 주: sha256가 `REPLACE_WITH_REAL_SHA256`인 동안에는 verify가 항상 실패하므로, 실제 배포 파일 확정(미해결 항목) 전까지는 임시로 검증을 우회하는 디버그 플래그가 필요할 수 있다. 구현 시 `GemmaModelSpec.sha256.isBlank()`이면 검증 생략하도록 분기.

- [ ] **Step 2: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (actual 파일 IO 함수는 Phase 4에서 작성하므로, 이 시점에 androidMain actual 스텁을 함께 추가해야 컴파일됨 — Step 3).

- [ ] **Step 3: actual 파일 IO 스텁 추가(Android/iOS)**

androidMain `LocalLlmPlatform.android.kt`에 추가(정식 구현):
```kotlin
import java.io.File
import java.security.MessageDigest

actual fun appendBytesToFile(path: String, bytes: ByteArray) {
    File(path).apply { parentFile?.mkdirs() }.appendBytes(bytes)
}
actual fun fileSize(path: String): Long = File(path).let { if (it.exists()) it.length() else 0L }
actual fun fileSha256(path: String): String? {
    val f = File(path); if (!f.exists()) return null
    val md = MessageDigest.getInstance("SHA-256")
    f.inputStream().use { ins ->
        val buf = ByteArray(1 shl 16); var r = ins.read(buf)
        while (r >= 0) { md.update(buf, 0, r); r = ins.read(buf) }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}
actual fun deleteFile(path: String) { File(path).delete() }
actual fun ensureDir(path: String) { File(path).mkdirs() }
```

iosMain `LocalLlmPlatform.ios.kt`에 추가 — NSFileManager/NSData + CryptoKit(SHA256)로 구현(정식은 Task 5.1에서 검증). 임시로 NSFileHandle append + CommonCrypto sha256:
```kotlin
// Task 5.1에서 CommonCrypto/CryptoKit 기반으로 정밀 구현. 우선 컴파일용 골격:
actual fun appendBytesToFile(path: String, bytes: ByteArray) { /* Task 5.1 */ }
actual fun fileSize(path: String): Long = 0L
actual fun fileSha256(path: String): String? = null
actual fun deleteFile(path: String) { /* Task 5.1 */ }
actual fun ensureDir(path: String) { /* Task 5.1 */ }
```

- [ ] **Step 4: 빌드 확인 + Commit**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.
```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/KtorModelDownloader.kt \
        apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.kt \
        apps/frontend/shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.android.kt \
        apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.ios.kt
git commit -m "feat(gemma): Ktor 모델 다운로더 + 파일 IO expect/actual 추가"
```

---

## Phase 3 — 엔진 인터페이스 & LocalChatStore (commonMain, TDD)

### Task 3.1: 엔진 인터페이스 + Fake 엔진

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/EngineModels.kt`
- Create: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/FakeLocalLlmEngine.kt`

- [ ] **Step 1: 인터페이스 작성**

`EngineModels.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class EngineState { UNINITIALIZED, LOADING, READY, GENERATING, ERROR }

data class SamplingParameters(
    val temperature: Float = 1.0f,
    val topP: Float = 0.95f,
    val topK: Int = 64,
    val maxTokens: Int = 2048,
)

/**
 * 온디바이스 LLM 엔진. Android는 Kotlin(litertlm-android), iOS는 Swift 구현을 Koin 주입한다.
 * iOS 호출 안정성을 위해 suspend 함수엔 @Throws 필수(메모리 노트 reference-kmm-suspend-throws).
 */
interface LocalLlmEngine {
    val state: StateFlow<EngineState>
    @Throws(Exception::class)
    suspend fun load(modelPath: String, params: SamplingParameters = SamplingParameters())
    fun generate(prompt: String): Flow<String>
    fun resetSession()
    fun release()
}
```

- [ ] **Step 2: Fake 작성**

`FakeLocalLlmEngine.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/** 테스트용 — load 호출 추적, generate 토큰/예외 주입. */
class FakeLocalLlmEngine(
    private val tokens: List<String> = listOf("안녕", "하세요"),
    private val throwOnGenerate: Boolean = false,
    private val loadShouldFail: Boolean = false,
) : LocalLlmEngine {
    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    override val state: StateFlow<EngineState> = _state
    var loadCount = 0; var releaseCount = 0; var lastPrompt: String? = null

    override suspend fun load(modelPath: String, params: SamplingParameters) {
        loadCount++
        if (loadShouldFail) { _state.value = EngineState.ERROR; throw RuntimeException("로드 실패") }
        _state.value = EngineState.READY
    }
    override fun generate(prompt: String): Flow<String> = flow {
        lastPrompt = prompt
        _state.value = EngineState.GENERATING
        if (throwOnGenerate) throw RuntimeException("추론 오류")
        tokens.forEach { emit(it) }
        _state.value = EngineState.READY
    }
    override fun resetSession() {}
    override fun release() { releaseCount++; _state.value = EngineState.UNINITIALIZED }
}
```

- [ ] **Step 3: 컴파일 확인 + Commit**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugUnitTestKotlinAndroid`
Expected: BUILD SUCCESSFUL.
```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/EngineModels.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/FakeLocalLlmEngine.kt
git commit -m "feat(gemma): LocalLlmEngine 인터페이스 + 테스트용 Fake 추가"
```

### Task 3.2: LocalChatStore 전송 성공 (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatStore.kt`
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatStoreTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`LocalChatStoreTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LocalChatStoreTest {
    private fun store(engine: LocalLlmEngine, scope: kotlinx.coroutines.CoroutineScope) =
        LocalChatStore(engine, modelPath = "/m/gemma.litertlm", scope = scope, history = NoopHistory)

    private object NoopHistory : LocalChatHistory {
        override fun load() = emptyList<ChatItem>()
        override fun save(items: List<ChatItem>) {}
        override fun clear() {}
    }

    @Test
    fun `전송 성공 - user CONFIRMED, assistant 토큰 누적, 스트리밍 종료`() = runTest {
        val s = store(FakeLocalLlmEngine(tokens = listOf("안", "녕")), this)
        s.sendMessage("hi")
        testScheduler.advanceUntilIdle()
        val items = s.items.value
        val user = items.filterIsInstance<ChatItem.UserMessage>().last()
        val assistant = items.filterIsInstance<ChatItem.AssistantMessage>().last()
        assertEquals(ChatItem.SendStatus.CONFIRMED, user.status)
        assertEquals("안녕", assistant.text)
        assertEquals(false, assistant.isStreaming)
        assertEquals(false, s.isStreaming.value)
    }

    @Test
    fun `엔진은 최초 1회만 load 된다`() = runTest {
        val engine = FakeLocalLlmEngine()
        val s = LocalChatStore(engine, "/m/gemma.litertlm", this, NoopHistory)
        s.sendMessage("a"); testScheduler.advanceUntilIdle()
        s.sendMessage("b"); testScheduler.advanceUntilIdle()
        assertEquals(1, engine.loadCount)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*LocalChatStoreTest*"`
Expected: 컴파일 실패 (`LocalChatStore`, `LocalChatHistory` 미정의).

- [ ] **Step 3: LocalChatHistory 인터페이스 + LocalChatStore 작성**

`LocalChatHistory.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem

/** Gemma 단일 로컬 대화의 영속화. */
interface LocalChatHistory {
    fun load(): List<ChatItem>
    fun save(items: List<ChatItem>)
    fun clear()
}
```

`LocalChatStore.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 온디바이스 Gemma 전용 대화 스토어. 에너지/광고/보상/상품 economy 없음.
 * ChatItem(UserMessage/AssistantMessage)만 재사용한다.
 */
class LocalChatStore(
    private val engine: LocalLlmEngine,
    private val modelPath: String,
    private val scope: CoroutineScope,
    private val history: LocalChatHistory,
) {
    private val _items = MutableStateFlow(history.load())
    val items: StateFlow<List<ChatItem>> = _items.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    val engineState: StateFlow<EngineState> = engine.state

    private var streamJob: Job? = null
    private var msgSeq = 0L

    fun sendMessage(text: String) {
        if (text.isBlank() || _isStreaming.value) return
        val userId = "u${msgSeq++}"
        val assistantId = "a${msgSeq++}"
        _items.update {
            it + ChatItem.UserMessage(userId, text, ChatItem.SendStatus.CONFIRMED) +
                ChatItem.AssistantMessage(assistantId, "", isStreaming = true)
        }
        _isStreaming.value = true
        streamJob?.cancel()
        streamJob = scope.launch {
            try {
                ensureLoaded()
                engine.generate(buildPrompt(text)).collect { token ->
                    updateAssistant(assistantId) { it.copy(text = it.text + token) }
                }
                updateAssistant(assistantId) { it.copy(isStreaming = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateAssistant(assistantId) { it.copy(isStreaming = false, isError = true) }
            } finally {
                _isStreaming.value = false
                history.save(_items.value)
            }
        }
    }

    fun stop() { streamJob?.cancel() }

    fun clear() {
        streamJob?.cancel()
        _items.value = emptyList()
        history.clear()
        engine.resetSession()
    }

    /** 모드 이탈/메모리 압박 시 호출 — 엔진 RAM 해제. */
    fun releaseEngine() { engine.release() }

    private suspend fun ensureLoaded() {
        if (engine.state.value == EngineState.UNINITIALIZED || engine.state.value == EngineState.ERROR) {
            engine.load(modelPath)
        }
    }

    /** MVP: 단순 단일 턴 프롬프트. 멀티턴은 엔진 Conversation 세션이 유지. */
    private fun buildPrompt(text: String): String = text

    private fun updateAssistant(id: String, transform: (ChatItem.AssistantMessage) -> ChatItem.AssistantMessage) {
        _items.update { list ->
            list.map { if (it is ChatItem.AssistantMessage && it.id == id) transform(it) else it }
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*LocalChatStoreTest*"`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatStore.kt \
        apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatHistory.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatStoreTest.kt
git commit -m "feat(gemma): LocalChatStore(전송 성공·단일 로드) 추가"
```

### Task 3.3: LocalChatStore 에러/취소 (TDD)

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatStoreTest.kt`

- [ ] **Step 1: 실패 테스트 추가**

`LocalChatStoreTest`에 추가:
```kotlin
@Test
fun `추론 예외면 assistant isError true 이고 스트리밍 종료`() = runTest {
    val s = LocalChatStore(FakeLocalLlmEngine(throwOnGenerate = true), "/m/g", this, NoopHistory)
    s.sendMessage("hi"); testScheduler.advanceUntilIdle()
    val assistant = s.items.value.filterIsInstance<ChatItem.AssistantMessage>().last()
    assertTrue(assistant.isError)
    assertEquals(false, assistant.isStreaming)
    assertEquals(false, s.isStreaming.value)
}

@Test
fun `load 실패면 assistant isError true`() = runTest {
    val s = LocalChatStore(FakeLocalLlmEngine(loadShouldFail = true), "/m/g", this, NoopHistory)
    s.sendMessage("hi"); testScheduler.advanceUntilIdle()
    val assistant = s.items.value.filterIsInstance<ChatItem.AssistantMessage>().last()
    assertTrue(assistant.isError)
}

@Test
fun `clear는 대화를 비우고 엔진 세션을 리셋한다`() = runTest {
    val engine = FakeLocalLlmEngine()
    val s = LocalChatStore(engine, "/m/g", this, NoopHistory)
    s.sendMessage("hi"); testScheduler.advanceUntilIdle()
    s.clear()
    assertEquals(0, s.items.value.size)
}

@Test
fun `releaseEngine은 엔진을 해제한다`() = runTest {
    val engine = FakeLocalLlmEngine()
    val s = LocalChatStore(engine, "/m/g", this, NoopHistory)
    s.releaseEngine()
    assertEquals(1, engine.releaseCount)
}
```

- [ ] **Step 2: 테스트 실행 (구현은 Task 3.2에 이미 존재 → PASS 기대)**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*LocalChatStoreTest*"`
Expected: PASS (6 tests). 만약 실패하면 Task 3.2의 `LocalChatStore` 에러 처리 분기를 수정.

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/LocalChatStoreTest.kt
git commit -m "test(gemma): LocalChatStore 에러·취소·clear·release 테스트 추가"
```

### Task 3.4: ChatModeStore (TDD)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/ChatModeStore.kt`
- Test: `shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/ChatModeStoreTest.kt`

- [ ] **Step 1: 실패 테스트 작성**

`ChatModeStoreTest.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatModeStoreTest {
    @Test
    fun `기본 모드는 CASH_AI`() {
        assertEquals(ChatModelMode.CASH_AI, ChatModeStore().mode.value)
    }
    @Test
    fun `select로 모드를 전환한다`() {
        val s = ChatModeStore()
        s.select(ChatModelMode.GEMMA_LOCAL)
        assertEquals(ChatModelMode.GEMMA_LOCAL, s.mode.value)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ChatModeStoreTest*"`
Expected: 컴파일 실패.

- [ ] **Step 3: 구현 작성**

`ChatModeStore.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ChatModelMode { CASH_AI, GEMMA_LOCAL }

class ChatModeStore {
    private val _mode = MutableStateFlow(ChatModelMode.CASH_AI)
    val mode: StateFlow<ChatModelMode> = _mode.asStateFlow()
    fun select(mode: ChatModelMode) { _mode.value = mode }
}
```

- [ ] **Step 4: 테스트 통과 + Commit**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest --tests "*ChatModeStoreTest*"`
Expected: PASS (2 tests).
```bash
git add apps/frontend/shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/localllm/ChatModeStore.kt \
        apps/frontend/shared/src/commonTest/kotlin/com/nomadclub/cashchat/shared/localllm/ChatModeStoreTest.kt
git commit -m "feat(gemma): 채팅 모델 모드 선택 스토어 추가"
```

### Task 3.5: 전체 shared 테스트 통과 확인

- [ ] **Step 1: 전체 단위 테스트 실행**

Run: `cd apps/frontend && ./gradlew :shared:testDebugUnitTest`
Expected: 신규 localllm 테스트 포함 BUILD SUCCESSFUL. (기존 테스트 회귀 없음 확인)

- [ ] **Step 2: 회귀 없으면 다음 페이즈로. 실패 시 systematic-debugging 적용.**

---

## Phase 4 — Android 엔진 & DI 배선

> 이 페이즈부터는 외부 SDK·실기기 의존이라 단위 TDD 대신 **빌드 + 실기기 계측 검증**으로 확인한다.

### Task 4.1: LiteRtLlmEngine (androidMain)

**Files:**
- Create: `shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/localllm/LiteRtLlmEngine.kt`

- [ ] **Step 1: SDK 래퍼 구현**

> LiteRT-LM Kotlin API: `Engine(EngineConfig(modelPath, Backend.GPU()))`, `engine.initialize()`,
> `engine.createConversation(ConversationConfig(SamplerConfig(...)))`, `conversation.sendMessageAsync(prompt): Flow<Message>` → `.map { it.text }`.
> 정확한 시그니처는 https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md 로 빌드 시 재확인(미해결 항목).

`LiteRtLlmEngine.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

class LiteRtLlmEngine : LocalLlmEngine {
    private val _state = MutableStateFlow(EngineState.UNINITIALIZED)
    override val state: StateFlow<EngineState> = _state

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @Throws(Exception::class)
    override suspend fun load(modelPath: String, params: SamplingParameters) {
        try {
            _state.value = EngineState.LOADING
            val cfg = EngineConfig(modelPath = modelPath, backend = Backend.GPU())
            engine = Engine(cfg).apply { initialize() }
            conversation = engine!!.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(
                        temperature = params.temperature, topP = params.topP, topK = params.topK,
                    ),
                ),
            )
            _state.value = EngineState.READY
        } catch (e: Exception) {
            _state.value = EngineState.ERROR
            throw e
        }
    }

    override fun generate(prompt: String): Flow<String> {
        val session = conversation ?: error("엔진이 초기화되지 않았습니다.")
        return session.sendMessageAsync(prompt)
            .onStart { _state.value = EngineState.GENERATING }
            .map { it.text }
            .onCompletion { _state.value = EngineState.READY }
    }

    override fun resetSession() {
        conversation?.close()
        conversation = engine?.createConversation()
    }

    override fun release() {
        conversation?.close(); conversation = null
        engine?.close(); engine = null
        _state.value = EngineState.UNINITIALIZED
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd apps/frontend && ./gradlew :shared:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL. (SDK API 시그니처 불일치 시 getting_started.md 기준으로 메서드명 보정)

- [ ] **Step 3: Commit**

```bash
git add apps/frontend/shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/localllm/LiteRtLlmEngine.kt
git commit -m "feat(gemma): Android LiteRT-LM 엔진 래퍼 추가"
```

### Task 4.2: Android Context 초기화 + Koin 모듈

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/nomadclub/cashchat/shared/di/SharedModule.kt`
- Create: `shared/src/androidMain/kotlin/com/nomadclub/cashchat/shared/di/AndroidLocalLlmModule.kt`
- Modify: Android 앱 Application 클래스 (Koin start 지점)

- [ ] **Step 1: commonMain DI에 로컬 LLM 공통 single 추가**

`SharedModule.kt`의 `sharedDataModule` 안에 추가(엔진은 `getOrNull`로 선택적):
```kotlin
single { com.nomadclub.cashchat.shared.localllm.ChatModeStore() }
single { com.nomadclub.cashchat.shared.localllm.DEFAULT_GEMMA_SPEC }
single<com.nomadclub.cashchat.shared.localllm.ModelDownloader> {
    com.nomadclub.cashchat.shared.localllm.KtorModelDownloader(get())
}
single {
    com.nomadclub.cashchat.shared.localllm.ModelDownloadStore(get(), get(), get())
}
```
> `LocalLlmEngine`/`LocalChatStore`는 플랫폼 모듈에서 등록한다(엔진 주입 출처가 다르므로).

- [ ] **Step 2: Android 전용 Koin 모듈 작성**

`AndroidLocalLlmModule.kt`:
```kotlin
package com.nomadclub.cashchat.shared.di

import com.nomadclub.cashchat.shared.localllm.LiteRtLlmEngine
import com.nomadclub.cashchat.shared.localllm.LocalChatHistory
import com.nomadclub.cashchat.shared.localllm.LocalChatStore
import com.nomadclub.cashchat.shared.localllm.LocalLlmEngine
import com.nomadclub.cashchat.shared.localllm.JsonFileLocalChatHistory
import org.koin.dsl.module

val androidLocalLlmModule = module {
    single<LocalLlmEngine> { LiteRtLlmEngine() }
    single<LocalChatHistory> { JsonFileLocalChatHistory() }
    // LocalChatStore 는 modelPath 가 런타임 결정이므로 팩토리로 노출
    factory { (modelPath: String, scope: kotlinx.coroutines.CoroutineScope) ->
        LocalChatStore(get(), modelPath, scope, get())
    }
}
```

- [ ] **Step 3: 공통 JSON 영속화 구현 추가**

`shared/src/commonMain/.../localllm/JsonFileLocalChatHistory.kt`:
```kotlin
package com.nomadclub.cashchat.shared.localllm

import com.nomadclub.cashchat.shared.chat.model.ChatItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class JsonFileLocalChatHistory(
    private val path: String = "${localModelsDir()}/local-chat.json",
) : LocalChatHistory {
    @Serializable private data class Row(val id: String, val role: String, val text: String)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): List<ChatItem> {
        val raw = fileSha256(path)?.let { readTextOrNull(path) } ?: return emptyList()
        return runCatching {
            json.decodeFromString<List<Row>>(raw).map {
                if (it.role == "user") ChatItem.UserMessage(it.id, it.text, ChatItem.SendStatus.CONFIRMED)
                else ChatItem.AssistantMessage(it.id, it.text, isStreaming = false)
            }
        }.getOrDefault(emptyList())
    }
    override fun save(items: List<ChatItem>) {
        val rows = items.mapNotNull {
            when (it) {
                is ChatItem.UserMessage -> Row(it.id, "user", it.text)
                is ChatItem.AssistantMessage -> Row(it.id, "assistant", it.text)
                else -> null
            }
        }
        writeText(path, json.encodeToString(rows))
    }
    override fun clear() { deleteFile(path) }
}
```
> `readTextOrNull`/`writeText`를 `LocalLlmPlatform.kt`에 `expect`로 추가하고 Android(`File.readText/writeText`)·iOS(`NSString`) actual 구현. (Task 4.1 빌드 시 함께)

- [ ] **Step 4: Application에서 Context/Koin 초기화 연결**

Android `Application.onCreate`에서 Koin 모듈 목록에 `androidLocalLlmModule` 추가하고:
```kotlin
com.nomadclub.cashchat.shared.localllm.AndroidLocalLlmContext.init(this)
```
(정확한 파일은 `grep -rl "startKoin\|androidContext" apps/frontend/app/src/main`로 확인.)

- [ ] **Step 5: 빌드 확인 + Commit**

Run: `cd apps/frontend && ./gradlew :shared:assembleDebug`
Expected: BUILD SUCCESSFUL.
```bash
git add apps/frontend/shared/src apps/frontend/app/src/main
git commit -m "feat(gemma): Android 엔진·영속화 Koin 배선 및 Context 초기화"
```

---

## Phase 5 — iOS 엔진 & DI 배선

### Task 5.1: iOS 플랫폼 actual 정밀 구현

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.ios.kt`

- [ ] **Step 1: NSFileManager/NSData 기반 파일 IO + sha256 구현**

iOS actual을 `platform.Foundation`(NSFileManager, NSData, NSFileHandle)과 `platform.CoreCrypto`/`platform.CommonCrypto`(CC_SHA256)로 구현. 정확한 cinterop 가용성은 빌드 시 확인(미해결 항목). 골격:
```kotlin
// appendBytesToFile: NSFileHandle(forWritingAtPath).seekToEndOfFile + writeData
// fileSize: NSFileManager.attributesOfItemAtPath[NSFileSize]
// fileSha256: NSData(contentsOfFile) → CC_SHA256
// deleteFile: NSFileManager.removeItemAtPath
// ensureDir: NSFileManager.createDirectoryAtPath(withIntermediateDirectories=true)
// availableStorageBytes: NSFileManager volume attributes(NSFileSystemFreeSize)
// readTextOrNull/writeText: NSString(contentsOfFile)/writeToFile
```

- [ ] **Step 2: 빌드 확인 + Commit**

Run: `cd apps/frontend && ./gradlew :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.
```bash
git add apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/localllm/LocalLlmPlatform.ios.kt
git commit -m "feat(gemma): iOS 파일 IO·sha256·저장공간 actual 구현"
```

### Task 5.2: doInitKoin에 엔진 주입 + iOS Koin 모듈

**Files:**
- Modify: `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/KoinIos.kt`
- Modify: `shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/IosBridges.kt`

- [ ] **Step 1: doInitKoin 시그니처 확장**

`KoinIos.kt`의 `doInitKoin`에 `gemmaEngine: LocalLlmEngine` 파라미터를 추가하고 module에 등록:
```kotlin
fun doInitKoin(
    baseUrl: String,
    tokenProvider: TokenProvider,
    adChatInterval: Long,
    gemmaEngine: com.nomadclub.cashchat.shared.localllm.LocalLlmEngine,
) {
    startKoin {
        modules(
            module {
                single<TokenProvider> { tokenProvider }
                single<com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider> {
                    com.nomadclub.cashchat.shared.ads.AdChatIntervalProvider { adChatInterval }
                }
                single<com.nomadclub.cashchat.shared.localllm.LocalLlmEngine> { gemmaEngine }
                single<com.nomadclub.cashchat.shared.localllm.LocalChatHistory> {
                    com.nomadclub.cashchat.shared.localllm.JsonFileLocalChatHistory()
                }
            },
            sharedDataModule(baseUrl),
        )
    }
}
```

- [ ] **Step 2: KoinHelper에 로컬 LLM 접근자 + Flow 콜렉터 추가**

`IosBridges.kt` `KoinHelper`에 추가:
```kotlin
private val chatMode: com.nomadclub.cashchat.shared.localllm.ChatModeStore by inject()
private val downloadStore: com.nomadclub.cashchat.shared.localllm.ModelDownloadStore by inject()
private val gemmaSpec: com.nomadclub.cashchat.shared.localllm.GemmaModelSpec by inject()
private val localEngine: com.nomadclub.cashchat.shared.localllm.LocalLlmEngine by inject()
private val localHistory: com.nomadclub.cashchat.shared.localllm.LocalChatHistory by inject()
fun chatModeStore() = chatMode
fun modelDownloadStore() = downloadStore
fun gemmaSpec() = gemmaSpec
/** Swift가 modelPath/scope를 넘겨 LocalChatStore를 생성. */
fun newLocalChatStore(modelPath: String) =
    com.nomadclub.cashchat.shared.localllm.LocalChatStore(
        localEngine, modelPath,
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
        localHistory,
    )
```
`FlowCollector`에 추가:
```kotlin
fun collectLocalChatItems(store: com.nomadclub.cashchat.shared.localllm.LocalChatStore,
                          onEach: (List<com.nomadclub.cashchat.shared.chat.model.ChatItem>) -> Unit) {
    scope.launch { store.items.collect { onEach(it) } }
}
fun collectLocalStreaming(store: com.nomadclub.cashchat.shared.localllm.LocalChatStore,
                          onEach: (Boolean) -> Unit) {
    scope.launch { store.isStreaming.collect { onEach(it) } }
}
fun collectDownloadState(store: com.nomadclub.cashchat.shared.localllm.ModelDownloadStore,
                         onEach: (com.nomadclub.cashchat.shared.localllm.ModelDownloadState) -> Unit) {
    scope.launch { store.state.collect { onEach(it) } }
}
```

- [ ] **Step 3: 빌드 확인 + Commit**

Run: `cd apps/frontend && ./gradlew :shared:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.
```bash
git add apps/frontend/shared/src/iosMain/kotlin/com/nomadclub/cashchat/shared/di/
git commit -m "feat(gemma): iOS Koin에 Gemma 엔진 주입·브릿지 접근자 추가"
```

### Task 5.3: Swift LiteRT-LM 엔진 구현 + Entitlements

**Files:**
- Create: `apps/frontend/CashChatIOS/CashChatIOS/.../LocalLlmEngineImpl.swift`
- Modify: iOS 앱 부트스트랩(`doInitKoin` 호출 지점)
- Modify: `CashChatIOS/CashChatIOS/*.entitlements`
- Modify: iOS 프로젝트 SPM 의존성(LiteRT-LM Swift, Early Preview)

- [ ] **Step 1: LiteRT-LM Swift 패키지 추가**

Xcode → Package Dependencies에 LiteRT-LM Swift 패키지 추가(Early Preview). 정확한 SPM URL/버전은 https://developers.google.com/edge/litert-lm 에서 확인(미해결 항목). 메모리 노트 `reference-ios-tnk-sdk-and-xcode-sync-groups` 패턴대로 헤드리스 통합 가능.

- [ ] **Step 2: LocalLlmEngine Swift 구현**

`LocalLlmEngineImpl.swift` — `CashChatShared.LocalLlmEngine` 프로토콜(Kotlin interface)을 채택. LiteRT-LM Swift API(Metal)를 래핑하고, `generate`는 Kotlin `Flow`를 반환해야 하므로 `FlowCollector` 역방향이 아니라 **Kotlin 측 `callbackFlow`를 Swift에서 만들 수 없다** → 대안: 엔진 인터페이스의 `generate`를 Swift에서 구현하기 위해, Swift는 토큰 콜백을 받는 보조 메서드를 노출하고 Kotlin `iosMain`에 `SwiftBackedLocalLlmEngine`(actual 어댑터)을 두어 `callbackFlow`로 감싼다.

> 구현 메모: `LocalLlmEngine.generate(): Flow<String>`를 Swift가 직접 만들기 어렵다. 따라서 iosMain에 어댑터를 추가한다:
> ```kotlin
> // iosMain: Swift가 구현하는 저수준 프로토콜
> interface SwiftLlmBridge {
>     fun load(modelPath: String, onReady: (Boolean) -> Unit)
>     fun generate(prompt: String, onToken: (String) -> Unit, onDone: (String?) -> Unit) // onDone(error?)
>     fun reset(); fun release()
> }
> class SwiftBackedLocalLlmEngine(private val bridge: SwiftLlmBridge) : LocalLlmEngine {
>     // state StateFlow, load는 suspendCancellableCoroutine, generate는 callbackFlow
> }
> ```
> Swift는 `SwiftLlmBridge`만 구현하고, `doInitKoin(gemmaEngine = SwiftBackedLocalLlmEngine(MySwiftBridge()))`로 주입.

이 어댑터 코드를 `shared/src/iosMain/.../localllm/SwiftBackedLocalLlmEngine.kt`로 작성(callbackFlow + suspendCancellableCoroutine, `@Throws`). Swift는 `SwiftLlmBridge` 채택 클래스만 작성.

- [ ] **Step 3: Entitlements 추가**

iOS 타깃 `.entitlements`에 추가:
```xml
<key>com.apple.developer.kernel.increased-memory-limit</key>
<true/>
<key>com.apple.developer.kernel.extended-virtual-addressing</key>
<true/>
```

- [ ] **Step 4: didReceiveMemoryWarning 훅**

iOS 앱에서 메모리 경고 수신 시 활성 `LocalChatStore.releaseEngine()` 호출 연결.

- [ ] **Step 5: clean build 검증 + Commit**

Run: 메모리 노트 `reference-ios-build-run-directly` 절차로 `xcodebuild clean build` (clean 필수 — `feedback-ios-verify-clean-build`).
Expected: 빌드 성공. 시뮬레이터 구동 후 다운로드→로드→토큰 스트림 수동 확인.
```bash
git add apps/frontend/CashChatIOS apps/frontend/shared/src/iosMain
git commit -m "feat(gemma): iOS Swift LiteRT-LM 엔진·어댑터·entitlements 추가"
```

---

## Phase 6 — UI (모델 스위처 + 다운로드 + Gemma 채팅)

### Task 6.1: Android — 모델 스위처 & Gemma 채팅 화면

**Files:**
- Create: `app/src/main/.../feature/chat/components/ModelSwitcher.kt`
- Create: `app/src/main/.../feature/chat/LocalChatViewModel.kt`
- Create: `app/src/main/.../feature/chat/GemmaModelDownloadCard.kt`
- Modify: `app/src/main/.../feature/chat/ChatScreen.kt`

- [ ] **Step 1: ModelSwitcher 컴포저블**

상단에 `Cash AI ↔ Gemma(로컬)` 토글(SegmentedButton). `canRunGemma`가 Insufficient면 Gemma 옵션 비활성 + 사유 툴팁. `onSelect(ChatModelMode)` 콜백.

- [ ] **Step 2: LocalChatViewModel**

`ChatModeStore`, `ModelDownloadStore`, `GemmaModelSpec`, `LocalChatStore`(팩토리, Ready 경로로 생성)를 묶는다. 상태: 모드, 다운로드 상태, 로컬 items/isStreaming, engineState. `send(text)`, `startDownload()`, `clear()`, `onLeaveMode()`(releaseEngine). Compose lifecycle `onTrimMemory`/`onStop` 시 `releaseEngine`.

- [ ] **Step 3: GemmaModelDownloadCard**

다운로드 상태별 UI: NotDownloaded(다운로드 버튼+크기), Downloading(진행률 바, received/total), Verifying(스피너), Failed(재시도), Ready(숨김).

- [ ] **Step 4: ChatScreen 통합**

상단에 `ModelSwitcher` 삽입. `GEMMA_LOCAL`이면: 에너지/HUD/광고/게이트/상품 UI 숨기고, Ready 전엔 `GemmaModelDownloadCard`, Ready면 기존 메시지 리스트 컴포저블 재사용해 로컬 items 렌더. `CASH_AI`면 기존 동작 유지.

- [ ] **Step 5: 빌드 + 프리뷰 검증**

Run: `cd apps/frontend && ./gradlew :app:assembleDebug` (JBR 필요 시 메모리 노트 참조)
Expected: BUILD SUCCESSFUL. 에뮬레이터/실기기에서 스위처 전환·다운로드 UI·로컬 대화 수동 확인(능력 게이팅 포함).

- [ ] **Step 6: Commit**

```bash
git add apps/frontend/app/src/main
git commit -m "feat(gemma): Android 모델 스위처·다운로드 카드·로컬 채팅 UI 추가"
```

### Task 6.2: iOS — 모델 스위처 & Gemma 채팅 화면 (SwiftUI)

**Files:**
- Modify: iOS 채팅 화면(SwiftUI) + 신규 스위처/다운로드 뷰

- [ ] **Step 1: 모델 스위처 뷰** — `KoinHelper.chatModeStore()` 구독, Segmented Picker, 능력 게이팅 비활성.
- [ ] **Step 2: 다운로드 뷰** — `collectDownloadState`로 상태별 UI(진행률/검증/실패/완료).
- [ ] **Step 3: 로컬 채팅 화면** — `KoinHelper.newLocalChatStore(modelPath)` + `collectLocalChatItems`/`collectLocalStreaming`. 메시지 리스트는 기존 채팅 뷰 재사용, economy UI 숨김. 화면 이탈/메모리 경고 시 `releaseEngine`. 메모리 노트 `reference-swiftui-emptyview-onappear-deadlock` 주의(로딩 placeholder를 EmptyView로 두지 말 것).
- [ ] **Step 4: clean build + 시뮬레이터 검증** — 다운로드→로드→스트림→전환 수동 확인.
- [ ] **Step 5: Commit**

```bash
git add apps/frontend/CashChatIOS
git commit -m "feat(gemma): iOS 모델 스위처·다운로드·로컬 채팅 SwiftUI 추가"
```

---

## Phase 7 — 라이선스 컴플라이언스

### Task 7.1: Apache 2.0 고지 + Powered by Gemma + 금지된 사용 정책

**Files:**
- Modify: 앱 내 오픈소스 라이선스 화면(Android/iOS), 서비스 약관 텍스트

- [ ] **Step 1: OSS 라이선스 목록에 Gemma / LiteRT-LM Apache 2.0 전문 추가.**
- [ ] **Step 2: Gemma 모드 화면에 "Powered by Gemma" 표기 추가.**
- [ ] **Step 3: 서비스 약관에 Gemma 금지된 사용 정책(무허가 의료/법률/금융 자동결정 등 제한) 흐름형 조항 반영.**
- [ ] **Step 4: Commit**

```bash
git commit -am "docs(gemma): Apache 2.0 고지·Powered by Gemma·금지된 사용 정책 반영"
```

---

## 미해결/검증 항목 (실행 중 확정)

1. Gemma 4 E2B `.litertlm` 실제 배포 URL·sha256·정확한 파일 크기 (HuggingFace) → `DEFAULT_GEMMA_SPEC`.
2. LiteRT-LM Android SDK 정확한 메서드 시그니처(`sendMessageAsync` 반환형, `Message.text`) 및 minSdk 요구.
3. LiteRT-LM iOS Swift 패키지 SPM URL/버전 및 스트리밍 콜백 시그니처 → `SwiftLlmBridge` 확정.
4. iOS CommonCrypto/CryptoKit cinterop 가용성(sha256).
5. Android Koin start 지점 파일 경로(`startKoin`/`androidContext`).

## 최종 검증

- [ ] `cd apps/frontend && ./gradlew :shared:testDebugUnitTest` — 전체 통과
- [ ] `./gradlew :app:assembleDebug` — Android 빌드
- [ ] iOS `xcodebuild clean build` — 시뮬레이터 빌드
- [ ] 실기기: 다운로드 → 로드 → 토큰 스트림 → 모드 전환 → 메모리 해제 수동 확인 (양 플랫폼)
- [ ] superpowers:requesting-code-review 로 머지 전 리뷰
