# reward 도메인 — 공유 용어 (Ubiquitous Language)

여러 스토리가 공유하는 용어·전제. 스토리 파일마다 복붙하지 않고 여기서 한 번만 정의한다.

| 용어 | 정의 |
| ---- | ---- |
| **코인** | 앱 내 재화. `domain/point`가 잔액·원장을 관리. 모든 적립은 `UserPointService.recordTransaction(idempotencyKey)`의 멱등 트랜잭션을 통해서만 이뤄진다. |
| **밥(에너지)** | `domain/energy`가 관리하는 채팅 연료(별도 재화). 룰렛·광고 시청·친구 초대 입력자 보상 등에서 지급되며 `EnergyService.charge`는 최대 밥 상한을 넘기지 않는다. 멱등 키가 없어 **상위 UNIQUE 제약·단일 사용 토큰**이 중복 지급을 막는다 — 친구 초대는 `invite_redemptions.invitee_user_id` UNIQUE, 광고 시청은 단일 사용 nonce + 멱등 키 `admob:reward:{nonce}`. |
| **멱등성 키(idempotencyKey)** | 동일 적립/차감이 재시도·중복 도착해도 1회만 반영되게 하는 키. 관례: `attendance:{userId}:{date}`, `admob:reward:{nonce}`, `tnk:offerwall:{seq_id}`, `referral:{inviteeUserId}`, `shop:purchase:{userId}:{idem}`. |
| **원장(ledger)** | 적립/거절 콜백을 결과 상태와 함께 기록하는 테이블(정산·디버깅·환수 대비). 상태값(예: `GRANTED`/`REJECTED_*`)은 운영 알람의 단일 source of truth. |
| **nonce** | 광고 시청 직전 서버가 발급하는 단일 사용·단기 TTL 토큰. AdMob `custom_data`에 실려 콜백에서 서버가 목적(코인/밥 적립 또는 룰렛 광고 스핀)과 소유자(`userId`)를 판별한다(클라이언트 식별값 미신뢰). |
| **불투명 토큰** | 오퍼월/외부 SDK에 넘기는 UUID. 내부 `userId`를 노출하지 않기 위한 매핑값. |
| **KST 리셋** | 모든 일자 판정·일일 한도 리셋은 `Asia/Seoul` 자정 기준. |
| **원자성** | 도장/적립, 차감/적재 등 관련 쓰기를 단일 `@Transactional`로 묶어 부분 성공을 배제. |
| **행운 룰렛 스핀** | 서버가 확률표로 상품을 결정하고 필요한 경우 밥을 지급하는 룰렛 1회 실행. 클라이언트는 결과 표시와 애니메이션만 담당한다. |
| **무료 스핀** | KST 당일 첫 1회 룰렛 스핀. 무료 스핀이 남아 있으면 광고 스핀용 nonce를 발급하지 않는다. |
| **광고 스핀** | AdMob SSV로 검증된 룰렛 광고 nonce를 소비해 실행하는 룰렛 스핀. 광고 자체는 밥을 직접 지급하지 않는다. |
| **확률표** | 룰렛 상품별 가중치 정책. 초기 구현은 백엔드 설정값으로 관리하며 클라이언트가 확률을 계산하지 않는다. |
| **명목 보상(prizeEnergy)** | 룰렛 상품에 표시된 밥 수량. 예: `JACKPOT_100`은 명목 보상 100. |
| **실제 지급량(awardedEnergy)** | 최대 밥 상한을 적용한 뒤 실제로 증가한 밥 수량. |
