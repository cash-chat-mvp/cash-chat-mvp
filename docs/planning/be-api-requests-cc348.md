# BE API 요청 — 육성형 AI 챗봇 FE 확장 (CC-348)

> 작성: 2026-06-11 · FE에서 UI를 선구현해 두었으며, 아래 API가 준비되면 FeatureFlag 활성 + 연결만 하면 됩니다.
> 공통: 인증 `Authorization: Bearer`, 에러 포맷 `{ "code", "message" }` (기존 규약 동일).
> 우선순위: P1(코어 경제 완결) > P2(채팅 UX) > P3(부가).

## P1-1. 포인트 잔액 조회

현재 잔액을 노출하는 API가 없습니다 (`UserResponse`에 points 없음, `PurchaseResponse.coinBalance`는 구매 시에만). HUD 코인 칩·진화 화면·상점이 모두 필요로 합니다.

```
GET /api/points/me
200: { "balance": 1250 }
```

- `UserResponse`에 필드를 추가하는 방안도 무방하나, 채팅 중 잦은 재조회가 있어 가벼운 전용 엔드포인트를 선호합니다.

## P1-2. 포인트로 밥 충전

연동 가이드 §4에 "예정"으로 명시된 기능. 게이트 바텀시트에 버튼 UI가 준비돼 있습니다.

```
POST /api/energy/topup
Request:  { "idempotencyKey": "<uuid>" }
200: { "energy": 25, "maxEnergy": 50, "costPoints": 500, "pointBalance": 750 }
402: INSUFFICIENT_POINTS
409: ENERGY_ALREADY_FULL (제안)
```

- 1회 충전량·비용은 서버 설정값으로(클라이언트는 응답만 신뢰). 충전량/비용을 사전 표시할 수 있도록 `GET /api/energy/me` 확장(P2-1)에 `topupCostPoints`, `topupAmount` 포함 요청.

## P1-3. 에너지 시간 자동회복

다마고치식 리텐션 장치. FE는 카운트다운 UI를 준비해 두었습니다.

```
GET /api/energy/me  (기존 응답 확장)
200: {
  "energy": 38, "maxEnergy": 50,
  "nextRecoverAt": "2026-06-11T12:00:00Z",   // null이면 만땅/비활성
  "recoverAmount": 5,
  "topupCostPoints": 500,                      // P1-2 사전 표시용
  "topupAmount": 25
}
```

- 회복은 서버 계산(레이지 정산)이면 충분 — FE는 `nextRecoverAt` 도달 시 재조회.

## P2-1. 대화방 삭제 · 이름 변경

```
DELETE /api/v1/chat/conversations/{conversationId}   → 204
PATCH  /api/v1/chat/conversations/{conversationId}   → 200 ConversationResponse
Request: { "title": "새 제목" }
404: CONVERSATION_NOT_FOUND (타인 소유 포함, 기존 규약 동일)
```

## P2-2. 쿠팡 큐레이션 상품 카드 (SSE 확장)

상세기획안 §3. FE는 `ChatItem.ProductCard` 렌더링이 준비돼 있습니다. 텍스트 스트림 종료 후 별도 이벤트로:

```
event: product
data: {"products":[{"title":"삼성 갤럭시 버즈3 Pro","price":149000,"rating":4.7,"reviewCount":32000,"imageUrl":"https://...","trackingUrl":"https://link.coupang.com/..."}]}
```

- 카드 0~3개. 파트너스 고지 문구는 FE가 항상 붙입니다.
- 메시지 조회(`GET .../messages`) 응답에도 동일 페이로드가 복원되도록 메시지 메타 저장 필요.

## P2-3. Progressive Ad Gate (SSE 확장)

상세기획안 §2.2. FE는 teaser+blur+CTA UI가 준비돼 있습니다. 게이트 판단은 전적으로 서버 책임(누적 비용·최소 메시지 간격).

```
event: gate
data: {"teaserChars":80,"rewardCoin":30}
```

- `gate` 이벤트 수신 시 FE는 해당 응답을 blur 처리하고 리워드 광고(기존 nonce/SSV 경로)로 해제.
- 해제 확인 방식 제안: 광고 적립 후 메시지 재조회 시 `status`가 `UNLOCKED`로 변경.

## P3-1. 진화 시도 기록 조회

원장(`evolution attempt ledger`)이 이미 쌓이고 있으므로 노출만 요청합니다.

```
GET /api/evolution/attempts?limit=20
200: { "attempts": [ { "success": false, "fromLevel": 3, "resultLevel": 3, "cost": 3000, "attemptedAt": "2026-06-10T09:00:00Z" } ] }
```

## P3-2. 캐릭터 이름

FE는 로컬(DataStore) 저장으로 우선 동작. 멀티디바이스 동기화를 위해:

```
PATCH /api/users/me/character-name
Request: { "name": "미래" }            // 1~10자
200: { "name": "미래" }
GET /api/users/me 응답에 characterName 포함
```

## P3-3. 대화 공개 공유 링크

`GET /api/v1/chat/history/{uuid}`는 본인만 접근 가능(403)하므로 공유 용도로는 부족합니다.

```
POST   /api/v1/chat/conversations/{id}/share    → 200 { "shareUrl": "https://.../share/<token>" }
DELETE /api/v1/chat/conversations/{id}/share    → 204 (링크 비활성화)
GET    /share/{token}                            → 비인증 조회(읽기 전용 스냅샷)
```

---

| # | 기능 | FE 상태 | 제안 엔드포인트 |
| --- | --- | --- | --- |
| P1-1 | 포인트 잔액 | 코인 칩 구현·숨김 | `GET /api/points/me` |
| P1-2 | 포인트 밥 충전 | 게이트 버튼 구현·비활성 | `POST /api/energy/topup` |
| P1-3 | 에너지 자동회복 | 카운트다운 구현·숨김 | `GET /api/energy/me` 확장 |
| P2-1 | 대화방 삭제·이름변경 | 메뉴 구현·비활성 | `DELETE/PATCH .../conversations/{id}` |
| P2-2 | 쿠팡 상품 카드 | 카드 렌더링 구현 | SSE `event: product` |
| P2-3 | Ad Gate | blur UI 구현 | SSE `event: gate` |
| P3-1 | 진화 시도 기록 | 타임라인 구현·숨김 | `GET /api/evolution/attempts` |
| P3-2 | 캐릭터 이름 | 로컬 저장 동작 | `PATCH /api/users/me/character-name` |
| P3-3 | 공개 공유 링크 | 내보내기는 FE 단독 동작 | `POST .../share` |
