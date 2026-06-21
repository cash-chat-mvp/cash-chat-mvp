# CC-355 코드리뷰 후속 작업 목록

- 작성일: 2026-06-21
- 출처: PR [#200](https://github.com/cash-chat-mvp/cash-chat-mvp/pull/200) 코드리뷰 (CodeRabbit / Gemini reviewer)
- 목적: 이번 PR에서 **즉시 반영하지 않고 후속으로 미룬** 리뷰 지적 사항을 추적한다.
  (이번 PR에서 반영 완료된 항목은 PR 커밋·스레드 답글 참고)

## 우선순위 요약

| # | 항목 | 영역 | 우선순위 | 트리거(언제 처리) |
|---|------|------|----------|-------------------|
| 1 | 릴리즈 시크릿 누락 fail-fast | CI/빌드 | 중 | 릴리즈 파이프라인 정비 시 |
| 2 | 채팅 배너 하단 고정 슬롯 + shimmer | Android UI | 중 | 배너 UX 설계 확정 후 |
| 3 | 오퍼월 진입 Analytics 이벤트 | Android | 중 | 광고 계측 슬라이스 |
| 4 | iOS 리워드 카드 실패 토스트 | iOS | 낮 | iOS 보상 UX 파리티 |
| 5 | ATT `@unknown default` 보수적 처리 | iOS | 낮 | 보안/개인정보 점검 시 |
| 6 | 광고 결과 Boolean → sealed 타입 | shared | 낮(heavy) | 보상 플로우 리팩터 |
| 7 | 룰렛 일일 리셋 정책 | shared(BE) | 중 | 룰렛 실 API 연동 |
| 8 | 룰렛 세그먼트 매핑 랜덤화 | shared(BE) | 낮 | 룰렛 실 API 연동 |
| 9 | 보상량(rewardAmount) 정량 피드백 | BE/FE | 낮 | BE 협업 |
| 10 | plan/스펙 문서 정합성 정리 | docs | 낮 | 문서 정리 |

---

## 1. 릴리즈 시크릿 누락 fail-fast

- **위치**: `apps/frontend/app/build.gradle.kts:46-47` (`TNK_APP_ID`), `.github/workflows/release-ios-distribute.yml:278-300` (`TNK_APP_ID_IOS`)
- **지적**: 시크릿이 비어 있어도 빈 문자열로 릴리즈가 생성되어, 오퍼월 진입이 런타임에서 실패할 수 있음.
- **후속**: 릴리즈 태스크에 한해 필수 시크릿 검증(`isBlank()` → `error()` / `: "${VAR:?}"`)으로 fail-fast.
- **보류 사유**: 컴파일/검증 빌드 흐름을 막지 않으려는 의도. 릴리즈 파이프라인 정비 시 Android·iOS 일괄 적용.

## 2. 채팅 배너 하단 고정 슬롯 + shimmer

- **위치**: `app/.../feature/chat/ChatScreen.kt:197-200`, `shared/.../ads/BannerAdSlot.kt`
- **지적**: 배너가 상단(헤더 아래)에 배치되어 "채팅 하단 고정 슬롯 + shimmer/placeholder" 가이드와 불일치.
- **후속**: `BannerAdSlot.CHAT_BOTTOM` 슬롯 신설 + 하단 배치 + 로딩 shimmer 적용.
- **보류 사유**: 현재 `CHAT_TOP` 상단 배치는 이 PR의 의도된 디자인. 슬롯 신설·위치 변경은 제품/UX 결정 필요.

## 3. 오퍼월 진입 성공/실패 Analytics 이벤트

- **위치**: `app/.../offerwall/TnkOfferwallManager.kt:27`
- **지적**: 오퍼월 진입 성공/실패 Firebase Analytics 이벤트 로깅 누락.
- **후속**: `ad_view`/`ad_failed` 등 기존 광고 이벤트 규약에 맞춰 오퍼월 진입 계측 추가.
- **보류 사유**: 버그가 아닌 계측 보강. 광고 Analytics 슬라이스에서 일괄.

## 4. iOS 리워드 카드 실패 경로 토스트

- **위치**: `CashChatIOS/.../BenefitZone/RewardAdCardView.swift:68`
- **지적**: 실패 경로에서 사용자 피드백(토스트) 미표시.
- **후속**: Android `RewardAdCard`(실패 토스트 반영 완료)와 파리티 맞춰 iOS에도 실패 토스트 추가.
- **보류 사유**: iOS 보상 UX 후속 작업으로 분리.

## 5. ATT `@unknown default` 보수적 처리

- **위치**: `CashChatIOS/.../Tracking/TrackingAuthorization.swift:53-55`
- **지적**: 미래에 추가될 수 있는 ATT 상태(`@unknown default`)를 `onAuthorized()`로 처리 → 미동의 상태에서 추적 진행 우려.
- **후속**: `@unknown default`를 `showSettingsAlert`로 라우팅(거부와 동일)하는 보수적 처리 검토.
- **보류 사유**: 현재 iOS ATT는 4개 케이스로 고정되어 도달 불가(전방호환 분기).

## 6. 광고 결과 Boolean → sealed 타입 확장

- **위치**: `shared/.../ads/AdRewardStore.kt:61-67` (`runRewardFlow`의 `showAd: ... -> Boolean`)
- **지적**: 광고 결과를 `Boolean`으로 축약 → "미시청 / 준비안됨 / 닫힘" 등 구분 손실.
- **후속**: 광고 결과를 sealed/enum으로 확장하고 호출부(Android/iOS) 반영.
- **보류 사유**: 호출부 전반 변경이 필요한 heavy-lift. 한도 사전 중단(반영 완료)으로 일부 케이스는 완화됨.

## 7. 룰렛 일일 리셋 정책

- **위치**: `shared/.../roulette/FakeRouletteRepository.kt:43` (`resetAtKst` 하드코딩)
- **지적**: 일일 리셋 정책이 코드에 반영되어 있지 않음.
- **후속**: 룰렛 실 API 연동 시 서버 기준 리셋 정책 반영(리셋은 서버 책임).
- **보류 사유**: BE 미연동 구간의 Fake 스텁.

## 8. 룰렛 세그먼트 매핑 랜덤화

- **위치**: `shared/.../roulette/FakeRouletteRepository.kt:74` (`segments.first { it.prize == prize }`)
- **지적**: 동일 보상이 항상 첫 번째 칸으로 고정됨.
- **후속**: 실 BE가 `segmentIndex`를 내려주면 해소. (휠 표시는 이번 PR에서 `seg.index` 기준으로 통일 완료)
- **보류 사유**: Fake 스텁 한정 동작.

## 9. 보상량(rewardAmount) 정량 피드백

- **위치**: `shared/.../ads/AdsApi.kt` (`AdRewardQuotaDto`), 토스트 문구
- **지적**: 보상량 필드가 없어 "에너지를 충전했어요!"처럼 수치 없는 피드백만 가능("⚡+5 에너지 받았어요!" 대비 UX 손실).
- **후속**: BE에서 `AdRewardQuotaDto`에 `rewardAmount` 필드 추가 협의 → 정량 토스트 노출.
- **보류 사유**: BE 협업 필요.

## 10. plan/스펙 문서 정합성 정리

- **위치**: `docs/superpowers/plans/2026-06-21-benefit-zone-friend-invite.md:35` (Task 6), `docs/planning/be-api-requests-cc355.md:5` (§5.3)
- **지적**: plan Task 6(온보딩 추천 코드 입력)이 BE 스펙 §5.3(추천 코드는 혜택존 친구초대 화면에서만)과 모순.
- **현황**: 온보딩 추천 코드 입력 필드는 이미 `9bb84ed`에서 revert됨 → **코드와 BE 스펙은 일치**. plan 문서만 stale.
- **후속**: plan 문서 Task 6 기술을 현행(혜택존 전용)에 맞춰 업데이트.
- **보류 사유**: 코드 영향 없는 문서 정합성 작업.

---

## 별도(코드 수정 대상 아님)

- **TNK SDK Privacy 매니페스트 형식 오류** — `CashChatIOS/Frameworks/TnkRwdSdk2.xcframework/.../PrivacyInfo.xcprivacy`의 `NSPrivacyTrackingDomains`에 `https://` URL이 들어가 호스트만 허용하는 Apple 규약과 불일치. 단, 해당 파일은 **서드파티 SDK 바이너리 내부 파일**이라 직접 수정 시 서명/무결성이 깨지고 업데이트 시 덮어써짐. → **TNK 측에 리포트** 필요(우리 코드 수정 대상 아님).
