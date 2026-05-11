# Current Task: CC-151
# [FE] Android 프로젝트 초기화 (모듈 구조, DI 설정)

**Jira**: CC-151  
**Epic**: A. FE Core / 의존성 정리  
**담당**: 김형민  
**우선순위**: P0  
**기간**: 3/15 – 3/17  
**상태**: 진행 중

---

## 작업 목표

Android(KMP) 프로젝트의 기반을 확보한다.
이후 모든 Epic(광고/분석/채팅)이 이 작업 결과물 위에서 동작하므로 가장 먼저 완료해야 한다.

---

## Task A-1. 의존성 추가 및 버전 정리

### 추가할 라이브러리

`app/build.gradle.kts`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`에 아래 라이브러리를 추가한다.

| 라이브러리 | 모듈 |
|---|---|
| AdMob | Android: `play-services-ads` / iOS: CocoaPods `Google-Mobile-Ads-SDK` |
| Firebase Analytics | `firebase-analytics` |
| Firebase Remote Config | `firebase-config` |
| Sentry KMP | `sentry-kotlin-multiplatform` |
| Ktor | `ktor-client-core`, `ktor-client-okhttp`(Android), `ktor-client-darwin`(iOS), `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` |
| kotlinx.serialization | `kotlinx-serialization-json` |
| SQLDelight | `runtime`, `android-driver`, `native-driver` |
| Koin | `koin-core`, `koin-android`, `koin-androidx-compose` |

### 완료 기준 (AC)

- [ ] 빌드 성공 (Android / iOS 각각)
- [ ] 라이브러리 간 버전 충돌 없음
- [ ] 모든 버전이 `libs.versions.toml` 버전 카탈로그로 일원화됨

---

## Task A-2. 공통 설정 / 키 관리 구조화

### 작업 내용

1. **Firebase 설정 파일 환경별 분리**
   - `google-services.json` → Android `dev` / `prod` flavor 별 경로에 배치
   - `GoogleService-Info.plist` → iOS `dev` / `prod` scheme 별 분리

2. **AdMob 키 분리**
   - AdMob App ID 및 Ad Unit ID를 `BuildConfig` 또는 `local.properties`로 관리
   - 소스코드에 직접 하드코딩 금지

3. **Sentry DSN 환경별 분리**
   - dev / prod DSN을 환경 설정에서 주입

4. **AppConfig / Koin 모듈 구성**
   - `AppConfig` object 또는 Koin module로 키 주입 구조 구현
   - 환경 변수 단일 진입점에서 관리

### 완료 기준 (AC)

- [ ] dev / prod 설정 분기 가능
- [ ] 키가 소스코드에 하드코딩되지 않음
- [ ] Koin 모듈 초기화 정상 동작 확인

---

## ⚠️ 주의사항

- **KMP vs Native 결정 필요**: DI 프레임워크(Koin vs Hilt) 선택은 KMP 사용 여부에 달려 있음.
  - KMP 사용 → **Koin** (commonMain에서 사용 가능)
  - Android Only → Hilt 선택 가능하나, 향후 KMP 전환 시 재작업 필요
  - MVP 설계 문서 기준으로는 **Koin** 으로 확정되어 있음
- 테스트 광고 단위 ID는 Google 공식 테스트 ID를 사용할 것 (실제 ID는 Task G-2에서 교체)

---

## 다음 작업 (이 태스크 완료 후)

- **Epic B - Task B-1**: AdMob SDK 통합 (Android + iOS) — `expect/actual` 구조로 `AdManager` 인터페이스 정의
- **Epic C - Task C-2**: Firebase Remote Config 연동
- **Epic D - Task D-1**: Sentry 연동
