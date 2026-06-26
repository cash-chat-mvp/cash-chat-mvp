# [이슈] AdMob 리워드 SSV 콜백 등록 & 검증 실패

- 상태: **Open / Blocked** (AdMob 콘솔 "URL 확인" 통과 안 됨)
- 작성일: 2026-06-23
- 영역: AdMob 리워드 광고 서버 사이드 검증(SSV) — iOS/Android 공통
- 관련 코드: `apps/backend/.../domain/ad/`, 운영 가이드 [docs/guides/firebase-remote-config-analytics.md](../guides/firebase-remote-config-analytics.md), [docs/admob-production-setup.md](../admob-production-setup.md)

---

## 1. 요약

리워드 광고 적립을 위해 AdMob 콘솔에 SSV 콜백 URL을 등록하려는데, **콘솔의 "URL 확인"이 통과되지 않아 등록이 막혀 있다.** 콜백 백엔드 엔드포인트(`GET /api/ads/google/ssv`)는 살아 있고 정상 동작하지만, AdMob의 확인 프로브에 대해 400을 반환한다.

추가로, 콜백이 동작하려면 **리워드 광고 단위 ID를 GitHub Secrets에 등록**해야 한다(iOS/Android 각각).

---

## 2. 배경

- 백엔드 SSV 엔드포인트: **`GET https://cashchat.duckdns.org/api/ads/google/ssv`**
  - 인증 없이 공개([SecurityConfig](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/common/security/config/SecurityConfig.kt) `permitAll`).
  - 흐름: 쿼리 파싱 → ad_unit 검증(옵션) → **user_id를 Long으로 파싱** → **서명 검증** → 이벤트 저장 + 적립. 실패 시 `InvalidGoogleAdSsvCallbackException` → **400**, 공개키 일시 장애 → **503**.
  - 서명·멱등 적립·일일 쿼터·nonce까지 구현 완료([GoogleAdSsvService.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt)).

---

## 3. 증상

AdMob 콘솔 리워드 단위 → SSV → 콜백 URL에 `https://cashchat.duckdns.org/api/ads/google/ssv` 입력 후 "URL 확인" 클릭 시:

```
{"1":{"1":7,"3":"Url error"},"2":400}
```

- 확인 폼 user_id에 **이메일**(`wildnomadlab@gmail.com`)을 넣었을 때 실패.
- user_id에 **숫자**를 넣어도 여전히 실패.

## 4. 확인된 사실 (재현/조사)

| 요청 | 결과 |
|---|---|
| `GET .../ssv` (파라미터 없음) | **400** `{"code":"INVALID_GOOGLE_AD_SSV_CALLBACK"}` — 우리 앱이 정상 응답(도달성 OK) |
| `GET .../ssv?...&signature=FAKE&key_id=...` | **400** (서명 불일치로 거부) |
| `HEAD .../ssv` | **401** (보안 설정이 `GET`만 허용 → HEAD 미허용) |

- 엔드포인트/배포/TLS는 정상. nginx 뒤 백엔드가 응답함.
- 백엔드는 **유효한 Google 서명이 없는 요청을 전부 400**으로 거부(의도적 엄격성).

## 5. 원인 가설 (가능성 순)

1. **AdMob "URL 확인" 프로브가 유효한 서명을 포함하지 않는다.**
   - 우리 파서는 `signature`·`key_id`가 마지막 두 파라미터로 반드시 존재할 것을 요구([GoogleAdSsvQueryParser.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt) `validateSignaturePosition`). 프로브에 서명이 없으면 user_id 값과 무관하게 **항상 400** → "숫자로도 실패" 증상과 일치.
2. **서명은 있으나 검증 실패 / 공개키 조회 문제.** 서명 불일치 → 400, gstatic 키 조회 실패 → 503([GoogleAdSsvSignatureVerifier.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt)). 받은 응답이 400이므로 키 조회보다는 서명/파라미터 단계 가능성.
3. (배제됨) user_id가 이메일이라 `toLongOrNull()` 실패 → 400. 숫자로 바꿔도 실패했으므로 이건 1차 원인 아님(단, 실제 운영에선 여전히 숫자 필수 — §8 참고).

## 6. 다음 진단 단계 (확정용)

**prod 백엔드 로그**에서 "URL 확인" 클릭 시점의 인바운드 요청을 확인한다. 무엇을 보는가:
- 요청이 **도달했는가** (도달 안 했으면 AdMob의 URL 형식/도메인 거부 → 콘솔 측 문제)
- 도달했다면 **메서드/쿼리스트링** (`signature`·`key_id` 포함 여부)
- 로그 태그: `GoogleAdSsvExceptionHandler`("Invalid Google Ad SSV callback: ...")의 사유 메시지 → 어느 검증에서 막혔는지 특정 가능.

```bash
# 예: 컨테이너 로그에서 SSV 관련만
docker logs <backend-container> 2>&1 | grep -iE "ssv|google ad"
```

## 7. 해결 옵션

- **옵션 A — 백엔드가 확인 프로브에 200 반환 (권장, 가설 1이 맞을 경우):**
  SSV 콜백은 "수신 확인은 200, 실제 적립은 내부 검증 후"가 표준 패턴이다. 엔드포인트가 **서명 없는/불완전한 확인 프로브에는 200을 반환**하고, 유효 서명이 있는 진짜 콜백만 검증·적립하도록 보완. (보안 무영향 — 적립은 서명 통과 시에만.) → 콘솔 "URL 확인" 통과.
- **옵션 B — 콘솔에서 확인 없이 저장 가능한지 재시도.** (가능하면 §6 로그로 실제 콜백만 검증)

> 결정 전 §6 로그로 프로브 내용을 먼저 확정할 것. 옵션 A 구현 시 `GoogleAdSsvController`/`GoogleAdSsvExceptionHandler`에서 "프로브 식별 → 200" 분기 추가.

---

## 8. 남은 작업 체크리스트

### (1) AdMob 콘솔
- [ ] 리워드 광고 단위 생성 (iOS / Android 각각)
- [ ] 각 리워드 단위 → 서버 사이드 인증(SSV) → 콜백 URL 등록:
      `https://cashchat.duckdns.org/api/ads/google/ssv`
- [ ] "URL 확인" 통과 (위 §5~§7 해결 후)

### (2) GitHub Secrets 등록  — **이번 이슈 핵심 액션**
저장소 → Settings → Secrets and variables → Actions:
- [ ] **`IOS_ADMOB_REWARDED_AD_UNIT_ID`** = iOS 리워드 단위 ID (`ca-app-pub-…/…`)
- [ ] **`ADMOB_REWARDED_AD_UNIT_ID`** = Android 리워드 단위 ID (`ca-app-pub-…/…`)

> ⚠️ iOS는 `IOS_` 접두, Android는 접두 없음. 플랫폼 값 섞이면 광고 미노출/정책 위반.
> 미등록 시 자동으로 Google 테스트 리워드 ID로 폴백([AppConfig](../../apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt) / [AppConfig.swift](../../apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift)).

### (3) (선택) 백엔드 ad_unit 검증 고정
- [ ] `app.ads.google.rewarded-ad-unit-id` = 리워드 단위 숫자 ID 설정 시, 해당 단위 콜백만 수락([GoogleAdSsvProperties.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/properties/GoogleAdSsvProperties.kt)). 비워두면 모든 단위 수락.

### (4) 클라이언트 — userId 숫자 주입 (운영 필수)
백엔드는 `user_id`를 **내부 유저 ID(Long)** 로 파싱한다. **이메일/문자열 금지.**
- [ ] iOS: `options.userIdentifier = String(userId)` (ServerSideVerificationOptions)
- [ ] Android: `ServerSideVerificationOptions.Builder().setUserId(userId.toString())`

이메일을 넣으면 운영에서도 모든 콜백이 400으로 거부되어 적립되지 않는다.

---

## 9. 참고

| 항목 | 위치 |
|---|---|
| SSV 컨트롤러 | [GoogleAdSsvController.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/controller/GoogleAdSsvController.kt) |
| 쿼리 파서(서명 위치 검증) | [GoogleAdSsvQueryParser.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvQueryParser.kt) |
| 서명 검증 | [GoogleAdSsvSignatureVerifier.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvSignatureVerifier.kt) |
| 검증/적립 서비스 | [GoogleAdSsvService.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/service/GoogleAdSsvService.kt) |
| 예외→HTTP 매핑(400/503) | [GoogleAdSsvExceptionHandler.kt](../../apps/backend/src/main/kotlin/com/wnl/cashchat/api/domain/ad/web/exception/GoogleAdSsvExceptionHandler.kt) |
| 이벤트 테이블 | `apps/backend/src/main/resources/db/migration/V4__google_ad_ssv_events.sql` |
