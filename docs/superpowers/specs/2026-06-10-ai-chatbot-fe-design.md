# 육성형 AI 챗봇 FE 설계 (CC-348)

- 작성일: 2026-06-10
- 기준 문서: [CC-311 프론트 연동 가이드](https://moneyfactoryslave.atlassian.net/wiki/spaces/FCTC/pages/19169388) (2026-06-07, source of truth)
- 참고: 기획 초안(14975038) · 상세기획안(14942281) · `docs/design-preview/index.html`

## 0. 범위 결정

| 결정 | 내용 |
| --- | --- |
| 기능 기준 | **BE 연동가이드(구현된 API)** 기준. 상세기획안의 EXP·진화석·Ad Gate(blur)·쿠팡 카드는 백엔드 미구현 → 이번 범위에서 제외하되 **확장 포인트로 자리만 설계** |
| 플랫폼 | Android UI 먼저, 데이터 레이어·도메인 로직은 KMM shared(commonMain)에 구현 (iOS 재사용 전제) |
| 대화방 UX | 다중 대화방 + 목록 화면 |
| 채팅 톱바 | 슬림 톱바 (아바타+레벨 / 코인 칩 / 밥 게이지 칩) |
| 밥 충전 게이트 | 바텀시트 |
| 진화 화면 | 풀스크린 스테이지 |
| 진화 연출 | 차지 & 플래시 (포켓몬형) |
| 데이터 레이어 | Ktor 기반 shared 레이어 (기존 auth Retrofit과 공존) |

### 경제 모델 요약 (BE 가이드)

- **밥(energy)**: 채팅 연료. 메시지 1회 = 밥 −1. 0이면 `409 INSUFFICIENT_ENERGY`.
- **포인트(코인)**: 진화·상점 전용. 채팅에 쓰이지 않음.
- **광고(AdMob 리워드 + SSV)**: 밥·코인 충전의 주 수단. 일일 쿼터 존재.
- **진화**: 포인트를 소모하는 확률 시도. 성공 시 레벨 +1 & 밥 보너스.
- 모든 수치(최대치·비용·확률·한도)는 서버 응답을 신뢰, 하드코딩 금지.

## 1. 화면 구성

모두 기존 Chat 탭 안에서 동작한다.

### 1.1 채팅 화면

- **슬림 톱바**: 좌측 대화방 목록 아이콘 → 아바타(레벨 뱃지)+이름 → 우측 코인 칩, 밥 게이지 칩.
  - 밥 칩은 잔량 비율에 따라 색 전환(20% 이하 경고색), 차감 시 카운트 애니메이션.
  - 아바타/레벨 탭 → 진화 스테이지 진입.
- 진입 시 `GET /api/evolution/me` + `GET /api/users/me` + `GET /api/energy/me` 병렬 호출로 톱바 구성.
- **메시지 리스트**: 사용자 버블(Primary `#5C6BFA`), AI 버블(Surface). 등장 시 slideIn+fadeIn spring.
  - 스트리밍: 점 3개 타이핑 인디케이터 → assistant 버블에 토큰 누적 + 깜빡이는 커서.
  - pending 사용자 메시지: 반투명 + 시계 아이콘.
- **빈 대화방**: 캐릭터 인사 + 추천 질문 칩 3~4개(탭 시 즉시 전송).
- 전송 버튼은 스트리밍 중 비활성.
- **메시지 모델은 sealed 타입**(Text / ProductCard / AdGate …)으로 설계 — 쿠팡 카드·Ad Gate 서버 구현 시 타입 추가만으로 수용 (확장 포인트).

### 1.2 대화방 목록 화면

- `GET /api/v1/chat/conversations` 리스트: 제목 + 상대시간. 상단 "새 대화" 버튼.
- 선택 시 `GET /api/v1/chat/conversations/{id}/messages`로 복원.
- 삭제는 BE 미지원으로 제외. 빈 상태: 캐릭터 일러스트 + 안내.

### 1.3 밥 충전 게이트 바텀시트

- 트리거: 전송 시 `409 INSUFFICIENT_ENERGY`.
- `ModalBottomSheet`: 🍚 "밥이 떨어졌어요!" + 쿼터(`GET /api/ads/reward/quota`의 remaining·`resetAtKst` 그대로 표시).
- 주 CTA "광고 보고 밥 채우기" → §3.2 광고 플로우 → 게이지 차오름 애니메이션 → 시트 자동 닫힘 → pending 메시지 자동 재전송.
- `remaining == 0`: CTA 비활성 + "내일 다시" 안내 + "포인트로 충전(준비 중)" 비활성 버튼(확장 포인트).

### 1.4 진화 스테이지 (풀스크린)

- 세로(portrait) 고정. 풀스크린 = 화면 회전이 아니라 채팅에서 전환되는 전용 화면(1뎁스, ✕로 복귀).
- 레이아웃: 닫기 ✕ / 코인 칩 / 캐릭터(120dp+, radial 글로우 배경) / 5단계 스텝 인디케이터 / 비용·확률 카드(`nextAttemptCost`, `nextSuccessRate`) / CTA.
- `isMaxLevel == true`: CTA 숨김, "최고 레벨" 뱃지.
- **차지 & 플래시 연출** (~2.5초, 결과는 API 응답을 미리 받고 연출 끝에 공개):
  1. CTA 탭 → 버튼 잠금 + `POST /api/evolution/attempt`
  2. 차지: scale 0.92 (0.8s)
  3. 글로우·진동 고조 (1.2s, 햅틱 점증)
  4. 성공: 화이트 플래시(120ms) → 새 레벨 캐릭터 + 파티클 + `HapticFeedback.LONG_PRESS` + 결과 카드("Lv.N 달성! ⚡밥 +N 보너스")
  5. 실패: 글로우 소멸 + 쉐이크 4회 + "아깝다! 다시 도전?" 카드
- 연출 중 API 에러(402 등) → 연출 중단 + 정상 에러 처리.
- 스킵: 2회차 시도부터 화면 탭 시 즉시 결과 공개.
- 종료 후 `evolution/me` + `energy/me` + `users/me` 재조회.

### 1.5 디자인 원칙

- 기존 디자인시스템 준수: Primary `#5C6BFA`, 다크 `#1C1C1E`/`#2C2C2E`. 라이트/다크 모두 지원.
- Material3 + spring 기반 모션. 화려한 연출은 진화 스테이지에 집중.

## 2. 아키텍처 — shared 데이터 레이어 (Ktor)

```
shared/commonMain/.../shared/
  core/network/   HttpClient 팩토리, TokenProvider, ApiException(code 기반)
  chat/           ChatApi(SSE), ChatRepository, ChatStore(메시지 상태머신)
  energy/         EnergyApi, (HudStore에 통합 조회)
  evolution/      EvolutionApi, EvolutionStore
  ads/            AdsApi(quota·issue-nonce), AdRewardStore(적립 폴링)
  wallet/         UserApi(GET /api/users/me 포인트)
  hud/            HudStore — level·isMaxLevel·points·energy/maxEnergy 통합 상태

app(Android)/.../feature/chat/
  ChatScreen, ChatViewModel          shared Store 구독 + UI 전용 상태
  ConversationListScreen
  EnergyGateBottomSheet
  evolution/EvolutionScreen          연출 포함
  components/                        말풍선·칩·게이지 등
```

### 2.1 네트워크

- Ktor `HttpClient` + kotlinx.serialization. 엔진: Android OkHttp / iOS Darwin.
- `TokenProvider` 인터페이스(`accessToken()`, `refresh()`)를 shared가 정의, Android에서 기존 토큰 저장소로 구현. 401 시 1회 refresh 후 재시도. 기존 auth Retrofit은 변경하지 않고 공존.
- 에러: 본문 `{ code, message }` 파싱 → `ApiException(code, message, httpStatus)`. 화면 분기는 code 기준.
- iOS에서 호출될 shared suspend 함수에는 `@Throws` 필수.

### 2.2 SSE

- `POST /api/v1/chat/stream`: `preparePost` + `bodyAsChannel` 라인 파싱(`event:`/`data:`) → `Flow<ChatStreamEvent>` (`Token` / `Error` / `Done`).
- 스트림 시작 전 HTTP 에러(409 등)는 Flow 이전에 `ApiException`으로 즉시 전파.

### 2.3 API 면적

| 모듈 | 엔드포인트 |
| --- | --- |
| chat | `POST/GET /api/v1/chat/conversations`, `GET .../{id}/messages`, `GET /api/v1/chat/history/{uuid}`, `POST /api/v1/chat/stream` |
| energy | `GET /api/energy/me` |
| evolution | `GET /api/evolution/me`, `POST /api/evolution/attempt` |
| ads | `GET /api/ads/reward/quota`, `POST /api/ads/reward/issue-nonce` |
| wallet | `GET /api/users/me` |

### 2.4 Store

- **ChatStore**: 메시지 상태머신 — `Sending(pending)` → `Confirmed` / `Blocked(에너지 부족)`, assistant 누적, 재전송 큐. 기존 mock 전면 교체(Android `ChatViewModel`의 mock 로직 제거 포함).
- **HudStore**: 톱바 상태 통합. `refresh()` 한 번으로 3개 API 병렬 재조회.
- **EvolutionStore**: 상태 조회 + 시도(idempotencyKey 관리).
- **AdRewardStore**: 쿼터 + nonce 발급 + 적립 폴링(2초 간격 최대 5회 재조회, 미변동 시 "보상 확인 중").

## 3. 핵심 플로우

### 3.1 메시지 전송

1. trim, 빈 문자열 중단 → `conversationId` 없으면 `POST /conversations` 선행
2. 사용자 메시지 optimistic pending → `POST /stream`
3. SSE 시작 → pending 확정, 토큰 누적 → `Done` 후 `energy/me` 재조회
4. `409 INSUFFICIENT_ENERGY` → pending 유지 + 게이트 시트 → 충전 후 `energy ≥ 1` 확인 → 같은 `conversationId`로 자동 재전송
5. 스트리밍 중 단절 → 부분 텍스트 유지 + "응답이 끊겼어요" 재시도 버튼

### 3.2 광고 보상

```
quota 확인 → issue-nonce → AdMob 리워드 표시(SSV customData = nonce)
→ 광고 닫힘 → energy/me·users/me·quota 폴링(2초×5회) → 게이지 갱신
```

- 적립은 서버 재조회 결과로만 반영(로컬 가산 금지). SSV 콜백은 서버 간 통신으로 클라이언트는 관여하지 않음.
- 5회 폴링 후 미변동 → "보상 확인 중" + 수동 새로고침.
- 광고 로드 실패/중도 이탈 → 토스트 + 쿼터 재조회.
- 기존 `RewardedAdManager` 재사용, `setServerSideVerificationOptions(customData = nonce)` 추가.

### 3.3 진화 시도

- 버튼 1탭 = 새 UUID(idempotencyKey). 같은 탭의 네트워크 재시도는 같은 키 재사용(멱등).
- 응답 후 `evolution/me` + `energy/me` + `users/me` 재조회.

## 4. 에러 매트릭스

| code (HTTP) | 처리 |
| --- | --- |
| `INSUFFICIENT_ENERGY` (409) | 게이트 바텀시트, 메시지 pending 유지 |
| `INSUFFICIENT_POINTS` (402) | "포인트 부족" 다이얼로그 + 광고 유도 |
| `ALREADY_MAX_LEVEL` (409) | 상태 재조회 후 최고 레벨 UI 전환 |
| `CONVERSATION_NOT_FOUND` (404) | 새 대화방 자동 생성 후 1회 재시도 |
| 401 (refresh 실패) | 기존 로그아웃 플로우 |
| 그 외 | 공통 에러 토스트 + 재시도 |

## 5. 테스트 전략

- **shared (Ktor MockEngine)**: SSE 라인 파서(청크 분할·error 이벤트·중도 단절), ChatStore 상태머신(pending→확정/blocked→재전송), 에러 코드 매핑.
- **Android**: 게이트 분기·진화 연출 상태를 ViewModel 레벨에서 테스트. 연출 자체는 수동 확인.

## 6. 범위 외 (확장 포인트만 마련)

- 쿠팡 파트너스 상품 카드 (메시지 sealed 타입으로 수용 가능)
- Progressive Ad Gate (응답 blur) — 백엔드 미구현
- 포인트로 밥 충전 — BE 엔드포인트 예정, 게이트 시트에 비활성 버튼만
- EXP·진화석·부적/보호권 아이템 — 현 백엔드 경제 모델에 없음
- iOS UI (shared 레이어는 이번에 준비됨)
