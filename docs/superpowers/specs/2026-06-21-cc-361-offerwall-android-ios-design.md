# TNK 오퍼월 플랫폼 분리 (Android/iOS) 설계

- **Jira**: CC-361
- **작성일**: 2026-06-21
- **대상 브랜치**: `feature/cc-361-offerwall-android-ios` → `dev`
- **범위**: 백엔드 (`apps/backend/`, `domain/offerwall/`)

## 배경 / 문제

TNK(T&K Factory) 오퍼월은 안드로이드 앱과 iOS 앱을 **각각 별도 앱으로 등록**하며, 앱마다 고유한 **앱키**와 **콜백(포스트백) URL**을 사용한다. 현재 백엔드의 `domain/offerwall/` 구현은 단일 앱을 전제로 한다:

- 단일 콜백 엔드포인트: `POST /api/offerwall/tnk/callback`
- 단일 설정값: `app.offerwall.tnk.appKey`
- 서명검증: `md5(appKey + md_user_nm + seq_id)` — 단일 키로만 검증

따라서 안드로이드/iOS 두 앱의 콜백을 동시에 정확히 검증·처리할 수 없다. 이 작업은 기존 구현을 **플랫폼 인지(platform-aware)** 구조로 확장한다.

> TNK 실제 앱 연동은 아직 이루어지지 않았다(테이블에 운영 데이터 없음). 따라서 하위호환을 유지하지 않고 **깔끔히 교체**한다.

## 목표

- 안드로이드/iOS 콜백을 **경로로 구분**하여 각각의 앱키로 서명검증한다.
- 콜백 처리·멱등성·포인트 적립의 기존 견고함(서명검증 우선, 멱등 insert, 행 잠금)을 그대로 유지한다.
- 어느 플랫폼에서 온 콜백인지 감사 가능하도록 기록한다.

## 비목표 (이번 작업 제외)

- 프론트엔드 TNK SDK 연동(안드로이드/iOS 각각의 `setUserName` 호출 등 앱 측 작업).
- 환산비율(`pointToCoinRatio`)·ACK 본문의 플랫폼별 분리 — 실제 단가 차이가 확인되기 전까지는 공통 유지(YAGNI).
- `user-token` 발급 흐름 변경 — 토큰은 유저당 1개로 플랫폼 무관하므로 변경 없음.

## 설계 결정 (확정)

| 항목 | 결정 |
|------|------|
| 플랫폼 구분 방식 | **경로 분리** — `/callback/{platform}` (`android`/`ios`) |
| 플랫폼별로 분리할 설정 | **앱키만** 분리. 환산비율·ACK는 공통 |
| 기존 무플랫폼 엔드포인트·단일 키 | **제거 후 교체** (하위호환 미유지) |

근거: TNK가 앱별로 콜백 URL을 따로 받기 때문에 경로 분리가 가장 자연스럽고, **서명검증 이전에 플랫폼이 확정**되어 잘못된 키로 검증할 여지가 없다.

## 상세 설계

### 1. 도메인 — `OfferwallPlatform` enum 신규

`domain/offerwall/`에 추가:

```kotlin
enum class OfferwallPlatform {
    ANDROID, IOS;

    companion object {
        // 경로값 "android"/"ios" → enum (대소문자 무시). 알 수 없는 값은 도메인 예외.
        fun from(raw: String): OfferwallPlatform =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
                ?: throw UnknownOfferwallPlatformException(raw)
    }
}
```

`UnknownOfferwallPlatformException`(도메인 예외)도 함께 추가한다.

### 2. 설정 — `TnkOfferwallProperties` 변경 (앱키만 분리)

```kotlin
@Validated
@ConfigurationProperties(prefix = "app.offerwall.tnk")
data class TnkOfferwallProperties(
    val android: Platform = Platform(),
    val ios: Platform = Platform(),
    @field:Positive val pointToCoinRatio: Double = 1.0,
    val ack: Ack = Ack(),
) {
    data class Platform(val appKey: String = "")
    data class Ack(val successBody: String = "SUCCESS")

    fun appKeyFor(platform: OfferwallPlatform): String = when (platform) {
        OfferwallPlatform.ANDROID -> android.appKey
        OfferwallPlatform.IOS -> ios.appKey
    }
}
```

`application.yaml` (base):

```yaml
app:
  offerwall:
    tnk:
      android:
        app-key: ${APP_OFFERWALL_TNK_ANDROID_APP_KEY:}
      ios:
        app-key: ${APP_OFFERWALL_TNK_IOS_APP_KEY:}
      point-to-coin-ratio: ${APP_OFFERWALL_TNK_POINT_TO_COIN_RATIO:1.0}
      ack:
        success-body: ${APP_OFFERWALL_TNK_ACK_SUCCESS_BODY:SUCCESS}
```

`application-prod.yaml`: 두 앱키를 기본값 없이 명시(미설정 시 빈 문자열 → fail-closed 유지). 기존 단일 `app.offerwall.tnk.app-key` 라인은 base/prod 모두에서 제거.

### 3. 엔드포인트 — `OfferwallController`

```kotlin
@PostMapping("/callback/{platform}")   // /api/offerwall/tnk/callback/android | .../ios
fun handleCallback(
    @PathVariable platform: String,
    @RequestParam("seq_id") seqId: String,
    @RequestParam("pay_pnt") payPnt: Long,
    @RequestParam("md_user_nm") mdUserNm: String,
    @RequestParam("md_chk") mdChk: String,
): ResponseEntity<String> {
    val resolvedPlatform = OfferwallPlatform.from(platform)   // 잘못된 값 → 400
    val rawQuery = "seq_id=$seqId&pay_pnt=$payPnt&md_user_nm=$mdUserNm&md_chk=$mdChk"
    tnkOfferwallService.handleCallback(
        resolvedPlatform,
        TnkOfferwallCallbackParams(seqId, payPnt, mdUserNm, mdChk, rawQuery),
        Instant.now(),
    )
    return ResponseEntity.ok(tnkOfferwallProperties.ack.successBody)
}
```

`user-token` 엔드포인트는 변경 없음.

### 4. 보안 — `SecurityConfig`

- 기존 `.requestMatchers(HttpMethod.POST, "/api/offerwall/tnk/callback").permitAll()` 라인 **제거**.
- 신규 `.requestMatchers(HttpMethod.POST, "/api/offerwall/tnk/callback/*").permitAll()` 추가.

### 5. 서명검증 — `TnkMdChecksumVerifier`

```kotlin
fun isValid(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams): Boolean {
    val appKey = tnkOfferwallProperties.appKeyFor(platform)
    if (appKey.isBlank()) return false   // fail-closed
    val expected = md5Hex(appKey + params.mdUserNm + params.seqId)
    return expected.equals(params.mdChk, ignoreCase = true)
}
```

### 6. 엔티티 / 마이그레이션 — 플랫폼 기록 + 멱등성 단위

`TnkOfferwallCallback`에 `platform` 컬럼 추가:

```kotlin
@Enumerated(EnumType.STRING)
@Column(name = "platform", nullable = false, length = 16)
val platform: OfferwallPlatform,
```

- 유니크 제약: `seq_id` → **`(platform, seq_id)`** 로 교체. (플랫폼이 다르면 동일 seq_id라도 독립 처리)
- 포인트 멱등키: `tnk:offerwall:${seqId}` → **`tnk:offerwall:${platform}:${seqId}`**
- `insertIfAbsent` 네이티브 쿼리에 `platform` 컬럼 추가, `findForUpdate`/`findBySeqId` 조회를 `(platform, seqId)` 기준으로 변경.

**Flyway 마이그레이션** `V{n}__add_platform_to_tnk_offerwall_callbacks.sql`:

```sql
ALTER TABLE tnk_offerwall_callbacks
    ADD COLUMN platform VARCHAR(16) NOT NULL;

ALTER TABLE tnk_offerwall_callbacks
    DROP INDEX uk_tnk_offerwall_callbacks_seq_id,
    ADD CONSTRAINT uk_tnk_offerwall_callbacks_platform_seq_id UNIQUE (platform, seq_id);
```

> TNK 미연동 상태 → 테이블이 비어 있어 NOT NULL 컬럼 추가가 안전하다. (마이그레이션 번호는 기존 `db/migration` 최신 버전 다음으로 부여)

### 7. 서비스 흐름 — `TnkOfferwallService.handleCallback`

시그니처: `handleCallback(platform: OfferwallPlatform, params: TnkOfferwallCallbackParams, now: Instant): TnkOfferwallStatus`

기존 흐름을 유지하되 `platform`을 관통시킨다:

1. `tnkMdChecksumVerifier.isValid(platform, params)` — 실패 시 `REJECTED_BAD_SIGNATURE`
2. `insertIfAbsent(platform, seqId, ...)` — 멱등 insert
3. `findForUpdate(platform, seqId)` — 행 잠금
4. 상태가 `PENDING`이 아니면 기존 상태 반환(재시도 멱등)
5. 유저 토큰 해석(`md_user_nm` → userId), 없으면 `REJECTED_UNKNOWN_USER`
6. `payPnt > 0` 검증, 아니면 `REJECTED_NON_POSITIVE`
7. 환산 후 `userPointService.recordTransaction(... idempotencyKey = "tnk:offerwall:${platform}:${seqId}", reason = OFFERWALL)`
8. 엔티티 `markGranted(userId, coinAmount)` + `platform` 저장 → `GRANTED`

`TnkOfferwallStatus` enum은 변경 없음(플랫폼은 직교 정보).

### 8. 예외 처리

`OfferwallExceptionHandler`(`@RestControllerAdvice(basePackages = ["...domain.offerwall"])`)에 추가:

```kotlin
@ExceptionHandler(UnknownOfferwallPlatformException::class)
fun handleUnknownPlatform(e: UnknownOfferwallPlatformException): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse("UNKNOWN_OFFERWALL_PLATFORM", "지원하지 않는 오퍼월 플랫폼입니다."))
```

(기존에 핸들러가 없으면 신규 생성, 있으면 케이스 추가)

## 테스트 (Kotest, 기존 패턴 준수)

1. 안드로이드 키로 서명된 콜백 → `/callback/android` → `GRANTED`, 포인트 적립.
2. iOS 키로 서명된 콜백 → `/callback/ios` → `GRANTED`.
3. **교차 검증**: 안드로이드 콜백을 iOS 키로 검증 시 → `REJECTED_BAD_SIGNATURE`.
4. 동일 `seq_id`가 양 플랫폼에서 도착 → 각각 독립 적립(멱등키 분리 검증).
5. 동일 플랫폼·동일 `seq_id` 재전송 → 1회만 적립(멱등성).
6. 잘못된 platform 경로값(`/callback/web`) → HTTP 400.
7. 앱키 미설정(빈 문자열) → fail-closed (`REJECTED_BAD_SIGNATURE`).
8. 알 수 없는 유저 토큰 → `REJECTED_UNKNOWN_USER`.

## 영향 받는 파일 (예상)

- 신규: `OfferwallPlatform.kt`, `UnknownOfferwallPlatformException.kt`, Flyway 마이그레이션 SQL, (필요 시) `OfferwallExceptionHandler.kt`
- 변경: `TnkOfferwallProperties.kt`, `OfferwallController.kt`, `TnkMdChecksumVerifier.kt`, `TnkOfferwallService.kt`, `TnkOfferwallCallback.kt`, `TnkOfferwallCallbackRepository.kt`, `SecurityConfig.kt`, `application.yaml`, `application-prod.yaml`
- 테스트: `TnkOfferwallService` 및 콜백 컨트롤러 테스트
