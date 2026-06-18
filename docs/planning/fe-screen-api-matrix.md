# FE(Android + iOS) 화면별 필요 API 매트릭스

- 작성일: 2026-06-18
- 브랜치: `feature/CC-349`
- 목적: 프론트 각 화면이 쓰는 API와 백엔드 구현 상태를 정리해 **미구현/이슈를 백엔드와 공유**한다.
- 베이스 URL: `https://cashchat.duckdns.org`

> **중요:** 데이터 레이어(`apps/frontend/shared`, KMM)는 **Android·iOS 공통**이다. 따라서 아래 API는
> 대부분 **두 플랫폼이 동일한 클라이언트로 동일하게 호출**한다. Android는 이미 대부분 연동·검증 완료,
> iOS는 동일 shared 레이어 위에서 화면을 붙이는 중(파리티 작업). 플랫폼이 갈리는 부분만 "플랫폼" 열에 표기.

## 상태 범례
- ✅ 구현됨 (백엔드 컨트롤러 존재, 정상)
- ❌ **미구현** (백엔드 엔드포인트 없음 → FE 임시값/차단)
- ⚠️ **이슈** (구현됐으나 동작 문제)

백엔드 현존 컨트롤러: `auth, v1/chat, attendance, shop, inventory, energy, evolution, ads, users` — **`points` 컨트롤러 없음.**

---

## 0. 인증 / 온보딩  ·  플랫폼: 공통

| 기능 | Method · Path | shared 클라이언트 | 상태 |
|---|---|---|---|
| 게스트 로그인 | `POST /api/auth/guest` | auth | ✅ |
| Google 로그인 | `POST /api/auth/callback/google` | auth | ✅ |
| Apple 로그인 | `POST /api/auth/callback/apple` | auth | ✅ |
| 토큰 갱신 | `POST /api/auth/refresh` | `TokenProvider.refresh` | ⚠️ 이슈 C |
| 로그아웃 | `POST /api/auth/logout` | auth | ✅ |

---

## 1. 채팅  ·  플랫폼: 공통 (Android 연동완료 / iOS 진행중)

| 기능 | Method · Path | shared 클라이언트 | 상태 |
|---|---|---|---|
| 대화 생성 | `POST /api/v1/chat/conversations` | `ChatApi.createConversation` | ✅ |
| 대화 목록 | `GET /api/v1/chat/conversations` | `ChatApi.listConversations` | ✅ |
| 메시지 조회 | `GET /api/v1/chat/conversations/{id}/messages` | `ChatApi.getMessages` | ✅ |
| **AI 스트리밍(SSE)** | `POST /api/v1/chat/stream` | `ChatApi.streamMessage` | ⚠️ **이슈 A** |
| 대화 삭제 | `DELETE /api/v1/chat/conversations/{id}` | `ChatApi.deleteConversation` | ✅ (FeatureFlag `CONVERSATION_EDIT` 차단, 미노출) |
| 대화 이름변경 | `PATCH /api/v1/chat/conversations/{id}` | `ChatApi.renameConversation` | ✅ (동상) |
| 에너지(밥) 조회 | `GET /api/energy/me` | `EnergyApi` | ✅ |
| 에너지 충전 | `POST /api/energy/topup` | `EnergyTopupApi` | ✅ |
| 광고 보상 쿼터 | `GET /api/ads/reward/quota` | `AdsApi` | ✅ |
| 광고 nonce 발급 | `POST /api/ads/reward/issue-nonce` | `AdsApi` | ✅ |
| 진화 상태 | `GET /api/evolution/me` | `EvolutionApi` | ✅ |
| 진화 시도 | `POST /api/evolution/attempt` | `EvolutionApi` | ✅ |
| 진화 기록 | `GET /api/evolution/attempts` | `EvolutionApi` | ✅ |

> 채팅 게이트/상품카드/진화/아바타는 iOS에서 후속 슬라이스. shared API는 동일(추가 BE 요청 없음).

---

## 2. 혜택존(리워드)  ·  플랫폼: 공통

| 기능 | Method · Path | shared 클라이언트 | 상태 |
|---|---|---|---|
| 월간 출석 조회 | `GET /api/attendance/me` | `AttendanceApi.getMonthly` | ✅ |
| 출석 체크인 | `POST /api/attendance/check-in` | `AttendanceApi.checkIn` | ✅ |
| **코인 잔액 조회** | `GET /api/points/me` | `PointsApi.getBalance` | ❌ **이슈 B (공통)** |

---

## 3. 상점  ·  플랫폼: 공통

| 기능 | Method · Path | shared 클라이언트 | 상태 |
|---|---|---|---|
| 아이템 목록 | `GET /api/shop/items?category=ENHANCE\|COSMETIC\|VOUCHER` | `ShopApi.getItems` | ✅ (phase1 ENHANCE만) |
| 구매 | `POST /api/shop/purchase` | `ShopApi.purchase` | ✅ (idempotencyKey UUID, 응답에 권위 `coinBalance`) |
| 인벤토리 | `GET /api/inventory/me` | `ShopApi.getInventory` | ✅ |
| **코인 잔액 표시** | `GET /api/points/me` | `PointsApi` | ❌ **이슈 B (공통)** |

---

## 4. 마이페이지  ·  플랫폼: 공통

| 기능 | Method · Path | shared 클라이언트 | 상태 |
|---|---|---|---|
| 코인/포인트 잔액 | `GET /api/points/me` | `PointsApi` | ❌ **이슈 B (공통)** |
| 사용자 프로필(이름/이메일) | `GET /api/users/me` (추정) | **shared 클라이언트 없음** | ⚠️ **이슈 D** — BE `/api/users` 컨트롤러는 존재하나 FE 클라이언트 미작성·응답 스펙 미확인. 양 플랫폼 현재 목업 프로필 |
| 로그아웃 | `POST /api/auth/logout` | auth | ✅ |

---

## 화면별 목업 잔존 현황 (교체 대상)

> "아직 가짜 데이터/하드코딩이라 실데이터로 바꿔야 하는" 부분만 정리. (기능 *미구현*=후속 슬라이스와 구분)

| 화면 | 실데이터 연동됨 | **아직 목업 → 교체 필요** | 교체에 필요한 것 |
|---|---|---|---|
| 인증/온보딩 | 로그인 전부 | — | — |
| 채팅 | 전송·스트리밍(SSE 이슈 A)·대화목록 | — (게이트/상품카드/진화/아바타는 *미구현*=후속, 목업 아님) | 이슈 A |
| 혜택존 | 출석 위젯·체크인 | 코인 잔액(임시 1250), 혜택 카드 3개(의도된 "준비중") | 이슈 B / (카드는 의도된 placeholder) |
| 상점 | 카탈로그·구매·인벤토리 | 코인 잔액(임시 1250) | 이슈 B |
| **마이페이지** | 설정·로그아웃·앱버전 | **거의 전부 목업** ↓ | ↓ |
| · 프로필 이름/이메일 | — | "홍길동 / gildong@kakao.com" 하드코딩 | 이슈 D (`/api/users/me`) |
| · 보유/누적 포인트 | — | 누적 "15,750" 하드코딩, 보유는 임시 1250 | 이슈 B (+누적은 이슈 E) |
| · statCard | 총 대화수(부분) | "연속 출석 7", "교환 상품 3" 하드코딩 | 출석=attendance·교환수=inventory로 도출 가능 |
| · 기프티콘 보관함 | — | badge "2" 가짜, 화면 없음 | 제품 결정 + (해당 시) 신규 API |
| · 포인트 적립/사용 내역 | — | 메뉴만, 화면/데이터 없음 | **이슈 E (신규 API)** |
| · 공지사항 | — | badge "N" 가짜, 화면 없음 | **이슈 F (신규 API)** |
| · 고객센터 | — | 동작 없음 | 정적/외부 링크(제품 결정) |

→ **마이페이지(Slice 3b)는 단순 연동이 아니다.** 실데이터 가능한 부분(프로필·보유포인트·출석/교환 stat·로그아웃)만 우선 연동하고, **내역/공지/기프티콘은 BE 신규 API·제품 결정 후** 단계적으로 채운다. (Android 마이페이지도 동일하게 목업 — 공통)

---

## 백엔드 협조 필요 (우선순위)

### 이슈 B — 코인 잔액 조회 API 부재  ❌ **(공통, 최우선)**
- `GET /api/points/me` 컨트롤러가 **백엔드에 없음**.
- shared `SharedModule`이 `single<PointsRepository> { LocalPointsRepository() }`(임시값 1250)로 등록 → **Android·iOS 모두 코인 표시가 가짜값**.
- 영향(양 플랫폼 동일): 혜택존·상점·마이페이지 잔액이 가짜 → 화면엔 코인이 있어 보여도 **서버 실잔액 기준으로 상점 구매가 거절**(사용자 혼란). *(현재 iOS 테스트에서 "1250인데 구매 거절"로 재현)*
- **요청:** `GET /api/points/me → { "balance": Long }` 구현(스펙 `docs/planning/be-api-requests-cc348.md`, `PointsApi`/`PointBalanceDto` 참고).
- 구현 후 FE: `RemotePointsRepository`로 교체 + `FeatureFlags.POINT_BALANCE` 해제(양 플랫폼 공통 1회 작업).

### 이슈 A — 채팅 SSE HTTP/2 스트림 리셋  ⚠️ **(서버 측)**
- `POST /api/v1/chat/stream`이 HTTP/2에서 응답 종료 직후 `RST_STREAM`/연결 끊김.
- **Android**: `androidMain`에서 OkHttp **HTTP/1.1 강제**로 우회 적용됨(동작).
- **iOS**: NSURLSession은 HTTP/1.1 강제 불가 + Ktor CIO native TLS 미지원 → **앱 단독 해결 불가**, 현재 끊김(-1005).
- **요청(서버 정식 해법, 양쪽 공통 이득):** nginx HTTP/2 SSE 종료 처리 보정 / `event: done` 명시 종료 / (최후)해당 location HTTP/2 비활성.
- 이미 문서화: Confluence "BE 요청 임시 게시글" 이슈 1 (`/spaces/FCTC/pages/22216706/BE`).

### 이슈 C — 인증 엔드포인트 hang  ⚠️ **(서버, 수정됨/재배포 확인)**
- `POST /api/auth/guest|refresh`가 DB 커넥션 풀 고갈로 무응답 이력.
- dev 수정 커밋 `cf28429` → **서버 재배포 반영 확인** 필요. (Confluence 이슈 2)

### 이슈 D — 사용자 프로필 응답 스펙  ⚠️ **(공통, 우선순위 낮음)**
- 마이페이지 프로필(이름/이메일/누적 포인트 등)용 `GET /api/users/me` 응답 스펙 확정 요청.
- 확정 시 FE shared에 `UserApi` 추가(양 플랫폼 공통).

### 이슈 E — 포인트 적립/사용 내역 API 부재  ❌ **(공통, 신규)**
- 마이페이지 "포인트 적립/사용 내역" 화면용 API 없음(points 도메인 컨트롤러 자체 부재).
- 요청: `GET /api/points/history`(페이지네이션) → 적립/사용 항목 목록 + 누적 포인트. 이슈 B와 같은 points 도메인으로 묶어 설계 권장.

### 이슈 F — 공지사항 API 부재  ❌ **(공통, 신규, 우선순위 낮음)**
- 마이페이지 "공지사항" 화면/배지용 API 없음.
- 요청: `GET /api/notices` → 공지 목록(+ 미확인 수). 제품 우선순위에 따라 후순위 가능.

> 이슈 E·F는 **제품 결정(해당 화면을 MVP에 포함할지)** 후 진행. 미포함이면 마이페이지에서 해당 메뉴 제거로 정리.

---

## 참고
- shared API 클라이언트: `apps/frontend/shared/src/commonMain/.../{chat,attendance,shop,wallet,energy,evolution,ads}/`
- 플랫폼 분기(SSE 엔진): `shared/src/androidMain/.../HttpClientEngine.android.kt`(HTTP/1.1 강제) vs `iosMain/.../HttpClientEngine.ios.kt`(Darwin, 강제 불가)
- iOS 화면: `CashChatIOS/CashChatIOS/{ChatScreen,BenefitZoneScreen,ShopScreen,ContentView}.swift`
- 관련: Confluence `/spaces/FCTC/pages/22216706/BE`, `docs/planning/be-api-requests-cc348.md`
