# CC-311 — 육성형 AI 챗봇 프론트 연동 가이드

> 대상: Android / iOS(KMM) 프론트 개발자
> 백엔드 경제 모델(CC-311)을 화면·API 흐름으로 연동하기 위한 문서.
> 밸런싱 수치(밥 최대치·진화 비용·광고 한도 등)는 **서버 설정값**이며 운영 중 변동될 수 있다.
> 클라이언트는 값을 하드코딩하지 말고 **항상 API 응답을 신뢰**한다.

## 0. 공통 사항

- **Base URL**: 환경별 호스트 + 아래 경로
- **인증**: 모든 사용자 API는 `Authorization: Bearer <accessToken>` 필요. 미인증 → `401`
- **에러 포맷** (공통):
  ```json
  { "code": "INSUFFICIENT_ENERGY", "message": "..." }
  ```
  화면 분기는 HTTP status 가 아니라 가급적 `code` 값으로 한다.
- **두 가지 지갑(분리)**: 채팅은 **밥(에너지)** 만 소비한다. **포인트(코인=돈)** 는 채팅에 쓰이지 않고 **진화·상점** 에만 쓰인다.

| 자원 | 의미 | 채팅 | 진화 | 광고 보상 | 조회 API |
|------|------|------|------|-----------|----------|
| 밥(energy) | 채팅 연료 | **−1/메시지** | (성공 시 보너스 충전) | +충전 | `GET /api/energy/me` |
| 포인트(point) | 환금성 코인(돈) | 안 씀 | **−시도비용** | +적립 | `GET /api/users/me` 등 |

---

## 1. 채팅 API · 화면 · 밥값 차감 로직

### 1-1. 화면 흐름

```
┌─ Chat 화면 (밥 있음)
│  [헤더]   🐣 미래 Lv.3      🪙 1,250 (코인=돈, 쌓임)      ⚡ 38 / 50 (밥)
│  ─────────────────────────────────────────────────────────
│  🐣  안녕! 뭐 도와줄까?
│  🙂  자취방 청소기 추천해줘
│  🐣  가성비 3가지 추천드려요...        ◀ 이 답변 1회 = 밥 −1 (코인은 그대로)
│  ─────────────────────────────────────────────────────────
│  [ 메시지 입력... ]                                  [ 전송 ]
└─
```

- 헤더에 **코인(🪙)** 과 **밥 게이지(⚡)** 를 분리 노출한다.
- 채팅 1회 전송 → **밥 −1**. 코인은 변하지 않는다.
- 밥이 0이면 전송 시 게이트(→ §4)로 전환.

#### 구현해야 할 화면/상태

| 화면/상태 | 필수 표시 | 필수 동작 |
|-----------|-----------|-----------|
| 채팅 첫 진입 | 캐릭터 레벨, 포인트, 밥 게이지, 입력창, 추천 질문 | `GET /api/evolution/me`, `GET /api/users/me`, `GET /api/energy/me` 로 헤더 상태를 구성한다. |
| 대화방 없음 | 빈 대화 상태, 입력창 | 첫 전송 전에 `POST /api/v1/chat/conversations` 로 대화방을 만든 뒤 `POST /api/v1/chat/stream` 을 호출한다. |
| 대화방 있음 | 기존 메시지 목록, 최신 헤더 상태 | `GET /api/v1/chat/conversations`, `GET /api/v1/chat/conversations/{conversationId}/messages` 로 목록/메시지를 복원한다. |
| 스트리밍 중 | 사용자 말풍선, assistant 말풍선 로딩/누적 텍스트, 전송 버튼 비활성화 | SSE `message` 이벤트의 `data` 를 같은 assistant 말풍선에 이어붙인다. |
| 스트리밍 실패 | 실패 말풍선 또는 재시도 액션 | `error` 이벤트 또는 네트워크 실패 시 같은 `conversationId` 로 재시도할 수 있게 한다. |
| 밥 부족 | 밥 0 게이지, 밥 충전 게이트, 광고 잔여 횟수 | `409 INSUFFICIENT_ENERGY` 수신 시 사용자 메시지를 확정 저장하지 말고 pending 상태로 보관한 뒤 §4 흐름을 탄다. |
| 광고 한도 도달 | 남은 횟수 0, 리셋 시각 | 광고 버튼을 비활성화하고 `resetAtKst` 를 서버 응답 그대로 표시한다. |
| 진화 가능/최고 레벨 | 현재 레벨, 비용, 확률 또는 최고 레벨 상태 | `GET /api/evolution/me` 응답으로 버튼 표시 여부를 결정한다. |

### 1-2. API

| 목적 | 메서드 · 경로 |
|------|---------------|
| 대화방 생성 | `POST /api/v1/chat/conversations` |
| 대화방 목록 | `GET /api/v1/chat/conversations` |
| 대화 메시지 조회 | `GET /api/v1/chat/conversations/{conversationId}/messages` |
| 히스토리(UUID) | `GET /api/v1/chat/history/{uuid}` |
| **응답 스트리밍** | `POST /api/v1/chat/stream` (SSE) |
| 밥 잔량 조회 | `GET /api/energy/me` |

**대화방 생성** — `POST /api/v1/chat/conversations`
```json
// Request (body 생략 가능)
{ "title": "영어 공부 팁" }
// Response 200
{ "conversationId": 7, "title": "영어 공부 팁",
  "createdAt": "...", "updatedAt": "..." }
```

**응답 스트리밍** — `POST /api/v1/chat/stream`
`Accept: text/event-stream`
```json
// Request
{ "conversationId": 7, "message": "안녕" }
```
응답은 **SSE**. 토큰이 `event: message` 로 흘러오고, 실패 시 `event: error`:
```
event: message
data: 안녕

event: message
data: 하세요

event: error
data: stream failed
```
> SSE 한 청크 = 부분 텍스트. 클라이언트는 누적해서 말풍선에 이어붙인다.

#### 채팅 전송 순서

```
1. 입력값 trim, 빈 문자열이면 중단
2. conversationId 가 없으면 POST /api/v1/chat/conversations
3. 사용자 메시지는 UI에 optimistic pending 으로 표시
4. POST /api/v1/chat/stream
5. 200 SSE → pending 사용자 메시지를 확정하고 assistant 말풍선에 청크 누적
6. 409 INSUFFICIENT_ENERGY → pending 사용자 메시지를 확정하지 않고 밥 충전 게이트 표시
7. 광고 충전 성공 후 GET /api/energy/me 로 밥 ≥ 1 확인
8. 직전에 막혔던 메시지를 같은 conversationId 로 재전송
```

> 서버는 스트림 시작 전에 밥을 검사/차감한다. `409 INSUFFICIENT_ENERGY` 는 사용자 메시지도 LLM 응답도 저장되지 않은 상태로 취급한다.

**밥 잔량** — `GET /api/energy/me`
```json
{ "energy": 38, "maxEnergy": 50 }
```

### 1-3. 밥값(에너지) 차감 로직 — 서버 내부

`POST /stream` 한 번이 서버에서 처리되는 순서(한 트랜잭션, 원자적):

```
1. 밥 게이트:   남은 밥 < 1 ?  → 409 INSUFFICIENT_ENERGY (LLM 호출 안 함)
2. 밥 −1                       ◀ 유일한 소비 (포인트는 안 건드림)
3. (서버 내부) 품질 재원 적립
4. 레벨에 따라 응답 모델 자동 배정   (레벨 높을수록 더 좋은 모델 확률↑ — 클라이언트 무관)
5. LLM 스트림 시작
```

- **포인트(코인)는 절대 차감되지 않는다.** 포인트가 0이어도 밥만 있으면 채팅 가능.
- 4단계의 모델 배정은 서버가 전부 처리하며 응답 본문(SSE 텍스트)만 받으면 된다.

### 1-4. 밥 부족 처리 (중요)

| 상황 | 응답 |
|------|------|
| 밥 부족 | **`409` `{ "code": "INSUFFICIENT_ENERGY" }`** |
| 대화방 없음/타인 소유 | `404` `CONVERSATION_NOT_FOUND` |
| UUID 히스토리 타인 소유 | `403` `CONVERSATION_ACCESS_DENIED` |
| 본문 invalid | `400` |
| 미인증 | `401` |

`409 INSUFFICIENT_ENERGY` 수신 → LLM 호출은 일어나지 않았으므로 **메시지를 보내지 말고** §4 "밥 충전" 게이트를 띄운다.

---

## 2. 광고 — 노출 횟수 · 로직

광고는 **밥/코인을 충전하는 주 수단**이다. 리워드 광고(AdMob) + 서버 검증(SSV)으로 보상한다.

### 2-1. 노출 횟수(쿼터) — `GET /api/ads/reward/quota`
```json
{
  "usedToday": 3,        // 오늘 시청·적립 횟수
  "dailyLimit": 10,      // 하루 한도 (서버 설정값)
  "remaining": 7,        // 남은 횟수
  "resetAtKst": "..."    // KST 기준 리셋 시각(다음날 자정)
}
```
- `remaining == 0` 이면 "오늘 광고 한도 도달" 안내 후 광고 버튼 비활성화.
- 한도/리셋 시각은 **서버 응답을 그대로 사용**한다(하드코딩 금지).

### 2-2. 광고 보상 로직(흐름)

```
[클라이언트]                         [서버]
1. nonce 발급      ──POST /api/ads/reward/issue-nonce──▶  { nonce, expiresAt }
2. 리워드 광고 표시 (AdMob, SSV custom data = nonce)
3. 시청 완료
4. (AdMob → 서버) ─────GET /api/ads/google/ssv─────▶  서명검증 + 보상 적립(밥+코인)
5. 적립 확인       ──GET /api/energy/me, /quota────▶  게이지·쿼터 갱신
```

- **`POST /api/ads/reward/issue-nonce`** → `{ "nonce": "...", "expiresAt": "..." }`
  광고를 띄우기 직전 발급받아 AdMob SSV custom data 로 넘긴다(만료 전 사용).
- **`GET /api/ads/google/ssv`** 는 **AdMob 서버가 호출**하는 콜백(클라이언트 직접 호출 X). 서버가 서명을 검증하고 보상을 멱등하게 적립한다.
- 적립은 비동기(콜백 시점)일 수 있으므로, 광고 종료 후 클라이언트는 `GET /api/energy/me` 와 `/quota` 를 **재조회**해 화면을 갱신한다.
- 보상 적립은 SSV `transactionId` 기준 **멱등** — 콜백이 재전송돼도 중복 적립되지 않는다.
- 보상 구성(밥·코인 수량)은 서버 설정값이며 운영 중 조정될 수 있다.
- 광고 종료 직후 보상이 아직 반영되지 않을 수 있다. 클라이언트는 짧은 간격으로 `GET /api/energy/me` 를 재조회하고, 일정 횟수 후에도 변동이 없으면 "보상 확인 중" 또는 재시도 안내를 표시한다.

---

## 3. 포인트로 진화시키는 로직

진화는 **포인트(코인)를 써서** 캐릭터 레벨을 올리는 확률 시도다. (밥과 무관)

### 3-1. 상태 조회 — `GET /api/evolution/me`
```json
{
  "level": 3,
  "isMaxLevel": false,
  "nextAttemptCost": 3000,   // 다음 진화 1회 비용(포인트). 서버 설정값
  "nextSuccessRate": 0.25    // 성공 확률(0~1)
}
```
- `isMaxLevel == true` 이면 `nextAttemptCost`/`nextSuccessRate` 는 `null` → "최고 레벨" 표시, 시도 버튼 숨김.

### 3-2. 진화 시도 — `POST /api/evolution/attempt`
```json
// Request — idempotencyKey 는 시도마다 새 UUID
{ "idempotencyKey": "a1b2c3d4-...." }
// Response 200
{ "success": true, "fromLevel": 3, "resultLevel": 4, "cost": 3000 }
//  실패 예: { "success": false, "fromLevel": 3, "resultLevel": 3, "cost": 3000 }
```

### 3-3. 로직 (서버 내부, 한 트랜잭션)
```
1. user_evolution 행 잠금(비관락)
2. 같은 idempotencyKey 기존 시도 있으면 → 그 결과 그대로 반환 (이중 차감 방지)
3. 최고 레벨이면 → 409 ALREADY_MAX_LEVEL
4. 포인트 −nextAttemptCost   (부족하면 402 INSUFFICIENT_POINTS → 롤백, 아무 변화 없음)
5. 확률 판정 → 성공 시 레벨 +1 & 밥 보너스 충전
6. 시도 결과 원장 기록
```

### 3-4. 에러 처리

| 상황 | 응답 |
|------|------|
| 포인트 부족 | `402` `INSUFFICIENT_POINTS` → "포인트 부족" 안내(광고/상점 유도) |
| 이미 최고 레벨 | `409` `ALREADY_MAX_LEVEL` |
| 미인증 | `401` |

> **idempotencyKey 규칙**: 버튼 1탭 = UUID 1개. 네트워크 재시도 시 **같은 키**로 재요청하면 이중 차감 없이 동일 결과를 받는다. 새 시도는 반드시 새 키.
>
> 성공 시 `resultLevel` 이 오르고 밥도 보너스로 충전되므로, 응답 후 `GET /api/evolution/me` + `GET /api/energy/me` 를 갱신한다.

---

## 4. 밥값 충전 — 화면 · 로직

### 4-1. 화면 흐름 (밥 0 → 게이트)

```
┌─ Chat 화면 (밥 0 → 게이트)
│  [헤더]   🐣 미래 Lv.3      🪙 1,250 (코인 그대로)        ⚡ 0 / 50 (밥 없음!)
│  ─────────────────────────────────────────────────────────
│  🙂  하나 더 물어봐도 돼?
│
│   ┌─ 🍚 밥이 떨어졌어요!  채워서 계속 대화해요
│   │     ▶  광고 보고 밥 채우기          (리워드 광고)
│   │     🪙 포인트로 밥 충전 (예정)
│   └─    (서버: 409 INSUFFICIENT_ENERGY)
│  ─────────────────────────────────────────────────────────
└─
```
- "빚이 차서 막혔다"가 아니라 **"밥이 떨어졌으니 채우자"** 는 보상 프레이밍.

### 4-2. 충전 로직

현재 구현된 충전 경로는 **광고 시청**이다(§2 와 동일 흐름):

```
1. POST /stream  →  409 INSUFFICIENT_ENERGY  수신
2. "밥 충전" 게이트 표시 + GET /api/ads/reward/quota 로 남은 횟수 확인
3. remaining > 0 → 광고 시청 (issue-nonce → 광고 → SSV)
4. 광고 종료 → GET /api/energy/me 재조회 → 게이지 갱신
5. 밥 ≥ 1 확인 후, 직전에 막혔던 메시지를 재전송
```

- **광고 한도(remaining=0)** 면 충전 불가 → "내일 다시" 또는 (예정) 포인트 충전 안내.
- `GET /api/energy/me` 의 `maxEnergy` 가 상한이므로 그 이상은 충전되지 않는다.

> **포인트로 밥 충전**은 기획에는 있으나 현재 전용 엔드포인트는 미구현(예정). 지금은 광고가 주 충전 경로이며, 진화 성공 시에도 밥이 보너스로 충전된다.

---

## 5. 요약 — 자원이 움직이는 지점

| 행동 | 밥(에너지) | 포인트(코인) | 관련 API |
|------|-----------|-------------|----------|
| 채팅 1회 | **−1** | – | `POST /api/v1/chat/stream` |
| 광고 시청(검증 완료) | **+충전** | **+적립** | `issue-nonce` → SSV |
| 진화 시도 | (성공 시 +보너스) | **−시도비용** | `POST /api/evolution/attempt` |
| 가입 시 | +초기 지급 | +초기 지급 | (자동) |

**핵심 한 줄**: 채팅=밥, 진화=포인트, 광고=둘 다 충전. 밥 0이면 `409`로 막히고 광고로 채워 재시도한다.

---

## 6. 프론트 연동 체크리스트

- `ApiService` 또는 KMM shared API 레이어에 채팅, 에너지, 광고, 진화 엔드포인트를 모두 추가한다.
- SSE는 Retrofit 일반 `Response` 가 아니라 OkHttp/EventSource 등 스트림을 청크 단위로 받을 수 있는 방식으로 구현한다.
- 채팅 화면 헤더는 `points` 만 받지 말고 `energy/maxEnergy`, `level`, `isMaxLevel` 도 상태로 가진다.
- `409 INSUFFICIENT_ENERGY` 는 일반 오류 토스트가 아니라 밥 충전 게이트로 분기한다.
- 광고 완료 후에는 로컬에서 포인트/밥을 임의 증가시키지 말고 서버 재조회 결과로만 갱신한다.
- 진화 시도는 버튼 1탭마다 새 `idempotencyKey` 를 만들고, 같은 탭의 네트워크 재시도에는 같은 키를 재사용한다.
