# AdMob 실 광고 연동 가이드

이 문서는 Cash Chat 앱(iOS·Android)에서 **Google 테스트 광고 → 실제(수익) AdMob 광고**로 전환할 때
무엇을, 어디에, 어떤 이름으로 설정해야 하는지 정리한 운영 가이드입니다.

> ⚠️ **핵심 주의 1** — AdMob 앱 ID와 광고 단위 ID는 **iOS / Android가 각각 다른 값**입니다. 같은 ID를 공유하면 안 됩니다.
> ⚠️ **핵심 주의 2** — GitHub Secrets 이름이 **플랫폼별로 다릅니다**. iOS는 `IOS_ADMOB_*`, Android는 `ADMOB_*`. 같은 광고라도 두 시크릿을 각각 등록해야 합니다.

---

## 1. 현재 동작 방식 (시크릿이 없어도 빌드는 통과)

두 플랫폼 모두, 광고 시크릿이 비어 있으면 **Google 공식 테스트 광고 ID로 자동 폴백**합니다.
따라서 시크릿을 설정하기 전까지는 "테스트 광고"가 노출되고, 빌드/배포는 실패하지 않습니다.

| 플랫폼 | 시크릿 주입 경로 |
|---|---|
| iOS | CI가 `Secrets.swift`를 생성 → `AppConfig.swift`가 읽음 |
| Android | CI가 `local.properties`에 기록 → `BuildConfig` → `AppConfig.kt`가 읽음 |

실 광고를 켜려면 아래 2단계만 하면 됩니다: **(A) AdMob 콘솔에서 ID 발급 → (B) GitHub Secrets 등록**.

---

## 2. (A) AdMob 콘솔에서 ID 발급

[AdMob 콘솔](https://apps.admob.com) 접속 후, **iOS 앱과 Android 앱을 각각** 등록/선택합니다.

### 앱 ID
- 앱 → **앱 설정**에서 확인. 형식: `ca-app-pub-XXXXXXXX~YYYYYYYY` (구분자 `~`)
- iOS 앱과 Android 앱의 앱 ID는 **서로 다릅니다.**

### 광고 단위 ID
- 각 앱 → **광고 단위** → 필요한 유형을 생성. 형식: `ca-app-pub-XXXXXXXX/ZZZZZZZZ` (구분자 `/`)
- 현재 앱에서 쓰는 유형: **배너 / 전면(Interstitial) / 네이티브 / 리워드(Rewarded)**
- iOS·Android 각각 따로 만들어야 합니다 (앱당 4개 → 총 8개 광고 단위).

> 구분자 주의: 앱 ID는 `~`, 광고 단위 ID는 `/` 입니다. 섞이면 광고가 안 뜨거나 정책 위반이 됩니다.

---

## 3. (B) GitHub Secrets 등록

**저장소** `cash-chat-mvp/cash-chat-mvp` → **Settings → Secrets and variables → Actions → New repository secret**

시크릿 이름은 워크플로우와 **정확히 일치**해야 합니다.

### iOS — `IOS_` 접두

| Secret 이름 | 값 (AdMob iOS 앱 기준) |
|---|---|
| `IOS_ADMOB_APP_ID` | `ca-app-pub-…~…` (iOS 앱 ID) |
| `IOS_ADMOB_BANNER_AD_UNIT_ID` | `ca-app-pub-…/…` (배너) |
| `IOS_ADMOB_INTERSTITIAL_AD_UNIT_ID` | `ca-app-pub-…/…` (전면) |
| `IOS_ADMOB_NATIVE_AD_UNIT_ID` | `ca-app-pub-…/…` (네이티브) |
| `IOS_ADMOB_REWARDED_AD_UNIT_ID` | `ca-app-pub-…/…` (리워드) |

### Android — 접두 없음

| Secret 이름 | 값 (AdMob Android 앱 기준) |
|---|---|
| `ADMOB_APP_ID` | `ca-app-pub-…~…` (Android 앱 ID) |
| `ADMOB_BANNER_AD_UNIT_ID` | `ca-app-pub-…/…` (배너) |
| `ADMOB_INTERSTITIAL_AD_UNIT_ID` | `ca-app-pub-…/…` (전면) |
| `ADMOB_NATIVE_AD_UNIT_ID` | `ca-app-pub-…/…` (네이티브) |
| `ADMOB_REWARDED_AD_UNIT_ID` | `ca-app-pub-…/…` (리워드) |

### `gh` CLI로 한 번에 등록

```bash
# iOS
gh secret set IOS_ADMOB_APP_ID               --repo cash-chat-mvp/cash-chat-mvp
gh secret set IOS_ADMOB_BANNER_AD_UNIT_ID    --repo cash-chat-mvp/cash-chat-mvp
gh secret set IOS_ADMOB_INTERSTITIAL_AD_UNIT_ID --repo cash-chat-mvp/cash-chat-mvp
gh secret set IOS_ADMOB_NATIVE_AD_UNIT_ID    --repo cash-chat-mvp/cash-chat-mvp
gh secret set IOS_ADMOB_REWARDED_AD_UNIT_ID  --repo cash-chat-mvp/cash-chat-mvp

# Android
gh secret set ADMOB_APP_ID                   --repo cash-chat-mvp/cash-chat-mvp
gh secret set ADMOB_BANNER_AD_UNIT_ID        --repo cash-chat-mvp/cash-chat-mvp
gh secret set ADMOB_INTERSTITIAL_AD_UNIT_ID  --repo cash-chat-mvp/cash-chat-mvp
gh secret set ADMOB_NATIVE_AD_UNIT_ID        --repo cash-chat-mvp/cash-chat-mvp
gh secret set ADMOB_REWARDED_AD_UNIT_ID      --repo cash-chat-mvp/cash-chat-mvp
```

> 일부만 등록해도 됩니다. **등록하지 않은 항목만** 테스트 ID로 폴백되고, 나머지는 실 ID로 빌드됩니다.

---

## 4. 적용 (재배포)

시크릿은 **워크플로우 실행 시점**에 주입됩니다. 등록 후 아래 릴리즈 브랜치에 푸시(또는 머지)하면 실 ID로 다시 빌드됩니다.

- iOS: `release/ios` 브랜치 → `release-ios-distribute.yml`
- Android: `release/android` 브랜치 → `release-android-distribute.yml`

---

## 5. 주의사항

- **테스트 ↔ 실 ID**: 개발/QA 중에는 반드시 테스트 ID(또는 [테스트 기기 등록](https://developers.google.com/admob/android/test-ads))을 사용하세요.
  실 광고 단위로 **본인이 반복 클릭하면 정책 위반**이며, 계정 정지 위험이 있습니다.
- **앱 ID 분리**: `IOS_ADMOB_APP_ID` ≠ `ADMOB_APP_ID`. 한쪽 값을 양쪽에 넣으면 다른 플랫폼이 잘못된 앱 ID로 빌드됩니다.
- **Android 런타임 크래시 주의**: AdMob 앱 ID가 비어 있으면 Mobile Ads SDK 초기화 시 앱이 시작부터 크래시할 수 있습니다. 현재는 테스트 ID 폴백으로 방지되지만, 실 운영에서는 `ADMOB_APP_ID`를 반드시 채우세요.
- **수익 집계**: 실 ID로 전환하면 노출/클릭이 실제 AdMob 계정에 집계됩니다. 무효 트래픽(자기 클릭, 봇)에 주의하세요.

---

## 6. 참고 — 코드/설정 위치

| 항목 | 위치 |
|---|---|
| iOS 시크릿 생성 | [.github/workflows/release-ios-distribute.yml](../.github/workflows/release-ios-distribute.yml) — `Create Secrets.swift` 스텝 |
| iOS 빌드체크(테스트 ID) | [.github/workflows/ios-build-check.yml](../.github/workflows/ios-build-check.yml) |
| iOS 광고 ID 노출 | [apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift](../apps/frontend/CashChatIOS/CashChatIOS/AppConfig.swift) |
| iOS 로컬 시크릿(gitignore) | `apps/frontend/CashChatIOS/CashChatIOS/Secrets.swift` |
| Android 시크릿 주입 | [.github/workflows/release-android-distribute.yml](../.github/workflows/release-android-distribute.yml) — `Write local.properties` 스텝 |
| Android 광고 ID 노출 | [apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt](../apps/frontend/app/src/main/java/com/nomadclub/cashchat/config/AppConfig.kt) |
| Android BuildConfig 주입 | [apps/frontend/app/build.gradle.kts](../apps/frontend/app/build.gradle.kts) |

### Google 공식 테스트 ID (폴백에 사용 중)

| 유형 | iOS | Android |
|---|---|---|
| 앱 ID | `ca-app-pub-3940256099942544~1458002511` | `ca-app-pub-3940256099942544~3347511713` |
| 배너 | `…/2934735716` | `…/6300978111` |
| 전면 | `…/4411468910` | `…/1033173712` |
| 네이티브 | `…/3986624511` | `…/2247696110` |
| 리워드 | `…/1712485313` | `…/5224354917` |

출처: <https://developers.google.com/admob/ios/test-ads>, <https://developers.google.com/admob/android/test-ads>
