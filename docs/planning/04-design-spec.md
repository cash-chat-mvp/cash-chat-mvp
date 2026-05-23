# 04. Design Spec — Figma Dev Mode 화면 스펙 시트

> 작성일: 2026-05-17  
> 상태: Draft  
> 대상: Figma Dev Mode, Android Compose (Material3), KMM

---

## 0. 글로벌 디자인 토큰

### 0.1 Color Tokens

| Token | HEX | Usage |
|-------|-----|-------|
| `color/primary` | `#6B4EFF` | 주요 액션, 헤더 강조, CTA 버튼 |
| `color/primary-container` | `#E8E1FF` | 내 채팅 버블, 선택 상태 배경 |
| `color/on-primary` | `#FFFFFF` | Primary 위 텍스트 |
| `color/on-primary-container` | `#210089` | Primary Container 위 텍스트 |
| `color/secondary` | `#FFB800` | 코인 아이콘, 보상 배지, 강조 수치 |
| `color/secondary-container` | `#FFF0B3` | 보상 카드 배경, 출석 체크 셀 활성 |
| `color/on-secondary` | `#1A1300` | Secondary 위 텍스트 |
| `color/tertiary` | `#00C9B1` | 성공 상태, 쿠팡 카드 포인트 |
| `color/tertiary-container` | `#BBFFF4` | 성공 스낵바 배경 |
| `color/error` | `#FF4545` | 진화 실패, 에러 상태 |
| `color/error-container` | `#FFDAD6` | 진화 실패 오버레이 배경 |
| `color/background` | `#FAFBFF` | 앱 배경 |
| `color/surface` | `#FFFFFF` | 카드, 시트, 다이얼로그 배경 |
| `color/surface-variant` | `#F2EFFF` | 입력 필드 배경, 세그먼트 탭 배경 |
| `color/surface-container-high` | `#E8E5F5` | 메시지 입력창 배경 |
| `color/outline` | `#C8C4D4` | 구분선, 카드 테두리 |
| `color/outline-variant` | `#E8E4F5` | 연한 구분선 |
| `color/on-surface` | `#1B1B2A` | 기본 텍스트 |
| `color/on-surface-variant` | `#47465A` | 보조 텍스트, placeholder |
| `color/scrim` | `#00000066` | 모달 딤 레이어 (40% 불투명) |
| `color/ad-gate-blur-tint` | `#6B4EFF26` | Ad Gate 블러 오버레이 틴트 (15% 불투명) |

#### 다크 테마 (선택적 Phase 2)
| Token | HEX |
|-------|-----|
| `color/background` | `#0F0F18` |
| `color/surface` | `#1A1A27` |
| `color/surface-variant` | `#252538` |
| `color/on-surface` | `#E6E1FF` |

---

### 0.2 Typography Tokens

Material3 TypeScale 기준. 폰트: `Pretendard` (Android), `Apple SD Gothic Neo` (iOS fallback)

| Token | Weight | Size | LineHeight | LetterSpacing | Usage |
|-------|--------|------|------------|---------------|-------|
| `type/display-large` | 700 | 57sp | 64sp | -0.25sp | 진화 단계 번호 (슬롯 머신) |
| `type/display-medium` | 700 | 45sp | 52sp | 0 | 이벤트 배너 타이틀 |
| `type/headline-large` | 700 | 32sp | 40sp | 0 | 화면 타이틀 |
| `type/headline-medium` | 600 | 28sp | 36sp | 0 | 섹션 헤더 |
| `type/headline-small` | 600 | 24sp | 32sp | 0 | 카드 타이틀 |
| `type/title-large` | 600 | 22sp | 28sp | 0 | 탭 선택 텍스트, 모달 타이틀 |
| `type/title-medium` | 500 | 16sp | 24sp | 0.15sp | 버튼 텍스트, 아이템 이름 |
| `type/title-small` | 500 | 14sp | 20sp | 0.1sp | 채팅 버블 시간 레이블 |
| `type/body-large` | 400 | 16sp | 24sp | 0.5sp | 채팅 버블 본문, AI 응답 |
| `type/body-medium` | 400 | 14sp | 20sp | 0.25sp | 보조 설명, 카드 본문 |
| `type/body-small` | 400 | 12sp | 16sp | 0.4sp | 타임스탬프, 주석 |
| `type/label-large` | 500 | 14sp | 20sp | 0.1sp | 칩, 배지 |
| `type/label-medium` | 500 | 12sp | 16sp | 0.5sp | 버튼 소형, 탭 레이블 |
| `type/label-small` | 500 | 11sp | 16sp | 0.5sp | 법적고지 텍스트, 미세 레이블 |

---

### 0.3 Spacing & Grid

| Token | Value | Usage |
|-------|-------|-------|
| `space/4` | 4dp | 아이콘-텍스트 간격 |
| `space/8` | 8dp | 인라인 요소 간격 |
| `space/12` | 12dp | 버블 내부 패딩 |
| `space/16` | 16dp | 카드 패딩, 섹션 내부 여백 |
| `space/20` | 20dp | 섹션 헤더 하단 여백 |
| `space/24` | 24dp | 섹션 간 구분 여백 |
| `space/32` | 32dp | 화면 상단/하단 여백 |
| `space/screen-h` | 16dp | 좌우 화면 여백 (horizontal padding) |

---

### 0.4 Shape Tokens

| Token | Radius | Usage |
|-------|--------|-------|
| `shape/none` | 0dp | 전체 화면 컨테이너 |
| `shape/extra-small` | 4dp | 배지, 작은 칩 |
| `shape/small` | 8dp | 버튼 소형, 스낵바 |
| `shape/medium` | 12dp | 카드, 채팅 버블 (꼬리 반대편) |
| `shape/large` | 16dp | 버텀 시트, 다이얼로그 |
| `shape/extra-large` | 28dp | FAB, 주요 CTA 버튼 |
| `shape/full` | 50% | 아바타, 코인 아이콘 |

---

### 0.5 Elevation & Shadow

| Level | dp | Shadow Color | Usage |
|-------|----|-------------|-------|
| `elevation/0` | 0dp | — | 기본 Surface |
| `elevation/1` | 1dp | `#0000001A` | BottomNav, 구분 필요한 카드 |
| `elevation/2` | 3dp | `#00000026` | 캐릭터 헤더 카드, 시트 |
| `elevation/3` | 6dp | `#0000003D` | 플로팅 버튼, 팝업 |
| `elevation/4` | 8dp | `#00000052` | 모달 다이얼로그 |

---

### 0.6 Motion Tokens

| Token | Duration | Easing | Usage |
|-------|----------|--------|-------|
| `motion/short1` | 50ms | LinearOutSlowIn | 미세 상태 변화 |
| `motion/short2` | 100ms | FastOutLinearIn | 리플, 탭 피드백 |
| `motion/medium1` | 200ms | FastOutSlowIn | 버튼, 칩 전환 |
| `motion/medium2` | 300ms | EmphasizedDecelerate | 카드 expand, 스낵바 |
| `motion/long1` | 400ms | EmphasizedDecelerate | 화면 전환, 모달 진입 |
| `motion/long2` | 500ms | EmphasizedDecelerate | 진화 결과 리빌 애니메이션 |
| `motion/extra-long` | 700ms~1200ms | 커스텀 스프링 | 슬롯 머신, 블라인드 해제 ripple |

---

## 1. Tab 01 — Chat 탭

### 1.1 ChatScreen (메인 채팅 화면)

```
┌─ StatusBar (System) ──────────────────────────┐
│  [← 뒤로] Cash Chat            [설정⚙] [검색🔍] │  ← TopAppBar
├───────────────────────────────────────────────┤
│  ┌─── CharacterHeaderCard ───────────────────┐ │
│  │  [캐릭터 아바타 72×72]  이름: 미래     Lv.3 │ │
│  │  ████████████░░  EXP 420/600             │ │
│  │  🪙 1,250                  [강화하기 →]  │ │
│  └───────────────────────────────────────────┘ │
├───────────────────────────────────────────────┤
│                                               │
│  [AI 버블]  안녕하세요! 오늘...  10:32 AM     │
│                                               │
│        [내 버블]  안녕, 요즘 사고 싶은게..  │
│                                    10:33 AM   │
│  [AI 버블]  쿠팡에서 비슷한 제품...  10:33    │
│  ┌──── CoupangProductCard ────────────────┐   │
│  │  [상품이미지 56×56]  상품명 2줄까지     │   │
│  │  ₩29,900  ★4.5 (1.2만개 리뷰)  [구매]  │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  ┌──── AdGateBlindCard ───────────────────┐   │  ← Ad Gate 발동 시
│  │  [AI] 이 문제의 핵심은 사실...          │   │
│  │  ████████████████████████ (blur)       │   │
│  │  ████████████████                      │   │
│  │  ┌─────────────────────────────────┐   │   │
│  │  │ 🔓 광고 1회 시청 후 답변 전체 보기 │   │   │
│  │  │     [▶ 광고 보기 (+30코인)]       │   │
│  │  └─────────────────────────────────┘   │   │
│  └────────────────────────────────────────┘   │
│                                               │
├───────────────────────────────────────────────┤
│  ┌── MessageInputBar ────────────────────────┐ │
│  │  [메시지 입력...          ] [전송↑]       │ │
│  └───────────────────────────────────────────┘ │
├───────────────────────────────────────────────┤
│  [Chat🗨] [혜택존🎁] [상점🛒] [마이👤]         │  ← BottomNav
└───────────────────────────────────────────────┘
```

#### CharacterHeaderCard

| 속성 | 값 |
|------|-----|
| 높이 | 96dp |
| 배경 | `color/surface`, elevation 2 |
| 패딩 (전체) | 16dp |
| 모서리 | `shape/large` (16dp) 하단만 |
| Avatar 크기 | 72×72dp, `shape/full` |
| Avatar 테두리 | 3dp, `color/primary` |
| 레벨 배지 | `type/label-large`, `color/primary-container` bg, `shape/extra-small` |
| EXP 바 높이 | 8dp, `shape/full` |
| EXP 바 활성 | `color/primary` (gradient: `#6B4EFF` → `#9B7FFF`) |
| EXP 바 비활성 | `color/outline-variant` |
| 코인 아이콘 | 20dp, `color/secondary` |
| 코인 텍스트 | `type/title-medium`, `color/secondary` |
| [강화하기] 버튼 | `type/label-large`, `color/primary` text, outlined, `shape/extra-small` |

#### ChatBubble — AI (Left)

| 속성 | 값 |
|------|-----|
| 최대 너비 | `화면너비 × 0.75` |
| 배경 | `color/surface-variant` |
| 모서리 | TL=4dp, TR=12dp, BR=12dp, BL=12dp |
| 패딩 | 12dp H, 10dp V |
| 텍스트 | `type/body-large`, `color/on-surface` |
| 타임스탬프 | `type/body-small`, `color/on-surface-variant` |
| 아바타 크기 | 32×32dp (버블 왼쪽) |
| 아바타-버블 간격 | 8dp |

#### ChatBubble — User (Right)

| 속성 | 값 |
|------|-----|
| 최대 너비 | `화면너비 × 0.75` |
| 배경 | `color/primary-container` |
| 모서리 | TL=12dp, TR=4dp, BR=12dp, BL=12dp |
| 패딩 | 12dp H, 10dp V |
| 텍스트 | `type/body-large`, `color/on-primary-container` |
| 타임스탬프 | `type/body-small`, `color/on-surface-variant` |

#### MessageInputBar

| 속성 | 값 |
|------|-----|
| 높이 | 최소 56dp |
| 배경 | `color/surface-container-high` |
| TextField 배경 | `color/surface-variant`, `shape/extra-large` |
| TextField 패딩 H | 16dp |
| TextField 텍스트 | `type/body-large` |
| 전송 버튼 | 40×40dp, `color/primary` bg, `shape/full`, 아이콘 24dp 흰색 |
| 전송 버튼 비활성 | `color/outline` bg |

---

### 1.2 AdGateBlindCard (Progressive Ad Gate UX)

```
┌─ AdGateBlindCard ─────────────────────────────┐
│  [AI 아바타 32dp]  미래                         │
│                                                │
│  이 문제의 핵심은 사실 수요와 공급의...          │  ← teaser (80자)
│                                                │
│  ┌── BlurLayer ─────────────────────────────┐  │
│  │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │  │  ← blurRadius: 20dp
│  │ ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░        │  │  ← tint: color/ad-gate-blur-tint
│  │ ░░░░░░░░░░░░░░░                            │  │
│  │                                            │  │
│  │  ┌── AdGateCTA ──────────────────────┐    │  │
│  │  │  🔓  답변 전체 보기               │    │  │
│  │  │  광고 1회 시청하면 코인도 +30      │    │  │
│  │  │  ┌────────────────────────────┐  │    │  │
│  │  │  │  ▶  광고 보기              │  │    │  │
│  │  │  └────────────────────────────┘  │    │  │
│  │  └───────────────────────────────────┘    │  │
│  └────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────┘
```

| 속성 | 값 |
|------|-----|
| Blur 구현 | Compose `graphicsLayer { renderEffect = BlurEffect(20f, 20f) }` |
| Blur tint | `color/ad-gate-blur-tint` (#6B4EFF @ 15% opacity) 오버레이 |
| Teaser 길이 | 80자 (Remote Config: `AD_GATE_TEASER_CHARS`) |
| Teaser 텍스트 | `type/body-large`, fade-out gradient 마지막 2줄 |
| AdGateCTA 배경 | `color/surface` @ 90% opacity, `shape/large`, 16dp 패딩 |
| AdGateCTA 아이콘 | 🔓 24dp |
| AdGateCTA 제목 | `type/title-medium`, `color/on-surface` |
| AdGateCTA 부제 | `type/body-small`, `color/secondary` (코인 수치 강조) |
| [광고 보기] 버튼 | 56dp 높이, `color/primary` bg, `shape/extra-large`, `type/title-medium` 흰색 |
| 해제 애니메이션 | blur 0으로 700ms `motion/extra-long`, ripple 효과 중심 → 외곽 |

---

### 1.3 CoupangProductCard

```
┌─ CoupangProductCard ──────────────────────────┐
│  [이미지     ]  삼성 갤럭시 버즈3 프로           │
│  [56×56dp   ]  블루투스 이어폰 ANC 탑재...      │
│                ₩ 149,000  ★4.7 (3.2만 리뷰)    │
│                [쿠팡에서 보기 →]               │
│  ⓘ 이 링크는 파트너스 활동으로 수수료가...      │  ← 법적고지
└───────────────────────────────────────────────┘
```

| 속성 | 값 |
|------|-----|
| 카드 배경 | `color/surface`, `shape/medium`, elevation 1 |
| 카드 패딩 | 12dp |
| 이미지 크기 | 56×56dp, `shape/small` |
| 이미지-콘텐츠 간격 | 12dp |
| 상품명 | `type/body-medium` Bold, 최대 2줄 |
| 가격 | `type/title-medium`, `color/error` |
| 별점 | 12dp star 아이콘, `color/secondary` |
| 리뷰수 | `type/body-small`, `color/on-surface-variant` |
| [쿠팡에서 보기] | `type/label-large`, `color/tertiary`, 텍스트 버튼 |
| 법적고지 | `type/label-small`, `color/on-surface-variant` @ 70% |
| 법적고지 아이콘 | ⓘ 12dp, 동일 색상 |

---

### 1.4 EvolutionSubScreen (모달 풀스크린)

캐릭터 헤더 [강화하기] 탭 시 진입. `ModalBottomSheet` 또는 `FullScreenDialog`

```
┌─ EvolutionSubScreen ──────────────────────────┐
│  ╳                         강화 · 진화          │  ← TopBar
├───────────────────────────────────────────────┤
│                                               │
│         ┌── 캐릭터 스테이지 뷰 ──┐             │
│         │  [캐릭터 이미지 160dp] │             │
│         │   ✨ 반짝이 파티클     │             │
│         └────────────────────────┘             │
│                                               │
│   Lv.3  알→병아리→청년→성인→궁극              │  ← 스텝 인디케이터
│   ●  ●  ●○  ○  ○                               │
│                                               │
│   EXP  420 / 600  ████████░░  (70%)           │
│   🪙 보유 코인: 1,250                          │
│   📦 진화석: 2개 보유                          │
│                                               │
│  ┌─── EvolutionAttemptCard ───────────────┐   │
│  │  이번 시도 성공확률: 55%               │   │
│  │  ─────────────────────                 │   │
│  │  진화석 1개 소모 (200코인 상당)        │   │
│  │                                        │   │
│  │  [확률 부적 사용 +10%p]  [보호권 사용] │   │
│  │                                        │   │
│  │  ┌──────────────────────────────────┐  │   │
│  │  │       🎰 진화 시도하기           │  │   │
│  │  └──────────────────────────────────┘  │   │
│  └─────────────────────────────────────────┘   │
│                                               │
│  [🛒 재료 더 구하기]  [⚡ 코인 충전하기]      │
└───────────────────────────────────────────────┘
```

#### EvolutionStepIndicator

| 속성 | 값 |
|------|-----|
| 완료 단계 도트 | 12dp, `color/primary` |
| 현재 단계 도트 | 16dp (강조), `color/primary`, 테두리 4dp `color/primary-container` |
| 미완 단계 도트 | 12dp, `color/outline` |
| 도트 간격 | 16dp |
| 연결선 | 2dp, 완료:`color/primary`, 미완:`color/outline-variant` |

#### EvolutionAttemptCard

| 속성 | 값 |
|------|-----|
| 배경 | `color/surface-variant`, `shape/large` |
| 패딩 | 20dp |
| 성공확률 텍스트 | `type/headline-small`, `color/primary` |
| 구분선 | 1dp, `color/outline-variant` |
| 소모 정보 | `type/body-medium`, `color/on-surface-variant` |
| 부적/보호권 칩 | `shape/small`, outlined `color/primary`, `type/label-large` |
| [진화 시도] 버튼 | 56dp 높이, full width, `color/primary` bg → gradient, `shape/extra-large` |
| 버튼 비활성 (재료 부족) | `color/surface-variant` bg, `color/outline` text |

#### SlotMachineAnimation (진화 시도 후)

| 속성 | 값 |
|------|-----|
| 등장 | 버튼 탭 → 화면 중앙 오버레이 `color/scrim` |
| 드럼 이미지 크기 | 3개 드럼 각 80×80dp, 간격 8dp |
| 회전 속도 | 초기 60ms/frame, 감속 curve 800ms |
| 성공 결과 | 파티클 폭발 (200파티클, 300ms), 진동 (HapticFeedback.LONG_PRESS) |
| 실패 결과 | 화면 흔들기 (ShakeAnimation, 4회, 300ms), 에러 색상 플래시 |
| 진화 성공 시 | 캐릭터 이미지 cross-fade (500ms) → 새 단계 이미지 |

---

## 2. Tab 02 — 혜택존 탭

### 2.1 RewardsZoneScreen

```
┌─ StatusBar ───────────────────────────────────┐
│  혜택존                          🪙 1,250      │  ← TopAppBar
├───────────────────────────────────────────────┤
│  ┌── AttendanceWidget ────────────────────┐   │  ← 항상 상단 고정
│  │  5월 출석체크                          │   │
│  │  ●●●●●●●○○○○○○○ (7 / 31)             │   │
│  │  오늘 보상: 🪙+30  📦진화석×1          │   │
│  │  [출석 도장 찍기  ✓]                   │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  ┌── DailyMissionCard ───────────────────┐    │
│  │  데일리 미션        [🔄 새로고침]      │    │
│  │  ☐  채팅 10회 보내기      +50🪙       │    │
│  │  ☐  친구 1명 초대         +200🪙      │    │
│  │  ☑  진화 시도 1회  ✓      +30🪙       │    │
│  └────────────────────────────────────────┘   │
│                                               │
│  ┌── RewardAdWidget ─────────────────────┐    │
│  │  리워드 광고  (오늘 8 / 10 남음)       │    │
│  │  광고 1회 시청 → 🪙+40                 │    │
│  │  [▶ 지금 시청하기]                    │    │
│  └────────────────────────────────────────┘   │
│                                               │
│  ─────── TNK 오퍼월 ─────────────────────     │
│  ┌── TnkOfferwallEmbed ──────────────────┐    │
│  │  (TNK SDK WebView / NativeView)       │    │
│  │  앱 설치 보상  +1,500🪙  [참여하기]    │    │
│  │  게임 가입     +800🪙   [참여하기]     │    │
│  │  설문 참여     +300🪙   [참여하기]     │    │
│  └────────────────────────────────────────┘   │
├───────────────────────────────────────────────┤
│  [Chat🗨] [혜택존🎁] [상점🛒] [마이👤]         │
└───────────────────────────────────────────────┘
```

#### AttendanceWidget

| 속성 | 값 |
|------|-----|
| 배경 | `color/primary-container` → `#E8E1FF` → `#FAFBFF` (수직 gradient) |
| 모서리 | `shape/large` |
| 패딩 | 16dp |
| 날짜 도트 크기 | 28×28dp |
| 출석 완료 도트 | `color/primary` bg, 체크 아이콘 (white, 14dp) |
| 오늘 도트 | `color/secondary` bg, 날짜 숫자 `color/on-secondary` |
| 미출석 도트 | `color/outline-variant` bg, 날짜 숫자 `color/on-surface-variant` |
| 보상 텍스트 | `type/body-medium`, 코인=`color/secondary` bold, 아이템=`color/primary` bold |
| [출석 도장] 버튼 | 48dp 높이, `color/primary` bg, `shape/extra-large` |
| 완료 후 버튼 | `color/tertiary` bg, 체크 아이콘 + "완료" 텍스트 |

#### DailyMissionCard

| 속성 | 값 |
|------|-----|
| 배경 | `color/surface`, `shape/large`, elevation 1 |
| 패딩 | 16dp |
| 헤더 | `type/title-large`, `color/on-surface` |
| [새로고침] | `type/label-large`, `color/primary`, 아이콘 버튼 |
| 미션 행 높이 | 48dp |
| 체크박스 | 20dp, Material3 Checkbox, `color/primary` |
| 완료 행 | 텍스트 `color/on-surface-variant`, strikethrough |
| 보상 | `type/label-large`, `color/secondary` bold |
| 행 구분 | 0.5dp `color/outline-variant` |

#### RewardAdWidget

| 속성 | 값 |
|------|-----|
| 배경 | `color/secondary-container`, `shape/large` |
| 패딩 | 16dp |
| 광고 횟수 | `type/body-medium`, 남은횟수=`color/secondary` bold |
| 보상 텍스트 | `type/body-medium`, `color/on-surface` |
| [지금 시청] 버튼 | 48dp, full width, `color/secondary` bg, `color/on-secondary` text, `shape/extra-large` |
| 횟수 소진 시 버튼 | `color/surface-variant` bg, `color/on-surface-variant` text, 비활성 |

---

## 3. Tab 03 — 상점 탭

### 3.1 ShopScreen

```
┌─ StatusBar ───────────────────────────────────┐
│  상점                            🪙 1,250      │  ← TopAppBar
├───────────────────────────────────────────────┤
│  [강화재료]  [외형]  [교환권]                  │  ← SegmentedButton
├───────────────────────────────────────────────┤
│                                               │
│  ─── 강화재료 탭 ────────────────────────     │
│  ┌── ShopItemCard ────────────────────────┐   │
│  │  ⏰ 한정 24H   패키지 특가!             │   │  ← FOMO 배지
│  │  ┌─[아이콘]─┐  강화 패키지              │   │
│  │  │ 64×64dp │  석5개 + 확률 부적1       │   │
│  │  └──────────┘  🪙 1,200  (정가 1,500)  │   │
│  │  보유: 0개          [구매하기]          │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  ┌── ShopItemCard ────────────────────────┐   │
│  │  [아이콘 64dp]  진화석 ×1              │   │
│  │                🪙 200                  │   │
│  │  보유: 2개      [구매하기]             │   │
│  └────────────────────────────────────────┘   │
│  ┌── ShopItemCard ────────────────────────┐   │
│  │  BEST  [아이콘]  진화석 ×5 (10%↓)     │   │
│  │                🪙 900  (정가 1,000)    │   │
│  │  보유: 0개      [구매하기]             │   │
│  └────────────────────────────────────────┘   │
│  (확률 부적, 보호권 카드 동일 패턴)            │
│                                               │
├───────────────────────────────────────────────┤
│  코인 부족? [🎁 혜택존에서 코인 벌기]          │  ← sticky footer
├───────────────────────────────────────────────┤
│  [Chat🗨] [혜택존🎁] [상점🛒] [마이👤]         │
└───────────────────────────────────────────────┘
```

#### SegmentedButton (탭)

| 속성 | 값 |
|------|-----|
| 높이 | 40dp |
| 배경 | `color/surface-variant`, `shape/extra-large` |
| 선택 세그먼트 | `color/primary-container` bg, `color/primary` text, `type/label-large` |
| 비선택 세그먼트 | transparent bg, `color/on-surface-variant` text, `type/label-large` |
| 외형 탭 (Phase 2) | `color/on-surface-variant @ 40%` (비활성) |

#### ShopItemCard

| 속성 | 값 |
|------|-----|
| 배경 | `color/surface`, `shape/large`, elevation 1 |
| 패딩 | 16dp |
| 아이콘 크기 | 64×64dp, `shape/medium`, `color/surface-variant` bg |
| 아이콘-콘텐츠 간격 | 12dp |
| 상품명 | `type/title-medium`, `color/on-surface` |
| 상품 설명 | `type/body-small`, `color/on-surface-variant`, 최대 1줄 |
| 가격 | `type/title-large`, `color/primary` |
| 정가 (할인 시) | `type/body-small`, strikethrough, `color/on-surface-variant` |
| 보유수량 | `type/label-medium`, `color/on-surface-variant` |
| [구매] 버튼 | 40dp 높이, `color/primary` outlined, `shape/small` |
| FOMO 배지 | `color/error` bg, `color/surface` text, `shape/extra-small`, `type/label-small` |
| BEST 배지 | `color/secondary` bg, `color/on-secondary` text |

---

### 3.2 NaverPayVoucherScreen (교환권 탭)

```
┌─ 교환권 탭 ────────────────────────────────────┐
│                                               │
│  ┌── BalanceSummaryCard ──────────────────┐   │
│  │  현재 보유 코인          🪙 12,500     │   │
│  │  ≈ 11,750원 ~ 12,375원 예상             │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  교환 단위 선택                               │
│  ┌── VoucherOptionCard (선택됨) ──────────┐   │
│  │  ✓  5,000코인 → 네이버페이 4,500원      │   │
│  │     (수수료 10% 적용)                   │   │
│  └────────────────────────────────────────┘   │
│  ┌── VoucherOptionCard ───────────────────┐   │
│  │     10,000코인 → 9,500원 (할증 혜택)   │   │
│  └────────────────────────────────────────┘   │
│  ┌── VoucherOptionCard ───────────────────┐   │
│  │     30,000코인 → 29,000원              │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  ⓘ 최초 교환 시 본인인증 1회 필요             │
│  ⓘ 일 1만원 / 월 5만원 한도                   │
│                                               │
│  ┌──────────────────────────────────────────┐ │
│  │       교환 신청하기                      │ │
│  └──────────────────────────────────────────┘ │
│                                               │
│  교환 내역                                    │
│  2026-05-15  5,000코인 → 4,500원  완료 ✓     │
│  2026-05-01  5,000코인 → 4,500원  완료 ✓     │
└───────────────────────────────────────────────┘
```

#### VoucherOptionCard

| 속성 | 값 |
|------|-----|
| 배경 (비선택) | `color/surface`, `shape/large`, 1dp border `color/outline` |
| 배경 (선택) | `color/primary-container`, `shape/large`, 2dp border `color/primary` |
| 패딩 | 16dp |
| 체크 아이콘 | 24dp, `color/primary` (선택 시만 표시) |
| 교환 텍스트 | `type/title-medium`, `color/on-surface` |
| 할인/혜택 텍스트 | `type/body-small`, `color/tertiary` |
| 선택 전환 | `motion/medium1` 200ms, background/border 색상 전환 |

#### BalanceSummaryCard

| 속성 | 값 |
|------|-----|
| 배경 | `color/primary-container` |
| 모서리 | `shape/large` |
| 패딩 | 20dp |
| 코인 수 | `type/display-medium`, `color/primary` |
| 예상 원화 | `type/body-medium`, `color/on-primary-container` |

#### KYC 바텀시트 (최초 교환 시)

```
┌─ KYC BottomSheet ─────────────────────────────┐
│  ─── 본인 인증 ─────────────────────────────  │
│                                               │
│  교환 서비스 이용을 위해 한 번만 인증합니다    │
│                                               │
│  [이메일 인증]                                │
│  [────────────────────────────]  [인증코드전송] │
│                                               │
│  [인증 완료하기]                              │
└───────────────────────────────────────────────┘
```

| 속성 | 값 |
|------|-----|
| 시트 모서리 | `shape/extra-large` (TL, TR만 28dp) |
| 배경 | `color/surface` |
| 드래그 핸들 | 32×4dp, `color/outline`, 중앙 정렬 |
| 타이틀 | `type/title-large` |
| 설명 | `type/body-medium`, `color/on-surface-variant` |
| 입력 필드 | `OutlinedTextField`, 기본 Material3 |

---

## 4. Tab 04 — 마이 탭

### 4.1 MyPageScreen

```
┌─ StatusBar ───────────────────────────────────┐
│  마이페이지                    [설정⚙]         │
├───────────────────────────────────────────────┤
│  ┌── ProfileCard ─────────────────────────┐   │
│  │  [프로필 이미지 64dp]  홍길동           │   │
│  │                        honggildong@... │   │
│  │  가입일: 2026-04-12     Lv.3 미래      │   │
│  └────────────────────────────────────────┘   │
│                                               │
│  ┌── StatsRow ───────────────────────────┐    │
│  │  🪙 누적 획득    💬 총 대화     🎯 연속출석  │
│  │  48,200코인      1,240회        7일          │
│  └────────────────────────────────────────┘   │
│                                               │
│  계정                                         │
│  ─────────────────────────────────────────    │
│  교환 내역                           >        │
│  알림 설정                           >        │
│  계정 연결 (Google)                  >        │
│  ─────────────────────────────────────────    │
│  고객센터                            >        │
│  이용약관                            >        │
│  개인정보처리방침                    >        │
│  ─────────────────────────────────────────    │
│  [로그아웃]                                   │
│  [회원탈퇴]                                   │
└───────────────────────────────────────────────┘
```

#### ProfileCard

| 속성 | 값 |
|------|-----|
| 배경 | `color/surface-variant`, `shape/large` |
| 패딩 | 16dp |
| 프로필 이미지 | 64×64dp, `shape/full`, 구글 프로필 또는 캐릭터 아바타 |
| 이름 | `type/title-large`, `color/on-surface` |
| 이메일 | `type/body-small`, `color/on-surface-variant` |
| 레벨 배지 | `color/primary-container` bg, `type/label-medium`, `shape/extra-small` |

#### StatsRow

| 속성 | 값 |
|------|-----|
| 배경 | `color/surface`, `shape/large`, elevation 1 |
| 패딩 | 16dp 전체, 8dp 사이 간격 |
| 각 스탯 | 아이콘 24dp + 수치 `type/headline-small` + 레이블 `type/body-small` |
| 구분선 | 1dp vertical, `color/outline-variant` |

#### 메뉴 리스트

| 속성 | 값 |
|------|-----|
| 행 높이 | 52dp |
| 텍스트 | `type/body-large`, `color/on-surface` |
| 화살표 아이콘 | 20dp, `color/on-surface-variant` |
| 구분선 | 0.5dp, `color/outline-variant` |
| 위험 액션 (로그아웃, 탈퇴) | `color/error` 텍스트 |

---

## 5. BottomNavigationBar

| 속성 | 값 |
|------|-----|
| 높이 | 80dp (아이콘 영역 56dp + 하단 Safe Area) |
| 배경 | `color/surface`, 상단 1dp border `color/outline-variant` |
| 선택 아이콘 | 24dp, `color/primary` |
| 선택 레이블 | `type/label-medium`, `color/primary` |
| 선택 인디케이터 | 64×32dp pill, `color/secondary-container` |
| 비선택 아이콘 | 24dp, `color/on-surface-variant` |
| 비선택 레이블 | `type/label-medium`, `color/on-surface-variant` |
| 배지 (미션 완료 등) | 8dp dot, `color/error`, 아이콘 우상단 |

---

## 6. 공통 컴포넌트

### 6.1 CoinBadge (코인 표시)

| 속성 | 값 |
|------|-----|
| 코인 아이콘 | 20dp, `color/secondary` |
| 수치 | `type/title-medium`, `color/secondary` |
| 배경 (헤더용) | `color/secondary-container`, `shape/extra-large`, 8dp H 패딩 |

### 6.2 LoadingState

| 속성 | 값 |
|------|-----|
| AI 응답 중 | 3dot 파동 애니메이션 (크기 8dp, 간격 4dp) |
| 색상 | `color/primary` |
| 버블 배경 | `color/surface-variant` (동일 ChatBubble 스타일) |

### 6.3 EmptyState

| 속성 | 값 |
|------|-----|
| 일러스트 | 120×120dp 중앙 정렬 |
| 타이틀 | `type/title-large`, 중앙 정렬 |
| 부제 | `type/body-medium`, `color/on-surface-variant`, 중앙 정렬 |
| CTA 버튼 | `color/primary` filled, `shape/extra-large` |

### 6.4 SnackBar (보상 토스트)

| 속성 | 값 |
|------|-----|
| 배경 | `color/primary` (코인 획득) / `color/tertiary` (성공) / `color/error-container` (실패) |
| 텍스트 | `type/body-medium`, 흰색 또는 `color/on-surface` |
| 아이콘 | 20dp |
| 높이 | 48dp |
| 모서리 | `shape/small` |
| 위치 | 화면 하단 BottomNav 위 8dp |
| 진입 | 하단에서 슬라이드업 `motion/medium2` |
| 자동 사라짐 | 3000ms |

---

## 7. 스크린 플로우 다이어그램

```
앱 진입
  │
  ├─ 미로그인 → OnboardingScreen → GoogleOAuth → 완료
  │
  └─ 로그인 → MainScreen (BottomNav)
               │
               ├─ [Chat] ChatScreen
               │         │
               │         ├─ [강화하기] → EvolutionSubScreen (BottomSheet)
               │         │               ├─ 재료 부족 → ShopScreen (강화재료)
               │         │               ├─ 슬롯 머신 애니메이션
               │         │               └─ 성공/실패 결과 → dismiss
               │         │
               │         ├─ [Ad Gate 발동] → BlindCard UI
               │         │                   └─ [광고 보기] → RewardedAd SDK
               │         │                                    └─ SSV 콜백 → 코인 +30 → Unblind
               │         │
               │         └─ [쿠팡 카드] → External Browser (파트너스 링크)
               │
               ├─ [혜택존] RewardsZoneScreen
               │         │
               │         ├─ [출석 도장] → API → 보상 SnackBar
               │         ├─ [광고 시청] → RewardedAd SDK → SSV 콜백 → 코인 적립
               │         └─ [TNK 오퍼월] → TNK SDK (NativeView/WebView)
               │
               ├─ [상점] ShopScreen
               │         │
               │         ├─ [강화재료 구매] → 코인 차감 → 인벤토리 업데이트
               │         └─ [교환권 신청] → KYC BottomSheet (최초) → VoucherAPI
               │
               └─ [마이] MyPageScreen
                         └─ [교환 내역] → VoucherHistoryScreen
```

---

## 8. Figma 파일 구조 권장

```
Cash Chat Design System
├── 🎨 Foundations
│   ├── Colors (Semantic + Raw)
│   ├── Typography
│   ├── Spacing & Grid
│   ├── Shadows
│   └── Motion
│
├── 🧩 Components
│   ├── Atoms (Button, Badge, Chip, Input, Icon)
│   ├── Molecules (ChatBubble, ShopItemCard, CoinBadge, SnackBar)
│   └── Organisms (CharacterHeader, AttendanceWidget, AdGateBlindCard, CoupangCard)
│
└── 📱 Screens
    ├── Chat Tab
    │   ├── ChatScreen (기본 상태)
    │   ├── ChatScreen (Ad Gate 활성)
    │   └── EvolutionSubScreen (단계별 5종)
    ├── 혜택존 Tab
    ├── 상점 Tab
    │   ├── 강화재료 탭
    │   ├── 교환권 탭
    │   └── KYC BottomSheet
    └── 마이 Tab
```

---

## 9. Android Compose 매핑 참고

| Spec Token | Compose API |
|------------|-------------|
| `color/primary` | `MaterialTheme.colorScheme.primary` |
| `color/secondary` | `MaterialTheme.colorScheme.secondary` |
| `shape/large` | `MaterialTheme.shapes.large` (16.dp) |
| `motion/medium2` | `tween(300, easing = FastOutSlowInEasing)` |
| Ad Gate blur | `Modifier.graphicsLayer { renderEffect = BlurEffect(20f, 20f, TileMode.Decal) }` (API 31+) |
| Ad Gate blur (API <31) | `Modifier.blur(20.dp)` (Compose 1.5+) |
| 슬롯 머신 | `InfiniteTransition` + `animateFloat`, `spring(stiffness = Spring.StiffnessLow)` |
| 진화 파티클 | `Canvas` + `LaunchedEffect` 코루틴 기반 |
