# 프리롤 네이티브 광고 설계 (Pre-roll Native Ad)

- 작성일: 2026-06-23
- 상태: **설계 (미구현 / 향후 작업)** — 지금 당장 구현하지 않으며, 기획·설계를 문서로 남김
- 관련 코드: `shared/src/commonMain/.../chat/ChatStore.kt`, `chat/ChatApi.kt`, `chat/model/ChatItem.kt`, 각 플랫폼 `ChatNativeAdView`
- API 대응 요청: [2026-06-23-preroll-native-ad-api-requirements.md](./2026-06-23-preroll-native-ad-api-requirements.md)

## 1. 목적

채팅 응답 중 일부에 대해, **응답을 보여주기 전에 네이티브 광고를 먼저 노출**하고
**최소 X초 뒤에 응답 스트림을 시작**한다. 광고 노출 시간을 보장해 수익화를 강화한다.

기존 2종 광고 동선과는 **별개의 새 동선**이다:
- 기존 ① 응답 **완료 후** 네이티브 광고 삽입 (`ChatStore.maybeInsertNativeAd()`)
- 기존 ② 리워드 `Gate` — 응답 티저만 보여주고 리워드 광고를 **시청해야** 잠금 해제
- **신규 ③ 프리롤** — 응답 **앞단**에 네이티브 광고 + X초 자동 대기 후 응답 (사용자 액션 불필요)

## 2. 핵심 결정 사항

| 항목 | 결정 | 비고 |
|---|---|---|
| 트리거 출처 | **서버 스트림 플래그** (`event: preroll`) | 클라 정책 아님. 서버/운영이 노출 대상 응답을 결정 |
| 타이밍 모델 | **백그라운드 프리페치 + 버퍼링** | 광고 노출 중 응답 토큰을 미리 받아 버퍼링, X초 후 한 번에 풀어 스트림 |
| 광고 실패/no-fill | **즉시 응답 노출** | 광고 버블 미생성·타이머 스킵. 사용자를 빈 화면으로 대기시키지 않음 |
| X초(노출 시간) 값 | **Remote Config 단일 출처** (`preroll_ad_delay_sec`) | 서버/운영이 RC로 제어. SSE 이벤트는 트리거만 담당하고 노출 시간은 싣지 않음 |
| 광고 버블 유지 | 응답 노출 후에도 thread에 유지 | 기존 `ChatItem.NativeAd` 컴포넌트 재사용 |
| 플랫폼 패리티 | 로직은 공유 `ChatStore`(commonMain)에 단일 구현 | Android/iOS 동시 적용. 뷰는 각 플랫폼 기존 `ChatNativeAdView` 재사용 |

## 3. 동작 흐름

```
사용자 메시지 전송
  └─ ChatStore.stream() 진입, SSE 연결
       │
       ├─ (서버) event: preroll  { adType:"native" }      ← 트리거만. 노출 시간 X는 RC에서 읽음
       │     └─ 클라: ChatItem.NativeAd 버블 삽입 + X초(=RC preroll_ad_delay_sec) 타이머 시작 + "프리롤 버퍼링 모드" 진입
       │
       ├─ (서버) event: token ...   ← 이 토큰들은 화면에 안 그리고 버퍼에 누적
       ├─ (서버) event: token ...
       │
       └─ X초 경과
             └─ 버퍼된 텍스트로 assistant 버블 생성 → 이후 토큰부터 실시간 스트림(기존 동작)
```

### 상태 전이 요지 (`ChatStore.stream()` 변경)

현재는 첫 `Token` 수신 시 곧바로 assistant 버블을 만들어 노출한다. 프리롤에서는
"버블 생성 시점"을 **타이머 만료 또는 폴백 시점까지 지연**시키고, 그 전 토큰은 버퍼에 모은다.

새 내부 상태(개념):
- `prerollActive: Boolean` — `preroll` 이벤트 수신 시 true
- `tokenBuffer: StringBuilder` — 프리롤 중 누적 토큰
- `prerollReleaseJob: Job?` — X초 타이머. 만료 시 버퍼 flush + 실시간 모드 전환

이벤트별 처리:
- `preroll` → 광고 버블 삽입, 타이머 시작, `prerollActive=true`
- `token`
  - `prerollActive==true`: `tokenBuffer`에 누적 (화면 변화 없음)
  - else: 기존대로 즉시 노출/누적
- 타이머 만료(또는 폴백) → `flushPreroll()`: 버퍼 텍스트로 assistant 버블 1개 생성, `prerollActive=false`, 이후 토큰 실시간 누적
- `done` → 스트림 종료(기존). 프리롤이 아직 안 풀렸으면 먼저 flush 후 종료
- `error`/`StreamError` → flush 후 에러 노출(아래 4.3)

## 4. 폴백 / 엣지 케이스

### 4.1 광고 로드 실패·no-fill
프리롤 광고가 로드 실패하거나 채워지지 않으면:
- 광고 버블을 **만들지 않는다**.
- X초 타이머를 **즉시 만료** 처리하여 버퍼를 바로 flush → 응답 즉시 노출.
- 즉, "광고가 안 뜨면 사용자는 평소처럼 바로 응답을 본다".

> 구현 메모: 광고 로드 성공/실패는 플랫폼 `ChatNativeAdView`/로더에서 발생하므로,
> 로더 콜백을 `ChatStore`로 전달하는 경로가 필요하다(콜백 또는 상태 Flow).
> 단순화를 위해 "광고 로드와 무관하게 X초 후 flush, 단 광고 실패 시 버블만 숨김" 방식도 가능 —
> 이 경우 실패해도 X초는 대기하므로, **4.1 결정(즉시 노출)을 만족하려면 로더 실패 신호가 타이머를 단축**해야 한다.

### 4.2 사용자 이탈 / 대화 전환 / 새 대화 / 로그아웃
프리롤 대기 중 이탈 시 `streamJob.cancel()`(기존)에 더해 **`prerollReleaseJob.cancel()`**와
버퍼·상태 초기화가 필요하다. `startNewConversation()` / `openConversation()` / `reset()`에
프리롤 상태 리셋을 추가한다(기존 `assistantResponseCount` 리셋과 동일 위치).

### 4.3 응답 자체 에러(`StreamError`)
- 프리롤이 아직 안 풀린 상태면: 버퍼 flush(빈 텍스트면 빈 버블 생성 생략) 후 에러 버블 노출.
- 이미 떠 있는 광고 버블은 **유지**(폴백 정책과 일관).

### 4.4 기존 리워드 `Gate`와의 관계
프리롤과 리워드 Gate는 **독립**이며, 한 응답에 동시에 적용하지 않는다(서버가 둘 중 하나만 발행).
동시 수신 시 우선순위는 미정 — 서버 계약으로 상호배타 보장(§ API 문서 참고).

### 4.5 중복 방지
한 응답 스트림에서 `preroll` 이벤트는 **최초 1회만** 유효. 2번째 이후는 무시한다.

## 5. 설정 (Remote Config)

| 키 | 기본값 | 설명 |
|---|---|---|
| `preroll_ad_enabled` | `false` | 클라 측 킬스위치. false면 `preroll` 이벤트를 무시하고 기존 동작 |
| `preroll_ad_delay_sec` | `3` | **광고 최소 노출 시간(초) 단일 출처.** 서버/운영이 RC로 제어 |

실효 X = `preroll_ad_delay_sec`. `preroll_ad_enabled=false`거나 `preroll_ad_delay_sec<=0`이면 프리롤 비활성(기존 동작).
노출 시간은 SSE 이벤트가 아니라 RC에서만 읽으므로, 코드·배포 없이 운영에서 조정 가능하다.

## 6. 영향 범위 / 변경 파일 (예상)

- `shared/.../chat/ChatApi.kt` — `ChatStreamEvent`에 `PreRollAd(adType)` 추가, SSE `event:"preroll"` 파싱 (노출 시간 미포함)
- `shared/.../chat/ChatStore.kt` — 버퍼링 상태머신, 타이머(RC `preroll_ad_delay_sec`), flush, 리셋 경로
- `shared/.../chat/model/ChatItem.kt` — 기존 `NativeAd` 재사용(필요 시 `isPreroll` 플래그)
- 각 플랫폼 RemoteConfigKeys/AppConfig — RC 키 2종(`preroll_ad_enabled`, `preroll_ad_delay_sec`) 추가
- (선택) 광고 로드 실패 신호를 `ChatStore`로 전달하는 경로 — 4.1 정책 충족용

## 7. 테스트 관점

- 단위(`ChatStoreNativeAdTest` 패턴): preroll 수신 → 버블 삽입·버퍼링, X초 후 flush 순서/내용
- 폴백: 광고 실패 시 즉시 flush, X초 대기 없음
- 이탈: 타이머 중 cancel 시 누수 없음(코루틴/타이머 정리)
- 에러: StreamError 시 버블·에러 처리
- 패리티: 동일 로직이 Android/iOS 모두에서 동작(commonMain 단일 구현)

## 8. 미해결 / 후속 결정

- [ ] 광고 실패 신호를 `ChatStore`까지 어떻게 전달할지(콜백 vs 상태 Flow) — 구현 시 확정
- [ ] preroll + gate 동시 수신 시 우선순위(서버 상호배타로 회피 예정)
- [ ] 애널리틱스 이벤트: 프리롤 노출/스킵/실패 계측 키 정의 (`ad_view` 확장)
