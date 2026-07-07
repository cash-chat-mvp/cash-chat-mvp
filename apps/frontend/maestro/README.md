# Maestro FE 인수 테스트 (CC-391 스파이크)

백엔드 서버 없이 `mock` 플레이버(인앱 Fake 백엔드)로 핵심 여정을 검증한다.

## 사전 준비

1. **에뮬레이터 실행** (또는 실기기 USB 연결). `adb devices` 로 `device` 상태 확인.
2. **maestro 설치** — 아래 "maestro 설치" 참고. `maestro --version` 으로 확인.
3. **mock APK 설치**:
   ```bash
   cd apps/frontend
   ./gradlew :app:installMockDebug
   ```

## 실행

```bash
cd apps/frontend
maestro test maestro                                # 전체 flow (config.yaml 의 flows/** 포함 패턴)
maestro test maestro/flows/chat/ai-response.yaml    # 단일 flow
```
> 서브디렉터리 flow 를 모두 포함하려면 반드시 `config.yaml` 이 있는 `maestro` 디렉터리를 대상으로 실행한다
> (`maestro test maestro/flows` 는 top-level flow 만 봐서 서브디렉터리를 놓친다).

검증됨: 6/6 flow green (Pixel 10 Pro 에뮬레이터, 약 3분).

각 flow 는 `docs/domains` 의 US/AC ID 를 주석으로 역참조한다.
시나리오는 flow 의 `launchApp.arguments.scenario` 로 주입된다
(`happy` | `chat_error` | `ad_quota_exceeded` | `offerwall_fail`) — mock 앱이 인텐트 extra
`scenario` 를 읽어 `MockBackendState` 에 반영한다.

## 수동 확인 / 데모 (사람이 화면 보며 검증)

`maestro test` 는 실행 중 **에뮬레이터 화면에 실제 탭·스크롤을 그대로 보여준다.** 창을 띄워 두고 아래를 실행하면 눈으로 확인할 수 있다.

**1) 그냥 보면서 실행 (정상 속도, 여정당 24~35초)**
```bash
cd apps/frontend
maestro test maestro/flows/rewarded-ad/watch-reward.yaml   # 단일 여정
maestro test maestro                                       # 전체 6개
```

**2) 딜레이 데모 (`maestro/demo/`) — 단계마다 ~1.3s 멈춤 + 스크린샷**
사람이 각 단계를 또렷이 볼 수 있게 느리게 진행하는 데모 버전. CI 대상이 아니며 `demo/` 아래에만 둔다.
```bash
maestro test maestro/demo/chat.yaml          # AI 채팅
maestro test maestro/demo/rewarded-ad.yaml   # 보상형 광고
maestro test maestro/demo/offerwall.yaml     # TNK 오퍼월
```
- 단계 사이 멈춤은 `demo/_pause.yaml`(evalScript busy-wait) 서브플로우로 구현했다. 더 느리게 보려면
  `_pause.yaml` 의 `1300`(ms)을 키운다.
- `takeScreenshot` 로 각 단계 캡처가 `~/.maestro/tests/<타임스탬프>/` 에 저장된다.

**3) 영상으로 남기기**
```bash
maestro record maestro/demo/chat.yaml        # 실행 영상(mp4) 저장
```

> `maestro studio` 를 실행하면 브라우저 UI 로 현재 화면의 요소를 직접 짚어 셀렉터를 확인/작성할 수 있다(셀렉터 보정에 유용).

## 에뮬레이터 관련 주의 (프리뷰 이미지에서 관측)

Android 17 프리뷰 이미지에서 다음 시스템 노이즈가 flow 를 방해할 수 있어 flow 에 방어 로직을 넣었다.
안정 이미지(API 34/35 + 기본 Gboard)에서는 대부분 발생하지 않는다.
- **16 KB 호환성 다이얼로그**: 첫 실행 시 표시 → 각 flow 가 `Don't Show Again` 을 조건부로 닫는다.
- **Gboard 스타일러스 온보딩("Try out your stylus")**: 입력창 포커스 시 표시되어 전송 버튼을 가림 →
  채팅 flow 가 `Cancel` 을 조건부로 닫는다(한 번 닫으면 재발 안 함).
- **한글 입력 불가**: Maestro `inputText` 는 유니코드 미지원(issue #146) → 채팅은 ASCII(`hello`) 전송.
  mock 은 메시지 내용과 무관하게 고정 SSE 응답을 반환하므로 검증에 영향 없음.
- **AdMob 크래시 회피**: `app/src/mock/AndroidManifest.xml` 이 AdMob APPLICATION_ID 를 Google 공개 테스트
  ID 로 대체한다(로컬에 `ADMOB_APP_ID` 없어도 `MobileAdsInitProvider` 크래시 방지, hermetic 유지).

## 셀렉터 메모

현재 화면에 `testTag` 는 없으며 표시 텍스트/`contentDescription` 으로 선택한다:
- 하단 네비 리워드 탭: `"리워드"`
- 채팅 입력창 placeholder: `"메시지를 입력하세요..."`, 전송 버튼: `contentDescription="전송"`
- 리워드 광고 카드: `"▶  광고 보기"` / 한도 초과 시 `"내일 다시 만나요"`
- 오퍼월 카드: `"TNK 오퍼월"`, 상단 코인 잔액: `"🪙 <정수>"` (천단위 콤마 없음)

flow 가 셀렉터로 취약하면 `maestro studio` 로 실제 노드를 확인해 보정한다.

## maestro 설치 (Windows)

maestro 는 JDK 가 필요하다(이 저장소는 JDK 21 사용). Git Bash 에서:

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
# 설치 후 PATH 에 ~/.maestro/bin 추가 (예: ~/.bashrc 에)
export PATH="$PATH:$HOME/.maestro/bin"
maestro --version
```

설치 스크립트가 Windows 에서 동작하지 않으면 GitHub Releases 에서
`maestro.zip` 을 받아 압축 해제 후 `maestro/bin` 을 PATH 에 추가한다:
https://github.com/mobile-dev-inc/maestro/releases

`adb` 도 PATH 에 있어야 한다(에뮬레이터 통신용):
```bash
export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"
```
