# Maestro FE 인수 테스트 (CC-391)

백엔드 서버 없이 `mock` 플레이버(인앱 Fake 백엔드)로 핵심 여정을 검증한다.
**정식 가이드(작성·수행·결과 확인·CI)는 [`docs/testing/maestro-e2e.md`](../../../docs/testing/maestro-e2e.md).** 이 파일은 빠른 참조용.

## 여정(유저 스토리) ↔ flow 구조 (`<verb>-<object>`)

```
maestro/flows/
  send-message/        US-CHAT-001    happy.yaml · stream-error.yaml
  watch-rewarded-ad/   US-REWARD-002  happy.yaml · quota-exceeded.yaml
  complete-offerwall/  US-REWARD-003  happy.yaml · token-fail.yaml
maestro/demo/          사람이 보기용(딜레이) 버전 + _pause.yaml
```
각 flow 는 상태 변화 지점에 `takeScreenshot` 을 남겨 HTML 리포트에서 확인할 수 있다.

## 실행 — 스크립트(권장)

에뮬레이터 부팅 + mock APK 설치 + 실행 + 리포트를 한 번에:
```bash
apps/frontend/scripts/maestro-e2e-test.sh                    # 전체
apps/frontend/scripts/maestro-e2e-test.sh send-message       # 단일 유저 스토리(개발 중)
apps/frontend/scripts/maestro-e2e-test.sh --report           # 전체 + HTML 리포트
apps/frontend/scripts/maestro-e2e-test.sh -h                 # 옵션
```

## 실행 — maestro 직접

```bash
cd apps/frontend
maestro test maestro                                  # 전체 (config.yaml 의 flows/** 포함 패턴)
maestro test maestro/flows/send-message               # 단일 유저 스토리(폴더)
maestro test maestro/flows/send-message/happy.yaml    # 단일 flow
```
> 서브디렉터리를 모두 포함하려면 `config.yaml` 이 있는 `maestro` 디렉터리를 대상으로 실행한다
> (`maestro test maestro/flows` 는 top-level 만 봐서 서브디렉터리를 놓친다).

검증됨: 6/6 flow green (Pixel 10 Pro 에뮬레이터, 약 3분).
시나리오는 flow 의 `launchApp.arguments.scenario` 로 주입된다
(`happy` | `chat_error` | `ad_quota_exceeded` | `offerwall_fail`) — mock 앱이 인텐트 extra
`scenario` 를 읽어 `MockBackendState` 에 반영한다.

## 리포트 / 스크린샷

```bash
maestro test maestro --format HTML-DETAILED --output report.html --debug-output ./maestro-debug
```
- `report.html`: 사람용 리포트. `--debug-output`: flow별 스크린샷·로그·commands JSON.
- 실패 스크린샷은 자동 저장, 그 외 지점은 flow 의 `takeScreenshot` 로 캡처된다(딜레이 불필요).
- 영상: `maestro record maestro/demo/send-message.yaml`.

## 데모 (사람이 화면 보며 검증)

```bash
maestro test maestro/demo/send-message.yaml
maestro test maestro/demo/watch-rewarded-ad.yaml
maestro test maestro/demo/complete-offerwall.yaml
```
단계 사이 멈춤은 `demo/_pause.yaml`(evalScript busy-wait). 더 느리게 보려면 `1300`(ms)을 키운다.

## 에뮬레이터 관련 주의 (프리뷰 이미지에서 관측)

Android 17 프리뷰 이미지의 시스템 노이즈에 대비해 flow 에 방어 로직을 넣었다.
안정 이미지(API 34/35 + 기본 Gboard)에서는 대부분 발생하지 않는다.
- **16 KB 호환성 다이얼로그**: 첫 실행 시 → 각 flow 가 `Don't Show Again` 을 조건부로 닫는다.
- **Gboard 스타일러스 온보딩("Try out your stylus")**: 입력창 포커스 시 전송 버튼을 가림 →
  채팅 flow 가 `Cancel` 을 조건부로 닫는다(한 번 닫으면 재발 안 함).
- **한글 입력 불가**: Maestro `inputText` 유니코드 미지원(issue #146) → 채팅은 ASCII(`hello`) 전송.
  mock 은 메시지 내용과 무관하게 고정 SSE 응답을 반환하므로 검증에 영향 없음.
- **AdMob 크래시 회피**: `app/src/mock/AndroidManifest.xml` 이 AdMob APPLICATION_ID 를 Google 공개 테스트
  ID 로 대체(로컬에 `ADMOB_APP_ID` 없어도 `MobileAdsInitProvider` 크래시 방지, hermetic 유지).

## 셀렉터 메모

`testTag` 없이 표시 텍스트/`contentDescription` 으로 선택한다:
- 하단 네비 리워드 탭 `"리워드"` · 채팅 입력창 placeholder `"메시지를 입력하세요..."` · 전송 `contentDescription="전송"`
- 리워드 광고 카드 `"▶  광고 보기"` / 한도 초과 `"내일 다시 만나요"`
- 오퍼월 카드 `"TNK 오퍼월"` · 상단 코인 잔액 `"🪙 <정수>"`(천단위 콤마 없음)

셀렉터가 취약하면 `maestro studio` 로 실제 노드를 확인해 보정한다.

## maestro 설치 (Windows)

JDK 필요(이 저장소 JDK 21). Git Bash 에서:
```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"
maestro --version
```
스크립트가 안 되면 [GitHub Releases](https://github.com/mobile-dev-inc/maestro/releases)의 `maestro.zip` 을
받아 `bin` 을 PATH 에 추가. `adb` 도 PATH 필요: `export PATH="$PATH:$LOCALAPPDATA/Android/Sdk/platform-tools"`.
