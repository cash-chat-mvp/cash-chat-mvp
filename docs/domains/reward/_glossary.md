# reward 도메인 — 공유 용어 (Ubiquitous Language)

여러 스토리가 공유하는 용어·전제. 스토리 파일마다 복붙하지 않고 여기서 한 번만 정의한다.

| 용어 | 정의 |
| ---- | ---- |
| **코인** | 앱 내 재화. `domain/point`가 잔액·원장을 관리. 모든 적립은 `UserPointService.recordTransaction(idempotencyKey)`의 멱등 트랜잭션을 통해서만 이뤄진다. |
| **밥(에너지)** | `domain/energy`가 관리하는 별도 재화. 광고 시청·친구 초대 입력자 보상 등에서 지급. `EnergyService.charge`는 멱등 키가 없어 상위 UNIQUE 제약이 중복 지급을 막는다. |
| **멱등성 키(idempotencyKey)** | 동일 적립/차감이 재시도·중복 도착해도 1회만 반영되게 하는 키. 관례: `attendance:{userId}:{date}`, `admob:reward:{nonce}`, `tnk:offerwall:{seq_id}`, `referral:{inviteeUserId}`, `shop:purchase:{userId}:{idem}`. |
| **원장(ledger)** | 적립/거절 콜백을 결과 상태와 함께 기록하는 테이블(정산·디버깅·환수 대비). 상태값(예: `GRANTED`/`REJECTED_*`)은 운영 알람의 단일 source of truth. |
| **nonce** | 광고 시청 직전 서버가 발급하는 단일 사용·단기 TTL 토큰. AdMob `custom_data`에 실려 콜백에서 `nonce → userId`를 서버측에서 해석(클라이언트 식별값 미신뢰). |
| **불투명 토큰** | 오퍼월/외부 SDK에 넘기는 UUID. 내부 `userId`를 노출하지 않기 위한 매핑값. |
| **KST 리셋** | 모든 일자 판정·일일 한도 리셋은 `Asia/Seoul` 자정 기준. |
| **원자성** | 도장/적립, 차감/적재 등 관련 쓰기를 단일 `@Transactional`로 묶어 부분 성공을 배제. |
