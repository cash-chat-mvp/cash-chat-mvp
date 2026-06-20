# CC-311 — 육성형 AI 챗봇 경제·성장 시스템 (BE) 설계

| 항목 | 내용 |
| --- | --- |
| Jira | CC-311 [BE] 육성형 AI 챗봇 API 개발 |
| 브랜치 | `feature/cc-311-growth-chatbot-api` (base: `dev`) |
| 기준일 | 2026-06-21 |
| 정본 스펙(SSOT) | Confluence — [AI Energy](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/23068784) · [AI API](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/23068807) |

> ⚠️ **수치 비공개 원칙**: 내부 원가·마진 수치(nano 원가, 공용 풀 마진, Energy backing 등)는 이 문서·소스에 하드코딩하지 않는다. 실제 값은 Confluence 정본 및 비공개 설정(환경변수/비공개 config)에만 둔다. 소스에는 `@ConfigurationProperties` 키와 비민감 기본값만 둔다.

---

## 1. 목표와 제약

기획 개정으로 경제 모델이 **"광고로 Energy 확보 → 채팅 완료 시 현금성 포인트·진화 경험치 획득"** 으로 바뀌었다. 이를 백엔드에 반영한다.

**확정 제약 (사용자 결정):**

1. **기존 코드 재사용·리팩터** — 신규 도메인을 별도로 짓지 않고 기존 `point`/`ad`/`attendance`/`chat` 위에서 리팩터한다.
2. **범위 = P0 전체 (S1~S5)** — P1(거래내역·pending→confirmed 배치·cashout eligibility), P2(대사·운영 대시보드)는 별도 티켓으로 연기.
3. **광고 SSV 검증 로직 불변** — `GoogleAdSsv*`(서명 검증·콜백·nonce·일일 한도) 파이프라인은 손대지 않는다. **보상 지급 결과만 포인트 → Energy 로 교체**한다.
4. **로그인/인증 불변** — `domain/auth/`·JWT 필터·`SecurityConfig` 인증 로직은 손대지 않는다. 모든 재화는 `userId` 귀속. 비로그인(`X-App-Id`)·계정 이전(명세 1.2)은 범위 밖.
5. **cashout 출금·voucher 교환은 범위 밖** (03-shop 별도). 단 `confirmedCashablePt` 분리는 S1에서 미리 도입.

---

## 2. 기존 코드 ↔ 신규 모델 매핑

| 기존 | 신규 모델에서의 처리 |
| --- | --- |
| `UserPoint` (단일 `balance`) | 의미 충돌(pending/confirmed·energy·evolution 미수용) → **신규 `user_wallet` 테이블로 대체**. `UserPoint`는 더 이상 적립 대상이 아님. |
| `PointTransaction` (원장, `idempotencyKey` unique) | **재사용·확장** → 통합 `WalletLedger` 원장. `txType` 추가로 Energy/Point/EXP 거래를 한 원장에 기록. 멱등 패턴(락-먼저 → 키 조회 → 가감 → INSERT)을 그대로 따른다. |
| `AdRewardService.grantFromCallback` | **보상 지급 한 줄만 교체**: `userPointService.recordTransaction(...point...)` → `energyService.grant(...Energy...)`. SSV 검증·nonce·일일 한도 로직은 불변. |
| `attendance/` | 출석 보상도 포인트 → **Energy** 로 교체. 출석은 명세대로 "출석용 광고 세션 SSV 성공 시 지급"이 이상적이나, 본 범위에선 기존 출석 지급 지점의 **재화만 Energy 로 교체**(흐름 구조 변경은 최소화). |
| `ChatService.stream` | `hasEnoughBalance` 게이트(차감 없음) → **Energy 예약→생성→영속→원자적 정산(Energy 차감 + pendingPt+1 + exp+1 + pool 적립) + `reward_settled` SSE + 실패 시 환불** 로 리팩터. |

---

## 3. 슬라이스 (빌드 순서 = 의존성, 각 슬라이스 = TDD + `/code-review`)

### S1 — Wallet / Energy core
신규 데이터 모델과 조회 API의 토대.

**엔티티**
```
user_wallet  (userId unique)
  energyAvailable, energyReserved
  pendingCashablePt, confirmedCashablePt
  evolutionLevel, evolutionExp, evolutionFailStack

energy_grant  (FIFO 만료 추적)
  grantId, userId, sourceType, grantedAmount, remainingAmount,
  grantedAt, expiresAt

shared_quality_pool  (전역 1행 — 사용자별 아님)
  balance

wallet_ledger  (= PointTransaction 확장)
  userId, txType, delta, balanceAfter, reason,
  referenceId, idempotencyKey(unique)
```

**서비스/규칙**
- `WalletService`: 지갑 보장 생성, 비관적 락 기반 가감.
- `EnergyService.grant()`: `energy_grant` 발행 + `energyAvailable += amount` + 원장(ENERGY_GRANTED). `MAX_ENERGY` 상한 적용(초과분 정책: 거부 또는 클램프 — 기본 거부).
- `EnergyService.reserve()/consume()/refund()`: 예약은 `energyAvailable -= 1, energyReserved += 1`(원자 조건부 `UPDATE ... WHERE energyAvailable >= 1`). 만료가 빠른 grant부터 차감(FIFO).
- 만료 처리는 조회 시점 계산 + (배치는 P1로 연기, 단 조회에서 만료분 제외).

**API**
- `GET /api/v1/economy/me` — 통합 스냅샷(energy/point/evolution/ad/features). 채팅·Energy·진화 화면 공통.
- `GET /api/v1/economy/policy` — 표시용 정책(서버 정책값).
- `GET /api/v1/wallet` — 지갑 요약.

**불변식:** I1(채팅 ⇒ available≥1), I5(cashablePt로 Energy 구매 불가), I9(pool ≥ 0), I12(잠재부채 추적용 합계).

### S2 — Ad → Energy (보상 재화 교체만)
- `AdRewardService.grantFromCallback`의 적립부를 `EnergyService.grant(sourceType=REWARDED_AD)`로 교체.
- 출석 지급부를 `EnergyService.grant(sourceType=ATTENDANCE_AD)`로 교체.
- **SSV 검증·nonce·일일 한도·서명 로직은 변경 없음** (제약 3).
- 멱등 키는 기존 `admob:reward:{transactionId}` 패턴 유지(원장 unique).

### S3 — Chat reward settlement
`ChatService.stream` 리팩터. 상태 흐름:
```
REQUESTED → ENERGY_RESERVED → GENERATING → RESPONSE_PERSISTED → REWARD_SETTLED
실패(모델 호출 전): ENERGY_RELEASED (보상 없음)
실패(모델 호출 후, 답변 미생성): Energy 환불 + operationalLoss 기록 + 보상 없음
```
- 진입 시 Energy 1개 **원자적 예약**(없으면 `ENERGY_INSUFFICIENT`/422).
- 스트림 종료(`doFinally`)에서 정산: 답변 정상 저장 시 **단일 트랜잭션**으로 `energyReserved -= 1`(최종 차감) + `pendingCashablePt += 1` + `evolutionExp += 1` + `sharedQualityPool += margin` + 원장 기록 + `messageId` 멱등.
- SSE 이벤트 추가: `meta` / `delta`(기존 message 청크) / `reward_settled`(정산 완료 후에만) / `done`.
- 멱등: `UNIQUE(userId, messageId, rewardType)` — 동일 messageId 재요청/재시도/재생성은 보상 없음(I3, I11).
- `GET /api/v1/messages/{messageId}/settlement` — 정산 상태 복구 조회.
- 보상 제외 규칙(명세 6.3)·메시지 길이/의미 무관(I10) 반영.

> 엔드포인트 형태: 명세는 `POST /chats/{chatId}/messages`(messageId + Idempotency-Key)를 제안. 기존은 `POST /api/v1/chat/stream`(conversationId Long). **기존 엔드포인트를 유지하되 요청에 `messageId`(Idempotency-Key) 필드를 추가**하는 방향으로 최소 변경(프론트 영향 최소화). 신규 경로 추가 여부는 plan 단계에서 확정.

### S4 — Evolution
- 신규 도메인. `GET /api/v1/evolution/me`, `POST /api/v1/evolution/attempts`(Idempotency-Key=evolutionAttemptId), `GET /api/v1/evolution/attempts/{id}`.
- **사용자당 단일 진화 라인**(레벨/EXP/failStack은 `user_wallet`에 보유). AI 캐릭터별 진화는 범위 밖.
- 진화 판정은 **서버 보안 난수**로 1회 결정·저장(재요청 시 저장 결과 반환, I14). EXP 차감·레벨·failStack 변경은 단일 트랜잭션.
- failStack에 따른 성공률 상승, `expectedLevel` 불일치 시 `EVOLUTION_LEVEL_MISMATCH`/422.
- `evolution_attempt` 기록: levelBefore, requiredExp, baseSuccessRate, failStack, finalSuccessRate, result, policyVersion, createdAt.
- 현재 진화는 **evolutionExp만 사용**(cashablePt 미사용, I6).

### S5 — Shared quality pool & routing
- S3 정산에서 `sharedQualityPool += margin` 적립(전역 풀).
- 모델 라우팅 게이트: `sharedQualityPool ≥ premiumDelta` 일 때만 상위 모델 사용, 부족 시 nano 강등(I8). 풀은 음수 불가(I9).
- 비용 상한(MAX_INPUT/OUTPUT/CONTEXT_TOKENS 등) 및 throttle 훅 — 기존 `LlmProvider` 라우팅에 결합.
- 긴급 중지 토글(`PREMIUM_ROUTING_ENABLED` 등)은 `@ConfigurationProperties`로.

---

## 4. 횡단 관심사

- **멱등성**: 잔액 변경 요청은 모두 고유 키. 기존 원장 unique 패턴 재사용. 키: `messageId`, `adSessionId`, `attendanceId`, `evolutionAttemptId`.
- **동시성**: 다기기 동시 채팅에서 Energy 중복 사용 금지 → 조건부 `UPDATE ... WHERE energyAvailable >= 1`, 영향 행 1일 때만 진행. 사용자별 동시 보상형 채팅 1개 제한.
- **긴급 중지 토글**: `REWARD_CHAT_ENABLED`, `REWARDED_AD_ENABLED`, `ATTENDANCE_REWARD_ENABLED`, `EVOLUTION_ENABLED`, `PREMIUM_ROUTING_ENABLED` → `features`로 `economy/me` 노출, 비활성 시 `FEATURE_DISABLED`/503.
- **오류 코드**: 명세 9 표 채택(`ENERGY_INSUFFICIENT` 422, `REWARD_ALREADY_SETTLED` 409, `EVOLUTION_*` 422 등). 기존 `ErrorResponse`/도메인 ExceptionHandler 패턴 사용.
- **설정**: dev=H2, prod=MySQL. 신규 테이블은 두 환경 모두 생성. 파라미터는 `@ConfigurationProperties`(기존 `PointProperties`/`AdRewardProperties` 패턴), 원가 수치는 비공개.

## 5. 테스트 전략

- Kotest + TestContainers(MySQL) 조합(기존 관례). 슬라이스마다 TDD로 먼저 실패 테스트 → 구현.
- 집중 검증: 멱등(중복 messageId/콜백), 동시성(동시 Energy 예약·차감 경쟁), 정산 원자성(중간 실패 롤백), 진화 RNG 1회 결정·재요청 동일 결과, FIFO 만료 차감, premium 게이팅 경계(pool 부족 시 강등).
- 각 슬라이스 완료 시 빌드/테스트 통과 → `/code-review`(로컬 diff) → 다음 슬라이스.

## 6. 완료 후

- CC-311 전체 완료 시 Confluence 「작업로그 > 백엔드」(page 14974978) 밑에 `[DOCS] CC-311 …` 페이지 작성.

## 7. 명시적 비범위 (이번 브랜치 제외)

- 비로그인(`X-App-Id`)·계정 이전, cashout 출금/voucher, P1(거래내역 API·pending→confirmed 확정 배치·cashout eligibility), P2(광고 수익 대사·잠재부채 대시보드·공용 풀 대사·위험계정 운영·Remote Config 감사), AI 캐릭터별 진화.
