# 채팅 인라인 네이티브 광고 설계

- 작성일: 2026-06-23
- 대상: `apps/frontend/` (shared `:shared`, Android `:app`, iOS `CashChatIOS`)
- 관련 문서: [apps/frontend/CLAUDE.md](../../../apps/frontend/CLAUDE.md) (Epic B Task B-4, Epic C `ad_chat_interval`), [2026-06-22-firebase-remote-config-analytics-design.md](./2026-06-22-firebase-remote-config-analytics-design.md)

## 1. 배경 / 목표

채팅방에서 AI 응답이 일정 횟수 누적될 때마다 AdMob **네이티브 광고**를 채팅 메시지처럼 리스트에 끼워 넣는다. 빈도는 코드 재배포 없이 Remote Config로 조정한다.

확인된 현재 상태:
- AdMob SDK는 Android(`play-services-ads`)·iOS(SPM `swift-package-manager-google-mobile-ads`) 양쪽에 이미 통합. 배너(`ads/BannerAd.kt`, `Ads/BannerAdView.swift`)·리워드(`ads/RewardedAdManager.kt`, `Ads/RewardedAdManager.swift`) 동작 중. **네이티브 광고만 미구현.**
- 채팅 리스트는 다형성 구조: `ChatItem`(sealed interface, `UserMessage`/`AssistantMessage`/`ProductCards`). 새 타입 추가로 메시지 사이 삽입 가능.
- Remote Config 키 `ad_chat_interval`(기본 `1`, "채팅 N회마다 네이티브 광고 삽입")은 Epic C에서 이미 정의됨. 본 작업에서 소비한다.
- 기존 `AdGateCard`(리워드 잠금 해제)는 별개 기능. 네이티브 광고는 보상 없는 **노출형**.

## 2. 핵심 설계 결정

| # | 결정 | 내용 |
|---|---|---|
| D1 | 빈도 | **N회마다**. `ad_chat_interval`(RemoteConfig) 재사용. 값 ≤ 0이면 광고 비활성. |
| D2 | 카운팅 기준 | **AI(assistant) 응답 완료 1건 = 1카운트.** `interval=3`이면 3·6·9번째 응답 *뒤에* 광고 삽입. 사용자 메시지는 세지 않음. |
| D3 | 책임 분리 | 삽입 *타이밍/위치*는 shared `ChatStore`가 결정해 `ChatItem.NativeAd(id)` placeholder만 리스트에 넣는다. 실제 광고 로딩·렌더링은 각 플랫폼 UI가 placeholder를 만나 수행. |
| D4 | UI 스타일 | **메시지 버블 스타일** — 좌측 정렬, AI 응답과 유사한 버블 안에 네이티브 광고. AdMob 정책상 `Ad` 라벨 필수. 아이콘·헤드라인·광고주·본문·CTA 버튼 포함. |
| D5 | 플랫폼 범위 | Android + iOS 동시. 정책 로직 shared, UI는 각 플랫폼. |
| D6 | 실패 처리 | 광고 로딩 실패 시 자리를 조용히 비움(스킵), 채팅 흐름 방해 안 함. `ad_failed`(ad_type=native) 애널리틱스 로깅. |
| D7 | 플레이스먼트명 | 애널리틱스/식별용 `chat_inline`. 기존 `BannerAdSlot.CHAT_TOP` 명명 컨벤션과 정렬. |

## 3. 컴포넌트

### 3.1 shared (`commonMain`)

- **`ChatItem.NativeAd`** — `data class NativeAd(override val id: String) : ChatItem`. 광고 데이터는 담지 않는 순수 placeholder(렌더링 시 플랫폼이 로딩).
- **`ChatStore`** 변경 — assistant 응답이 *완료*될 때(스트리밍 종료 시점) 누적 카운트를 증가시키고, `count % interval == 0 && interval > 0`이면 직전 assistant 메시지 뒤에 `ChatItem.NativeAd`를 1건 삽입. interval 값은 RemoteConfig에서 읽어 주입(생성자 파라미터 또는 provider 함수로 주입해 테스트 가능하게).
  - 같은 응답에 광고가 중복 삽입되지 않도록 idempotent하게(이미 NativeAd가 뒤따르면 재삽입 금지).

### 3.2 Android (`:app`)

- **`ads/NativeAdManager.kt`** — `play-services-ads`의 `AdLoader`로 `NativeAd` 로딩. ad unit ID는 `AppConfig`의 계층형 폴백(RemoteConfig→빌드타임→Google 테스트 네이티브 ID `ca-app-pub-3940256099942544/2247696110`)에서 획득. 로딩 결과를 콜백/`Flow`로 노출. 사용 후 `NativeAd.destroy()`로 해제.
- **`ads/NativeAdView.kt`** (Compose) — `ChatItem.NativeAd`를 받아 위 매니저로 광고를 로딩하고 버블 스타일로 렌더링. `NativeAdView`(AdMob의 네이티브 광고 컨테이너)에 헤드라인/아이콘/CTA를 바인딩(`AndroidView` interop). 로딩 전/실패 시 빈 자리(또는 미표시).
- **`ChatScreen.kt`** — `LazyColumn`의 `items` 분기에 `is ChatItem.NativeAd -> NativeAdView(...)` 추가.

### 3.3 iOS (`CashChatIOS`)

- **`Ads/NativeAdManager.swift`** — `GADAdLoader`로 `GADNativeAd` 로딩. ad unit ID는 iOS `AppConfig`의 동일 계층형 폴백(테스트 네이티브 ID `ca-app-pub-3940256099942544/3986624511`).
- **`Ads/NativeAdCardView.swift`** (SwiftUI) — `GADNativeAdView`(UIKit) 래핑해 버블 스타일 렌더링. 로딩 전/실패 시 미표시.
- **`ChatScreen.swift`** — 채팅 리스트에서 `ChatItem.NativeAd` 케이스에 위 뷰 연결.

## 4. 데이터 흐름

```
사용자 메시지 전송 → AI 응답 스트리밍 → 응답 완료
  → ChatStore: assistantCount++
  → assistantCount % ad_chat_interval == 0 && interval > 0 ?
       └ yes → items 리스트에 ChatItem.NativeAd(id) 삽입(직전 응답 뒤)
  → UI(LazyColumn / List): NativeAd placeholder 발견
       └ NativeAdManager.load() → 성공: 버블 렌더 / 실패: 빈 자리 + ad_failed 로깅
```

## 5. 에러 처리

- 광고 로딩 실패(`onAdFailedToLoad`): placeholder를 렌더하지 않음(빈 자리). 사용자 흐름 유지. `ad_failed`(ad_type=`native`, error_code) 애널리틱스 로깅.
- `ad_chat_interval` ≤ 0 또는 RemoteConfig 미fetch: 광고 미삽입(안전한 기본값).
- 메모리: Android `NativeAd.destroy()`, iOS는 ARC + 매니저 보유 해제로 누수 방지.

## 6. 테스트

- **shared(`commonTest`)**: `ChatStore` 카운팅 — interval=3일 때 3·6·9번째 assistant 응답 뒤에 정확히 NativeAd 삽입, interval≤0이면 미삽입, 같은 응답에 중복 삽입 안 됨. 기존 `BannerAdSlotTest` 순수 로직 패턴 준용.
- **플랫폼 UI**: 테스트 광고 ID로 수동 검증(버블 렌더, `Ad` 라벨 노출, 실패 시 빈 자리).

## 7. 범위 밖 (YAGNI)

- 광고 프리로딩/풀링 최적화(초기엔 placeholder 도달 시 on-demand 로딩).
- 사용자 메시지 기준 카운팅(본 설계는 assistant 응답 기준).
- 빈도 외 타게팅/세그먼트.
