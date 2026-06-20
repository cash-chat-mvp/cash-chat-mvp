# 일반 PR 제목/디스크립션을 리뷰 직렬화의 첫 단계로 통합

- 작성일: 2026-06-10
- 관련 브랜치: `fix/CC-347`
- 증상 PR: [#181](https://github.com/cash-chat-mvp/cash-chat-mvp/pull/181) (CC-346, 제목 prefix·본문 자동화 미적용)

## 1. 문제 (확정된 근본 원인)

`pr-review.yml`(자동 리뷰)과 `pr-description.yml`(제목/본문 자동화)이 둘 다
`pull_request_target: opened` 에서 동일한 동시성 그룹
`gemini-pr-${{ pull_request.number }}` 로 진입한다. 두 워크플로 모두
`concurrency.cancel-in-progress: false` 이므로:

1. `opened` 시 두 run이 같은 그룹으로 들어옴 → 하나는 in_progress, 하나는 pending.
2. 보통 리뷰 run이 그룹을 점유(in_progress), 디스크립션 run은 pending 으로 대기.
3. 이후 `synchronize`(push) 또는 `comment` 이벤트가 같은 그룹으로 들어오면,
   GitHub Actions 는 그룹당 pending 을 1개만 유지하므로 **대기 중이던
   디스크립션 run 을 취소**하고 새 run 으로 교체한다.

결과적으로 디스크립션 run 이 실행되지 못해 제목 `[CC-xxx]` prefix 와 본문
자동화가 적용되지 않는다.

### 증거
- PR #181 의 `PR Description Auto Fill` run 이 `cancelled` (약 9초) 로 종료.
- PR #181 제목이 `혜택존 리뉴얼 Android/IOS 화면 구현` — `[CC-346]` prefix 없음.
- `pr-rule-check.yml` 이 `^\[CC-\d+\]` 미충족으로 실패하는 2차 증상도 동일 원인.

## 2. 핵심 통찰

`pr-review.yml` 의 자동 리뷰는 `pull_request_target` 에 `branches: [dev]` 필터가
걸려 있어 **base 가 `dev` 인 일반 PR 에서만** 동작한다. 릴리즈 PR
(`dev → release/android` / `release/ios`)은 리뷰 job 자체가 트리거되지 않으므로
동시성 경합이 없고, 이 버그의 영향도 받지 않는다.

→ 수정은 **일반 PR 경로에만** 필요하다.

## 3. 설계

선택한 접근: **리뷰 워크플로에 통합** (디스크립션을 같은 run 안에서 리뷰보다 먼저).
같은 워크플로 run 은 동시성 그룹을 통째로 점유하므로, 이후 push/comment run 은
pending 으로 줄서기만 하고 진행 중인 describe→review run 을 취소하지 못한다.

### 3.1 `pr-review.yml` — 새 `describe` job 추가 (일반 PR 전용)

- `needs: resolve-gemini-model`
- `if: github.event_name == 'pull_request_target' && github.event.action == 'opened'`
- `permissions: { contents: read, pull-requests: write }` (제목/본문 업데이트)
- 단계:
  1. Checkout (scripts)
  2. Get Commit List — base..head `git log` (기존 pr-description 로직 이식)
  3. Get Diff — diffstat + 12KB 절단 diff (노이즈 제외 glob 유지)
  4. Resolve author key — `lib_keys.sh` 의 `key_suffix_for`
  5. AI Summary — **공용 `ai_generate`(lib_ai.sh) 재사용**, `resolve-gemini-model`
     의 `model1` 사용. `model1` 출력의 `gemini/` prefix 는 호출 전 제거.
     실패 시 기존과 동일한 한국어 placeholder 요약으로 폴백.
  6. Extract Jira Issue Key — `head_ref + title` 에서 `CC-[0-9]+` 추출
  7. Update PR Title — 이미 `[CC-\d+]` 형식이면 skip, 아니면 `[CC-xxx] <정리된 제목>`
  8. Update PR Description — 기존 본문 템플릿(Jira/AI요약/커밋/체크리스트/명령어) 유지
  9. Track RPD — `<suffix|shared>:model1:1`

> 인라인 `gen_summary` 와 `vars.GEMINI_MODELS` 직접 재정렬 로직은 제거하고
> 검증된 `ai_generate`(개인키→공용키 폴백, 백오프 재시도)로 대체한다.

### 3.2 `pr-review.yml` — `review-gemini-auto` 가 `describe` 에 의존

```yaml
review-gemini-auto:
  needs: [resolve-gemini-model, describe]
  if: |
    always() &&
    needs.resolve-gemini-model.result == 'success' &&
    github.event_name == 'pull_request_target' && github.event.action == 'opened'
```

- `always()` 로 describe 가 실패/취소돼도 리뷰는 진행하되, `needs` 로 인해 **항상
  describe 완료 이후** 실행된다(같은 run = 직렬화, 중간 취소 없음).
- `review-on-push`(synchronize), 댓글 명령 job 들은 describe 와 무관하므로 변경 없음.

### 3.3 `pr-description.yml` → `release-pr-description.yml` (릴리즈 전용 축소)

- 파일명을 `release-pr-description.yml` 로 변경, `name:` 도 릴리즈 전용으로 갱신.
- 트리거를 base 기준으로 한정:
  ```yaml
  on:
    pull_request_target:
      types: [opened]
      branches: [release/android, release/ios]
  ```
- 일반 PR 단계 전부 제거: Get Diff (general PR), Resolve author key,
  AI Summary (general PR), Track RPD, Extract Jira Issue Key, Update PR Title,
  Update PR Description.
- 유지: Checkout, Get Commit List, 릴리즈 버전/노트 계산 및 `Fill Release PR
  title & body` 경로. (릴리즈 PR 은 리뷰와 경합 없으므로 동시성 그룹은 그대로 둬도
  무방하나, 릴리즈 전용이므로 충돌 가능성 없음.)

## 4. 데이터 흐름 (수정 후)

- `opened` (base dev): 단일 run → `resolve-gemini-model` → `describe`
  (제목/본문) → `review-gemini-auto`. run 전체가 그룹 점유.
- `synchronize`: 별도 run → `review-on-push`. 진행 중 run 있으면 pending 으로 대기.
- `comment` 명령: 별도 run → 해당 명령 job. 동일하게 pending 직렬화.
- `opened` (base release/*): `release-pr-description.yml` 만 동작.

## 5. 에러 처리

- AI 요약 실패: `ai_generate` 폴백 후에도 빈 응답이면 placeholder 요약 사용(기존과 동일).
- 제목/본문 업데이트 API 오류: describe job 실패 → 하지만 `review-gemini-auto`
  는 `always()` 로 영향받지 않고 진행.
- describe 의 권한 부족 방지를 위해 job-level `pull-requests: write` 명시.

## 6. 테스트/검증

- 셸 스크립트 단위 테스트(`review-scripts-test.yml`, `scripts/review/tests/*`)는
  `ai_generate` 재사용으로 추가 커버됨 — 기존 테스트 통과 확인.
- 워크플로 YAML 문법: `actionlint`(있으면) 또는 GitHub Actions 파서로 검증.
- 통합 검증: 일반 PR `opened` 시 제목 `[CC-xxx]` prefix + 본문 채워짐,
  이어서 자동 리뷰 1회 실행, 같은 PR 에 push/comment 가 와도 describe run 이
  취소되지 않음(런 목록에서 `cancelled` describe 없음).

## 7. 범위 밖 (YAGNI)

- 릴리즈 PR 경로 로직 변경 없음(파일 이동·트리거 한정만).
- `extract-issue-from-pr.yaml`, `pr-rule-check.yml` 은 제목을 읽기만 하므로 변경 없음.
- 키 매핑/RPD 회계 정책 변경 없음.
