# 혜택존(Offerwall) — TNK Factory 오퍼월 백엔드 기술 설계

> 상태: Draft
> 범위: 백엔드 (TNK 오퍼월 적립 채널)
> 관련 기획: [Confluence — 혜택존 TNK 오퍼월](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14909530), [Confluence — overview](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/14975052), `docs/features/reward/spec.md`(선행 Phase 1)
> 관련 SDK: [tnk_sdk_rwd_br (Android)](https://github.com/tnkfactory/tnk_sdk_rwd_br), [ios-sdk-rwd2 (iOS)](https://github.com/tnkfactory/ios-sdk-rwd2)
> Jira: CC-288

## 목표 (Goal)

혜택존에 **TNK Factory 오퍼월** 적립 채널을 백엔드로 추가한다. 사용자가 TNK 오퍼(앱 설치·가입·설문 등)를 완료하면 TNK 서버가 우리 백엔드 콜백(서버 포스트백/S2S)으로 적립을 통보하고, 백엔드는 다음을 수행한다.

1. **사용자 토큰 발급**: 프론트가 TNK SDK `setUserName(token)`에 넣을 불투명 토큰을 사용자당 1개 발급·해석한다 (내부 `userId` 비노출).
2. **포스트백 검증 적립**: TNK 콜백의 `md_chk` 해시를 검증하고, 토큰으로 `userId`를 해석한 뒤, 설정된 환산비로 `pay_pnt`를 코인으로 환산해 멱등 적립한다.
3. **콜백 원장 기록**: 수신한 모든 콜백을 결과 상태와 함께 `tnk_offerwall_callbacks`에 기록한다 (취소/환수 수동 정산 및 향후 자동화 대비).

적립은 기존 `domain/point/UserPointService.recordTransaction(idempotencyKey)`의 멱등성 트랜잭션을 통해 수행한다. 구조는 기존 `domain/ad`(Google AdMob SSV 리워드)의 콜백·멱등 적립 패턴을 준용한다.

## 핵심 설계 결정 (Decisions)

| # | 결정 | 내용 |
| - | ---- | ---- |
| D1 | 사용자 식별값 | TNK `setUserName`에는 **불투명 토큰(UUID)** 을 전달한다. 내부 `userId`를 직접 노출하지 않으며, `offerwall_user_tokens` 매핑 테이블로 `token → userId`를 해석한다. |
| D2 | 코인 환산 | `pay_pnt`(TNK 포인트) → 코인은 **설정 가능한 환산비** `app.offerwall.tnk.point-to-coin-ratio`(기본 `1.0`)를 곱해 산출한다. |
| D3 | 취소/환수 | **이번 범위 외**. 모든 콜백을 ledger에 기록하되 자동 차감은 하지 않는다. ledger `status`는 향후 `CANCELED` 등으로 확장 가능하게 설계한다. |
| D4 | 토큰 API | `POST /api/offerwall/tnk/user-token` — get-or-create(멱등). |
| D5 | ACK 형식 | TNK가 기대하는 정확한 ack 본문/HTTP 메서드가 SDK 문서에 미명시 → **합리적 기본값(HTTP 200 + `SUCCESS` 본문)으로 구현하고 ack 문자열을 상수로 분리**, 정확한 규격은 TNK 확인 후 조정(아래 "검증 필요 항목"). |

## 유저 스토리 · 인수 조건

> 이 기능의 **유저 스토리와 관찰 가능한 인수 조건(검증 기준선)** 은 도메인 카탈로그가 단일 소유한다(SSOT): [US-REWARD-003 TNK 오퍼월 적립](../../domains/reward/US-REWARD-003-tnk-offerwall.md).
> 본 문서는 그 계약을 만족시키는 **백엔드 구현 상세**(설계 결정·API·데이터 모델·트랜잭션 불변식·시퀀스)를 담는다.

## 구현 불변식 (Design Invariants)

관찰 가능한 AC는 위 US 파일이 소유하고, 아래는 그것을 보장하는 백엔드 구현 규칙이다(핵심 설계 결정은 위 "핵심 설계 결정" 표 참조).

- **토큰 발급**은 get-or-create(멱등). `(userId, token=UUID)` 사용자당 1행, `token` UNIQUE로 동시 최초 호출도 하나만 생성.
- **정상 적립**은 **단일 `@Transactional`** — `md_user_nm → userId` 해석 → `coinAmount = floor(pay_pnt × ratio)` → 멱등 적립(`tnk:offerwall:{seq_id}`) → ledger `GRANTED` INSERT → 성공 ack(HTTP 200 + `SUCCESS`).
- **멱등**: `seq_id` UNIQUE(1차) + 멱등키 `tnk:offerwall:{seq_id}`(이중 방어선). 중복/동시 `seq_id`는 1건만 적립, 나머지는 멱등 종료하되 재전송 중단을 위해 성공 ack.
- **서명 검증은 DB 쓰기보다 먼저**(fail-closed) — `md_chk` 불일치/`appKey` 미설정 시 원장 행을 만들지 않고 warn 로그만(public 엔드포인트 원장 오염 방지, AdMob SSV 패턴과 정합).
- **미지의 토큰**(서명 통과·토큰 미매핑)은 `REJECTED_UNKNOWN_USER`(user_id=null)로 기록, 미적립.
- `tnk_offerwall_callbacks.status`는 정산·환수·알람의 단일 source of truth. 취소/환수 자동 차감은 범위 외(D3).

## API 계약 (요약)

| Method | Path | 인증 | 설명 |
| ------ | ---- | ---- | ---- |
| `POST` | `/api/offerwall/tnk/user-token` | 사용자 | TNK `setUserName`용 불투명 토큰 get-or-create. 응답 `{ token }` |
| `POST` | `/api/offerwall/tnk/callback` | 없음(TNK 서버) | TNK 서버 포스트백 수신. 파라미터 `seq_id`, `pay_pnt`, `md_user_nm`, `md_chk`. 성공 시 `SUCCESS` ack |

> 콜백 파라미터·`md_chk` 산식(`MD5(appKey + md_user_nm + seq_id)`)·HTTP 메서드는 TNK Android/iOS SDK 가이드 문서 기준이며, 정확한 인코딩/연결 순서/ack 규격은 "검증 필요 항목" 참조.

## 사용자 흐름 (User Flow)

1. 사용자가 혜택존 탭의 TNK 오퍼월 영역에 진입한다.
2. 프론트가 `POST /api/offerwall/tnk/user-token`으로 불투명 토큰을 받아 TNK SDK `setUserName(token)`에 설정한다.
3. 사용자가 오퍼월에서 오퍼를 선택·완료한다(앱 설치·가입·설문 등).
4. TNK가 전환을 확인하면 백엔드 콜백 `POST /api/offerwall/tnk/callback`으로 포스트백을 전송한다.
5. 백엔드가 `md_chk`를 검증하고 토큰으로 `userId`를 해석한 뒤, 환산비로 코인을 멱등 적립하고 ledger에 기록한다.
6. 백엔드가 TNK에 성공 ack를 반환한다.

### 순차 흐름도 (Sequence Diagram)

```mermaid
sequenceDiagram
    participant User as 사용자
    participant FE as 혜택존 화면
    participant SDK as TNK SDK
    participant TNK as TNK 서버
    participant API as CashChat 백엔드
    participant DB as DB

    User->>FE: 오퍼월 진입
    FE->>API: POST /api/offerwall/tnk/user-token
    API->>DB: offerwall_user_tokens get-or-create
    API-->>FE: { token }
    FE->>SDK: setUserName(token) → 오퍼월 노출
    User->>SDK: 오퍼 완료(설치/가입/설문)
    SDK->>TNK: 전환 보고
    TNK->>API: POST /api/offerwall/tnk/callback?seq_id&pay_pnt&md_user_nm&md_chk
    API->>API: md_chk == MD5(appKey + md_user_nm + seq_id) 검증 (DB 쓰기 전)
    alt 서명 실패 (또는 appKey 미설정)
        API->>API: warn 로그만 (원장 행 미생성)
        API-->>TNK: ack(거절)
    else 서명 성공
        API->>DB: insertIfAbsent(PENDING) + seq_id 중복 체크
        alt 이미 처리됨
            API-->>TNK: SUCCESS (멱등)
        else 신규
            API->>DB: offerwall_user_tokens 조회 (token → userId)
            alt 미지 토큰
                API->>DB: tnk_offerwall_callbacks INSERT (REJECTED_UNKNOWN_USER)
                API-->>TNK: ack(거절)
            else 유효 토큰
                Note over API,DB: 단일 @Transactional
                API->>DB: BEGIN
                API->>API: coin = floor(pay_pnt × ratio)
                API->>API: recordTransaction(key="tnk:offerwall:{seq_id}")
                API->>DB: point_transaction INSERT
                API->>DB: tnk_offerwall_callbacks INSERT (GRANTED)
                API->>DB: COMMIT
                API-->>TNK: SUCCESS
            end
        end
    end
```

## 데이터 모델 (Flyway V11)

### `offerwall_user_tokens`

| 컬럼 | 타입 | 비고 |
| ---- | ---- | ---- |
| `user_id` | BIGINT | PK, 사용자당 1행 |
| `token` | VARCHAR | UNIQUE, UUID |
| `created_at` / `updated_at` | (BaseEntity) | |

### `tnk_offerwall_callbacks` (원장, 환수-대응 가능 설계)

| 컬럼 | 타입 | 비고 |
| ---- | ---- | ---- |
| `id` | BIGINT | PK |
| `seq_id` | VARCHAR | UNIQUE (중복 콜백 방어) |
| `md_user_nm` | VARCHAR | 콜백 원본 토큰 |
| `user_id` | BIGINT | 해석된 사용자(미지 시 null) |
| `pay_pnt` | BIGINT | TNK 원본 포인트 |
| `coin_amount` | BIGINT | 환산 적립 코인(거절 시 0) |
| `status` | ENUM | `GRANTED` / `REJECTED_BAD_SIGNATURE` / `REJECTED_UNKNOWN_USER` (확장: `CANCELED`) |
| `raw_query` | TEXT | 콜백 원본 쿼리/바디 |
| `created_at` / `updated_at` | (BaseEntity) | |

## 설정 (`app.offerwall.tnk.*`)

| 키 | 기본값 | 설명 |
| -- | ------ | ---- |
| `app-key` | (시크릿, env 주입) | `md_chk` 검증용 공유 시크릿 |
| `point-to-coin-ratio` | `1.0` | `pay_pnt → 코인` 환산비 |
| `ack.success-body` | `SUCCESS` | TNK 성공 ack 본문(상수 분리, 검증 후 조정) |

## 검증 항목 (TNK Android SDK 가이드로 확인 완료)

[tnk_sdk_rwd_br Android_Guide.md](https://github.com/tnkfactory/tnk_sdk_rwd_br)의 "자체 서버 포인트 관리" 콜백 규격으로 아래를 확인했다. **현재 구현이 모두 일치하여 코드 변경 불필요.**

- [x] **포스트백 HTTP 메서드** — "호출방식: HTTP POST". 구현 일치(`@PostMapping` + `@RequestParam`, query/form 모두 바인딩).
- [x] **`md_chk` 산식** — 원문 "`app_key + md_user_nm + seq_id`의 MD5 Hash", 예제 `DigestUtils.md5Hex(appKey + mdUserName + seqId)`. `TnkMdChecksumVerifier`의 `MD5(appKey + mdUserNm + seqId)` lowercase hex와 정확히 일치. **`pay_pnt`는 해시에 포함되지 않음(TNK 표준 설계)** — 따라서 적립액 무결성은 `app_key` 비밀성 + TNK S2S 채널에 의존한다(앱 결함 아님). app_key 노출 시 재발급 필요.
- [x] **성공 응답** — "HTTP 리턴코드 200이면 정상 처리로 판단"(본문 형식 미지정). 구현은 `200 OK` 반환으로 충족(`SUCCESS` 본문은 무해한 부가값).
- [x] **중복 처리** — "seq_id로 반드시 중복체크". `seq_id` UNIQUE + 멱등키 `tnk:offerwall:{seq_id}`로 충족.
- [x] **취소/환수 콜백** — Android 가이드에 적립 콜백만 존재, 차감/취소 콜백 **언급 없음**. 따라서 "기록만"(D3) 범위로 충분.
- [ ] **콜백 추가 파라미터** — TNK는 `app_id, pay_dt, app_nm, pay_amt, actn_id`도 전송. 현재는 무시(필요 4개만 사용). 감사 강화를 위해 `actn_id`(0 설치형/1 실행형/…)·`app_nm` 등을 ledger에 추가 기록하는 것은 후속 선택사항.
- [ ] dev/prod 콜백 URL 등록 및 `app-key`(=TNK 콘솔 APP KEY) 시크릿 주입 — 콜백 URL: `https://cashchat.duckdns.org/api/offerwall/tnk/callback`, TNK 콘솔 `매체관리 > 기본설정 > 무료충전소 > 포인트 관리(자체서버에서 관리) > URL`에 등록.

## 범위 외 (Out Of Scope)

- 자동 취소/환수(claw-back) 처리 — 콜백 기록만, 자동 차감은 후속 (D3)
- 프론트엔드(KMM `shared/`, Android/iOS TNK SDK) 통합 — 별도 작업
- 추가 오퍼월(Buzzvil, AdiSON 등) 통합
- 오퍼월 일일 한도/빈도 제어(오퍼월은 리워드 광고와 달리 캡 미적용)
- 적립 코인의 동적 환산비 관리 UI(설정값 기반, 관리자 UI 없음)
- 오퍼월 노출 목록 조회/큐레이션(클라이언트 SDK가 직접 TNK에서 로드)
