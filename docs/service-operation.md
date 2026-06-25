# Reward & Monetization Policy (서비스 운영 정책)

> **목적** — Cash Chat 서비스의 보상·수익화 구조를 운영팀이 이해하고 정책을 결정할 수 있도록
> **(1) 현재 코드에 구현된 동작(as-is)** 과 **(2) 업계 관행 기반 권장 정책(to-be)** 을 함께 정리한다.
>
> **작성 기준** — 2026-06-23, `dev` 브랜치 기준 실제 코드 실측.
> 각 항목의 근거 파일·설정값을 명시했으며, 코드와 어긋나면 **코드가 정답**이다.
>
> **범위 주의** — 본 문서의 "수익 분배(§4)"는 **회사 수익 회계 분배(ledger)** 를 의미한다.
> **사용자 현금 환전/출금 기능은 현재 코드에 존재하지 않는다.** 포인트는 앱 내(상점·진화)에서만 소비된다.

---

## 1. 개요

### 1.1 두 개의 분리된 화폐 (가장 중요한 전제)

Cash Chat은 **성격이 완전히 다른 두 화폐**를 운용한다. 이를 혼동하면 정책 전체가 어긋난다.

| 화폐 | 코드명 | 성격 | 적립 | 소비 | 현금성 |
|---|---|---|---|---|---|
| **밥 (Energy)** | `UserEnergy` | 채팅 **연료** | 가입 보너스·정산·진화 보정 | 채팅 1회당 1 | ❌ 없음 |
| **돈 (Point/Coin)** | `UserPoint` | **적립형 보상** 화폐 | 출석·광고·오퍼월·정산 | 상점·진화 | 앱 내 가치 |

- **밥(Energy)**: 채팅을 돌리기 위한 소모성 자원. 돈을 건드리지 않는다. 가입 시 50 지급, 최대 50.
  근거: `domain/energy/service/EnergyService.kt`, `EnergyProperties.kt` (`max-energy: 50`, `signup-bonus: 50`).
- **돈(Point)**: 적립·차감이 원장(`point_transaction`)에 기록되는 보상 화폐. 초기 잔액 1.
  근거: `domain/point/persistence/entity/UserPoint.kt`, `PointTransaction.kt`, `PointProperties.kt`.

> ⚠️ **초안과의 차이**: "채팅 이용"은 포인트를 **주는** 행위가 아니라 **밥을 쓰는** 행위다.
> 채팅으로 직접 적립되는 포인트는 없다(§2.3 참조).

### 1.2 서비스 수익 구조 (요약)

```
[리워드 광고 시청 / 오퍼월 참여]
        │  외부 광고사 수익(gross revenue)
        ▼
  ┌─────────────────────────────┐
  │  Ledger (회사 수익 분배 회계)   │  risk 15% → service 10% → 최소이익 가드
  └─────────────────────────────┘
        │  유저 보상분(cashable pt + energy)
        ▼
  [유저 포인트/밥 적립]  ──►  [상점 구매 / 진화(육성)]  ──►  소진
```

- 회사는 **리워드 광고(AdMob SSV)** 와 **오퍼월(TNK)** 로 수익을 얻는다.
- 수익의 일부를 사용자에게 포인트·밥으로 환원하고, 회계상 분배를 `ledger_entry`에 불변 기록한다.
- 사용자는 포인트를 **상점 아이템 구매**와 **진화(확률형 레벨업)** 에 소비한다.

### 1.3 포인트의 역할

- **유저 측**: 출석/광고/오퍼월로 모은 포인트 → 상점·진화에 소비하는 **인앱 보상 경제의 기축 통화**.
- **회사 측**: 모든 적립·차감이 `point_transaction` 원장에 `idempotencyKey`와 함께 기록되어
  **감사 가능(auditable)** 하고 **중복 적립이 구조적으로 차단**된다(§5.4).

---

## 2. 포인트 지급 정책

### 2.1 적립/차감 채널 전체 (코드 기준)

`PointTransactionReason` enum이 정의하는 6개 채널이 전부다.
근거: `domain/point/persistence/entity/PointTransactionReason.kt`

| 사유 | 방향 | 현행 값 | 근거 |
|---|---|---|---|
| `ATTENDANCE` 출석 | 적립 | 일차별 시드(20/50/100/300) | `AttendanceService.kt` |
| `AD_REWARD` 광고 | 적립 | **40코인 고정**, 일 10회 한도 | `AdRewardService.kt`, `AdRewardProperties.kt` |
| `OFFERWALL` 오퍼월 | 적립 | `pay_pnt × ratio`(현재 1.0) | `TnkOfferwallService.kt` |
| `LEDGER_REWARD` 정산 | 적립 | AD: cashable 4 + 밥 3 | `LedgerService.kt`, `application.yaml` |
| `EVOLUTION_ATTEMPT` 진화 | 차감 | 500/1200/3000/8000 | `EvolutionService.kt` |
| `SHOP_PURCHASE` 상점 | 차감 | 아이템 가격 | `ShopPurchaseService.kt` |

### 2.2 출석 체크 (Attendance)

**현행 (as-is)**
- 하루 1회 출석. 같은 날 중복은 `AlreadyCheckedInException`(409)으로 거절.
  유니크 제약 `uq_attendance_log_user_date` + 사전 `exists` 검사 이중 방어.
- **연속(streak)** 누적: 어제 출석했으면 streak+1, 끊기면 1로 리셋.
- 일차별 보상(시드, `V3__attendance.sql`):
  - 기본(`day_count=0`) **20코인**, 7일 **50**, 14일 **100**, 30일 **300** + 보너스 아이템.
  - 마일스톤(7/14/30)이 아닌 일차는 기본 20코인으로 폴백.
- 출석 로그 INSERT와 코인 적립이 **단일 트랜잭션** — "도장만 찍히고 코인 없음" 부분 성공 차단.
- 멱등키: `attendance:{userId}:{today}` → 동일 날짜 재호출 시 이중 적립 없음.
- 근거: `domain/attendance/service/AttendanceService.kt:32-76`

**업계 관행 (to-be)**
- **streak 보상 테이블 확장**: 현재 31일+ 구간이 Phase 1 임시(기본 보상 폴백)다.
  캐시워크·토스 출석류는 보통 7/14/21/30일 마일스톤 + 월간 리셋을 명확히 정의한다.
  31일 이후 정책(무한 누적 vs 30일 순환)을 확정 권장.
- **타임존 고정**: 출석은 `LocalDate`(서버 로컬) 기준이다. 광고·정산은 KST(`Asia/Seoul`) 고정인데
  출석만 서버 타임존을 따르면 자정 경계에서 불일치가 생길 수 있다. **전 채널 KST 통일** 권장.

### 2.3 미션 수행 / 채팅 이용

**현행 (as-is)**
- **별도의 "미션" 적립 채널은 코드에 없다.** `PointTransactionReason`에 미션 항목 없음.
- **채팅은 포인트를 적립하지 않는다.** 채팅은 밥(energy)을 1 소모하고, 그 마진을
  공용 품질 풀(quality pool)에 적립해 프리미엄 모델 재원으로 쓴다(§3.4).
  근거: `domain/chat/service/routing/ChatModelRouter.kt:43-84`

**업계 관행 (to-be)**
- 초안의 "미션 수행" 항목을 살리려면 신규 적립 채널(`MISSION` 등) 설계가 필요하다.
  업계 표준은 일일/주간 미션(예: "채팅 5회", "친구 초대") → 검증 가능한 이벤트 기반 적립이다.
- 친구 초대(invite) 기능은 프론트(`shared/.../invite/`)에 존재하나
  **백엔드 포인트 적립 연동 여부는 별도 확인 필요**(현재 `PointTransactionReason`에 초대 보상 없음).

### 2.4 광고 참여 (AD_REWARD)

**현행 (as-is)** — `domain/ad/service/AdRewardService.kt`
- 리워드 광고 1회 정상 시청 → **40코인 고정** 적립(`coin-amount: 40`).
- **일일 한도 10회**(`daily-limit: 10`), KST 자정 리셋. 한도 초과 시 `REJECTED_OVER_QUOTA`.
- **nonce 단일 사용**: 서버가 발급한 nonce(TTL 10분)를 광고 SDK의 `user_id`에 실어 보내고,
  콜백에서 1회만 소모. 재사용 시 `REJECTED_INVALID_NONCE`.
- 멱등키: `admob:reward:{transactionId}`.

### 2.5 오퍼월 참여 (OFFERWALL)

**현행 (as-is)** — `domain/offerwall/service/TnkOfferwallService.kt`
- TNK 서버 포스트백 → `pay_pnt × point_to_coin_ratio`(현재 **1.0**)를 코인으로 환산 적립.
- 환산은 `BigDecimal` + `RoundingMode.FLOOR`(정밀도 손실/음수 wrap 방지).
- `pay_pnt <= 0` 은 기록만 하고 미적립(`REJECTED_NON_POSITIVE`).
- 멱등키: `tnk:offerwall:{platform}:{seqId}`.

**업계 관행 (to-be, 2.4·2.5 공통)**
- **환산비(ratio)·광고 단가는 운영 변수**다. 현재 AdMob 40코인 고정 + 오퍼월 ratio 1.0은
  광고 eCPM 대비 보상 비율이 정책으로 명문화돼 있지 않다. **"보상률 = 유저 환원 ÷ 광고 수익"**
  목표치(업계 통상 50~70%)를 정하고 그에 맞춰 `coin-amount`/`ratio`를 조정 권장.
- **AdMob과 Ledger 이중 적립 확인 필요** → §3.1, §6.2에서 상술.

---

## 3. 수익화 구조

### 3.1 Google AdMob (리워드 광고 SSV)

**현행 (as-is)** — `domain/ad/web/controller/GoogleAdSsvController.kt`

AdMob **SSV(Server-Side Verification)** 콜백 `GET /api/ads/google/ssv` 1건이
**두 개의 적립 경로를 연속 호출**한다:

```
GET /api/ads/google/ssv
  │
  ├─ 1) googleAdSsvService.verifyAndStore()
  │       서명 검증 → 이벤트 저장 → LedgerService.recordRevenue()
  │       → LEDGER_REWARD: cashable 4pt + 밥 3 적립 + 회사 수익 분배 기록
  │
  └─ 2) adRewardService.grantFromCallback()
          nonce 소모 → 일 10회 한도 → AD_REWARD: 40코인 적립
```

➡️ **리워드 광고 1회 = 유저에게 40 + 4 = 44코인 + 밥 3 적립** (+ 회사 수익 분배 ledger 기록).

> ⚠️ **운영 확인 필요 (중요)**: 이 이중 적립이 **의도된 설계**인지, 아니면
> 구(舊) AD_REWARD 경로와 신(新) Ledger 경로가 전환기에 **둘 다 켜져 있는 것**인지 확인이 필요하다.
> `RevenueSource` enum 주석에 "현재 AD만 지원, 오퍼월 등 추가 예정(CC-339)"이라 적혀 있어
> Ledger가 통합 회계로 **이행 중**임을 시사한다. §6.2 참조.

- 서명 검증: Google 공개키(`verifier-keys.json`, 24h 캐시)로 SSV 서명 검증.
- 광고 단위 검증: `rewarded-ad-unit-ids`(`APP_ADS_GOOGLE_REWARDED_AD_UNIT_IDS`) 설정 시 일치 확인.
- 실패해도 SSV 엔드포인트는 2xx 반환 → Google 재시도 폭주(retry storm) 방지.
- 설정: `infra`/환경변수 `APP_ADS_GOOGLE_*`, 상세는 `docs/admob-production-setup.md`.

### 3.2 TNK Offerwall

**현행 (as-is)** — `domain/offerwall/web/controller/OfferwallController.kt`
- `POST /api/offerwall/tnk/user-token`: 유저별 안정적 불투명 토큰 발급(`setUserName`용, get-or-create).
- `POST /api/offerwall/tnk/callback/{platform}`: TNK 서버 포스트백 수신(`platform` = android/ios).
- 플랫폼별 앱키로 `md_chk` 체크섬 검증(§5.4). 성공 시 본문 `SUCCESS` 응답.
- **오퍼월은 Ledger를 거치지 않고 `OFFERWALL` 채널로 직접 적립**한다(AdMob과 비대칭).

### 3.3 기타 광고 네트워크

**현행 (as-is)**
- 코드에 연동된 광고/오퍼월은 **AdMob(리워드 + 배너/전면)과 TNK** 둘뿐이다.
  - 배너/전면/네이티브 광고 단위는 CI 시크릿에 분리 주입(최근 커밋 `862fb23`).
  - 배너 슬롯: `shared/.../ads/BannerAdSlot.kt`, 기능 플래그 `FeatureFlags.kt`.

**업계 관행 (to-be)**
- 다중 오퍼월/미디에이션(예: AdPopcorn, IronSource, 추가 오퍼월사) 도입 시
  `RevenueSource`·`OfferwallPlatform` enum 확장과 **소스별 보상 설정의 통일된 회계 경로(Ledger)** 로
  수렴시키는 것이 중복·정산 오류를 줄인다(현재 AdMob만 Ledger, 오퍼월은 직접 적립인 비대칭 해소).

### 3.4 채팅 경제 루프 (참고 — 직접 수익은 아님, CC-340)

**현행 (as-is)** — `ChatModelRouter.kt`, `QualityPoolService.kt`
- 채팅 1회: **밥 1 차감 → 공용 품질 풀에 마진 32 centi-pt 적립 → 모델 티어 결정**.
- 모델 티어: `NANO`(기본/무료) / `MINI` / `GPT`. 진화 레벨이 높을수록 상위 티어 확률↑.
- 프리미엄 티어(MINI/GPT)는 **공용 풀 잔액 + 유저별 일 50회 캡**으로 게이팅, 부족 시 NANO로 강등.
- 이 구조는 "유저 보상"이 아니라 **LLM 원가 통제 장치**다(프리미엄 응답 재원을 풀로 관리).

---

## 4. 수익 분배 정책 (회사 수익 회계 — Ledger)

> **본 장은 회사의 수익 분배 회계를 다룬다. 사용자 현금 출금은 §4.4 참조(현재 미구현).**

### 4.1 광고 수익 집계 방식

**현행 (as-is)** — `domain/ledger/service/LedgerService.kt`
- 외부 수익 이벤트(현재 `RevenueSource.AD`)가 들어오면 `recordRevenue()`가 단일 트랜잭션에서 분배.
- `gross_revenue` = AdMob SSV 콜백의 `reward_amount`.
- 모든 분배 결과를 `ledger_entry`에 **불변(immutable) 감사 기록**으로 저장.
  멱등키 유니크 제약 `uq_ledger_entry_user_key`로 같은 이벤트 중복 분배 차단.

### 4.2 분배 공식 (포인트 기반 분배 기준)

`LedgerService.recordRevenue` 단일 공식 (설정: `app.ledger`):

```
1. risk reserve   = floor(gross × riskRate)           # riskRate = 0.15
2. net            = gross − risk
3. service reserve= floor(net × serviceRate)          # serviceRate = 0.10
4. companyProfit  = net − service − cashablePt − energy
5. 이익 가드(I3)  : companyProfit ≥ max(minProfitFloor, floor(net × minProfitRate))
                    # minProfitFloor = 2, minProfitRate = 0.20
                    # 미달 시 IllegalArgumentException → 적립 거부(보상 과다 방지)
6. 유저 적립      : cashablePt(LEDGER_REWARD) → energy
```

- AD 소스 유저 보상: `cashable-pt: 4`, `energy: 3` (`application.yaml`).
- **이익 가드**가 핵심: 광고 수익이 너무 작거나 보상 설정이 과하면 **적립 자체를 거부**해
  회사 손실을 구조적으로 막는다(profit guard, `docs/planning/06-ai-chat-profit-guard.md` 참고).

### 4.3 정산 주기 / 지급 제외 조건

**현행 (as-is)**
- **정산 주기 개념 없음(실시간 분배)**: 광고 콜백 수신 즉시 동기 분배·적립. 배치 정산 없음.
- 지급 제외(거부) 조건:
  - 광고: nonce 무효/만료, 일 10회 한도 초과, 서명 검증 실패, 광고단위 불일치.
  - 오퍼월: 서명(md_chk) 실패, 미존재 토큰, `pay_pnt ≤ 0`, 중복 seqId.
  - 정산: 이익 가드 미달(보상 과다), 수익원 보상 설정 누락.

**업계 관행 (to-be)**
- 실시간 분배는 단순하지만 **광고사 사후 정산 조정(클로백/무효 트래픽 차감)** 을 반영하기 어렵다.
  AdMob/오퍼월은 무효 클릭·취소를 사후 차감하는데, 현재는 이미 적립된 포인트를 되돌리는 장치가 없다.
  → **보류(holdback)·정산 마감(예: D+1~D+7) 후 확정** 모델 도입 검토 권장.
- `risk reserve`(15%)는 이 클로백/어뷰징 손실 흡수용 적립금으로 보이나,
  **실제 환수 프로세스와 연결돼 있지 않다**. 적립금 운용 정책 명문화 필요.

### 4.4 사용자 출금/환전 (현재 미구현 — 향후 정책 방향)

> **현재 코드에 사용자 현금 출금/환전 기능은 존재하지 않는다.**
> 포인트는 상점 구매·진화에만 소비되는 **인앱 화폐**이며, 외부 인출 경로가 없다.

향후 현금화 도입 시 업계 표준으로 정의해야 할 항목(체크리스트):
- **환전 비율**: 포인트 → 현금/기프티콘 환율(예: 10,000P = 10,000원).
- **최소 출금 금액** 및 출금 수수료.
- **본인 인증·계좌 검증**: 1인 1계정, 명의 일치(어뷰징·자금세탁 방지).
- **세무 처리**: 기타소득 원천징수(국내 리워드 앱은 연 누적 기준 신고 의무 발생 가능).
- **출금 보류 기간**: 적립 후 N일 보류(클로백·어뷰징 검증 윈도).
- **지급 제외**: 부정 적립 의심 계정 동결, 정책 위반 시 몰수 근거.

---

## 5. 외부 연동 정책

### 5.1 AdMob 연동 방식

**현행 (as-is)**
- 클라이언트가 리워드 광고 표시 전 `POST /api/ads/reward/issue-nonce`로 서버 nonce 발급.
- nonce를 AdMob SDK의 SSV `user_id` 파라미터에 실어 광고 노출.
- 광고 완료 → Google 서버가 `GET /api/ads/google/ssv?...` 콜백 → 서버가 서명 검증 후 적립.
- **클라이언트가 보낸 user_id를 직접 신뢰하지 않는다** — 서버 발급 nonce를 통해서만 유저 식별.

### 5.2 TNK Offerwall 연동 방식

**현행 (as-is)**
- 클라이언트가 `POST /api/offerwall/tnk/user-token`로 안정적 토큰 발급받아 TNK SDK `setUserName`에 설정.
- 유저가 오퍼월 미션 완료 → TNK 서버가 `POST /api/offerwall/tnk/callback/{platform}` 포스트백.
- 서버가 플랫폼 앱키로 `md_chk` 검증 후 토큰→userId 해석하여 적립.

### 5.3 콜백 / 포스트백 처리 (공통 패턴)

**현행 (as-is)** — AdMob·TNK 모두 동일 방어 패턴:
1. **서명 검증을 DB 쓰기보다 먼저** — 미검증 public 요청이 원장 행을 만드는 것을 차단(fail-closed).
2. **멱등 INSERT(PENDING/VERIFIED) → 행 락(SELECT FOR UPDATE) → 상태 전이** 원자 처리.
3. 종결 상태(GRANTED/REJECTED) 재수신은 **멱등하게 건너뜀** — 광고사 재전송에도 이중 적립 없음.
4. 처리 실패해도 **2xx 반환**으로 광고사 재시도 폭주 방지(에러는 로그로만).

### 5.4 중복 지급 방지

**현행 (as-is)** — 다층 방어:
- **포인트 원장 멱등키**: `point_transaction.idempotency_key` 유니크 제약이 **최종 방어선**.
  채널별 키 — 출석 `attendance:{u}:{date}`, 광고 `admob:reward:{txId}`,
  오퍼월 `tnk:offerwall:{platform}:{seqId}`, 정산 `ledger:{source}:{u}:{key}`.
- **비관적 락(`findForUpdate`)**: 동일 키/유저 동시 호출을 행 락으로 직렬화 → 두 번째 호출은
  첫 호출이 커밋한 원장을 보고 그대로 반환(이중 적립 없음).
- **nonce 단일 사용**(광고), **seqId/transactionId 유니크**(오퍼월/광고 이벤트 테이블).

### 5.5 부정 참여 방지

**현행 (as-is)**
- 서명 검증(AdMob SSV / TNK md_chk)으로 위조 콜백 차단.
- 일일 한도(광고 10회/일), 유저별 프리미엄 캡(채팅 50회/일).
- nonce TTL 10분 — 오래된/재사용 nonce 거부.

**업계 관행 (to-be)**
- **디바이스/IP 기반 어뷰징 탐지 없음** — 현재는 콜백 진위만 검증하고 유저 행동 패턴은 보지 않는다.
  리워드 앱 표준은 (1) 1기기 다계정 탐지, (2) 비정상 적립 속도 룰, (3) 에뮬레이터/루팅 탐지,
  (4) 광고사 IVT(무효 트래픽) 신호 연동을 둔다. 어뷰징 의심 계정 **자동 플래그 + 보류** 체계 권장.
- **출석 streak 어뷰징**: 서버 타임존 기반이라 클라이언트 시간 조작 영향은 적으나,
  자동화(봇) 출석에 대한 방어는 없다.

---

## 6. 운영 예외 처리 (Runbook)

### 6.1 광고 보상 미지급

**현상**: 유저가 광고를 봤는데 포인트가 안 들어옴.
**점검 순서**:
1. `google_ad_ssv_events` 테이블에서 `transaction_id`로 이벤트 조회 → `reward_status` 확인.
   - `VERIFIED`(적립 미결정): 적립 실패로 남은 상태 → AdMob 재전송 시 재적립 시도됨.
   - `REJECTED_OVER_QUOTA`: 일 10회 한도 초과(정상 거부).
   - `REJECTED_INVALID_NONCE`: nonce 만료/재사용(정상 거부 또는 nonce 발급 누락).
2. 콜백 자체가 없으면: 서명 검증 실패(4xx 로그) 또는 광고단위 불일치 확인.
3. `point_transaction`에서 `admob:reward:{txId}` 키 존재 여부로 적립 완료 판정.

### 6.2 중복 포인트 지급

**현상**: 한 번의 행동에 포인트가 두 번 들어옴.
**점검 순서**:
1. **AdMob 44코인(40+4) 이중 적립은 현재 설계상 정상**일 수 있다(§3.1).
   `AD_REWARD` 40 + `LEDGER_REWARD` 4가 함께 찍혔다면 이중 적립이 아니라 **두 경로 합산**이다.
   → **운영 정책 결정 필요**: 두 경로를 유지할지, Ledger로 단일화(AD_REWARD 비활성)할지 확정.
2. 같은 멱등키로 두 행이 있으면 그것은 진짜 버그 → 유니크 제약 위반 로그·DB 무결성 점검.
3. 그 외 채널은 멱등키 유니크 제약상 구조적으로 중복 불가(§5.4) — 발생 시 락/트랜잭션 경계 검토.

### 6.3 어뷰징 의심 사용자

**현행 대응 수단(코드 내)**:
- 일일 한도 초과는 자동 거부됨. 그 외 자동 동결/차단 장치는 **없음**.
**권장 운영 절차(to-be)**:
1. `point_transaction` 적립 패턴 조회(단시간 대량 적립, 동일 기기 다계정).
2. 의심 계정 수동 플래그 → 적립 보류 → 소명 절차.
3. 확정 시 포인트 몰수·계정 정지 근거 정책 필요(현재 몰수 기능 미구현 → 수동 DB 조정).

### 6.4 외부 광고사 장애

**현행 (as-is)**
- 광고사 콜백 실패 시 서버는 2xx 반환·로그만 남김 → 광고사 자체 재시도에 의존.
- `VERIFIED`로 남은 이벤트는 AdMob 재전송 시 재적립되므로 **일시 장애는 자동 복구** 경향.
- Google 공개키 일시 불가 시 SSV 503 응답.
**권장 (to-be)**
- 콜백 유실(광고사 재전송도 실패)에 대비한 **수동 재처리(reconciliation) 도구**와
  광고사 리포트 대조 배치(일/주 단위 적립 vs 광고사 정산 리포트) 도입 권장.

---

## 부록 A. 핵심 설정값 (application.yaml 기준)

| 영역 | 키 | 기본값 |
|---|---|---|
| 포인트 | `app.points.initial-balance` | 1 |
| 광고 보상 | `app.ads.reward.coin-amount` | 40 |
| 광고 보상 | `app.ads.reward.daily-limit` | 10 |
| 광고 보상 | `app.ads.reward.nonce-ttl` | 10m |
| 오퍼월 | `app.offerwall.tnk.point-to-coin-ratio` | 1.0 |
| 밥 | `app.energy.max-energy` / `signup-bonus` | 50 / 50 |
| 정산 | `app.ledger.risk-rate` / `service-rate` | 0.15 / 0.10 |
| 정산 | `app.ledger.min-profit-rate` / `min-profit-floor` | 0.20 / 2 |
| 정산 | `app.ledger.rewards[AD]` cashable / energy | 4 / 3 |
| 진화 | `app.evolution.rules` 비용/확률 | 500@0.7 / 1200@0.5 / 3000@0.25 / 8000@0.1 |
| 채팅 | `app.routing.meal-margin-centi-pt` | 32 |
| 채팅 | `app.quality.premium-daily-cap-per-user` | 50 |

## 부록 B. 주요 근거 파일

- 포인트 원장: `domain/point/service/UserPointService.kt`, `persistence/entity/PointTransaction.kt`
- 출석: `domain/attendance/service/AttendanceService.kt`, `db/migration/V3__attendance.sql`
- 광고(SSV): `domain/ad/web/controller/GoogleAdSsvController.kt`, `service/AdRewardService.kt`, `service/GoogleAdSsvService.kt`
- 오퍼월: `domain/offerwall/service/TnkOfferwallService.kt`, `web/controller/OfferwallController.kt`
- 정산(Ledger): `domain/ledger/service/LedgerService.kt`, `properties/LedgerProperties.kt`
- 채팅 경제: `domain/chat/service/routing/ChatModelRouter.kt`, `domain/quality/service/QualityPoolService.kt`
- 진화·상점·밥: `domain/evolution/service/EvolutionService.kt`, `domain/shop/service/`, `domain/energy/service/EnergyService.kt`

## 부록 C. 운영팀 결정 대기 항목 (Open Questions)

1. **AdMob 이중 적립(40 + 4)** — 의도된 합산인가, Ledger 단일화 대상인가? (§3.1, §6.2)
2. **오퍼월의 Ledger 미경유** — 회계 통일을 위해 오퍼월도 Ledger로 수렴할 것인가? (§3.3)
3. **출석 31일+ 보상 정책** 확정 (§2.2)
4. **타임존 통일**(출석 서버로컬 vs 광고/정산 KST) (§2.2)
5. **사용자 현금 출금/환전** 도입 여부 및 정책 (§4.4)
6. **어뷰징 탐지·보류·몰수** 체계 도입 여부 (§5.5, §6.3)
7. **risk reserve(15%) 적립금의 실제 환수·운용** 프로세스 (§4.3)
