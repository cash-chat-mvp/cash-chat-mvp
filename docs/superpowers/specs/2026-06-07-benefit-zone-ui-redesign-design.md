# 혜택존 UI 개편 — 설계 문서

- 작성일: 2026-06-07
- 선행: `2026-06-07-benefit-zone-foundation-attendance-design.md`(Phase F+1, 출석 기능 구현 완료)
- 디자인 시안: `.superpowers/brainstorm/8396-*/content/final-b.html`, `hero-week.html` (브라우저 브레인스토밍 산출물, gitignore)

## 0. 배경 & 목표

Phase F+1로 출석체크가 실연동되었으나 혜택존 화면이 미완성이다:
- **Android** `BenefitZoneScreen`: 출석 위젯 + 회색 placeholder 3개(미션/광고/TNK) — 빈 박스라 어색.
- **iOS** `RewardsView`(`ContentView.swift`): 기존 **목업 미션 카드(커피 교환 progress + 출석/채팅/동영상 3개)가 그대로 남아있고** 그 위에 새 출석 위젯이 얹혀 옛 목업과 신규가 뒤섞임.

**목표:** 양 플랫폼 혜택존을 하나의 정돈된 디자인으로 통일. 출석을 **히어로(주간 7일 뷰)**로 강조하고, 미구현 섹션(미션/광고/TNK)을 출시 우선순위 순서로 **정직한 소개 카드**(가짜 데이터 없음)로 표현한다.

**비목표:** 미션/광고/TNK의 실제 기능 구현(각 후속 Phase). BE·shared 로직 변경(기존 상태로 충분).

## 1. 최종 화면 구성 (Android + iOS 공통)

세로 스크롤, 위→아래 순서:

1. **헤더** — 좌측 `혜택존` 타이틀, 우측 코인 칩 `🪙 {balance}`(연한 노랑 pill). balance는 기존 `PointsRepository.balance`(현재 `LocalPointsRepository` 잠정값).
2. **출석 히어로 카드** (보라 그라데이션 `#5C6BFA → #8466FA`, radius 22, 그림자)
   - 상단 행: `🔥 {currentStreak}일 연속 출석`(좌) + `{month}월 {weekOfMonth}주차`(우, 반투명 pill)
   - **주간 7칸 (일~토)**: 각 칸 = 요일 라벨(일·월·…·토) + 도트.
     - 완료(checkedDays에 해당 일 포함): 흰 배경 + 보라 `✓`
     - 오늘: 노란 배경(`#FFB800`) + 일(day) 숫자 + 글로우(box-shadow)
     - 미출석/미래: 반투명 흰 배경 + 일 숫자
   - 오늘 보상 박스(반투명): `🎁 오늘 보상 🪙+{nextReward.coin}` + 보너스 아이템(`📦 {itemCode} {qty}개`)
   - CTA 버튼(노랑 `#FFB800`, 풀폭, radius 99): `출석 도장 찍기`. `todayChecked`면 비활성 + 라벨 `오늘 출석 완료`.
3. **리워드 광고 카드** (흰 카드) — 아이콘 `📺` + 제목 `리워드 광고` + 배지 `곧 출시`(파랑). 설명 `광고 1회 시청 → 🪙+40 코인 · 하루 10회까지`.
4. **데일리 미션 카드** (흰 카드, 흐리게 opacity ~0.72) — `🎯` + `데일리 미션` + 배지 `준비중`(회색). 설명 `매일 바뀌는 3가지 미션을 완료하고 코인 적립`.
5. **TNK 오퍼월 카드** (흰 카드, 흐리게) — `🎮` + `TNK 오퍼월` + 배지 `준비중`. 설명 `앱 설치·설문 참여로 대량 코인 (최대 🪙+1,500)`.

**색상 토큰:** primary `#5C6BFA`, hero gradient end `#8466FA`, accent `#FFB800`, coin text `#B07C00`, 배지 곧출시 bg `#E3F0FF`/text `#2D6FE0`, 배지 준비중 bg `#F0EEF8`/text `#9A95AD`, 카드 border `#F0EEF8`.

## 2. 주간(이번 주) 뷰 로직 — UI 레이어

기존 `AttendanceUiState`가 제공하는 것으로 충분(추가 BE/Store 변경 없음):
`year, month, checkedDays: List<Int>, currentStreak, todayChecked, nextReward`.

**오늘(day) 결정:** 디바이스 로컬 날짜의 일(day-of-month)을 사용한다. (서버 KST와 미세 차이 가능하나 표시용으로 허용. 출석 처리 자체는 서버가 KST로 판정.)

**주간 7칸 계산:**
1. 오늘이 포함된 주의 일요일~토요일 7개 날짜를 구한다(로컬 캘린더 기준, 주 시작 = 일요일).
2. 각 칸에 대해:
   - 그 날짜의 **월이 현재 표시 월(`month`)과 같고** 일이 `checkedDays`에 포함 → 완료(✓).
   - 그 날짜가 오늘 → 오늘 강조(`todayChecked`면 ✓ 스타일도 함께 적용 가능).
   - 그 외 → 미출석/미래(반투명).
3. **월 경계 단순화(MVP 허용):** 이번 주가 두 달에 걸치면, 현재 표시 월이 아닌 칸은 `checkedDays`로 상태를 알 수 없으므로 **중립(번호만)**으로 표시한다. 정확한 cross-month 출석 표시는 후속 개선 과제.

**주차(`weekOfMonth`):** 표시용. 오늘 날짜 기준 `(day-of-month - 1) / 7 + 1` 정도의 단순 계산 또는 캘린더 주차. 정확도보다 표시 일관성 우선.

> 플랫폼별 날짜 계산: Android는 `java.util.Calendar`(또는 코어 라이브러리 desugaring 없이 안전한 `Calendar`)로 주 범위 계산 — `java.time` 사용 시 desugaring 미설정이라 런타임 크래시 위험(Phase 1 경험), **`java.util.Calendar` 사용**. iOS는 `Calendar.current`.

## 3. 미구현 카드 상호작용

- 세 카드 모두 현재 비기능(Phase 2~4). 가짜 수치/리스트 노출 금지 — 한 줄 소개 + 배지만.
- 탭 동작: 가벼운 토스트 `곧 만나요!` 노출(선택적, 저비용). 비활성 비주얼(흐림) 유지.
- 각 카드는 향후 Phase에서 실제 컴포넌트로 교체될 자리표시 — 구조적으로 독립 컴포넌트로 분리해 교체 용이하게.

## 4. 변경 범위 (파일)

### Android (`apps/frontend/app`)
- `feature/rewards/AttendanceWidget.kt` — 월 전체 도트 그리드 → **주간 7칸 히어로**로 개편(보라 그라데이션, 요일 라벨, streak 배지, 보상 박스, CTA). 주간 계산 헬퍼 포함.
- `feature/rewards/BenefitZoneScreen.kt` — 헤더(코인 칩 스타일) + 히어로 + 3개 소개 카드로 재구성. 기존 회색 `PhasePlaceholder` → `BenefitInfoCard`(아이콘/제목/배지/설명) 컴포넌트로 교체. 미구현 카드 탭 토스트(선택).
- (신규 작은 컴포넌트 파일 분리 가능: `BenefitInfoCard.kt` — 단일 책임)

### iOS (`apps/frontend/CashChatIOS`)
- `BenefitZone/AttendanceViewModel.swift`의 `AttendanceWidgetView` — **주간 7칸 히어로**로 개편(동일 디자인). 주간 계산은 `Calendar.current`.
- `ContentView.swift`의 `RewardsView` — **기존 목업 전면 제거**: `missions` 배열, `claimedIDs`, 커피 교환 progress 카드, mission `ForEach`, `targetPoints` 등 삭제. 새 구성으로 교체: 헤더(혜택존+코인) + `AttendanceWidgetView` + 3개 소개 카드(`BenefitInfoCardView` 신규, 신규 파일은 Xcode 타깃 멤버십 필요).
- 코인 잔액: `AttendanceViewModel.balance` 사용(이미 노출). `appState.points`와 별개(잔재 정리 주의 — 헤더는 ViewModel balance로 통일).

### shared / BE
- **변경 없음.** 기존 `AttendanceStore`/`AttendanceUiState`/`PointsRepository`로 충분.

## 5. 검증

- **Android:** `./gradlew :app:assembleDebug` 빌드 성공 + 수동(출석 탭: 주간 뷰 렌더, 오늘 강조, 출석 도장→완료 전환, 미구현 카드 탭 토스트).
- **iOS:** 사용자 Xcode 빌드(신규 카드 파일 타깃 멤버십 추가 → `embedAndSignAppleFrameworkForXcode` → 빌드) + 런타임. 기존 목업이 사라지고 새 레이아웃이 보이는지 확인.
- shared 단위 테스트는 영향 없음(로직 불변)이나 회귀 확인차 `:shared:testDebugUnitTest` 1회 실행.

## 6. 위험 / 미결

- **월 경계 주간뷰**: cross-month 칸 중립 표시(단순화). 사용자 혼선 적으나 추후 정확화 가능.
- **iOS 신규 파일 멤버십**: `BenefitInfoCardView` 새 Swift 파일은 pbxproj 타깃 추가 필요(헤드리스 불가) — 사용자 Xcode 단계.
- **코인 잔액 이원화**: 헤더는 `PointsRepository`(Long), 기존 chat/shop은 `PointsStore`(Int). 본 개편은 혜택존 헤더만 다룸. 통합은 `GET /api/points/me` BE 준비 후 별도 과제.
- 디바이스 로컬 날짜 vs 서버 KST 미세 차이(표시용 허용).

## 7. 구현 단위

단일 implementation plan으로 진행(Android UI 개편 → iOS UI 개편 → 검증). shared/BE 무변경이라 범위가 작고 명확.
