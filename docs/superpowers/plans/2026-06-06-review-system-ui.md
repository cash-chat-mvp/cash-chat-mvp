# 리뷰 시스템 개발자 UI 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 코드 리뷰 시스템의 개발자 노출 UI(명령어 표·봇 상태 코멘트)를 일관된 카드 시스템으로 통일하고, 명령어 매칭을 견고하게 바꾸며, `/help` 명령어와 종합 사용 가이드를 추가한다.

**Architecture:** 표시·파싱 로직은 전부 테스트 가능한 셸 라이브러리(`lib_cards.sh`/`lib_cmd.sh`/`lib_help.sh`)로 빼고, GitHub Actions 워크플로(`pr-review.yml`)는 그 라이브러리를 호출하는 얇은 배선만 담당한다. 봇 코멘트는 GitHub Alerts + 숨김 마커(`<!-- cashchat-ai-review:KIND -->`)를 가진 단일 "카드" 포맷으로 통일한다.

**Tech Stack:** Bash, GitHub Actions, GitHub REST API(curl), jq, pr-agent(Qodo) 도커 이미지. 테스트는 `.github/scripts/review/tests/` 의 자작 assert 하니스(`bash test_*.sh`).

**Spec:** `docs/superpowers/specs/2026-06-06-review-system-ui-design.md`

---

## File Structure

**신규 (셸 라이브러리 — 순수 함수, 네트워크 없음, 단위 테스트 대상):**
- `.github/scripts/review/lib_cards.sh` — 카드 렌더(`render_card`, `card_docs_url`, `card_has_marker`, 상태→Alert 매핑)
- `.github/scripts/review/lib_cmd.sh` — 코멘트 본문 앵커 파싱(`detect_command`)
- `.github/scripts/review/lib_help.sh` — `/help` 명령어 레퍼런스 카드(`render_help_card`, 명령어 표 단일 소스)

**신규 (테스트):**
- `.github/scripts/review/tests/test_cards.sh`
- `.github/scripts/review/tests/test_cmd.sh`
- `.github/scripts/review/tests/test_help.sh`

**신규 (문서):**
- `docs/review/ai-code-review.md` — 종합 사용 가이드(한국어)

**수정:**
- `.github/scripts/review/lib_comments.sh` — 카드/마커 기반 진행·실패·정리 로직
- `.github/scripts/review/resolve_command.sh` — resolve 승인/보류 카드화
- `.github/scripts/review/resolve_threads.sh` — 자동 리졸브/변경검토 카드화
- `.github/workflows/pr-review.yml` — `detect-command` 잡, 워커 잡 게이트 전환, `help` 잡, `/ask` 실패 카드화, model-resolve 앵커 정합
- `.github/workflows/pr-description.yml` — 명령어 표 통일 + 안내 문구
- `.github/PULL_REQUEST_TEMPLATE.md` — 명령어 표 통일 + 안내 문구
- `.github/scripts/review/tests/test_comments.sh` — 카드/마커 어서션으로 갱신

> **명령어 표 동기화 주의:** 동일한 명령어 표가 4곳(`PULL_REQUEST_TEMPLATE.md`, `pr-description.yml`, `lib_help.sh`, `docs/review/ai-code-review.md`)에 존재한다. 한 곳을 바꾸면 나머지도 동일 내용으로 맞추고, 각 파일에 "다른 곳과 동기화 유지" 주석을 남긴다.

---

## Task 1: 카드 렌더 라이브러리 (`lib_cards.sh`)

**Files:**
- Create: `.github/scripts/review/lib_cards.sh`
- Test: `.github/scripts/review/tests/test_cards.sh`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `.github/scripts/review/tests/test_cards.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cards.sh"
echo "test_cards"

card="$(render_card progress '🔍 AI 코드 리뷰 진행 중' '변경된 코드를 살펴보는 중입니다.' '`/gemini-review` 재요청')"

# Alert 토큰 매핑
assert_eq "$(printf '%s' "$card" | grep -c '^> \[!NOTE\]')" "1" "progress → [!NOTE]"
assert_eq "$(render_card quota t b | grep -c '^> \[!WARNING\]')" "1" "quota → [!WARNING]"
assert_eq "$(render_card error t b | grep -c '^> \[!CAUTION\]')" "1" "error → [!CAUTION]"
assert_eq "$(render_card approve t b | grep -c '^> \[!TIP\]')" "1" "approve → [!TIP]"

# 본문 줄은 blockquote 안에
assert_eq "$(printf '%s' "$card" | grep -c '^> 변경된 코드를 살펴보는 중입니다.')" "1" "본문 blockquote 처리"

# 숨김 마커 포함 + card_has_marker 탐지
assert_eq "$(printf '%s' "$card" | grep -c '<!-- cashchat-ai-review:progress -->')" "1" "progress 마커 포함"
card_has_marker "$card"; assert_rc $? 0 "card_has_marker 양성"
card_has_marker "일반 코멘트"; assert_rc $? 1 "card_has_marker 음성"

# 문서 URL: env 있으면 blob, 없으면 상대경로
( unset GITHUB_SERVER_URL GITHUB_REPOSITORY; assert_eq "$(card_docs_url)" "docs/review/ai-code-review.md" "env 없으면 상대경로" )
assert_eq "$(GITHUB_SERVER_URL=https://github.com GITHUB_REPOSITORY=o/r card_docs_url)" "https://github.com/o/r/blob/dev/docs/review/ai-code-review.md" "env 있으면 blob URL"

t_summary
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash .github/scripts/review/tests/test_cards.sh`
Expected: FAIL — `lib_cards.sh: No such file` 또는 `render_card: command not found`.

- [ ] **Step 3: `lib_cards.sh` 구현**

Create `.github/scripts/review/lib_cards.sh`:

```bash
#!/usr/bin/env bash
# 봇 코멘트 "카드" 렌더(순수 함수, 네트워크 없음). lib_comments/lib_help 등이 source.
# 모든 카드 = GitHub Alert 상태줄 → 본문(blockquote) → 푸터(다음 동작·문서 링크) → 숨김 마커.

CARD_DOCS_PATH="docs/review/ai-code-review.md"

# 상태(KIND) → GitHub Alert 토큰
_card_alert() {
  case "${1:-}" in
    progress|help|hold) echo "NOTE" ;;
    clean|approve)      echo "TIP" ;;
    quota)              echo "WARNING" ;;
    error)              echo "CAUTION" ;;
    *)                  echo "NOTE" ;;
  esac
}

# 문서 blob URL (env 없으면 상대 경로). 자동 리뷰 기준 브랜치는 dev.
card_docs_url() {
  if [ -n "${GITHUB_SERVER_URL:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
    printf '%s/%s/blob/dev/%s' "$GITHUB_SERVER_URL" "$GITHUB_REPOSITORY" "$CARD_DOCS_PATH"
  else
    printf '%s' "$CARD_DOCS_PATH"
  fi
}

# render_card KIND TITLE BODY [FOOTER_ACTION]  → stdout
render_card() {
  local kind="${1:-}" title="${2:-}" body="${3:-}" action="${4:-}"
  local alert url; alert="$(_card_alert "$kind")"; url="$(card_docs_url)"
  printf '> [!%s]\n' "$alert"
  printf '> **%s**\n' "$title"
  printf '%s\n' "$body" | while IFS= read -r line; do printf '> %s\n' "$line"; done
  printf '\n'
  if [ -n "$action" ]; then
    printf '<sub>다음: %s · <a href="%s">사용법</a></sub>\n' "$action" "$url"
  else
    printf '<sub><a href="%s">AI 리뷰 사용법</a></sub>\n' "$url"
  fi
  printf '<!-- cashchat-ai-review:%s -->\n' "$kind"
}

# card_has_marker BODY — 봇 카드 마커가 있으면 rc0
card_has_marker() { printf '%s' "${1:-}" | grep -q '<!-- cashchat-ai-review:'; }
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `bash .github/scripts/review/tests/test_cards.sh`
Expected: PASS — `── N passed, 0 failed ──`.

- [ ] **Step 5: 커밋**

```bash
git add .github/scripts/review/lib_cards.sh .github/scripts/review/tests/test_cards.sh
git commit -m "feat(review): 봇 코멘트 카드 렌더 라이브러리 추가"
```

---

## Task 2: 마커 기반 notice 탐지 (`lib_comments.sh`)

기존 한국어 본문 텍스트 매칭을 카드 마커 기반으로 전환한다. `clean` 은 pr-agent 코멘트 내부 한 줄이라 standalone 카드가 아니므로 cleanup 대상에서 제외하고, `help` 도 리뷰 흐름 cleanup에서 제외한다(리뷰 실행이 도움말을 지우지 않도록).

**Files:**
- Modify: `.github/scripts/review/lib_comments.sh:1-9` (헤더/`is_notice_body`), `:43-54` (`cleanup_notices`)
- Test: `.github/scripts/review/tests/test_comments.sh`

- [ ] **Step 1: 테스트 갱신(실패 유도)**

`.github/scripts/review/tests/test_comments.sh` 의 `is_notice_body` 관련 줄을 아래로 교체하고, 파일 상단 `. "$DIR/lib_comments.sh"` 앞에 `. "$DIR/lib_cards.sh"` 를 추가:

```bash
. "$DIR/lib_cards.sh"
. "$DIR/lib_comments.sh"
```

기존 두 줄
```bash
is_notice_body '## 🔍 AI 코드 리뷰를 진행하고 있어요'; assert_rc $? 0 "진행 코멘트 → notice"
is_notice_body '## PR 리뷰 가이드'; assert_rc $? 1 "리뷰 결과 → notice 아님"
```
을 다음으로 교체:
```bash
# 마커 기반: 리뷰 흐름 notice(progress/quota/error)는 notice, help/clean·리뷰결과는 아님
is_notice_body "$(render_card progress t b)"; assert_rc $? 0 "progress 카드 → notice"
is_notice_body "$(render_card error t b)"; assert_rc $? 0 "error 카드 → notice"
is_notice_body 'Preparing review...'; assert_rc $? 0 "pr-agent 영문 notice → notice"
is_notice_body "$(render_card help t b)"; assert_rc $? 1 "help 카드 → notice 아님"
is_notice_body '## PR 리뷰 가이드'; assert_rc $? 1 "리뷰 결과 → notice 아님"
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash .github/scripts/review/tests/test_comments.sh`
Expected: FAIL — `help 카드 → notice 아님` 등에서 실패(현재 정규식이 마커를 모름).

- [ ] **Step 3: `lib_comments.sh` 수정**

파일 최상단(주석 다음)에 카드 라이브러리 로드를 추가. `lib_comments.sh:3` 부근에 삽입:
```bash
# 카드 렌더 의존(같은 디렉터리)
. "$(dirname "${BASH_SOURCE[0]}")/lib_cards.sh"
```

`is_notice_body`(6-9줄)를 교체:
```bash
# ── 정리 대상(notice) 판별: 리뷰 흐름 카드(progress/quota/error) 또는 pr-agent 영문 notice → rc0 ──
# help/clean 은 제외(리뷰 실행이 도움말/정상완료 안내를 지우지 않도록).
is_notice_body() {
  local b="${1:-}"
  printf '%s' "$b" | grep -qE '<!-- cashchat-ai-review:(progress|quota|error) -->' && return 0
  printf '%s' "$b" | grep -qE '^(Failed to generate code suggestions for PR|Preparing review\.\.\.|Preparing suggestions\.\.\.)$' && return 0
  return 1
}
```

`cleanup_notices`(46-54줄)의 jq select 를 마커/영문 기준으로 교체:
```bash
cleanup_notices() {
  local api; api="$(_api)"
  curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments?per_page=100" \
    | jq -r '.[] | select(.user!=null and .user.type=="Bot" and .body!=null and (
        (.body|test("<!-- cashchat-ai-review:(progress|quota|error) -->")) or
        (.body=="Failed to generate code suggestions for PR") or
        (.body=="Preparing review...") or (.body=="Preparing suggestions...")
      )) | .id' \
    | while read -r cid; do
        [ -n "$cid" ] && curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" "$api/issues/comments/${cid}" >/dev/null || true
      done || true
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `bash .github/scripts/review/tests/test_comments.sh`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add .github/scripts/review/lib_comments.sh .github/scripts/review/tests/test_comments.sh
git commit -m "refactor(review): notice 탐지를 카드 마커 기반으로 전환"
```

---

## Task 3: 진행/실패 카드 + 제자리 업데이트 (`lib_comments.sh`)

**Files:**
- Modify: `.github/scripts/review/lib_comments.sh` (`post_progress` 57-62줄, `notify_failure` 70-81줄)
- Test: `.github/scripts/review/tests/test_comments.sh`

- [ ] **Step 1: 테스트 추가(실패 유도)**

`test_comments.sh` 의 `t_summary` 앞에 추가:
```bash
# 진행/실패 카드 본문 빌더(네트워크 분리된 순수 함수)
assert_eq "$(progress_card | grep -c '<!-- cashchat-ai-review:progress -->')" "1" "progress_card 마커"
assert_eq "$(progress_card | grep -c '^> \[!NOTE\]')" "1" "progress_card NOTE"
assert_eq "$(failure_card quota '/gemini-review' | grep -c '^> \[!WARNING\]')" "1" "quota → WARNING"
assert_eq "$(failure_card quota '/gemini-review' | grep -c '`/gemini-review`')" "1" "quota 카드에 재시도 명령"
assert_eq "$(failure_card transient '/openai-review' | grep -c '^> \[!CAUTION\]')" "1" "transient → CAUTION"
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash .github/scripts/review/tests/test_comments.sh`
Expected: FAIL — `progress_card: command not found`.

- [ ] **Step 3: `lib_comments.sh` 구현**

`post_progress` 위에 순수 빌더 두 개 추가:
```bash
# 진행 카드 본문(순수) — 네트워크 없음
progress_card() {
  render_card progress '🔍 AI 코드 리뷰 진행 중' \
$'변경된 코드를 살펴보는 중입니다 — 보통 1~2분 정도 걸려요.\n끝나면 이 안내는 결과로 바뀌거나 사라집니다.' \
    ''
}
# 실패 카드 본문(순수). $1=classify(quota|transient) $2=재시도 명령
failure_card() {
  local cls="${1:-transient}" retry="${2:-/gemini-review}"
  if [ "$cls" = "quota" ]; then
    render_card quota '오늘의 AI 리뷰 사용량을 모두 사용했어요' \
$'무료 등급의 일일 호출 한도(RPD)에 도달해 이번 리뷰를 완료하지 못했어요.\n한도가 회복되면 다시 시도해 주세요. 🙏' \
      "\`${retry}\` 재요청"
  else
    render_card error 'AI 리뷰를 완료하지 못했어요' \
$'일시적인 오류로 리뷰 생성에 실패했어요. 잠시 후 다시 시도해 주세요.' \
      "\`${retry}\` 재요청"
  fi
}
```

`post_progress`(기존 body heredoc 사용 부분)를 빌더 사용으로 교체:
```bash
post_progress() {
  local api; api="$(_api)" body; body="$(progress_card)"
  PROGRESS_ID=$(curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$body" '{body:$b}')" | jq -r '.id // empty' || true)
}
```

`notify_failure` 를 제자리 PATCH 우선으로 교체:
```bash
# 전 모델 실패 시: 진행 카드를 실패 카드로 제자리 업데이트(없으면 신규 게시). $1=로그 $2=재시도명령
notify_failure() {
  local log="$1" retry="${2:-/gemini-review}" api; api="$(_api)"
  local cls body; cls="$(ai_classify "$(cat "$log" 2>/dev/null)")"; body="$(failure_card "$cls" "$retry")"
  if [ -n "${PROGRESS_ID:-}" ]; then
    curl -s -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
      "$api/issues/comments/${PROGRESS_ID}" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
    PROGRESS_ID=""   # EXIT 트랩(clear_progress)이 실패 카드를 지우지 않도록 해제
  else
    cleanup_notices
    curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
      "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
  fi
}
```

> 주: `notify_failure` 는 `ai_classify`(lib_ai.sh)에 의존한다. 호출하는 워크플로 잡들은 이미 `lib_ai.sh` 를 source 하므로 추가 작업 불필요.

- [ ] **Step 4: 테스트 통과 확인**

Run: `bash .github/scripts/review/tests/test_comments.sh`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add .github/scripts/review/lib_comments.sh .github/scripts/review/tests/test_comments.sh
git commit -m "feat(review): 진행·실패 코멘트 카드화 및 실패 시 제자리 업데이트"
```

---

## Task 4: 명령어 앵커 파싱 라이브러리 (`lib_cmd.sh`)

**Files:**
- Create: `.github/scripts/review/lib_cmd.sh`
- Test: `.github/scripts/review/tests/test_cmd.sh`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `.github/scripts/review/tests/test_cmd.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cmd.sh"
echo "test_cmd"

# 양성: 명령어가 맨 앞(선행 공백 허용), 인자 유무 무관
detect_command "$(printf '/gemini-review')" gemini-review; assert_rc $? 0 "정확히 명령어만"
detect_command "$(printf '  /gemini-review')" gemini-review; assert_rc $? 0 "선행 공백 허용"
detect_command "$(printf '/ask 이거 왜 이렇게 했나요?')" ask; assert_rc $? 0 "ask + 인자"
detect_command "$(printf '맥락 설명\n/resolve 추후 처리')" resolve; assert_rc $? 0 "둘째 줄 맨 앞 명령"

# 음성: 산문 중간 언급 / 유사어 / 코드블록 속
detect_command "$(printf '/ask 가 왜 안되죠? 라고 묻고 싶을 때')" ask; assert_rc $? 0 "맨 앞이면 인자 취급(양성)"
detect_command "$(printf '왜 /ask 가 안되죠?')" ask; assert_rc $? 1 "문장 중간 언급 → 무시"
detect_command "$(printf '/asking about something')" ask; assert_rc $? 1 "유사어(/asking) → 무시"
detect_command "$(printf '예시: `/review` 를 칩니다')" review; assert_rc $? 1 "백틱/문장 속 → 무시"

t_summary
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash .github/scripts/review/tests/test_cmd.sh`
Expected: FAIL — `lib_cmd.sh: No such file`.

- [ ] **Step 3: `lib_cmd.sh` 구현**

Create `.github/scripts/review/lib_cmd.sh`:

```bash
#!/usr/bin/env bash
# 코멘트 본문 명령어 앵커 파싱(순수 함수). contains() 의 부분 문자열 오발동을 막는다.
# 규칙: 어떤 줄이든 "맨 앞(선행 공백 허용)에 /명령" 으로 시작하고, 그 뒤가 공백 또는 줄끝.

# detect_command BODY CMD  → 매칭되면 rc0
# CMD 예: gemini-review / openai-review / ask / resolve / help
detect_command() {
  local body="${1:-}" cmd="${2:-}"
  [ -z "$cmd" ] && return 1
  printf '%s' "$body" | grep -qE "^[[:space:]]*/${cmd}([[:space:]]|$)"
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `bash .github/scripts/review/tests/test_cmd.sh`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add .github/scripts/review/lib_cmd.sh .github/scripts/review/tests/test_cmd.sh
git commit -m "feat(review): 명령어 앵커 파싱 라이브러리(lib_cmd) 추가"
```

---

## Task 5: `detect-command` 잡 + 워커 게이트 전환 (`pr-review.yml`)

워크플로 YAML은 `lib_cmd.sh` 를 호출하는 얇은 배선만 한다(로직은 Task 4에서 검증됨).

**Files:**
- Modify: `.github/workflows/pr-review.yml`

- [ ] **Step 1: `detect-command` 잡 추가**

`jobs:` 최상단(`resolve-gemini-model` 앞)에 추가. 댓글 이벤트 전용으로 권한·봇·PR 검사를 일원화하고 명령별 출력을 낸다:

```yaml
  # ====================================================
  # 댓글 명령어 앵커 파싱 + 권한 게이트 (댓글 이벤트 전용 단일 소스)
  # ====================================================
  detect-command:
    name: Detect Command
    if: github.event_name == 'issue_comment' || github.event_name == 'pull_request_review_comment'
    runs-on: ubuntu-latest
    permissions:
      contents: read
    outputs:
      authorized: ${{ steps.d.outputs.authorized }}
      is_gemini_review: ${{ steps.d.outputs.is_gemini_review }}
      is_openai_review: ${{ steps.d.outputs.is_openai_review }}
      is_ask: ${{ steps.d.outputs.is_ask }}
      is_resolve: ${{ steps.d.outputs.is_resolve }}
      is_help: ${{ steps.d.outputs.is_help }}
    steps:
      - name: Checkout (scripts)
        uses: actions/checkout@v5
      - name: Parse
        id: d
        env:
          BODY: ${{ github.event.comment.body }}
          ASSOC: ${{ github.event.comment.author_association }}
          IS_BOT: ${{ github.event.comment.user.type == 'Bot' }}
          IS_PR: ${{ github.event.issue.pull_request != null || github.event_name == 'pull_request_review_comment' }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_cmd.sh
          authorized=false
          if [ "$IS_BOT" != "true" ] && [ "$IS_PR" = "true" ] && \
             printf '%s' "$ASSOC" | grep -qxE 'OWNER|MEMBER|COLLABORATOR'; then
            authorized=true
          fi
          echo "authorized=$authorized" >> "$GITHUB_OUTPUT"
          for c in gemini-review openai-review ask resolve help; do
            key="is_${c//-/_}"
            if detect_command "$BODY" "$c"; then echo "$key=true" >> "$GITHUB_OUTPUT"; else echo "$key=false" >> "$GITHUB_OUTPUT"; fi
          done
```

- [ ] **Step 2: `resolve-gemini-model` `if` 의 댓글 분기 앵커 정합**

`resolve-gemini-model` 의 `if:`(31-41줄) 에서 `contains(github.event.comment.body, '/gemini-review')` → `startsWith(github.event.comment.body, '/gemini-review')`, `contains(... '/ask')` → `startsWith(... '/ask')` 로 교체. (`/resolve` 는 이미 `startsWith`.) `pull_request_target` 분기는 그대로. 이 잡은 모델 HTTP 확인만 하는 저비용 게이트라 최종 차단은 워커의 detect-command 가 담당.

- [ ] **Step 3: 댓글 워커 잡들을 detect-command 출력으로 게이트**

각 잡에 `needs` 에 `detect-command` 추가하고 `if` 를 출력 기반으로 교체:

`review-gemini-manual`:
```yaml
    needs: [resolve-gemini-model, detect-command]
    if: needs.detect-command.outputs.authorized == 'true' && needs.detect-command.outputs.is_gemini_review == 'true'
```
`resolve-openai-model` 과 `review-openai-manual`:
```yaml
    # resolve-openai-model
    needs: [detect-command]
    if: needs.detect-command.outputs.authorized == 'true' && needs.detect-command.outputs.is_openai_review == 'true'
    # review-openai-manual
    needs: [resolve-openai-model, detect-command]
    if: needs.detect-command.outputs.authorized == 'true' && needs.detect-command.outputs.is_openai_review == 'true'
```
`ask-gemini`:
```yaml
    needs: [resolve-gemini-model, detect-command]
    if: needs.detect-command.outputs.authorized == 'true' && needs.detect-command.outputs.is_ask == 'true'
```
`resolve-command`:
```yaml
    needs: [resolve-gemini-model, detect-command]
    if: needs.detect-command.outputs.authorized == 'true' && needs.detect-command.outputs.is_resolve == 'true'
```

> `resolve-gemini-model` 은 `pull_request_target` 에서도 도므로 `detect-command` 를 `needs` 에 넣지 않는다(댓글 이벤트에서만 detect-command 가 돌기 때문). 워커들은 `resolve-gemini-model` 과 `detect-command` 를 함께 `needs` 하며, 두 잡 모두 댓글 이벤트에서 정상 실행된다.

- [ ] **Step 4: 워크플로 문법 검증**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/pr-review.yml')); print('YAML OK')"`
Expected: `YAML OK`.

추가로 `lib_cmd` 회귀:
Run: `bash .github/scripts/review/tests/test_cmd.sh`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add .github/workflows/pr-review.yml
git commit -m "refactor(review): 명령어 매칭을 detect-command 잡으로 일원화(앵커 기반)"
```

---

## Task 6: `/help` 명령어 (`lib_help.sh` + 워크플로 `help` 잡)

**Files:**
- Create: `.github/scripts/review/lib_help.sh`
- Test: `.github/scripts/review/tests/test_help.sh`
- Modify: `.github/workflows/pr-review.yml`

- [ ] **Step 1: 실패하는 테스트 작성**

Create `.github/scripts/review/tests/test_help.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_cards.sh"
. "$DIR/lib_help.sh"
echo "test_help"

h="$(render_help_card)"
assert_eq "$(printf '%s' "$h" | grep -c '<!-- cashchat-ai-review:help -->')" "1" "help 마커"
assert_eq "$(printf '%s' "$h" | grep -c '`/gemini-review`')" "1" "gemini-review 안내"
assert_eq "$(printf '%s' "$h" | grep -c '`/openai-review`')" "1" "openai-review 안내"
assert_eq "$(printf '%s' "$h" | grep -c '`/ask')" "1" "ask 안내"
assert_eq "$(printf '%s' "$h" | grep -c '`/resolve`')" "1" "resolve 안내"
assert_eq "$(printf '%s' "$h" | grep -c '공통 키')" "1" "자동/키 안내 문구"
t_summary
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `bash .github/scripts/review/tests/test_help.sh`
Expected: FAIL — `lib_help.sh: No such file`.

- [ ] **Step 3: `lib_help.sh` 구현**

Create `.github/scripts/review/lib_help.sh`:

```bash
#!/usr/bin/env bash
# /help 명령어 레퍼런스 카드(명령어 표 단일 소스). lib_cards.sh 를 source 한 뒤 사용.
# ⚠️ 동기화 유지: 같은 표가 .github/PULL_REQUEST_TEMPLATE.md, .github/workflows/pr-description.yml,
#                docs/review/ai-code-review.md 에도 있다. 바꾸면 네 곳을 함께 맞출 것.
render_help_card() {
  local body
  body=$(cat <<'MD'
PR 코멘트에 아래 명령어를 입력하면 AI 리뷰를 사용할 수 있어요.

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 이 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

ℹ️ 자동 리뷰는 PR을 열 때 공통 키로 1회만 실행돼요. 이후 푸시에는 자동 재리뷰가 없으니(해결된 코멘트만 자동 정리), 다시 받으려면 위 명령어로 요청하세요. 명령어 리뷰는 요청자 개인 키(미등록 시 공통 키)로 동작해 공통 일일 한도를 아낍니다.
MD
)
  render_card help '🛟 AI 코드 리뷰 명령어 도움말' "$body" ''
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `bash .github/scripts/review/tests/test_help.sh`
Expected: PASS.

- [ ] **Step 5: 워크플로 `help` 잡 추가**

`.github/workflows/pr-review.yml` 의 `jobs:` 에 추가:

```yaml
  # ====================================================
  # /help — 명령어 도움말 카드 (AI 호출 없음, 비용 0)
  # ====================================================
  help:
    name: Help
    needs: detect-command
    if: needs.detect-command.outputs.authorized == 'true' && needs.detect-command.outputs.is_help == 'true'
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - name: Checkout (scripts)
        uses: actions/checkout@v5
      - name: Generate Gemini bot token
        id: bot-token
        uses: actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1
        with:
          app-id: ${{ secrets.GEMINI_BOT_APP_ID }}
          private-key: ${{ secrets.GEMINI_BOT_PRIVATE_KEY }}
      - name: Post help card
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          PR_NUMBER: ${{ github.event.issue.number }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_cards.sh
          . .github/scripts/review/lib_help.sh
          api="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
          # 기존 help 카드 정리(중복 게시 방지) — help 마커만 대상
          curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
            "$api/issues/${PR_NUMBER}/comments?per_page=100" \
            | jq -r '.[]|select(.user!=null and .user.type=="Bot" and .body!=null and (.body|test("<!-- cashchat-ai-review:help -->")))|.id' \
            | while read -r cid; do [ -n "$cid" ] && curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" "$api/issues/comments/${cid}" >/dev/null || true; done || true
          body="$(render_help_card)"
          curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
            "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
```

- [ ] **Step 6: 검증 + 커밋**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pr-review.yml')); print('YAML OK')"`
Expected: `YAML OK`.
Run: `bash .github/scripts/review/tests/test_help.sh`
Expected: PASS.

```bash
git add .github/scripts/review/lib_help.sh .github/scripts/review/tests/test_help.sh .github/workflows/pr-review.yml
git commit -m "feat(review): /help 명령어로 명령어 도움말 카드 게시"
```

---

## Task 7: `/ask` 실패 메시지 카드화 (`pr-review.yml`)

**Files:**
- Modify: `.github/workflows/pr-review.yml` (`ask-gemini` 잡의 실패 처리 블록, 기존 432-446줄 부근)

- [ ] **Step 1: `ask-gemini` 실패 블록을 카드로 교체**

`ask-gemini` 의 `run:` 스크립트 상단에 라이브러리 로드를 추가하고, 실패 시 평문 `msg=...` 게시를 카드로 교체. 실패 분기 전체를 다음으로 교체:

```bash
          if [ "$docker_status" -ne 0 ] || grep -qE "$PR_AGENT_FAIL_MARKERS" "$LOG" || grep -qiE "RESOURCE_EXHAUSTED|rate.?limit|429" "$LOG"; then
            . .github/scripts/review/lib_ai.sh
            . .github/scripts/review/lib_cards.sh
            PR_NUM="${{ github.event.issue.number || github.event.pull_request.number }}"
            api="${GITHUB_API_URL}/repos/${{ github.repository }}"
            cls="$(ai_classify "$(cat "$LOG" 2>/dev/null)")"
            if [ "$cls" = "quota" ]; then
              body="$(render_card quota '오늘의 AI 사용량을 모두 사용했어요' '무료 등급 일일 한도(RPD)에 도달했어요. 잠시 후 다시 `/ask` 로 질문해 주세요. 🙏' '`/ask 질문내용` 재시도')"
            else
              body="$(render_card error '답변을 생성하지 못했어요' '일시적인 오류가 발생했어요. 잠시 후 다시 `/ask` 로 질문해 주세요.' '`/ask 질문내용` 재시도')"
            fi
            if [ "${{ github.event_name }}" = "pull_request_review_comment" ]; then
              curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
                "$api/pulls/${PR_NUM}/comments/${{ github.event.comment.id }}/replies" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null || true
            else
              curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
                "$api/issues/${PR_NUM}/comments" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null || true
            fi
            echo "::error::/ask 실패"
            exit 1
          fi
```

- [ ] **Step 2: 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pr-review.yml')); print('YAML OK')"`
Expected: `YAML OK`.

- [ ] **Step 3: 커밋**

```bash
git add .github/workflows/pr-review.yml
git commit -m "feat(review): /ask 실패 메시지를 통일 카드로 정렬"
```

---

## Task 8: resolve 계열 메시지 카드화 (`resolve_command.sh`, `resolve_threads.sh`)

**Files:**
- Modify: `.github/scripts/review/resolve_command.sh` (55줄 보류, 91줄 승인)
- Modify: `.github/scripts/review/resolve_threads.sh` (53줄 자동 리졸브, 63줄 변경검토)
- Test: `.github/scripts/review/tests/test_resolve_command.sh`, `test_resolve_threads.sh`

- [ ] **Step 1: 두 스크립트에 카드 라이브러리 로드 추가**

각 파일 상단(다른 `.` source 와 같은 위치)에 추가:
```bash
. "$(dirname "${BASH_SOURCE[0]}")/lib_cards.sh"
```
> 워크플로에서 이 스크립트를 source 하기 전에 `lib_cards.sh` 가 같은 디렉터리에 있으므로 동작한다.

- [ ] **Step 2: 메시지 빌더를 카드로 교체**

`resolve_command.sh` 보류(기존 `🤖 **resolve 보류**` gh_reply):
```bash
    gh_reply "$ROOT_ID" "$(render_card hold 'resolve 보류' "$(printf '%s\n아직 리졸브하기 이르다고 판단했어요. 반영 후 다시 `/resolve \"사유\"` 해주세요.' "${RESOLVE_REASON_AI:-제시한 사유만으로는 해결을 확인하기 어렵습니다.}")" '')"
```
`resolve_command.sh` 승인(기존 `🤖 **resolve 승인**`):
```bash
      gh_reply "$ROOT_ID" "$(render_card approve 'resolve 승인 — 추후 처리 항목 등록' "$(printf '%s\n\n- 서브태스크: [%s](%s)\n- 상위 티켓: %s\n- 사유: %s\n\n이 스레드는 리졸브됩니다. 🙏' "${RESOLVE_REASON_AI:-반영 확인}" "$NEW_KEY" "$NEW_URL" "$PARENT" "$REASON")" '')"
```
`resolve_threads.sh` 자동 리졸브(기존 `🤖 **자동 리졸브 판단**`):
```bash
        -d "$(jq -n --arg b "$(render_card approve '자동 리졸브 판단' "$(printf '%s\n최신 변경에서 해결된 것으로 판단되어 자동 리졸브합니다. (%s)' "${reason:-최신 변경에서 해결된 것으로 판단됩니다.}" "$model")" '')" '{body:$b}')" >/dev/null 2>&1 || true
```
`resolve_threads.sh` 변경검토(기존 `🤖 **변경 검토**`):
```bash
        -d "$(jq -n --arg b "$(render_card hold '변경 검토' "$(printf '%s\n아직 해결되지 않았거나 추가 확인이 필요해 보여 리졸브하지 않았습니다.' "${reason:-남은 우려가 있어 보입니다.}")" '')" '{body:$b}')" >/dev/null 2>&1 || true
```

- [ ] **Step 3: 기존 테스트 회귀 + (있으면) 카드 어서션 추가**

기존 테스트가 메시지 본문 텍스트를 단정하면 카드 포맷에 맞게 갱신. 우선 회귀 실행:
Run: `bash .github/scripts/review/tests/test_resolve_command.sh`
Run: `bash .github/scripts/review/tests/test_resolve_threads.sh`
Expected: PASS (텍스트 단정이 깨지면 카드 마커/제목 기준으로 갱신 후 통과).

- [ ] **Step 4: 커밋**

```bash
git add .github/scripts/review/resolve_command.sh .github/scripts/review/resolve_threads.sh .github/scripts/review/tests/test_resolve_command.sh .github/scripts/review/tests/test_resolve_threads.sh
git commit -m "feat(review): resolve 승인/보류·자동리졸브 메시지 카드화"
```

---

## Task 9: 명령어 표 통일 (PR 템플릿 + pr-description)

**Files:**
- Modify: `.github/PULL_REQUEST_TEMPLATE.md`
- Modify: `.github/workflows/pr-description.yml:341-349`

- [ ] **Step 1: PR 템플릿 표 교체**

`.github/PULL_REQUEST_TEMPLATE.md` 의 `> AI 코드 리뷰 명령어` 이하 표 전체를 교체:

```markdown
> AI 코드 리뷰 명령어 · 자세한 사용법: [docs/review/ai-code-review.md](../docs/review/ai-code-review.md)
<!-- 동기화 유지: 같은 표가 .github/workflows/pr-description.yml, .github/scripts/review/lib_help.sh, docs/review/ai-code-review.md 에도 있음 -->

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 명령어 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

> ℹ️ 자동 리뷰는 PR을 열 때 **공통 키로 1회만** 실행돼요. 이후 푸시에는 자동 재리뷰가 없으니(해결된 코멘트만 자동 정리), 다시 받으려면 위 명령어로 요청하세요. 명령어 리뷰는 **요청자 개인 키**(미등록 시 공통 키)로 동작해 공통 일일 한도를 아낍니다.
```

- [ ] **Step 2: pr-description.yml 표 교체**

`.github/workflows/pr-description.yml` 의 `aiReviewCommands` 배열(341-349줄)을 교체:

```javascript
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
```

- [ ] **Step 3: 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pr-description.yml')); print('YAML OK')"`
Expected: `YAML OK`.

두 표 내용 일치 육안 확인(명령어 행 6개 동일).

- [ ] **Step 4: 커밋**

```bash
git add .github/PULL_REQUEST_TEMPLATE.md .github/workflows/pr-description.yml
git commit -m "docs(review): 명령어 표 통일 + 자동/키 전략 안내 문구 추가"
```

---

## Task 10: 종합 사용 가이드 (`docs/review/ai-code-review.md`)

**Files:**
- Create: `docs/review/ai-code-review.md`

- [ ] **Step 1: 가이드 작성**

Create `docs/review/ai-code-review.md` (한국어). 아래 구조를 그대로 채운다(명령어 표는 Task 9 와 동일 내용 사용):

```markdown
# AI 코드 리뷰 사용 가이드

<!-- 동기화 유지: 명령어 표는 .github/PULL_REQUEST_TEMPLATE.md / pr-description.yml / lib_help.sh 와 동일 -->

이 저장소는 pr-agent(Qodo) 기반 AI 코드 리뷰를 GitHub Actions 로 제공합니다. 리뷰는
Gemini(자동·무료 등급)와 OpenAI(수동·유료), 그리고 CodeRabbit 을 사용할 수 있습니다.

## 1. 한눈에 보기

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 명령어 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

> 명령어는 코멘트(또는 줄) **맨 앞**에 입력해야 인식됩니다. 문장 중간의 `/ask` 같은 언급은
> 트리거되지 않습니다. 권한: OWNER/MEMBER/COLLABORATOR 만 명령어를 사용할 수 있습니다.

## 2. 명령어 상세

### 자동 리뷰 (PR을 열 때 1회)
PR을 `dev` 로 열면 Gemini 가 자동으로 라인별 리뷰 + 코드 개선 제안을 1회 게시합니다.
이때는 **공통 키**를 사용합니다. 이후 커밋을 푸시해도 **전체 재리뷰는 자동으로 돌지 않고**,
이미 해결된 리뷰 코멘트 스레드만 자동으로 정리(리졸브)합니다.

### `/gemini-review`
다시 리뷰가 필요할 때 PR 코멘트로 입력합니다. 호출: `/review` + `/improve` 2회분.
요청자 **개인 키**로 동작하며, 미등록 사용자는 공통 키로 폴백합니다.

### `/openai-review`
OpenAI 모델로 더 깊은 리뷰가 필요할 때 사용합니다. **항상 비용이 발생**합니다.
호출: `/review` + `/improve`. 요청자 개인 키(미등록 시 공통 키).

### `/ask 질문내용`
AI 리뷰 코멘트(라인/PR)에 답글로 후속 질문을 합니다. 따옴표는 필요 없습니다.
저비용 모델(model_weak)을 사용합니다. 예: `/ask 이 함수가 null 일 때 어떻게 되나요?`

### `/resolve`
리뷰 코멘트 **답글**로 입력하면, AI가 해당 지적이 반영/처리됐는지 판단합니다.
타당하면 추후 처리 항목을 **Jira 서브태스크**로 등록하고 스레드를 리졸브합니다.
사유를 덧붙일 수 있습니다: `/resolve 다음 PR에서 일괄 처리`.

### `/help`
사용 가능한 명령어 도움말 카드를 PR 코멘트로 게시합니다. (AI 호출 없음)

### `@coderabbitai review`
CodeRabbit 앱으로 리뷰를 요청합니다. (수동 · 비용 발생)

## 3. 리뷰 결과 읽는 법

- **PR 리뷰 가이드**: 난이도, 중점 리뷰 영역, 보안/테스트 관찰 사항 요약.
- **PR 코드 개선 제안**: 라인에 바로 커밋 가능한 AS-IS/TO-BE 제안.
- **인라인 코멘트**: 변경 라인에 붙는 구체 지적. Conventional Comments 접두사로 분류됩니다.
  - `[Suggestion]` 개선 제안 / `[Nitpick]` 사소(머지 무방) / `[Question]` 의도 질문 /
    `[Compliment]` 좋은 코드 칭찬.

## 4. 비용 & 키 전략

- **자동 리뷰는 공통 키로 1회만** — 무료 등급 일일 호출 한도(RPD)를 아끼기 위함입니다.
- **명령어 리뷰는 요청자 개인 키** — 본인 할당량을 쓰고, 미등록자는 공통 키로 폴백합니다.
  개인 키가 등록된 사용자: `gudals-kim`, `seedplan005`/`jwchoi42`, `jeonj95`/`unistuj`.
- **OpenAI 는 항상 비용 발생** — 꼭 필요한 PR 에만 `/openai-review` 를 사용하세요.
- 사용량이 소진되면 봇이 "오늘의 AI 리뷰 사용량을 모두 사용했어요" 카드를 띄웁니다.
  한도 회복 후 다시 명령어로 요청하세요.

## 5. FAQ / 트러블슈팅

- **푸시했는데 리뷰가 다시 안 돌아요** — 정상입니다. 자동 재리뷰는 없고, 필요하면
  `/gemini-review` 로 요청하세요.
- **명령어가 안 먹어요** — 코멘트 맨 앞에 입력했는지, 권한(OWNER/MEMBER/COLLABORATOR)이
  있는지 확인하세요.
- **리뷰가 실패했어요** — 봇이 실패/사용량 카드를 띄웁니다. 잠시 후 같은 명령어로 재시도하세요.

## 6. 참고 (구현 파일)

- 워크플로: `.github/workflows/pr-review.yml`, `.github/workflows/pr-description.yml`
- 공유 스크립트: `.github/scripts/review/` (`lib_cards.sh`, `lib_cmd.sh`, `lib_help.sh`,
  `lib_comments.sh`, `lib_ai.sh`, `lib_keys.sh`, `resolve_*.sh`, `run_pr_agent.sh`)
- 공통 설정: `.pr_agent.toml`
```

- [ ] **Step 2: 링크 점검 + 커밋**

Run: `test -f docs/review/ai-code-review.md && echo OK`
Expected: `OK`.

```bash
git add docs/review/ai-code-review.md
git commit -m "docs(review): AI 코드 리뷰 종합 사용 가이드 추가"
```

---

## Task 11: 전체 회귀 + 마무리

**Files:** (없음 — 검증만)

- [ ] **Step 1: 리뷰 셸 테스트 전체 실행**

Run:
```bash
for t in .github/scripts/review/tests/test_*.sh; do echo "== $t =="; bash "$t" || echo "FAILED: $t"; done
```
Expected: 모든 파일 `── N passed, 0 failed ──`, `FAILED:` 출력 없음.

- [ ] **Step 2: 워크플로 YAML 문법 최종 검증**

Run:
```bash
python3 -c "import yaml; [yaml.safe_load(open(f)) for f in ['.github/workflows/pr-review.yml','.github/workflows/pr-description.yml']]; print('YAML OK')"
```
Expected: `YAML OK`.

- [ ] **Step 3: 명령어 표 4곳 동기화 육안 확인**

`.github/PULL_REQUEST_TEMPLATE.md`, `.github/workflows/pr-description.yml`, `.github/scripts/review/lib_help.sh`, `docs/review/ai-code-review.md` 의 명령어 행 6개가 동일한지 확인.

- [ ] **Step 4: 최종 커밋(필요 시)**

검증만 했다면 추가 커밋 없음. 누락 수정이 있었다면:
```bash
git add -A && git commit -m "test(review): 리뷰 UI 변경 전체 회귀 통과"
```

---

## Self-Review 결과(작성자 점검)

- **스펙 커버리지**: A(Task 9) · B(Task 1·2·3) · C(Task 7) · D(Task 3) · E(Task 8) · F(Task 10) ·
  G(Task 4·5) · H(Task 6) — 전 항목 태스크 매핑됨.
- **의도된 편차**: spec 의 `clean=[!TIP]` 카드는 pr-agent 의 제안 코멘트 *내부* 한 줄이라
  standalone 카드로 만들지 않고 기존 `localize_suggestions_body` 의 친근한 한 줄을 유지한다
  (코멘트 구조 훼손 및 macOS/GNU sed 멀티라인 비호환 회피). 카드 시스템에는 `clean` 매핑만 남겨둠.
- **타입/이름 일관성**: `render_card`, `card_docs_url`, `card_has_marker`, `detect_command`,
  `render_help_card`, `progress_card`, `failure_card`, 마커 `cashchat-ai-review:KIND` 전 태스크 일치.
```
