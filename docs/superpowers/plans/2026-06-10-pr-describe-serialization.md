# PR 제목/디스크립션 직렬화 통합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 일반 PR(base `dev`)의 제목/디스크립션 자동화를 `pr-review.yml` 안의 첫 job 으로 통합해, `opened` 시 `describe → auto-review` 가 같은 run(=같은 동시성 그룹)에서 순차 실행되도록 만들어 디스크립션 run 취소 버그를 없앤다.

**Architecture:** `pr-review.yml` 에 `describe` job 을 추가하고 `resolve-gemini-model` 의 `model1` + 공용 `ai_generate` 를 재사용한다. `review-gemini-auto` 는 `needs: [resolve-gemini-model, describe]` 로 describe 이후 실행된다(실패해도 `always()` 로 진행). 기존 `pr-description.yml` 은 `release-pr-description.yml` 로 이름을 바꾸고 릴리즈 PR 전용으로 축소한다.

**Tech Stack:** GitHub Actions YAML, bash, `actions/github-script@v7`, Google Gemini generateContent, 로컬 액션 `./.github/actions/rpd`, 공유 셸 라이브러리(`.github/scripts/review/lib_ai.sh`, `lib_keys.sh`).

---

## File Structure

- **Modify → Rename:** `.github/workflows/pr-description.yml` → `.github/workflows/release-pr-description.yml`
  - 릴리즈 PR(`dev → release/android|ios`) 전용. 일반 PR 단계 전부 제거.
- **Modify:** `.github/workflows/pr-review.yml`
  - 새 `describe` job 추가(일반 PR 제목/본문). `review-gemini-auto` 가 `describe` 에 의존.
- **변경 없음:** `extract-issue-from-pr.yaml`, `pr-rule-check.yml`(제목을 읽기만 함), 공유 셸 스크립트(`ai_generate` 그대로 재사용).

> 참고: 이 변경은 워크플로 배선(YAML)이 핵심이라 셸 단위 테스트로 직접 커버되지 않는다. 검증은 (1) YAML 파싱/`actionlint`, (2) 기존 셸 테스트 무회귀, (3) 실제 PR 한 건으로 통합 확인으로 한다.

---

## Task 1: `pr-description.yml` 을 릴리즈 전용으로 축소 + 파일명 변경

**Files:**
- Rename: `.github/workflows/pr-description.yml` → `.github/workflows/release-pr-description.yml`

- [ ] **Step 1: git mv 로 파일명 변경**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
git mv .github/workflows/pr-description.yml .github/workflows/release-pr-description.yml
```

- [ ] **Step 2: 릴리즈 전용 내용으로 전체 교체**

`.github/workflows/release-pr-description.yml` 의 **전체 내용**을 아래로 교체한다(일반 PR 단계 삭제, 트리거를 release 브랜치로 한정):

```yaml
name: Release PR Description Auto Fill

on:
  pull_request_target:
    types: [opened]
    # 릴리즈 PR(dev → release/*) 전용. 일반 PR 의 제목/본문은 pr-review.yml 의
    # describe job 이 처리한다(리뷰와 같은 run 으로 직렬화).
    branches: [release/android, release/ios]

# 릴리즈 PR 은 pr-review 자동리뷰(branches:[dev])와 경합하지 않지만, 동일 PR 의
# 중복 실행 방지를 위해 PR별 group 을 유지한다.
concurrency:
  group: gemini-pr-${{ github.event.pull_request.number || github.event.issue.number }}
  cancel-in-progress: false

jobs:
  update-description:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          fetch-tags: true

      - name: Get Commit List
        id: commits
        env:
          BASE_REF: ${{ github.base_ref }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
        run: |
          git fetch --no-tags origin "pull/${PR_NUMBER}/head:pr-${PR_NUMBER}-head"

          COMMITS=$(git log "origin/${BASE_REF}..pr-${PR_NUMBER}-head" \
            --no-merges \
            --pretty=format:"- %s (%h)" 2>/dev/null || \
            git log --no-merges --pretty=format:"- %s (%h)" -20)

          if [ -z "$COMMITS" ]; then
            COMMITS="- 커밋 내역 없음"
          fi

          EOF=$(dd if=/dev/urandom bs=15 count=1 status=none | base64)
          echo "commits<<$EOF" >> $GITHUB_OUTPUT
          echo "$COMMITS" >> $GITHUB_OUTPUT
          echo "$EOF" >> $GITHUB_OUTPUT

      # ── dev → release/android or release/ios 배포 PR 전용 처리 ──────────
      - name: Compute version & AI release notes (release PR only)
        if: github.head_ref == 'dev' && (github.base_ref == 'release/android' || github.base_ref == 'release/ios')
        id: release
        env:
          GITHUB_TOKEN:          ${{ secrets.GITHUB_TOKEN }}
          GOOGLE_GEMINI_API_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}
          GEMINI_MODELS:         ${{ vars.GEMINI_MODELS }}
          GEMINI_API_VERSION:    ${{ vars.GEMINI_API_VERSION }}
          PR_TITLE:              ${{ github.event.pull_request.title }}
          PR_NUMBER:             ${{ github.event.pull_request.number }}
          PR_LABELS_JSON:        ${{ toJSON(github.event.pull_request.labels.*.name) }}
        run: |
          set -euo pipefail

          # 플랫폼 판별
          BASE_REF="${{ github.base_ref }}"
          if [[ "$BASE_REF" == "release/android" ]]; then
            PLATFORM="android"
            PLATFORM_LABEL="Android"
          else
            PLATFORM="ios"
            PLATFORM_LABEL="iOS"
          fi
          TAG_PREFIX="${PLATFORM}/v"
          echo "platform=${PLATFORM}"           >> "$GITHUB_OUTPUT"
          echo "platform_label=${PLATFORM_LABEL}" >> "$GITHUB_OUTPUT"

          # PR 제목에서 Jira 티켓 추출
          ISSUE_KEY=$(echo "$PR_TITLE" | grep -oE 'CC-[0-9]+' | head -1 || true)
          echo "issue_key=${ISSUE_KEY}" >> "$GITHUB_OUTPUT"

          # ── Semantic version 계산 (플랫폼별 태그 기준) ─────────────────────
          git fetch --no-tags origin "pull/${PR_NUMBER}/head:pr-${PR_NUMBER}-head" 2>/dev/null || true

          LAST_TAG=$(git tag --list "${TAG_PREFIX}*" --sort=-version:refname | head -1 || echo "")
          HAS_PREV_TAG=true
          if [ -z "$LAST_TAG" ]; then
            HAS_PREV_TAG=false
            LAST_TAG="${TAG_PREFIX}0.0.0"
          fi
          LAST_VER="${LAST_TAG#${TAG_PREFIX}}"
          IFS='.' read -r MAJOR MINOR PATCH <<< "$LAST_VER"
          echo "ℹ️ [${PLATFORM}] 기준 버전: ${LAST_TAG}"

          if [ "$HAS_PREV_TAG" = "true" ]; then
            COMMITS_RAW=$(git log "${LAST_TAG}..pr-${PR_NUMBER}-head" --pretty=format:"%B" --no-merges 2>/dev/null || echo "")
          else
            COMMITS_RAW=$(git log "pr-${PR_NUMBER}-head" --pretty=format:"%B" --no-merges 2>/dev/null | head -100 || echo "")
          fi

          # PR 라벨에서 major 제어 확인
          HAS_MAJOR_LABEL=$(echo "$PR_LABELS_JSON" | jq -r 'if type == "array" then map(select(test("version\\.major"; "i"))) | length > 0 else false end // false')

          FEAT_COUNT=$(echo "$COMMITS_RAW" | grep -cE '^\[[^]]+\]\s+feat(\([^)]+\))?\s*[:：!]|^feat(\([^)]+\))?\s*[:：!]' 2>/dev/null) || FEAT_COUNT=0
          FIX_COUNT=$(echo "$COMMITS_RAW"  | grep -cE '^\[[^]]+\]\s+fix(\([^)]+\))?\s*[:：!]|^fix(\([^)]+\))?\s*[:：!]'   2>/dev/null) || FIX_COUNT=0
          BREAKING=$(echo "$COMMITS_RAW"   | grep -cE 'BREAKING[[:space:]]CHANGE|^feat(\([^)]+\))?!|^fix(\([^)]+\))?!'    2>/dev/null) || BREAKING=0

          if [ "$BREAKING" -gt 0 ] || [ "$HAS_MAJOR_LABEL" = "true" ]; then
            NEW_VERSION="$((MAJOR + 1)).0.0"
          elif [ "$FEAT_COUNT" -gt 0 ]; then
            NEW_VERSION="${MAJOR}.$((MINOR + 1)).0"
          elif [ "$FIX_COUNT" -gt 0 ]; then
            NEW_VERSION="${MAJOR}.${MINOR}.$((PATCH + 1))"
          else
            NEW_VERSION="${MAJOR}.${MINOR}.$((PATCH + 1))"
          fi

          echo "version=${NEW_VERSION}" >> "$GITHUB_OUTPUT"
          echo "ℹ️ [${PLATFORM}] 예상 버전: ${LAST_TAG} → ${TAG_PREFIX}${NEW_VERSION}"

          # ── Gemini AI 릴리즈 노트 생성 ──────────────────────────────────
          if [ "$HAS_PREV_TAG" = "true" ]; then
            COMMIT_LIST=$(git log "${LAST_TAG}..pr-${PR_NUMBER}-head" \
              --pretty=format:"- %s" --no-merges 2>/dev/null | head -50 || echo "")
          else
            COMMIT_LIST=$(git log "pr-${PR_NUMBER}-head" \
              --pretty=format:"- %s" --no-merges 2>/dev/null | head -50 || echo "")
          fi

          if [ -z "${GOOGLE_GEMINI_API_KEY:-}" ] || [ -z "$COMMIT_LIST" ]; then
            NOTES="${PLATFORM_LABEL} v${NEW_VERSION} 새 빌드가 배포될 예정입니다."
          else
            FEAT_COMMITS=$(echo "$COMMIT_LIST" | grep -E '^- \[[^]]+\]\s+feat\s*[:：]|^- feat\s*[:：]' | sed 's/^- /  /' || echo "  없음")
            FIX_COMMITS=$(echo "$COMMIT_LIST"  | grep -E '^- \[[^]]+\]\s+fix\s*[:：]|^- fix\s*[:：]'   | sed 's/^- /  /' || echo "  없음")
            OTHER_COMMITS=$(echo "$COMMIT_LIST" | grep -vE '^- \[[^]]+\]\s+(feat|fix)\s*[:：]|^- (feat|fix)\s*[:：]' | sed 's/^- /  /' || echo "  없음")

            PROMPT=$(jq -rn \
              --arg platform "$PLATFORM_LABEL" \
              --arg ver   "$NEW_VERSION" \
              --arg feat  "$FEAT_COMMITS" \
              --arg fix   "$FIX_COMMITS" \
              --arg other "$OTHER_COMMITS" \
              '"Cash Chat \($platform) 앱 v\($ver) 한국어 릴리즈 노트를 작성해주세요.\n\n[새 기능 커밋]\n\($feat)\n\n[버그 수정 커밋]\n\($fix)\n\n[기타 변경 커밋]\n\($other)\n\n작성 규칙:\n- 사용자가 직접 느끼는 변화만 작성 (\"~ 개선했습니다\" 형태)\n- 개발자 용어·함수명·파일명 금지\n- QA·리팩토링·코드정리 언급 금지\n- 이모지로 시작하는 불릿 포인트 3~5줄\n- 전체 200자 이내로 간결하게\n- 변경사항 없는 섹션은 생략"')

            GEMINI_API_VERSION="${GEMINI_API_VERSION:-v1beta}"
            MODELS_CSV="${GEMINI_MODELS:-gemini-2.5-flash,gemini-2.5-flash-lite,gemini-1.5-flash}"
            IFS=',' read -ra MODELS <<< "$MODELS_CSV"

            REQUEST_BODY=$(jq -n --arg p "$PROMPT" '{
              contents: [{"parts": [{"text": $p}]}],
              generationConfig: { temperature: 0.4 }
            }')

            HTTP_RESPONSE=$(mktemp)
            HTTP_CODE="000"
            USED_MODEL=""
            for model in "${MODELS[@]}"; do
              model="${model// /}"
              echo "::notice::Gemini 모델 시도: $model"
              HTTP_CODE=$(curl -s -o "$HTTP_RESPONSE" -w "%{http_code}" \
                "https://generativelanguage.googleapis.com/${GEMINI_API_VERSION}/models/${model}:generateContent?key=${GOOGLE_GEMINI_API_KEY}" \
                -H "content-type: application/json" \
                --data "$REQUEST_BODY") || HTTP_CODE="000"
              echo "::notice::Gemini API 응답 (모델: $model, HTTP $HTTP_CODE): $(head -c 500 "$HTTP_RESPONSE")"
              if [ "$HTTP_CODE" = "200" ]; then
                USED_MODEL="$model"
                break
              elif [ "$HTTP_CODE" = "429" ]; then
                echo "::warning::Gemini 429 ($model) — 60초 후 다음 모델로 전환"
                sleep 60
              elif [ "$HTTP_CODE" = "404" ]; then
                echo "::warning::Gemini 404 ($model) — 모델 없음, 다음 모델로 전환"
              else
                echo "::warning::Gemini $HTTP_CODE ($model) — 재시도 불가 오류, 중단"
                break
              fi
            done
            echo "used_model=${USED_MODEL}" >> "$GITHUB_OUTPUT"

            if [ "$HTTP_CODE" != "200" ]; then
              echo "::warning::Gemini API 실패 (HTTP $HTTP_CODE)"
              NOTES="${PLATFORM_LABEL} v${NEW_VERSION} 새 빌드가 배포될 예정입니다."
            else
              NOTES=$(jq -r '.candidates[0].content.parts[0].text // empty' "$HTTP_RESPONSE" 2>/dev/null || echo "")
              if [ -z "$NOTES" ]; then
                echo "::warning::Gemini 응답 파싱 실패: $(head -c 300 "$HTTP_RESPONSE")"
                NOTES="${PLATFORM_LABEL} v${NEW_VERSION} 새 빌드가 배포될 예정입니다."
              fi
            fi

            NOTES="${NOTES:0:500}"
          fi

          EOF=$(dd if=/dev/urandom bs=15 count=1 status=none | base64)
          echo "notes<<$EOF" >> "$GITHUB_OUTPUT"
          echo "$NOTES" >> "$GITHUB_OUTPUT"
          echo "$EOF" >> "$GITHUB_OUTPUT"

      - name: Fill Release PR title & body (release PR only)
        if: github.head_ref == 'dev' && (github.base_ref == 'release/android' || github.base_ref == 'release/ios')
        uses: actions/github-script@v7
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          script: |
            const issueKey      = ${{ toJSON(steps.release.outputs.issue_key) }};
            const version       = ${{ toJSON(steps.release.outputs.version) }};
            const notes         = ${{ toJSON(steps.release.outputs.notes) }};
            const commits       = ${{ toJSON(steps.commits.outputs.commits) }};
            const usedModel     = ${{ toJSON(steps.release.outputs.used_model) }};
            const platformLabel = ${{ toJSON(steps.release.outputs.platform_label) }};

            const prefix = issueKey ? `[${issueKey}] ` : '';
            const newTitle = `${prefix}CashChat ${platformLabel} - RELEASE : v${version}`;

            const jiraUrl = issueKey
              ? `https://moneyfactoryslave.atlassian.net/browse/${issueKey}`
              : null;

            const body = [
              `## 🚀 ${platformLabel} 릴리즈 PR`,
              jiraUrl ? `연관 Jira: [${issueKey}](${jiraUrl})` : '',
              '',
              '## 📣 릴리즈 노트 (AI 생성)',
              usedModel ? `> AI Model: \`${usedModel}\`` : '> AI Model: fallback (생성 실패)',
              notes,
              '',
              '## 📝 포함 커밋',
              commits,
              '',
              '## ✅ 배포 전 체크리스트',
              platformLabel === 'Android'
                ? '- [ ] Android 빌드 정상 확인\n- [ ] Firebase App Distribution 테스터 배포 확인'
                : '- [ ] iOS 빌드 정상 확인\n- [ ] TestFlight 업로드 확인',
            ].filter(line => line !== null).join('\n');

            await github.rest.pulls.update({
              owner: context.repo.owner,
              repo: context.repo.repo,
              pull_number: context.issue.number,
              title: newTitle,
              body: body.trim()
            });
            console.log(`✅ ${platformLabel} 릴리즈 PR 설정 완료 — "${newTitle}"`);
```

- [ ] **Step 3: YAML 파싱 검증**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/release-pr-description.yml')); print('OK')"
```
Expected: `OK`

- [ ] **Step 4: 일반 PR 단계가 모두 제거됐는지 확인**

Run:
```bash
grep -nE 'Update PR Title|Update PR Description|AI Summary \(general PR|Resolve author key|Extract Jira Issue Key' .github/workflows/release-pr-description.yml || echo "GENERAL STEPS REMOVED"
```
Expected: `GENERAL STEPS REMOVED`

- [ ] **Step 5: Commit**

```bash
git add .github/workflows/release-pr-description.yml
git commit -m "refactor(review): pr-description를 릴리즈 PR 전용으로 축소·이름 변경

일반 PR 제목/본문 단계 제거, 트리거를 release/* base로 한정.
일반 PR 처리는 pr-review.yml describe job으로 이관 예정.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: `pr-review.yml` 에 `describe` job 추가

**Files:**
- Modify: `.github/workflows/pr-review.yml` (`resolve-gemini-model` job 정의 끝과 `review-gemini-auto` job 사이에 새 job 삽입)

- [ ] **Step 1: `describe` job 블록 삽입**

`.github/workflows/pr-review.yml` 에서 `review-gemini-auto:` job 정의 **바로 위**(즉 `# Gemini 자동 리뷰 ...` 주석 블록 앞)에 아래 job 을 추가한다:

```yaml
  # ====================================================
  # 일반 PR 제목/디스크립션 자동화 (opened 전용, 직렬화의 첫 단계)
  #   같은 run 안에서 describe → auto-review 순으로 실행되어 동시성 그룹을
  #   통째로 점유 → 이후 push/comment run 이 describe 를 취소하지 못한다.
  #   릴리즈 PR(base release/*)은 이 워크플로 자체가 트리거되지 않음(branches:[dev]).
  # ====================================================
  describe:
    name: PR Describe (title + body)
    needs: resolve-gemini-model
    if: github.event_name == 'pull_request_target' && github.event.action == 'opened'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - name: Checkout (scripts)
        uses: actions/checkout@v5
        with:
          fetch-depth: 0
          fetch-tags: true

      - name: Get Commit List
        id: commits
        env:
          BASE_REF: ${{ github.base_ref }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
        run: |
          git fetch --no-tags origin "pull/${PR_NUMBER}/head:pr-${PR_NUMBER}-head"

          COMMITS=$(git log "origin/${BASE_REF}..pr-${PR_NUMBER}-head" \
            --no-merges \
            --pretty=format:"- %s (%h)" 2>/dev/null || \
            git log --no-merges --pretty=format:"- %s (%h)" -20)

          if [ -z "$COMMITS" ]; then
            COMMITS="- 커밋 내역 없음"
          fi

          EOF=$(dd if=/dev/urandom bs=15 count=1 status=none | base64)
          echo "commits<<$EOF" >> $GITHUB_OUTPUT
          echo "$COMMITS" >> $GITHUB_OUTPUT
          echo "$EOF" >> $GITHUB_OUTPUT

      - name: Get Diff
        id: diff
        env:
          BASE_REF: ${{ github.base_ref }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
        run: |
          set -euo pipefail
          RANGE="origin/${BASE_REF}...pr-${PR_NUMBER}-head"
          EXCLUDES=(':(exclude)**/*.lock' ':(exclude)**/*.lockb' ':(exclude)**/package-lock.json'
                    ':(exclude)**/*.png' ':(exclude)**/*.jpg' ':(exclude)**/*.jpeg' ':(exclude)**/*.webp'
                    ':(exclude)**/*.pdf' ':(exclude)**/*.svg' ':(exclude)**/build/**' ':(exclude)**/dist/**'
                    ':(exclude)**/*.xcworkspacedata' ':(exclude)**/*.pbxproj')
          DIFFSTAT=$(git diff --stat "$RANGE" -- "${EXCLUDES[@]}" 2>/dev/null | tail -60 || true)
          [ -z "$DIFFSTAT" ] && DIFFSTAT="(변경 통계 없음)"
          DIFF=$(git diff --no-color -U2 "$RANGE" -- "${EXCLUDES[@]}" 2>/dev/null | head -c 12000 || true)
          [ -z "$DIFF" ] && DIFF="(diff 없음)"
          EOF=$(dd if=/dev/urandom bs=15 count=1 status=none | base64)
          {
            echo "diffstat<<$EOF"; echo "$DIFFSTAT"; echo "$EOF"
            echo "diff<<$EOF"; echo "$DIFF"; echo "$EOF"
          } >> "$GITHUB_OUTPUT"

      - name: Resolve author key
        id: whichkey
        env:
          LOGIN: ${{ github.event.pull_request.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_keys.sh
          echo "suffix=$(key_suffix_for "$LOGIN")" >> "$GITHUB_OUTPUT"

      - name: AI Summary
        id: ai
        env:
          MAPPED_KEY:  ${{ secrets[format('GEMINI_KEY_{0}', steps.whichkey.outputs.suffix)] }}
          DEFAULT_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}
          MODEL1:      ${{ needs.resolve-gemini-model.outputs.model1 }}
          COMMITS:     ${{ steps.commits.outputs.commits }}
          DIFFSTAT:    ${{ steps.diff.outputs.diffstat }}
          DIFF:        ${{ steps.diff.outputs.diff }}
          PR_TITLE:    ${{ github.event.pull_request.title }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_ai.sh
          PRIMARY_KEY="${MAPPED_KEY:-$DEFAULT_KEY}"   # 작성자 개인키 우선, 없으면 공용키
          MODEL="${MODEL1#gemini/}"                   # resolve 출력의 gemini/ prefix 제거
          SUMMARY=""
          if [ -n "$PRIMARY_KEY" ] && [ -n "${COMMITS:-}" ]; then
            PROMPT=$(jq -rn \
              --arg t "${PR_TITLE:-}" --arg c "$COMMITS" --arg s "${DIFFSTAT:-}" --arg d "${DIFF:-}" \
              '"당신은 시니어 리뷰어입니다. 아래 PR 제목·커밋·변경통계·실제 diff를 근거로, 리뷰어가 30초 만에 맥락을 잡도록 한국어 마크다운 요약을 작성하세요.\n\n반드시 아래 형식을 그대로 따르세요(불릿은 각 1~3개, 근거 없는 추측·일반론·파일명 나열 금지):\n\n**한 줄 요약**: (이 PR이 무엇을 하는지 한 문장)\n\n**주요 변경**\n- (핵심 동작/구조 변경을 결과 중심으로)\n\n**왜**\n- (이 변경이 필요한 이유/배경)\n\n**리뷰 포커스**\n- (리뷰어가 특히 확인해야 할 위험·엣지케이스·호환성)\n\n제목: \($t)\n\n커밋:\n\($c)\n\n변경통계:\n\($s)\n\nDiff(일부 절단됨):\n\($d)"')
            PAYLOAD=$(mktemp); OUT=$(mktemp)
            jq -n --arg p "$PROMPT" '{contents:[{parts:[{text:$p}]}],generationConfig:{temperature:0.3,maxOutputTokens:1024}}' > "$PAYLOAD"
            # 공용 ai_generate: 개인키 우선, rate-limit/실패 시 공용키 폴백(백오프 포함)
            if ai_generate "$PRIMARY_KEY" "$DEFAULT_KEY" "$MODEL" "$PAYLOAD" "$OUT"; then
              SUMMARY=$(jq -r '.candidates[0].content.parts[0].text // empty' "$OUT" 2>/dev/null || echo "")
            fi
          fi
          [ -z "$SUMMARY" ] && SUMMARY="_(AI 요약을 생성하지 못했어요 — 아래 커밋 내역을 참고해주세요)_"
          SUMMARY="${SUMMARY:0:1800}"
          EOF=$(dd if=/dev/urandom bs=15 count=1 status=none | base64)
          echo "summary<<$EOF" >> "$GITHUB_OUTPUT"
          echo "$SUMMARY" >> "$GITHUB_OUTPUT"
          echo "$EOF" >> "$GITHUB_OUTPUT"

      - name: Track RPD (요약=작성자 키 · model1)
        uses: ./.github/actions/rpd
        with:
          increments: "${{ steps.whichkey.outputs.suffix != '' && steps.whichkey.outputs.suffix || 'shared' }}:model1:1"

      - name: Extract Jira Issue Key
        id: extract
        env:
          HEAD_REF: ${{ github.head_ref }}
          PR_TITLE: ${{ github.event.pull_request.title }}
        run: |
          ISSUE_KEY=$(echo "${HEAD_REF} ${PR_TITLE}" \
            | grep -oE 'CC-[0-9]+' | head -1)
          echo "issue_key=$ISSUE_KEY" >> $GITHUB_OUTPUT

      - name: Update PR Title
        uses: actions/github-script@v7
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          script: |
            const issueKey = ${{ toJSON(steps.extract.outputs.issue_key) }};
            const currentTitle = context.payload.pull_request.title;

            if (/^\[CC-\d+\]\s+\S.*/.test(currentTitle)) {
              console.log('✅ 제목 이미 올바른 형식:', currentTitle);
              return;
            }

            if (!issueKey) {
              console.log('⚠️ Jira 티켓 번호를 찾을 수 없어 제목을 수정하지 않습니다.');
              return;
            }

            const cleanTitle = currentTitle.replace(/CC-\d+\s*/g, '').trim();
            const newTitle = `[${issueKey}] ${cleanTitle}`;

            await github.rest.pulls.update({
              owner: context.repo.owner,
              repo: context.repo.repo,
              pull_number: context.issue.number,
              title: newTitle
            });
            console.log(`✅ 제목 변경: "${currentTitle}" → "${newTitle}"`);

      - name: Update PR Description
        uses: actions/github-script@v7
        with:
          github-token: ${{ secrets.GITHUB_TOKEN }}
          script: |
            const issueKey = ${{ toJSON(steps.extract.outputs.issue_key) }};
            const commits = ${{ toJSON(steps.commits.outputs.commits) }};
            const summary = ${{ toJSON(steps.ai.outputs.summary) }};

            const jiraUrl = issueKey
              ? `https://moneyfactoryslave.atlassian.net/browse/${issueKey}`
              : '없음';

            const checklist = [
              '- [ ] 코드 리뷰 요청 전 셀프 리뷰 완료',
              '- [ ] 테스트 확인',
              '- [ ] 불필요한 코드/주석 제거',
            ].join('\n');

            // 동기화 유지: PULL_REQUEST_TEMPLATE.md / lib_help.sh / docs/review/ai-code-review.md 와 동일하게
            const aiReviewCommands = [
              '| 명령어 | 설명 | 사용 위치 |',
              '|---|---|---|',
              '| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |',
              '| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |',
              '| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |',
              '| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |',
              '| `/help` | 명령어 도움말 표시 | PR 코멘트 |',
              '| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |',
              '',
              'ℹ️ 자동 리뷰는 PR을 열 때 공통 키로 1회만 실행돼요. 이후 푸시에는 자동 재리뷰가 없으니(해결된 코멘트만 자동 정리), 다시 받으려면 위 명령어로 요청하세요. 명령어 리뷰는 요청자 개인 키(미등록 시 공통 키)로 동작해 공통 일일 한도를 아낍니다.',
            ].join('\n');

            const body = [
              '## 📋 연관 Jira 티켓',
              issueKey ? `[${issueKey}](${jiraUrl})` : '없음',
              '',
              '## 🤖 AI 요약',
              summary,
              '',
              '## 📝 커밋 내역',
              commits,
              '',
              '## ✅ 체크리스트',
              checklist,
              '',
              '## 🤖 AI 코드 리뷰 명령어',
              aiReviewCommands,
            ].join('\n');

            await github.rest.pulls.update({
              owner: context.repo.owner,
              repo: context.repo.repo,
              pull_number: context.issue.number,
              body: body.trim()
            });
```

- [ ] **Step 2: YAML 파싱 검증**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
python3 -c "import yaml; d=yaml.safe_load(open('.github/workflows/pr-review.yml')); assert 'describe' in d['jobs'], 'describe job 없음'; print('OK describe job 존재')"
```
Expected: `OK describe job 존재`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(review): 일반 PR describe job을 리뷰 워크플로에 추가

opened 시 제목/본문 자동화를 ai_generate + resolve된 model1로 수행.
직렬화의 첫 단계로 실행되도록 별도 job 구성.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: `review-gemini-auto` 가 `describe` 이후 실행되도록 배선

**Files:**
- Modify: `.github/workflows/pr-review.yml` (`review-gemini-auto` job 의 `needs` 와 `if`)

- [ ] **Step 1: `needs` 와 `if` 수정**

`review-gemini-auto:` job 에서 아래 두 줄을 찾아:
```yaml
    needs: resolve-gemini-model
    if: github.event_name == 'pull_request_target' && github.event.action == 'opened'
```
다음으로 교체한다:
```yaml
    needs: [resolve-gemini-model, describe]
    if: |
      always() &&
      needs.resolve-gemini-model.result == 'success' &&
      github.event_name == 'pull_request_target' && github.event.action == 'opened'
```

> `always()` 로 describe 가 실패/취소돼도 리뷰는 진행하되, `needs` 때문에 항상 describe 완료 이후 실행된다(같은 run 직렬화). `review-on-push`, 댓글 명령 job 들은 describe 와 무관하므로 변경하지 않는다.

- [ ] **Step 2: 배선 검증 (needs 에 describe 포함 확인)**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
python3 -c "
import yaml
d = yaml.safe_load(open('.github/workflows/pr-review.yml'))
needs = d['jobs']['review-gemini-auto']['needs']
assert 'describe' in needs and 'resolve-gemini-model' in needs, needs
assert 'always()' in d['jobs']['review-gemini-auto']['if']
print('OK review-gemini-auto needs:', needs)
"
```
Expected: `OK review-gemini-auto needs: ['resolve-gemini-model', 'describe']`

- [ ] **Step 3: review-on-push 는 변경되지 않았는지 확인**

Run:
```bash
python3 -c "
import yaml
d = yaml.safe_load(open('.github/workflows/pr-review.yml'))
assert d['jobs']['review-on-push']['needs'] == 'resolve-gemini-model', d['jobs']['review-on-push']['needs']
print('OK review-on-push 변경 없음')
"
```
Expected: `OK review-on-push 변경 없음`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(review): auto-review가 describe 이후 실행되도록 직렬화

needs에 describe 추가 + always()로 describe 실패해도 리뷰 진행.
opened 시 describe→auto-review를 같은 run에서 순차 실행.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: 전체 검증 (lint + 무회귀 + 일관성)

**Files:** 없음 (검증 전용)

- [ ] **Step 1: 두 워크플로 YAML 파싱**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
for f in .github/workflows/pr-review.yml .github/workflows/release-pr-description.yml; do
  python3 -c "import yaml; yaml.safe_load(open('$f')); print('OK $f')"
done
```
Expected: 두 줄 모두 `OK ...`

- [ ] **Step 2: actionlint (있으면) 실행**

Run:
```bash
command -v actionlint >/dev/null && actionlint .github/workflows/pr-review.yml .github/workflows/release-pr-description.yml || echo "actionlint 미설치 — YAML 파싱으로 갈음"
```
Expected: actionlint 출력 없음(통과) 또는 `actionlint 미설치 — ...`

- [ ] **Step 3: 기존 셸 테스트 무회귀 확인**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp/.github/scripts/review
for t in tests/test_ai.sh tests/test_keys.sh; do echo "== $t =="; bash "$t"; done
```
Expected: 각 테스트 통과(0 종료). `ai_generate`/`key_suffix_for` 회귀 없음.

- [ ] **Step 4: 잔여 일반 PR 처리 중복 없음 확인**

Run:
```bash
cd /Users/gudals-mac/Documents/nomade/cash-chat-mvp
echo "release 파일에 일반 단계 잔존?:"; grep -nE 'Update PR Title|AI Summary$|Resolve author key' .github/workflows/release-pr-description.yml || echo "  없음(정상)"
echo "review 파일 describe job?:"; grep -nE 'name: PR Describe' .github/workflows/pr-review.yml
```
Expected: release 파일 `없음(정상)`, review 파일에 `name: PR Describe (title + body)` 매칭.

- [ ] **Step 5: 통합 검증 메모 (실제 PR 필요)**

이 변경은 GitHub Actions 런타임에서만 끝단 검증이 가능하다. 머지 후 첫 일반 PR `opened` 에서 다음을 확인한다:
1. 제목이 `[CC-xxx] ...` 로 자동 prefix.
2. 본문에 Jira/AI요약/커밋/체크리스트/명령어 섹션이 채워짐.
3. `describe` job 이 `auto-review` 보다 먼저 완료(같은 run 내 순차).
4. 같은 PR 에 push/comment 발생 시 런 목록에 `cancelled` 상태의 describe run 이 없음.

검증 명령:
```bash
gh run list --repo cash-chat-mvp/cash-chat-mvp --workflow=pr-review.yml -L 5
```
Expected: `opened` run 이 `success`, describe 관련 `cancelled` 없음.

---

## Self-Review 결과

- **Spec coverage:** 3.1(describe job)=Task 2, 3.2(needs/always)=Task 3, 3.3(release 축소·rename)=Task 1, §6 검증=Task 4. 모든 spec 절이 task 에 매핑됨.
- **Placeholder scan:** 프롬프트·스크립트·github-script 전체를 실제 내용으로 포함. TBD/TODO 없음.
- **Type consistency:** `ai_generate PRIMARY FALLBACK MODEL PAYLOAD(파일) OUT(파일)` 시그니처(lib_ai.sh)와 호출 일치. `model1` 의 `gemini/` prefix 제거(`${MODEL1#gemini/}`) 반영. step output 이름(`commits`,`diff`,`summary`,`issue_key`) 일관.
