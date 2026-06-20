# CC-356 포인트 조회 API 설계 (이슈 B + E)

- **작성일**: 2026-06-19
- **Jira**: CC-356
- **범위**: `GET /api/points/me` (잔액 조회) + `GET /api/points/history` (적립/사용 내역) — 단일 PR
- **대상**: `apps/backend/` (`com.wnl.cashchat.api.domain.point`)

## 배경 / 목적

FE(Android·iOS)가 코인 잔액을 임시값 `1250`으로 하드코딩한 탓에 "잔액이 있어 보이는데 구매가 거절"되는 현상이 발생 중. FE의 최우선 블로커를 해소하기 위해 실제 잔액 조회 API(이슈 B)와, 마이페이지 내역 화면용 적립/사용 내역 API(이슈 E)를 제공한다.

이미 존재하는 자산:

- `UserPoint` 엔티티 — `balance: Long` 보유, `user_points` 테이블
- `PointTransaction` 원장 — `user_id` 인덱스 + `idempotency_key` 유니크 제약, 적립/차감이 이미 쌓이고 있음
- `UserPointService`, `UserPointRepository`, `PointTransactionRepository`
- `PointExceptionHandler`
- 공유 확장 `com.wnl.cashchat.api.common.security.userId(): Long`

→ **엔티티·DB 스키마 변경 없음.** 조회 메서드와 web 레이어만 신규 추가한다.

## 아키텍처 / 파일 배치

기존 `domain/point/` 구조를 그대로 따른다.

```
domain/point/
  service/UserPointService.kt          (+ getBalance, getHistory 메서드 추가)
  persistence/repository/
    PointTransactionRepository.kt       (+ findByUserId(userId, Pageable))
  web/
    controller/PointController.kt        (신규)
    response/PointBalanceResponse.kt     (신규)
    response/PointHistoryResponse.kt     (신규: 래퍼 + 아이템 DTO)
```

컨트롤러는 `InventoryController`/`EnergyController` 패턴을 따르며, 공유 확장 `authentication.userId()`(`common.security`)를 사용한다(로컬 재정의 금지).

## 이슈 B — `GET /api/points/me`

### Service
```kotlin
@Transactional(readOnly = true)
fun getBalance(userId: Long): Long =
    userPointRepository.findByUserId(userId)?.balance ?: 0L
```
- **미초기화 시 `0` 반환.** GET은 부수효과(행 생성) 없이 동작하며, 기존 `hasEnoughBalance`가 누락 행을 0으로 취급하는 것과 일관. 실제로는 가입/로그인 시 `AuthService`가 `ensureInitialized`를 호출하므로 인증 사용자에겐 행이 존재한다.

### Controller
```kotlin
@RestController
@RequestMapping("/api/points")
class PointController(private val userPointService: UserPointService) {
    @GetMapping("/me")
    fun getMyBalance(authentication: Authentication): PointBalanceResponse =
        PointBalanceResponse(userPointService.getBalance(authentication.userId()))
}
```

### 응답 (FE 확정)
```json
{ "balance": 1350 }
```
`PointBalanceResponse(val balance: Long)`

## 이슈 E — `GET /api/points/history`

### Repository
```kotlin
fun findByUserId(userId: Long, pageable: Pageable): Page<PointTransaction>
```

### Service
```kotlin
@Transactional(readOnly = true)
fun getHistory(userId: Long, pageable: Pageable): Page<PointTransaction> =
    pointTransactionRepository.findByUserId(userId, pageable)
```

### Controller
- `@GetMapping("/history")`
- `@RequestParam(defaultValue = "0") page`, `@RequestParam(defaultValue = "20") size`
- `size`는 `1..100`으로 클램프(예외 대신 보정), `page`는 음수면 0으로 보정
- 정렬: **`id DESC`(최신순)** — `createdAt`은 동일 `Instant` 충돌 가능성이 있어 단조 증가하는 PK로 결정적 정렬을 보장
- `PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))`로 조회 후 커스텀 래퍼로 매핑

### 응답 (커스텀 DTO 래퍼)
```json
{
  "content": [
    { "delta": 100, "balanceAfter": 1350, "reason": "ATTENDANCE", "createdAt": "2026-06-19T12:34:56Z" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 53,
  "totalPages": 3,
  "hasNext": true
}
```
- `PointHistoryItemResponse(delta: Long, balanceAfter: Long, reason: PointTransactionReason, createdAt: Instant)`
- `PointHistoryResponse(content: List<PointHistoryItemResponse>, page: Int, size: Int, totalElements: Long, totalPages: Int, hasNext: Boolean)` + `from(Page<PointTransaction>)` 팩토리
- `reason`은 enum 이름 문자열(`ATTENDANCE`, `SHOP_PURCHASE` 등) 그대로 직렬화
- `createdAt`은 `Instant`(UTC ISO-8601)

Spring `Page<T>` 직접 직렬화는 `pageable`/`sort` 등 불필요 필드 노출과 버전 간 직렬화 불안정 때문에 채택하지 않고, 안정적인 커스텀 래퍼를 사용한다.

## 에러 처리

- 인증 누락/잘못된 principal → `userId()`가 `AuthenticationCredentialsNotFoundException` throw (기존 패턴, 401)
- `size` 0 이하/과대 → `1..100`으로 클램프, `page` 음수 → 0 보정 (예외 대신 보정)
- 숫자 파싱 불가 파라미터 → Spring 기본 400
- 잔액 조회는 항상 200(미초기화도 0). 신규 비즈니스 예외 없음 → `PointExceptionHandler` 변경 없음

## 테스트 (TDD, Kotest)

- **Service 단위**(mock repository)
  - `getBalance`: 행 존재 시 잔액 반환 / 누락 시 `0`
  - `getHistory`: 전달된 `Pageable`로 위임하고 `Page` 결과를 그대로 반환
- **Controller**(기존 `*ControllerTest` 패턴)
  - `/me`: 200 + `{ "balance": ... }`
  - `/history`: 페이지 메타 필드, `id DESC` 정렬, 기본 size(20) / 커스텀 size, size 클램프(>100 → 100), 빈 결과(`hasNext=false`)
  - 인증 처리(principal 누락 시 401)

## 확정 기본값

| 항목 | 값 |
|---|---|
| 미초기화 잔액 | `0` |
| 기본 page / size | `0` / `20` |
| 최대 size | `100` (초과 시 클램프) |
| 정렬 | `id DESC` (최신순) |
| `reason` 직렬화 | enum 이름 문자열 |

## 범위 밖 (Out of Scope)

- 포인트 적립/차감 로직 변경 (이미 `recordTransaction`에 존재)
- DB 스키마/마이그레이션 변경
- 커서 기반 무한 스크롤 페이지네이션 (현재 마이페이지 내역엔 오프셋 페이지로 충분)
