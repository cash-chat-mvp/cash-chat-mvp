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
maestro test maestro/flows                          # 전체 flow
maestro test maestro/flows/chat/ai-response.yaml    # 단일 flow
```

각 flow 는 `docs/domains` 의 US/AC ID 를 주석으로 역참조한다.
시나리오는 flow 의 `launchApp.arguments.scenario` 로 주입된다
(`happy` | `chat_error` | `ad_quota_exceeded` | `offerwall_fail`) — mock 앱이 인텐트 extra
`scenario` 를 읽어 `MockBackendState` 에 반영한다.

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
