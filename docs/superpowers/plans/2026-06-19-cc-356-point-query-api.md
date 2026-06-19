# CC-356 포인트 조회 API 구현 계획 (이슈 B + E)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증 사용자의 코인 잔액(`GET /api/points/me`)과 적립/사용 내역(`GET /api/points/history`, 오프셋 페이지네이션)을 조회하는 읽기 전용 API를 추가한다.

**Architecture:** 기존 `domain/point/` 구조에 web 레이어(컨트롤러 + 응답 DTO)와 서비스/리포지토리 조회 메서드만 추가한다. 엔티티·DB 스키마 변경 없음. 컨트롤러는 `InventoryController` 패턴(`@WebMvcTest` 슬라이스 + 공유 `authentication.userId()` 확장)을 따른다.

**Tech Stack:** Kotlin 1.9.25, Spring Boot 3.5.11, Spring Data JPA, Java 21, Kotest(FunSpec) + mockito-kotlin, `@WebMvcTest`.

## Global Constraints

- 패키지 루트: `com.wnl.cashchat.api`
- 인증 principal 추출은 공유 확장 `com.wnl.cashchat.api.common.security.userId()` 사용 (컨트롤러 로컬 재정의 금지)
- 커밋 메시지: Conventional Commits, 한국어 본문 허용. 예: `feat(point): ...`
- 테스트: Kotest `FunSpec` + `io.kotest.extensions.spring.SpringExtension`, 컨트롤러는 `@WebMvcTest(... ::class)` + `@AutoConfigureMockMvc(addFilters = false)`
- 잔액 조회 응답 스펙(FE 확정): `{ "balance": Long }`
- 내역 응답: 커스텀 래퍼 `{ content, page, size, totalElements, totalPages, hasNext }`
- 기본값: 미초기화 잔액 `0`, 기본 page `0` / size `20`, 최대 size `100`(클램프), 정렬 `id DESC`
- 작업 디렉터리: `apps/backend/` 에서 gradle 실행

---

### Task 1: `UserPointService.getBalance` 잔액 조회 메서드

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointServiceTest.kt`

**Interfaces:**
- Consumes: `UserPointRepository.findByUserId(userId: Long): UserPoint?` (이미 존재)
- Produces: `fun UserPointService.getBalance(userId: Long): Long`

- [ ] **Step 1: 실패하는 테스트 작성**

`UserPointServiceTest.kt`의 `FunSpec({ ... })` 블록 안(기존 테스트들 옆)에 추가:

```kotlin
test("getBalance returns the stored balance when a point row exists") {
    val user = User(id = 1L, role = Role.GUEST, provider = AuthProviderType.NONE, name = "Guest")
    whenever(userPointRepository.findByUserId(1L)).thenReturn(UserPoint(user = user, balance = 1350L))

    userPointService.getBalance(1L) shouldBe 1350L
}

test("getBalance returns zero when the point row is missing") {
    whenever(userPointRepository.findByUserId(1L)).thenReturn(null)

    userPointService.getBalance(1L) shouldBe 0L
}
```

(파일 상단 import에 `com.wnl.cashchat.api.domain.user.persistence.entity.User`, `Role`, `AuthProviderType`는 이미 존재 — 신규 import 불필요.)

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*UserPointServiceTest*"`
Expected: FAIL — `unresolved reference: getBalance` (컴파일 오류)

- [ ] **Step 3: 최소 구현**

`UserPointService.kt`의 `hasEnoughBalance` 메서드 바로 아래에 추가:

```kotlin
@Transactional(readOnly = true)
fun getBalance(userId: Long): Long =
    userPointRepository.findByUserId(userId)?.balance ?: 0L
```

(`org.springframework.transaction.annotation.Transactional`는 이미 import됨.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "*UserPointServiceTest*"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointService.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointServiceTest.kt
git commit -m "feat(point): UserPointService.getBalance 잔액 조회 메서드 추가"
```

---

### Task 2: `GET /api/points/me` 컨트롤러 + 응답 DTO

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/web/response/PointBalanceResponse.kt`
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/web/controller/PointController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/web/controller/PointControllerTest.kt`

**Interfaces:**
- Consumes: `UserPointService.getBalance(userId: Long): Long` (Task 1), `Authentication.userId(): Long` (공유 확장)
- Produces: `class PointController` (`@RequestMapping("/api/points")`), `data class PointBalanceResponse(val balance: Long)`

- [ ] **Step 1: 실패하는 테스트 작성**

`PointControllerTest.kt` 신규 생성:

```kotlin
package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.jwt.JwtTokenHandler
import com.wnl.cashchat.api.domain.point.service.UserPointService
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PointController::class)
@AutoConfigureMockMvc(addFilters = false)
class PointControllerTest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var mockMvc: MockMvc

    @MockBean lateinit var userPointService: UserPointService
    @MockBean lateinit var jwtTokenHandler: JwtTokenHandler
    @MockBean(name = "jpaMappingContext") lateinit var jpaMappingContext: JpaMetamodelMappingContext

    private val principal = UsernamePasswordAuthenticationToken(1L, null)

    init {
        test("GET /api/points/me returns the user's balance") {
            whenever(userPointService.getBalance(eq(1L))).thenReturn(1350L)

            mockMvc.perform(get("/api/points/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.balance").value(1350))
        }

        test("GET /api/points/me returns zero for an uninitialized user") {
            whenever(userPointService.getBalance(eq(1L))).thenReturn(0L)

            mockMvc.perform(get("/api/points/me").principal(principal))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.balance").value(0))
        }
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*PointControllerTest*"`
Expected: FAIL — `unresolved reference: PointController` (컴파일 오류)

- [ ] **Step 3: 응답 DTO 작성**

`PointBalanceResponse.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.web.response

data class PointBalanceResponse(
    val balance: Long,
)
```

- [ ] **Step 4: 컨트롤러 작성**

`PointController.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.userId
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.point.web.response.PointBalanceResponse
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/points")
class PointController(
    private val userPointService: UserPointService,
) {
    @GetMapping("/me")
    fun getMyBalance(authentication: Authentication): PointBalanceResponse =
        PointBalanceResponse(userPointService.getBalance(authentication.userId()))
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "*PointControllerTest*"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/web/
git add apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/web/controller/PointControllerTest.kt
git commit -m "feat(point): GET /api/points/me 잔액 조회 엔드포인트 추가"
```

---

### Task 3: `PointTransactionRepository.findByUserId(Pageable)` + `UserPointService.getHistory`

**Files:**
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/repository/PointTransactionRepository.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointService.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointServiceTest.kt`

**Interfaces:**
- Consumes: `PointTransactionRepository.findByUserId(userId: Long, pageable: Pageable): Page<PointTransaction>` (이 태스크에서 추가)
- Produces: `fun UserPointService.getHistory(userId: Long, pageable: Pageable): Page<PointTransaction>`

- [ ] **Step 1: 실패하는 테스트 작성**

`UserPointServiceTest.kt`에 추가. 파일 상단 import에 다음을 추가:

```kotlin
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
```

테스트 본문(`FunSpec({ ... })` 안):

```kotlin
test("getHistory delegates to the repository with the given pageable and returns the page") {
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
    val txn = PointTransaction(
        userId = 1L,
        delta = 100L,
        balanceAfter = 1350L,
        reason = PointTransactionReason.ATTENDANCE,
        idempotencyKey = "key-1",
    )
    val page = PageImpl(listOf(txn), pageable, 1L)
    whenever(pointTransactionRepository.findByUserId(1L, pageable)).thenReturn(page)

    userPointService.getHistory(1L, pageable) shouldBe page
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*UserPointServiceTest*"`
Expected: FAIL — `unresolved reference: getHistory` 및 `findByUserId` 오버로드 없음(컴파일 오류)

- [ ] **Step 3: 리포지토리 메서드 추가**

`PointTransactionRepository.kt` 를 다음으로 교체:

```kotlin
package com.wnl.cashchat.api.domain.point.persistence.repository

import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PointTransactionRepository : JpaRepository<PointTransaction, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): PointTransaction?

    fun findByUserId(userId: Long, pageable: Pageable): Page<PointTransaction>
}
```

- [ ] **Step 4: 서비스 메서드 추가**

`UserPointService.kt`의 `getBalance` 아래에 추가:

```kotlin
@Transactional(readOnly = true)
fun getHistory(userId: Long, pageable: Pageable): Page<PointTransaction> =
    pointTransactionRepository.findByUserId(userId, pageable)
```

`UserPointService.kt` 상단 import에 추가:

```kotlin
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "*UserPointServiceTest*"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/persistence/repository/PointTransactionRepository.kt \
        apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointService.kt \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/service/UserPointServiceTest.kt
git commit -m "feat(point): 포인트 내역 페이지 조회(getHistory) 서비스/리포지토리 추가"
```

---

### Task 4: `GET /api/points/history` 컨트롤러 + 페이지 응답 DTO

**Files:**
- Create: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/web/response/PointHistoryResponse.kt`
- Modify: `apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/web/controller/PointController.kt`
- Test: `apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/web/controller/PointControllerTest.kt`

**Interfaces:**
- Consumes: `UserPointService.getHistory(userId: Long, pageable: Pageable): Page<PointTransaction>` (Task 3)
- Produces:
  - `data class PointHistoryItemResponse(delta: Long, balanceAfter: Long, reason: PointTransactionReason, createdAt: Instant)`
  - `data class PointHistoryResponse(content: List<PointHistoryItemResponse>, page: Int, size: Int, totalElements: Long, totalPages: Int, hasNext: Boolean)` + `companion object { fun from(page: Page<PointTransaction>): PointHistoryResponse }`
  - `PointController.getMyHistory(authentication, page, size): PointHistoryResponse`

- [ ] **Step 1: 실패하는 테스트 작성**

`PointControllerTest.kt`의 `init { ... }` 블록 안에 테스트 추가. 파일 상단 import에 추가:

```kotlin
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import org.mockito.kotlin.any
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
```

테스트 본문:

```kotlin
test("GET /api/points/history returns items with page metadata") {
    val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
    val txn = PointTransaction(
        userId = 1L,
        delta = 100L,
        balanceAfter = 1350L,
        reason = PointTransactionReason.ATTENDANCE,
        idempotencyKey = "key-1",
    )
    whenever(userPointService.getHistory(eq(1L), any())).thenReturn(PageImpl(listOf(txn), pageable, 53L))

    mockMvc.perform(get("/api/points/history").principal(principal))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.content[0].delta").value(100))
        .andExpect(jsonPath("$.content[0].balanceAfter").value(1350))
        .andExpect(jsonPath("$.content[0].reason").value("ATTENDANCE"))
        .andExpect(jsonPath("$.content[0].createdAt").exists())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(53))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.hasNext").value(true))
}

test("GET /api/points/history requests id DESC sort and respects custom page/size") {
    val captor = argumentCaptor<Pageable>()
    whenever(userPointService.getHistory(eq(1L), captor.capture()))
        .thenReturn(PageImpl(emptyList(), PageRequest.of(2, 10, Sort.by(Sort.Direction.DESC, "id")), 0L))

    mockMvc.perform(
        get("/api/points/history").param("page", "2").param("size", "10").principal(principal)
    ).andExpect(status().isOk)

    val used = captor.firstValue
    used.pageNumber shouldBe 2
    used.pageSize shouldBe 10
    used.sort shouldBe Sort.by(Sort.Direction.DESC, "id")
}

test("GET /api/points/history clamps size above the maximum to 100") {
    val captor = argumentCaptor<Pageable>()
    whenever(userPointService.getHistory(eq(1L), captor.capture()))
        .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id")), 0L))

    mockMvc.perform(
        get("/api/points/history").param("size", "999").principal(principal)
    ).andExpect(status().isOk)

    captor.firstValue.pageSize shouldBe 100
}

test("GET /api/points/history returns hasNext false on the last page") {
    whenever(userPointService.getHistory(eq(1L), any()))
        .thenReturn(PageImpl(emptyList(), PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id")), 0L))

    mockMvc.perform(get("/api/points/history").principal(principal))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.content.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalElements").value(0))
}
```

추가 import (matcher/captor):

```kotlin
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.argumentCaptor
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd apps/backend && ./gradlew test --tests "*PointControllerTest*"`
Expected: FAIL — `/api/points/history` 매핑 없음(404) 및 `unresolved reference: PointHistoryResponse`(컴파일 오류)

- [ ] **Step 3: 페이지 응답 DTO 작성**

`PointHistoryResponse.kt`:

```kotlin
package com.wnl.cashchat.api.domain.point.web.response

import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransaction
import com.wnl.cashchat.api.domain.point.persistence.entity.PointTransactionReason
import org.springframework.data.domain.Page
import java.time.Instant

data class PointHistoryItemResponse(
    val delta: Long,
    val balanceAfter: Long,
    val reason: PointTransactionReason,
    val createdAt: Instant,
) {
    companion object {
        fun from(transaction: PointTransaction): PointHistoryItemResponse =
            PointHistoryItemResponse(
                delta = transaction.delta,
                balanceAfter = transaction.balanceAfter,
                reason = transaction.reason,
                createdAt = transaction.createdAt,
            )
    }
}

data class PointHistoryResponse(
    val content: List<PointHistoryItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
) {
    companion object {
        fun from(page: Page<PointTransaction>): PointHistoryResponse =
            PointHistoryResponse(
                content = page.content.map(PointHistoryItemResponse::from),
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                hasNext = page.hasNext(),
            )
    }
}
```

- [ ] **Step 4: 컨트롤러에 `/history` 추가**

`PointController.kt` 를 다음으로 교체(잔액 엔드포인트 유지 + 내역 추가):

```kotlin
package com.wnl.cashchat.api.domain.point.web.controller

import com.wnl.cashchat.api.common.security.userId
import com.wnl.cashchat.api.domain.point.service.UserPointService
import com.wnl.cashchat.api.domain.point.web.response.PointBalanceResponse
import com.wnl.cashchat.api.domain.point.web.response.PointHistoryResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/points")
class PointController(
    private val userPointService: UserPointService,
) {
    @GetMapping("/me")
    fun getMyBalance(authentication: Authentication): PointBalanceResponse =
        PointBalanceResponse(userPointService.getBalance(authentication.userId()))

    @GetMapping("/history")
    fun getMyHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PointHistoryResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"))
        return PointHistoryResponse.from(userPointService.getHistory(authentication.userId(), pageable))
    }

    private companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd apps/backend && ./gradlew test --tests "*PointControllerTest*"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/point/web/ \
        apps/backend/src/test/kotlin/com/wnl/cashchat/api/domain/point/web/controller/PointControllerTest.kt
git commit -m "feat(point): GET /api/points/history 내역 페이지 조회 엔드포인트 추가"
```

---

### Task 5: 전체 빌드 검증

**Files:** 없음(검증 전용)

- [ ] **Step 1: 전체 빌드 + 테스트**

Run: `cd apps/backend && ./gradlew clean build`
Expected: BUILD SUCCESSFUL — 전체 테스트 통과(신규 `PointControllerTest`, 갱신된 `UserPointServiceTest` 포함)

- [ ] **Step 2: 실패 시 처리**

빌드/테스트 실패 시 해당 태스크로 돌아가 수정 후 재실행. 모든 테스트가 통과해야 완료.

---

## Self-Review

**Spec coverage:**
- 이슈 B(잔액 조회): Task 1(서비스) + Task 2(엔드포인트/DTO) ✔
- 이슈 E(내역 조회): Task 3(리포지토리/서비스) + Task 4(엔드포인트/페이지 DTO) ✔
- 미초기화 0 반환: Task 1 ✔ / 기본·최대 size, page 보정, id DESC 정렬: Task 4 ✔
- 커스텀 페이지 래퍼(content/page/size/totalElements/totalPages/hasNext): Task 4 DTO ✔
- reason enum 문자열 직렬화: Task 4 테스트(`"ATTENDANCE"`) ✔
- 단일 PR 묶음: 모든 태스크가 한 브랜치(`feature/cc-356-point-api`) ✔

**Placeholder scan:** TBD/TODO/모호 표현 없음. 모든 코드 스텝에 실제 코드 포함 ✔

**Type consistency:**
- `getBalance(Long): Long` — Task 1 정의, Task 2 사용 일치 ✔
- `getHistory(Long, Pageable): Page<PointTransaction>` — Task 3 정의, Task 4 사용 일치 ✔
- `PointHistoryResponse.from(Page<PointTransaction>)`, `PointHistoryItemResponse.from(PointTransaction)` — Task 4 내부 일치 ✔
- `findByUserId(Long, Pageable): Page<PointTransaction>` — Task 3 리포지토리/서비스/테스트 일치 ✔
- `PointController` 생성자 의존성 `UserPointService` — Task 2/4 동일 ✔

이슈 없음.
