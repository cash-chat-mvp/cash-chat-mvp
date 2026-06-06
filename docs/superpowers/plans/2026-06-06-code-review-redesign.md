# 코드리뷰 시스템 재설계 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI 코드리뷰 파이프라인을 공유 스크립트 기반으로 재구성해 안정성·라인별 리뷰·정확한 빌드 트리거를 확보한다.

**Architecture:** 1055줄 모놀리식 `pr-review.yml`의 5중 복붙 bash를 `.github/scripts/review/*.sh` 라이브러리로 추출하고, 각 워크플로 잡은 `source` 후 함수 호출만 한다. 무료 Gemini 등급을 유지하되 지수 백오프 재시도·concurrency·멱등 재시도로 안정화한다. 빌드 체크는 경량 `detect` 잡이 빌드 영향 글로브를 판정해 무거운 빌드 잡을 게이트한다.

**Tech Stack:** GitHub Actions (YAML), Bash, jq, curl, GitHub REST/GraphQL API, Google Gemini API, OpenAI API, pr-agent(Qodo) docker 이미지, Jira REST API v3.

**스펙:** [docs/superpowers/specs/2026-06-06-code-review-redesign-design.md](../specs/2026-06-06-code-review-redesign-design.md)

---

## 파일 구조 (생성/수정)

생성:
- `.github/scripts/review/lib_keys.sh` — login → 키 SUFFIX 매핑 (단일 소스)
- `.github/scripts/review/lib_ai.sh` — Gemini/OpenAI HTTP 호출 백오프 재시도 + 실패 분류
- `.github/scripts/review/lib_comments.sh` — 안내 코멘트 정리/실패 알림/진행 코멘트/한국어 라벨 치환
- `.github/scripts/review/run_pr_agent.sh` — pr-agent docker 실행 + 하드 실패 마커 판정 + 멱등 재시도
- `.github/scripts/review/resolve_threads.sh` — push 시 변경 라인 기반 미해결 스레드 판단 (#2)
- `.github/scripts/review/resolve_command.sh` — `/resolve "사유"` AI 판단 + Jira 서브태스크 (#4)
- `.github/scripts/build/detect_build_changes.sh` — 빌드 영향 글로브 판정 (android/ios 공용)
- `.github/scripts/review/tests/*.sh` — 순수 로직 단위 테스트 (bash 네이티브)

수정:
- `.github/workflows/pr-review.yml` — 모놀리식 분해, 트리거/잡 재구성
- `.github/workflows/pr-description.yml` — #0 공통 키/model1
- `.github/workflows/android-build-check.yml` — detect+gate
- `.github/workflows/ios-build-check.yml` — detect+gate
- `.pr_agent.toml` — `/improve` 커밋 제안 설정 확인

## 테스트 전략

bats/shellcheck가 로컬에 없으므로 **순수 bash 단위 테스트**를 쓴다. 각 테스트는 라이브러리를 `source`하고,
외부 명령(`curl`/`docker`/`gh`)을 셸 함수로 **모킹**한 뒤 함수 출력/종료코드를 assert한다.
curl·GitHub API 오케스트레이션이 무거운 함수는 단위 테스트 대신 **순수 로직 부분만 함수로 분리**해 테스트하고,
전체 동작은 마지막 통합(테스트 PR) 단계에서 검증한다.

공용 assert 헬퍼를 먼저 만든다.

---

### Task 0: 테스트 하니스 + 디렉터리

**Files:**
- Create: `.github/scripts/review/tests/_assert.sh`

- [ ] **Step 1: assert 헬퍼 작성**

`.github/scripts/review/tests/_assert.sh`:

```bash
#!/usr/bin/env bash
# 단위 테스트 공용 assert 헬퍼. 각 테스트 스크립트가 source 한다.
set -uo pipefail
T_PASS=0; T_FAIL=0
assert_eq() { # $1=actual $2=expected $3=label
  if [ "$1" = "$2" ]; then T_PASS=$((T_PASS+1)); echo "  ✓ $3";
  else T_FAIL=$((T_FAIL+1)); echo "  ✗ $3"; echo "    expected: [$2]"; echo "    actual:   [$1]"; fi
}
assert_rc() { # $1=actual_rc $2=expected_rc $3=label
  if [ "$1" = "$2" ]; then T_PASS=$((T_PASS+1)); echo "  ✓ $3";
  else T_FAIL=$((T_FAIL+1)); echo "  ✗ $3 (rc expected $2 got $1)"; fi
}
t_summary() { echo "── $T_PASS passed, $T_FAIL failed ──"; [ "$T_FAIL" -eq 0 ]; }
```

- [ ] **Step 2: 디렉터리 확인**

Run: `mkdir -p .github/scripts/review/tests .github/scripts/build && ls .github/scripts/review/tests/_assert.sh`
Expected: 경로 출력(파일 존재)

- [ ] **Step 3: Commit**

```bash
git add .github/scripts/review/tests/_assert.sh
git commit -m "test(ci): 리뷰 스크립트 단위 테스트 assert 헬퍼 추가"
```

---

### Task 1: lib_keys.sh — login → 키 SUFFIX 매핑

현행 `pr-review.yml`의 `case "$login_lc"` 블록(3곳 복붙)을 단일 함수로.

**Files:**
- Create: `.github/scripts/review/lib_keys.sh`
- Test: `.github/scripts/review/tests/test_keys.sh`

- [ ] **Step 1: 실패 테스트 작성**

`.github/scripts/review/tests/test_keys.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_keys.sh"
echo "test_keys"
assert_eq "$(key_suffix_for gudals-kim)" "GUDALS" "gudals-kim → GUDALS"
assert_eq "$(key_suffix_for GUDALS-KIM)" "GUDALS" "대문자도 매핑(대소문자 무시)"
assert_eq "$(key_suffix_for seedplan005)" "CHOI" "seedplan005 → CHOI"
assert_eq "$(key_suffix_for jwchoi42)" "CHOI" "jwchoi42 → CHOI"
assert_eq "$(key_suffix_for jeonj95)" "JEON" "jeonj95 → JEON"
assert_eq "$(key_suffix_for unistuj)" "JEON" "unistuj → JEON"
assert_eq "$(key_suffix_for someone-else)" "" "미매핑 → 빈 문자열"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_keys.sh`
Expected: FAIL — `lib_keys.sh` 없음 (`No such file`)

- [ ] **Step 3: 구현**

`.github/scripts/review/lib_keys.sh`:

```bash
#!/usr/bin/env bash
# GitHub login → 작성자별 API 키 SUFFIX 매핑(단일 소스).
# 사용처: secrets[format('GEMINI_KEY_{0}', <suffix>)]
key_suffix_for() {
  local login_lc
  login_lc=$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')
  case "$login_lc" in
    gudals-kim) echo "GUDALS" ;;
    seedplan005|jwchoi42) echo "CHOI" ;;
    jeonj95|unistuj) echo "JEON" ;;
    *) echo "" ;;
  esac
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_keys.sh`
Expected: `7 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/review/lib_keys.sh .github/scripts/review/tests/test_keys.sh
git commit -m "feat(ci): 리뷰어 키 매핑 lib_keys.sh 추출"
```

---

### Task 2: lib_ai.sh — 백오프 재시도 + 실패 분류

모든 Gemini/OpenAI HTTP 호출을 감싸 429/503/RESOURCE_EXHAUSTED 시 재시도한다.
순수 로직(`ai_is_rate_limited`, `ai_classify`)을 분리해 테스트한다.

**Files:**
- Create: `.github/scripts/review/lib_ai.sh`
- Test: `.github/scripts/review/tests/test_ai.sh`

- [ ] **Step 1: 실패 테스트 작성**

`.github/scripts/review/tests/test_ai.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_ai.sh"
echo "test_ai"
# 1) rate-limit 감지
ai_is_rate_limited '{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}'; assert_rc $? 0 "429 본문 → rate limited(rc0)"
ai_is_rate_limited 'oops rate limit exceeded'; assert_rc $? 0 "rate limit 문구 → rc0"
ai_is_rate_limited '{"candidates":[]}'; assert_rc $? 1 "정상 응답 → not limited(rc1)"
# 2) 실패 분류: quota vs transient
assert_eq "$(ai_classify '{"error":{"code":429}}')" "quota" "429 → quota"
assert_eq "$(ai_classify 'insufficient_quota')" "quota" "insufficient_quota → quota"
assert_eq "$(ai_classify 'some 503 overloaded')" "transient" "503 → transient"
# 3) ai_retry: 모킹된 호출이 2번째에 성공하면 재시도로 0 반환
attempts=0
mockcall() { attempts=$((attempts+1)); [ "$attempts" -ge 2 ]; }
AI_RETRY_SLEEP=0 ai_retry mockcall; rc=$?
assert_rc "$rc" 0 "ai_retry: 2번째 성공 시 rc0"
assert_eq "$attempts" "2" "ai_retry: 정확히 2회 시도"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_ai.sh`
Expected: FAIL — `lib_ai.sh` 없음

- [ ] **Step 3: 구현**

`.github/scripts/review/lib_ai.sh`:

```bash
#!/usr/bin/env bash
# AI 호출 공용: rate-limit 감지 / 실패 분류 / 지수 백오프 재시도.
# AI_RETRY_SLEEP(초, 기본 20), AI_RETRY_MAX(기본 2)로 조정 가능(테스트에서 0으로 단축).

ai_is_rate_limited() { # $1=응답 본문. rate-limit이면 rc0.
  printf '%s' "${1:-}" | grep -qiE 'RESOURCE_EXHAUSTED|insufficient_quota|"code": ?429|rate.?limit|\b429\b|\b503\b|overloaded'
}

ai_classify() { # $1=로그/응답 → "quota" | "transient"
  if printf '%s' "${1:-}" | grep -qiE 'RESOURCE_EXHAUSTED|insufficient_quota|rate.?limit|\b429\b'; then
    echo "quota"
  else
    echo "transient"
  fi
}

# ai_retry CMD [ARGS...] — CMD가 rc0 낼 때까지 지수 백오프로 최대 AI_RETRY_MAX회 재시도.
# CMD는 일시 실패 시 비0을 반환해야 한다(호출자가 응답 검사 후 false를 반환하도록 래핑).
ai_retry() {
  local max="${AI_RETRY_MAX:-2}" base="${AI_RETRY_SLEEP:-20}" n=0 delay
  while :; do
    n=$((n+1))
    if "$@"; then return 0; fi
    if [ "$n" -ge "$max" ]; then return 1; fi
    delay=$(( base * n ))   # 20s, 40s ...
    [ "$delay" -gt 0 ] && sleep "$delay"
  done
}

# gemini_generate KEY MODEL PROMPT_JSON_FILE OUT_FILE — generateContent 1회 호출.
# rate-limit/HTTP 오류면 rc1(ai_retry가 재시도). 성공 시 OUT_FILE에 본문 저장 후 rc0.
gemini_generate() {
  local key="$1" model="$2" payload="$3" out="$4"
  local code
  code=$(curl -s -o "$out" -w '%{http_code}' --max-time 30 -X POST \
    "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${key}" \
    -H "Content-Type: application/json" --data-binary "@${payload}") || code="000"
  if [ "$code" != "200" ] || ai_is_rate_limited "$(cat "$out" 2>/dev/null)"; then return 1; fi
  return 0
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_ai.sh`
Expected: `7 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/review/lib_ai.sh .github/scripts/review/tests/test_ai.sh
git commit -m "feat(ci): AI 호출 백오프 재시도 lib_ai.sh 추가"
```

---

### Task 3: lib_comments.sh — 코멘트 정리/실패 알림/진행/한국어화

현행 `cleanup_notices`/`notify_failure`/`localize_comments`/진행 코멘트 trap을 단일 파일로.
순수 로직(`localize_review_body`, `localize_suggestions_body`)은 sed 파이프이므로 단위 테스트한다.

**Files:**
- Create: `.github/scripts/review/lib_comments.sh`
- Test: `.github/scripts/review/tests/test_comments.sh`

- [ ] **Step 1: 실패 테스트 작성**

`.github/scripts/review/tests/test_comments.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_comments.sh"
echo "test_comments"
assert_eq "$(printf '## PR Reviewer Guide' | localize_review_body)" "## PR 리뷰 가이드" "리뷰 가이드 제목 한국어화"
assert_eq "$(printf 'Estimated effort to review' | localize_review_body)" "리뷰 예상 난이도" "난이도 라벨"
assert_eq "$(printf 'Recommended focus areas for review' | localize_review_body)" "중점 리뷰 영역" "중점 영역 라벨"
assert_eq "$(printf '## PR Code Suggestions' | localize_suggestions_body)" "## PR 코드 개선 제안" "코드 제안 제목"
assert_eq "$(printf 'Why: ' | localize_suggestions_body)" "이유: " "Why 라벨"
# 안내 코멘트 판별 정규식: 진행/실패/사용량 코멘트는 notice이고 실제 리뷰는 아님
is_notice_body '## 🔍 AI 코드 리뷰를 진행하고 있어요'; assert_rc $? 0 "진행 코멘트 → notice"
is_notice_body '## PR 리뷰 가이드'; assert_rc $? 1 "리뷰 결과 → notice 아님"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_comments.sh`
Expected: FAIL — `lib_comments.sh` 없음

- [ ] **Step 3: 구현**

`.github/scripts/review/lib_comments.sh` (현행 sed 맵과 curl 헬퍼를 단일화):

```bash
#!/usr/bin/env bash
# 리뷰 코멘트 공용: 안내 코멘트 정리 / 실패 알림 / 진행 코멘트 / 한국어 라벨 치환.
# 필요 env: GITHUB_TOKEN, GITHUB_API_URL, GITHUB_REPOSITORY, PR_NUMBER

# ── 안내성 코멘트(진행/실패/사용량/심층권장) 본문 판별: notice면 rc0 ──
is_notice_body() {
  printf '%s' "${1:-}" | grep -qE \
    '^(Failed to generate code suggestions for PR|Preparing review\.\.\.|Preparing suggestions\.\.\.)$|^## ⏳ 오늘의 AI 리뷰|^## ⚠️ AI 리뷰를 완료하지|^## 🔍 AI 코드 리뷰를 진행|^## 💡 심층 리뷰를 권장'
}

# ── 리뷰 가이드 본문 영문 라벨 → 한국어 (stdin → stdout) ──
localize_review_body() {
  sed \
    -e 's/## Incremental PR Reviewer Guide/## 증분 PR 리뷰 가이드/g' \
    -e 's/## PR Reviewer Guide/## PR 리뷰 가이드/g' \
    -e 's/Here are some key observations to aid the review process:/리뷰에 참고할 주요 관찰 사항입니다:/g' \
    -e 's/Estimated effort to review/리뷰 예상 난이도/g' \
    -e 's/No relevant tests/관련 테스트 없음/g' \
    -e 's/PR contains tests/테스트 포함됨/g' \
    -e 's/No security concerns identified/보안 이슈 없음/g' \
    -e 's/Security concerns/보안 이슈/g' \
    -e 's/No major issues detected/주요 이슈 없음/g' \
    -e 's/Recommended focus areas for review/중점 리뷰 영역/g' \
    -e 's/No TODO sections/TODO 섹션 없음/g' \
    -e 's/TODO sections/TODO 섹션/g' \
    -e 's/Contribution time estimate/기여 소요 시간 추정/g' \
    -e 's/Ticket compliance check/티켓 준수 확인/g' \
    -e 's/Relevant ticket/관련 티켓/g'
}

# ── 코드 제안 본문 영문 라벨 → 한국어 (stdin → stdout) ──
localize_suggestions_body() {
  sed \
    -e 's/## PR Code Suggestions/## PR 코드 개선 제안/g' \
    -e 's/No code suggestions found for the PR./✅ 개선할 점을 찾지 못했어요 — 코드가 깔끔합니다! 👍 (사용량 소진이 아닌 정상 완료입니다)/g' \
    -e 's#<strong>Category</strong>#<strong>분류</strong>#g' \
    -e 's/<strong>Suggestion/<strong>제안/g' \
    -e 's#<strong>Impact</strong>#<strong>영향도</strong>#g' \
    -e 's/Suggestion importance\[1-10\]:/제안 중요도[1-10]:/g' \
    -e 's/Why: /이유: /g'
}

_api() { echo "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"; }

# ── 안내성 코멘트만 삭제(리뷰 결과물 보존) ──
cleanup_notices() {
  local api; api="$(_api)"
  curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments?per_page=100" \
    | jq -r '.[] | select(.user!=null and .user.type=="Bot" and .body!=null and ((.body=="Failed to generate code suggestions for PR") or (.body=="Preparing review...") or (.body=="Preparing suggestions...") or (.body|test("^## ⏳ 오늘의 AI 리뷰")) or (.body|test("^## ⚠️ AI 리뷰를 완료하지")) or (.body|test("^## 🔍 AI 코드 리뷰를 진행")) or (.body|test("^## 💡 심층 리뷰를 권장")))) | .id' \
    | while read -r cid; do
        [ -n "$cid" ] && curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" "$api/issues/comments/${cid}" >/dev/null || true
      done || true
}

# ── 진행 코멘트 게시(전역 PROGRESS_ID 설정); clear_progress를 trap에 등록해 사용 ──
post_progress() {
  local api; api="$(_api)"
  local body='## 🔍 AI 코드 리뷰를 진행하고 있어요\n\n⏳ 변경된 코드를 살펴보는 중입니다 — 보통 1~2분 정도 걸려요.\n\n리뷰가 끝나면 이 안내는 자동으로 사라지고 결과가 게시됩니다.'
  PROGRESS_ID=$(curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$(printf "$body")" '{body:$b}')" | jq -r '.id // empty' || true)
}
clear_progress() {
  [ -z "${PROGRESS_ID:-}" ] && return 0
  curl -s -X DELETE -H "Authorization: Bearer $GITHUB_TOKEN" "$(_api)/issues/comments/${PROGRESS_ID}" >/dev/null || true
  PROGRESS_ID=""
}

# ── 전 모델 실패 시: 영문 실패 코멘트 정리 후 한국어 안내. $1=로그파일 $2=재요청명령 ──
notify_failure() {
  local log="$1" retry="${2:-/gemini-review}" api; api="$(_api)"
  cleanup_notices
  local msg
  if [ "$(ai_classify "$(cat "$log" 2>/dev/null)")" = "quota" ]; then
    msg=$(printf '## ⏳ 오늘의 AI 리뷰 사용량을 모두 사용했어요\n\n무료 등급의 **일일 호출 한도(RPD)** 에 도달해 이번 리뷰를 완료하지 못했습니다.\n\n👉 잠시 후 한도가 회복되면 `%s` 로 다시 시도해 주세요. 🙏' "$retry")
  else
    msg=$(printf '## ⚠️ AI 리뷰를 완료하지 못했어요\n\n일시적인 오류로 리뷰 생성에 실패했습니다. 잠시 후 `%s` 로 다시 시도해 주세요.' "$retry")
  fi
  curl -s -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/issues/${PR_NUMBER}/comments" -d "$(jq -n --arg b "$msg" '{body:$b}')" >/dev/null
}

# ── 게시된 리뷰 가이드/코드 제안 코멘트를 한국어화(최신 1건씩) ──
localize_comments() {
  local api; api="$(_api)"
  local comments rid sid body
  comments=$(curl -s -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$api/issues/${PR_NUMBER}/comments?per_page=100")
  rid=$(printf '%s' "$comments" | jq -r '[.[]|select(.body!=null and (.body|test("PR Reviewer Guide")))]|last|.id // empty')
  if [ -n "$rid" ]; then
    body=$(printf '%s' "$comments" | jq -r --argjson id "$rid" '.[]|select(.id==$id)|.body' | localize_review_body)
    curl -s -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$api/issues/comments/${rid}" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
  fi
  sid=$(printf '%s' "$comments" | jq -r '[.[]|select(.body!=null and (.body|test("PR Code Suggestions")))]|last|.id // empty')
  if [ -n "$sid" ]; then
    body=$(printf '%s' "$comments" | jq -r --argjson id "$sid" '.[]|select(.id==$id)|.body' | localize_suggestions_body)
    curl -s -X PATCH -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$api/issues/comments/${sid}" -d "$(jq -n --arg b "$body" '{body:$b}')" >/dev/null
  fi
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_comments.sh`
Expected: `7 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/review/lib_comments.sh .github/scripts/review/tests/test_comments.sh
git commit -m "feat(ci): 코멘트 정리/실패알림/한국어화 lib_comments.sh 추출"
```

---

### Task 4: run_pr_agent.sh — pr-agent 실행 + 멱등 재시도

하드 실패 마커 판정(`has_fail_marker`)을 순수 로직으로 분리해 테스트한다.

**Files:**
- Create: `.github/scripts/review/run_pr_agent.sh`
- Test: `.github/scripts/review/tests/test_run_pr_agent.sh`

- [ ] **Step 1: 실패 테스트 작성**

`.github/scripts/review/tests/test_run_pr_agent.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/run_pr_agent.sh"
echo "test_run_pr_agent"
log=$(mktemp)
printf 'Failed to review PR: boom\n' > "$log"
has_fail_marker "$log"; assert_rc $? 0 "하드 실패 마커 감지(rc0)"
printf 'INFO Reviewing PR ... published review\n' > "$log"
has_fail_marker "$log"; assert_rc $? 1 "정상 로그 → 마커 없음(rc1)"
printf 'transient 429 but litellm recovered, review posted\n' > "$log"
has_fail_marker "$log"; assert_rc $? 1 "일시 429 복구 → 마커 아님(rc1)"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_run_pr_agent.sh`
Expected: FAIL — `run_pr_agent.sh` 없음

- [ ] **Step 3: 구현**

`.github/scripts/review/run_pr_agent.sh`:

```bash
#!/usr/bin/env bash
# pr-agent docker 실행 래퍼. 하드 실패(코멘트 미게시) 마커에서만 재시도(멱등).
# 필요 env: PR_AGENT_IMAGE, GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_API_URL, GITHUB_SERVER_URL
PR_AGENT_FAIL_MARKERS="${PR_AGENT_FAIL_MARKERS:-Failed to review PR:|Failed to generate code suggestions for PR, error:|Failed to generate prediction with any model}"

has_fail_marker() { grep -qE "$PR_AGENT_FAIL_MARKERS" "$1"; }

# run_pr_agent EVENT_NAME EVENT_FILE OUT_LOG -- DOCKER_ENV_ARGS...
# docker run 후 status/마커 판정. 하드 실패면 1회 재실행(중복 코멘트 방지).
# 성공 rc0 / 최종 실패 rc1(로그는 OUT_LOG에 남음).
run_pr_agent() {
  local event_name="$1" event_file="$2" out="$3"; shift 3
  [ "$1" = "--" ] && shift
  local attempt st
  for attempt in 1 2; do
    set +e
    docker run --rm \
      -e GITHUB_TOKEN \
      -e GITHUB_EVENT_NAME="$event_name" \
      -e GITHUB_REPOSITORY="$GITHUB_REPOSITORY" \
      -e GITHUB_API_URL="$GITHUB_API_URL" \
      -e GITHUB_SERVER_URL="$GITHUB_SERVER_URL" \
      -e GITHUB_EVENT_PATH=/github/workflow/event.json \
      "$@" \
      -v "${event_file}:/github/workflow/event.json:ro" \
      "$PR_AGENT_IMAGE" 2>&1 | tee "$out"
    st=${PIPESTATUS[0]}
    set -e
    if [ "$st" -eq 0 ] && ! has_fail_marker "$out"; then return 0; fi
    # 하드 실패 → 첫 시도면 백오프 후 재실행
    [ "$attempt" -eq 1 ] && { echo "::warning::pr-agent 하드 실패 — 30초 후 1회 재시도"; sleep 30; }
  done
  return 1
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_run_pr_agent.sh`
Expected: `3 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/review/run_pr_agent.sh .github/scripts/review/tests/test_run_pr_agent.sh
git commit -m "feat(ci): pr-agent 실행+멱등 재시도 run_pr_agent.sh 추가"
```

---

### Task 5: detect_build_changes.sh — 빌드 영향 글로브 판정

android/ios 빌드 체크가 공유한다. 글로브 매칭이 순수 로직이라 테스트하기 좋다.

**Files:**
- Create: `.github/scripts/build/detect_build_changes.sh`
- Test: `.github/scripts/review/tests/test_detect_build.sh`

- [ ] **Step 1: 실패 테스트 작성**

`.github/scripts/review/tests/test_detect_build.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/../.." && pwd)"
. "$DIR/scripts/review/tests/_assert.sh"
. "$DIR/scripts/build/detect_build_changes.sh"
echo "test_detect_build"
FILES=$'apps/frontend/app/src/Main.kt\ndocs/readme.md'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "y" "app/ 변경 → android"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "n" "app/만 → ios 아님"
FILES=$'apps/frontend/shared/src/Common.kt'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "y" "shared/ → android"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "y" "shared/ → ios"
FILES=$'apps/frontend/app/build.gradle.kts'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "y" "*.gradle.kts → android"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "y" "*.gradle.kts → ios"
FILES=$'apps/frontend/CashChatIOS/App.swift'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "n" "CashChatIOS/만 → android 아님"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "y" "CashChatIOS/ → ios"
FILES=$'docs/x.md\napps/backend/Main.kt'
assert_eq "$(printf '%s' "$FILES" | affects_android && echo y || echo n)" "n" "무관 변경 → android 아님"
assert_eq "$(printf '%s' "$FILES" | affects_ios && echo y || echo n)" "n" "무관 변경 → ios 아님"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_detect_build.sh`
Expected: FAIL — `detect_build_changes.sh` 없음

- [ ] **Step 3: 구현**

`.github/scripts/build/detect_build_changes.sh`:

```bash
#!/usr/bin/env bash
# 빌드 영향 파일 판정(stdin = 변경 파일 목록, 한 줄에 하나). 영향 있으면 rc0.
# 라벨 글로브보다 넓게: shared/ 와 Gradle 설정까지 포함.
_AND_RE='^apps/frontend/app/|^apps/frontend/shared/|^apps/frontend/.*\.gradle\.kts$|^apps/frontend/gradle/'
_IOS_RE='^apps/frontend/CashChatIOS/|^apps/frontend/shared/|^apps/frontend/.*\.gradle\.kts$|^apps/frontend/gradle/'
affects_android() { grep -qE "$_AND_RE"; }
affects_ios() { grep -qE "$_IOS_RE"; }

# emit_changed_range — synchronize면 이번 push(before...head), 아니면 PR 전체(base...head)
# 필요 env: ACTION, BEFORE, HEAD_SHA, BASE_SHA
changed_range() {
  if [ "${ACTION:-}" = "synchronize" ] && [ -n "${BEFORE:-}" ] && ! printf '%s' "$BEFORE" | grep -qE '^0+$'; then
    echo "${BEFORE}...${HEAD_SHA}"
  else
    echo "${BASE_SHA}...${HEAD_SHA}"
  fi
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_detect_build.sh`
Expected: `10 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/build/detect_build_changes.sh .github/scripts/review/tests/test_detect_build.sh
git commit -m "feat(ci): 빌드 영향 글로브 판정 detect_build_changes.sh 추가"
```

---

### Task 6: android-build-check.yml — detect+gate 전환

**Files:**
- Modify: `.github/workflows/android-build-check.yml`

- [ ] **Step 1: detect 잡 + gate 구조로 교체**

`.github/workflows/android-build-check.yml`을 아래로 전면 교체:

```yaml
name: Android Build Check

on:
  pull_request:
    types: [opened, synchronize, reopened]
    branches: [dev]

concurrency:
  group: android-build-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  detect:
    runs-on: ubuntu-latest
    outputs:
      build: ${{ steps.f.outputs.build }}
    steps:
      - name: Checkout (scripts only)
        uses: actions/checkout@v5
      - name: Detect Android-relevant changes
        id: f
        env:
          GH_TOKEN: ${{ github.token }}
          REPO: ${{ github.repository }}
          ACTION: ${{ github.event.action }}
          BEFORE: ${{ github.event.before }}
          HEAD_SHA: ${{ github.event.pull_request.head.sha }}
          BASE_SHA: ${{ github.event.pull_request.base.sha }}
        run: |
          set -euo pipefail
          . .github/scripts/build/detect_build_changes.sh
          RANGE="$(changed_range)"
          echo "::notice::비교 범위 $RANGE"
          FILES=$(gh api "repos/${REPO}/compare/${RANGE}" --paginate -q '.files[].filename' 2>/dev/null || true)
          printf '변경 파일:\n%s\n' "$FILES"
          if printf '%s\n' "$FILES" | affects_android; then
            echo "build=true" >> "$GITHUB_OUTPUT"; echo "::notice::Android 영향 변경 — 빌드 진행"
          else
            echo "build=false" >> "$GITHUB_OUTPUT"; echo "::notice::Android 영향 없음 — 빌드 잡 스킵"
          fi

  build-check:
    needs: detect
    if: needs.detect.outputs.build == 'true'
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: apps/frontend
    steps:
      - name: Checkout
        uses: actions/checkout@v5
      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Cache Gradle
        uses: actions/cache@v5
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('apps/frontend/**/*.gradle.kts', 'apps/frontend/gradle/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-
      - name: Write google-services.json
        env:
          GOOGLE_SERVICES_JSON: ${{ secrets.GOOGLE_SERVICES_JSON }}
        run: |
          if [ -n "$GOOGLE_SERVICES_JSON" ]; then
            printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json
          else
            echo "⚠️ 시크릿 없음 — 더미 google-services.json 생성"
            cat > app/google-services.json << 'EOF'
          {
            "project_info": { "project_number": "000000000000", "project_id": "dummy-project", "storage_bucket": "dummy-project.appspot.com" },
            "client": [
              { "client_info": { "mobilesdk_app_id": "1:000000000000:android:0000000000000000", "android_client_info": { "package_name": "com.nomadclub.cashchat" } }, "api_key": [ { "current_key": "DUMMY_KEY_FOR_BUILD_CHECK" } ] },
              { "client_info": { "mobilesdk_app_id": "1:000000000000:android:0000000000000001", "android_client_info": { "package_name": "com.nomadclub.cashchat.dev" } }, "api_key": [ { "current_key": "DUMMY_KEY_FOR_BUILD_CHECK" } ] }
            ],
            "configuration_version": "1"
          }
          EOF
          fi
      - name: Make gradlew executable
        run: chmod +x gradlew
      - name: Build Debug APK (검증용)
        run: ./gradlew :app:assembleDebug
```

- [ ] **Step 2: YAML 파싱 검증**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/android-build-check.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/android-build-check.yml
git commit -m "ci(android): detect+gate로 빌드 영향 변경시에만 실행"
```

---

### Task 7: ios-build-check.yml — detect+gate 전환

**Files:**
- Modify: `.github/workflows/ios-build-check.yml`

- [ ] **Step 1: detect 잡 + gate 구조로 교체**

`.github/workflows/ios-build-check.yml`을 아래로 전면 교체:

```yaml
name: iOS Build Check

on:
  pull_request:
    types: [opened, synchronize, reopened]
    branches: [dev]

concurrency:
  group: ios-build-${{ github.event.pull_request.number }}
  cancel-in-progress: true

jobs:
  detect:
    runs-on: ubuntu-latest
    outputs:
      build: ${{ steps.f.outputs.build }}
    steps:
      - name: Checkout (scripts only)
        uses: actions/checkout@v5
      - name: Detect iOS-relevant changes
        id: f
        env:
          GH_TOKEN: ${{ github.token }}
          REPO: ${{ github.repository }}
          ACTION: ${{ github.event.action }}
          BEFORE: ${{ github.event.before }}
          HEAD_SHA: ${{ github.event.pull_request.head.sha }}
          BASE_SHA: ${{ github.event.pull_request.base.sha }}
        run: |
          set -euo pipefail
          . .github/scripts/build/detect_build_changes.sh
          RANGE="$(changed_range)"
          echo "::notice::비교 범위 $RANGE"
          FILES=$(gh api "repos/${REPO}/compare/${RANGE}" --paginate -q '.files[].filename' 2>/dev/null || true)
          printf '변경 파일:\n%s\n' "$FILES"
          if printf '%s\n' "$FILES" | affects_ios; then
            echo "build=true" >> "$GITHUB_OUTPUT"; echo "::notice::iOS 영향 변경 — 빌드 진행"
          else
            echo "build=false" >> "$GITHUB_OUTPUT"; echo "::notice::iOS 영향 없음 — 빌드 잡 스킵"
          fi

  build-check:
    needs: detect
    if: needs.detect.outputs.build == 'true'
    runs-on: macos-latest
    defaults:
      run:
        working-directory: apps/frontend/CashChatIOS
    steps:
      - name: Checkout
        uses: actions/checkout@v5
      - name: Set up JDK 21
        uses: actions/setup-java@v5
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Cache Gradle
        uses: actions/cache@v5
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('apps/frontend/**/*.gradle.kts', 'apps/frontend/gradle/libs.versions.toml') }}
          restore-keys: |
            ${{ runner.os }}-gradle-
      - name: Cache SPM packages
        uses: actions/cache@v5
        with:
          path: ~/Library/Developer/Xcode/DerivedData/*/SourcePackages/checkouts
          key: ${{ runner.os }}-spm-${{ hashFiles('apps/frontend/CashChatIOS/CashChatIOS.xcodeproj/project.pbxproj') }}
          restore-keys: |
            ${{ runner.os }}-spm-
      - name: Create Secrets.swift
        env:
          IOS_GOOGLE_CLIENT_ID: ${{ secrets.IOS_GOOGLE_CLIENT_ID }}
          GOOGLE_CLIENT_ID: ${{ secrets.GOOGLE_CLIENT_ID }}
          API_SERVER_BASE_URL: ${{ secrets.API_SERVER_BASE_URL }}
        run: |
          cat > CashChatIOS/Secrets.swift << EOF
          // Auto-generated for CI build check
          enum Secrets {
              static let googleIOSClientId = "$IOS_GOOGLE_CLIENT_ID"
              static let googleWebClientId = "$GOOGLE_CLIENT_ID"
              static let apiBaseUrl = "$API_SERVER_BASE_URL"
          }
          EOF
      - name: Build iOS app (simulator, 검증용)
        run: |
          xcodebuild \
            -project CashChatIOS.xcodeproj \
            -scheme CashChatIOS \
            -destination 'generic/platform=iOS Simulator' \
            -configuration Debug \
            CODE_SIGNING_ALLOWED=NO \
            build
```

- [ ] **Step 2: YAML 파싱 검증**

Run: `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/ios-build-check.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ios-build-check.yml
git commit -m "ci(ios): detect+gate로 빌드 영향 변경시에만 실행"
```

---

### Task 8: resolve_threads.sh — push 시 스레드 판단 (#2)

현행 `auto_resolve_addressed_threads`를 스크립트로 옮기되, lib_ai 백오프를 적용하고
미해결인데 파생이슈로 판단되면 코멘트만 남기는 분기를 추가한다.

**Files:**
- Create: `.github/scripts/review/resolve_threads.sh`
- Test: `.github/scripts/review/tests/test_resolve_threads.sh`

- [ ] **Step 1: 실패 테스트 작성 (verdict 파싱 순수 로직)**

`.github/scripts/review/tests/test_resolve_threads.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_ai.sh"
. "$DIR/resolve_threads.sh"
echo "test_resolve_threads"
assert_eq "$(parse_verdict $'yes\n로직이 수정됨')" "yes" "첫 줄 yes 추출"
assert_eq "$(parse_verdict $'No\n아직 미반영')" "no" "첫 줄 no 추출(소문자화)"
assert_eq "$(parse_verdict $'**YES**\n근거')" "yes" "마크다운/공백 제거"
assert_eq "$(parse_reason $'yes\n변수명이 개선되었습니다')" "변수명이 개선되었습니다" "둘째 줄 사유 추출"
assert_eq "$(parse_reason $'yes')" "" "사유 없으면 빈 문자열"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_resolve_threads.sh`
Expected: FAIL — `resolve_threads.sh` 없음

- [ ] **Step 3: 구현**

`.github/scripts/review/resolve_threads.sh`:

```bash
#!/usr/bin/env bash
# push(synchronize) 시: 변경 라인에 걸린 미해결 리뷰 스레드를 model1로 판단.
# yes → 근거 답글 + resolve / no → 코멘트만(파생이슈 가능). lib_ai.sh를 먼저 source할 것.
# 필요 env: GITHUB_TOKEN, GITHUB_API_URL, GITHUB_REPOSITORY, PR_NUMBER, GEMINI_KEY, GEMINI_MODEL(=model1)

parse_verdict() { printf '%s' "${1:-}" | head -1 | tr '[:upper:]' '[:lower:]' | tr -d ' *`' ; }
parse_reason()  { printf '%s' "${1:-}" | sed -n '2p' | sed 's/^[[:space:]]*//' ; }

resolve_threads() {
  local api="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
  local owner="${GITHUB_REPOSITORY%%/*}" name="${GITHUB_REPOSITORY##*/}"

  local pr_files_json changed_files
  pr_files_json=$(curl -s --max-time 15 -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/pulls/${PR_NUMBER}/files?per_page=100" 2>/dev/null || echo '[]')
  changed_files=$(printf '%s' "$pr_files_json" | jq -r '.[].filename' 2>/dev/null || true)
  [ -z "$changed_files" ] && { echo "::notice::변경 파일 없음"; return 0; }

  local threads_json unresolved
  threads_json=$(curl -s --max-time 15 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/graphql" \
    -d "$(jq -n --arg owner "$owner" --arg name "$name" --argjson number "$PR_NUMBER" \
      '{query:"query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){pullRequest(number:$number){reviewThreads(first:100){nodes{id isResolved comments(first:1){nodes{body path databaseId}}}}}}}",variables:{owner:$owner,name:$name,number:$number}}')" \
    2>/dev/null || echo '{}')
  unresolved=$(printf '%s' "$threads_json" | jq -c '.data.repository.pullRequest.reviewThreads.nodes // [] | map(select(.isResolved==false)) | .[]' 2>/dev/null || true)
  [ -z "$unresolved" ] && { echo "::notice::미해결 스레드 없음"; return 0; }

  local model="${GEMINI_MODEL##gemini/}" resolved=0
  while IFS= read -r node; do
    local tid body fpath cdb fdiff prompt payload out raw verdict reason
    tid=$(printf '%s' "$node" | jq -r '.id // empty')
    body=$(printf '%s' "$node" | jq -r '.comments.nodes[0].body // empty')
    fpath=$(printf '%s' "$node" | jq -r '.comments.nodes[0].path // empty')
    cdb=$(printf '%s' "$node" | jq -r '.comments.nodes[0].databaseId // empty')
    [ -z "$tid" ] && continue
    printf '%s' "$changed_files" | grep -qxF "$fpath" || continue   # 이번에 바뀐 파일만

    fdiff=$(printf '%s' "$pr_files_json" | jq -r --arg p "$fpath" '.[]|select(.filename==$p)|.patch // ""' 2>/dev/null | head -80 || true)
    prompt=$(printf '코드 리뷰 코멘트가 아래 diff로 해결되었나요?\n첫 줄에 "yes" 또는 "no"만 쓰고, 둘째 줄에 한국어 한 문장으로 근거(yes면 해결 이유, no면 남은 이슈/파생 우려)를 쓰세요.\n\n파일: %s\n코멘트: %s\n\nDiff:\n%s' "$fpath" "$body" "$fdiff")
    payload=$(mktemp); out=$(mktemp)
    jq -n --arg p "$prompt" '{contents:[{role:"user",parts:[{text:$p}]}],generationConfig:{maxOutputTokens:256,temperature:0}}' > "$payload"
    if ! ai_retry gemini_generate "$GEMINI_KEY" "$model" "$payload" "$out"; then
      echo "::warning::스레드 판단 실패(쿼터/오류) — 건너뜀: $fpath"; sleep 3; continue
    fi
    raw=$(jq -r '.candidates[0].content.parts[0].text // "no"' "$out" 2>/dev/null || echo "no")
    sleep 3   # RPM 보호
    verdict=$(parse_verdict "$raw"); reason=$(parse_reason "$raw")

    if [[ "$verdict" == yes* ]]; then
      [ -n "$cdb" ] && [ "$cdb" != "null" ] && curl -s --max-time 10 -X POST \
        -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
        "$api/pulls/${PR_NUMBER}/comments/${cdb}/replies" \
        -d "$(jq -n --arg b "$(printf '🤖 **자동 리졸브 판단**\n\n%s\n\n_최신 변경에서 해결된 것으로 판단되어 자동 리졸브합니다. (%s)_' "${reason:-최신 변경에서 해결된 것으로 판단됩니다.}" "$model")" '{body:$b}')" >/dev/null 2>&1 || true
      curl -s --max-time 10 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
        "https://api.github.com/graphql" \
        -d "$(jq -n --arg id "$tid" '{query:"mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}",variables:{id:$id}}')" >/dev/null 2>&1 || true
      resolved=$((resolved+1)); echo "::notice::✅ 리졸브: $fpath"
    else
      # 미해결: 파생이슈/남은 우려를 답글로만 남기고 resolve하지 않음
      [ -n "$cdb" ] && [ "$cdb" != "null" ] && curl -s --max-time 10 -X POST \
        -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
        "$api/pulls/${PR_NUMBER}/comments/${cdb}/replies" \
        -d "$(jq -n --arg b "$(printf '🤖 **변경 검토**\n\n%s\n\n_아직 해결되지 않았거나 추가 확인이 필요해 보여 리졸브하지 않았습니다._' "${reason:-남은 우려가 있어 보입니다.}")" '{body:$b}')" >/dev/null 2>&1 || true
      echo "::notice::↺ 미해결 유지: $fpath"
    fi
  done <<< "$unresolved"
  echo "::notice::총 ${resolved}개 자동 리졸브"
  return 0
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_resolve_threads.sh`
Expected: `5 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/review/resolve_threads.sh .github/scripts/review/tests/test_resolve_threads.sh
git commit -m "feat(ci): push 스레드 판단 resolve_threads.sh 추가(파생이슈 분기 포함)"
```

---

### Task 9: resolve_command.sh — /resolve AI 판단 + Jira (#4)

현행 `resolve-to-jira-subtask` 잡을 스크립트로 옮기고, **앞단에 AI 판단**을 추가한다.
순수 로직(`parse_resolve_reason`, `extract_jira_parent`)을 테스트한다.

**Files:**
- Create: `.github/scripts/review/resolve_command.sh`
- Test: `.github/scripts/review/tests/test_resolve_command.sh`

- [ ] **Step 1: 실패 테스트 작성**

`.github/scripts/review/tests/test_resolve_command.sh`:

```bash
#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_ai.sh"
. "$DIR/resolve_command.sh"
echo "test_resolve_command"
assert_eq "$(parse_resolve_reason '/resolve "추후 일괄 수정"')" "추후 일괄 수정" "따옴표 사유 추출"
assert_eq "$(parse_resolve_reason '/resolve 이미 반영함')" "이미 반영함" "무따옴표 사유"
assert_eq "$(parse_resolve_reason '/resolve')" "추후 일괄 수정 예정" "사유 없으면 기본값"
assert_eq "$(extract_jira_parent '[CC-342] Apple oauth' 'feature/CC-999')" "CC-342" "제목 우선 추출"
assert_eq "$(extract_jira_parent 'no ticket' 'feature/CC-777')" "CC-777" "제목 없으면 브랜치에서"
assert_eq "$(extract_jira_parent 'nope' 'feature/x')" "" "둘 다 없으면 빈값"
t_summary
```

- [ ] **Step 2: 실패 확인**

Run: `bash .github/scripts/review/tests/test_resolve_command.sh`
Expected: FAIL — `resolve_command.sh` 없음

- [ ] **Step 3: 구현**

`.github/scripts/review/resolve_command.sh`:

```bash
#!/usr/bin/env bash
# /resolve "사유": AI(model1)가 사유+원본 코멘트+diff로 resolve 타당성 판단.
# 타당 → 근거 답글 + Jira 서브태스크 + 스레드 resolve / 부당 → 코멘트만.
# lib_ai.sh를 먼저 source. 필요 env: GITHUB_TOKEN, GITHUB_API_URL, GITHUB_REPOSITORY,
#   PR_NUMBER, PR_TITLE, HEAD_REF, PR_HTML_URL, COMMENT_BODY, COMMENT_ID, IN_REPLY_TO, COMMENTER,
#   GEMINI_KEY, GEMINI_MODEL(=model1), JIRA_BASE_URL, JIRA_EMAIL, JIRA_TOKEN

parse_resolve_reason() {
  local r
  r=$(printf '%s' "${1:-}" | sed -E 's#^/resolve[[:space:]]*##' | head -1)
  r=$(printf '%s' "$r" | sed -E 's/^["“”'"'"']//; s/["“”'"'"']$//; s/[[:space:]]*$//')
  [ -z "$r" ] && r="추후 일괄 수정 예정"
  printf '%s' "$r"
}
extract_jira_parent() { printf '%s %s' "${1:-}" "${2:-}" | grep -oE 'CC-[0-9]+' | head -1 || true; }

# AI 판단: rc0=resolve 타당(yes), rc1=부당(no). 근거는 전역 RESOLVE_REASON_AI에 저장.
RESOLVE_REASON_AI=""
ai_judge_resolve() { # $1=사유 $2=원본코멘트 $3=diff
  local model="${GEMINI_MODEL##gemini/}" payload out prompt raw verdict
  prompt=$(printf '리뷰어가 아래 사유로 이 코드리뷰 스레드를 resolve 요청했습니다.\n사유가 타당한지(코드가 실제로 반영되었거나, 추후 처리로 분류하는 게 합리적인지) 판단하세요.\n첫 줄에 "yes"(리졸브 타당) 또는 "no"(아직 이르다)만, 둘째 줄에 한국어 한 문장 근거.\n\n사유: %s\n원본 코멘트: %s\n\nDiff:\n%s' "$1" "$2" "$3")
  payload=$(mktemp); out=$(mktemp)
  jq -n --arg p "$prompt" '{contents:[{role:"user",parts:[{text:$p}]}],generationConfig:{maxOutputTokens:256,temperature:0}}' > "$payload"
  if ! ai_retry gemini_generate "$GEMINI_KEY" "$model" "$payload" "$out"; then
    RESOLVE_REASON_AI="AI 판단을 가져오지 못해 사유를 신뢰해 처리합니다."; return 0   # 폴백: 타당 처리
  fi
  raw=$(jq -r '.candidates[0].content.parts[0].text // "yes"' "$out" 2>/dev/null || echo "yes")
  verdict=$(printf '%s' "$raw" | head -1 | tr '[:upper:]' '[:lower:]' | tr -d ' *`')
  RESOLVE_REASON_AI=$(printf '%s' "$raw" | sed -n '2p' | sed 's/^[[:space:]]*//')
  [[ "$verdict" == yes* ]]
}

gh_reply() { curl -s --max-time 15 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
  "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/comments/$1/replies" \
  -d "$(jq -n --arg b "$2" '{body:$b}')" >/dev/null || true; }

run_resolve_command() {
  local API="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
  local REASON ROOT_ID ROOT_JSON ORIG_BODY ORIG_PATH ROOT_NODE_ID FDIFF
  REASON="$(parse_resolve_reason "$COMMENT_BODY")"
  ROOT_ID="${IN_REPLY_TO:-$COMMENT_ID}"
  ROOT_JSON=$(curl -s --max-time 15 -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$API/pulls/comments/${ROOT_ID}")
  ORIG_BODY=$(printf '%s' "$ROOT_JSON" | jq -r '.body // ""'); [ -z "$ORIG_BODY" ] && ORIG_BODY="(원본 코멘트를 불러오지 못했습니다)"
  ORIG_PATH=$(printf '%s' "$ROOT_JSON" | jq -r '.path // ""')
  ROOT_NODE_ID=$(printf '%s' "$ROOT_JSON" | jq -r '.node_id // ""')
  FDIFF=$(printf '%s' "$ROOT_JSON" | jq -r '.diff_hunk // ""' | head -60)

  # ── AI 판단 ──
  if ! ai_judge_resolve "$REASON" "$ORIG_BODY" "$FDIFF"; then
    gh_reply "$ROOT_ID" "$(printf '🤖 **resolve 보류**\n\n%s\n\n아직 리졸브하기 이르다고 판단했어요. 반영 후 다시 `/resolve \"사유\"` 해주세요.' "${RESOLVE_REASON_AI:-제시한 사유만으로는 해결을 확인하기 어렵습니다.}")"
    echo "::notice::AI 판단: 보류"; return 0
  fi

  # ── Jira 상위 티켓 ──
  local PARENT PROJECT_KEY SUBTASK_TYPE_ID
  PARENT="$(extract_jira_parent "$PR_TITLE" "$HEAD_REF")"
  if [ -z "$PARENT" ]; then
    gh_reply "$ROOT_ID" "🤖 resolve는 타당하나 Jira 티켓(CC-###)을 못 찾아 서브태스크 없이 스레드만 리졸브할게요."
  else
    PROJECT_KEY="${PARENT%%-*}"
    SUBTASK_TYPE_ID=$(curl -s --max-time 20 --user "${JIRA_EMAIL}:${JIRA_TOKEN}" -H "Accept: application/json" \
      "${JIRA_BASE_URL}/rest/api/3/issue/createmeta/${PROJECT_KEY}/issuetypes" \
      | jq -r '[(.values // .issueTypes // [])[]|select(.subtask==true)][0].id // empty')
    [ -z "$SUBTASK_TYPE_ID" ] && SUBTASK_TYPE_ID=$(curl -s --max-time 20 --user "${JIRA_EMAIL}:${JIRA_TOKEN}" -H "Accept: application/json" \
      "${JIRA_BASE_URL}/rest/api/3/issuetype" | jq -r '[.[]|select(.subtask==true)][0].id // empty')
    if [ -n "$SUBTASK_TYPE_ID" ]; then
      local SUMMARY DESCRIPTION PAYLOAD RESP CODE RBODY NEW_KEY NEW_URL
      SUMMARY=$(printf '[리뷰 후속] %s' "$(printf '%s' "$ORIG_BODY" | tr '\n' ' ' | cut -c1-180)")
      DESCRIPTION=$(jq -n --arg reason "$REASON" --arg orig "$ORIG_BODY" --arg path "$ORIG_PATH" --arg pr "$PR_HTML_URL" --arg who "$COMMENTER" '
        {type:"doc",version:1,content:[
          {type:"paragraph",content:[{type:"text",text:"GitHub PR 리뷰에서 추후 처리로 분류된 항목입니다."}]},
          {type:"paragraph",content:[{type:"text",text:("사유: " + $reason)}]},
          {type:"paragraph",content:[{type:"text",text:("요청자: @" + $who)}]},
          ($path|if .=="" then empty else {type:"paragraph",content:[{type:"text",text:("파일: " + .)}]} end),
          {type:"paragraph",content:[{type:"text",text:"원본 리뷰 코멘트:"}]},
          {type:"blockquote",content:($orig|split("\n")|map(if .=="" then {type:"paragraph"} else {type:"paragraph",content:[{type:"text",text:.}]} end))},
          {type:"paragraph",content:[{type:"text",text:"PR: "},{type:"text",text:$pr,marks:[{type:"link",attrs:{href:$pr}}]}]}
        ]}')
      PAYLOAD=$(jq -n --arg pk "$PROJECT_KEY" --arg parent "$PARENT" --arg tid "$SUBTASK_TYPE_ID" --arg summary "$SUMMARY" --argjson desc "$DESCRIPTION" \
        '{fields:{project:{key:$pk},parent:{key:$parent},issuetype:{id:$tid},summary:$summary,description:$desc}}')
      RESP=$(curl -s --max-time 25 --user "${JIRA_EMAIL}:${JIRA_TOKEN}" -w $'\n%{http_code}' -X POST \
        -H "Content-Type: application/json" -H "Accept: application/json" "${JIRA_BASE_URL}/rest/api/3/issue" --data "$PAYLOAD")
      CODE=$(printf '%s' "$RESP" | tail -1); RBODY=$(printf '%s' "$RESP" | sed '$d')
      if [ "$CODE" = "201" ]; then
        NEW_KEY=$(printf '%s' "$RBODY" | jq -r '.key'); NEW_URL="${JIRA_BASE_URL}/browse/${NEW_KEY}"
        gh_reply "$ROOT_ID" "$(printf '🤖 **resolve 승인** — %s\n\n📌 추후 처리 항목을 Jira 서브태스크로 등록했어요.\n- 서브태스크: [%s](%s)\n- 상위 티켓: %s\n- 사유: %s\n\n이 스레드는 리졸브됩니다. 🙏' "${RESOLVE_REASON_AI:-반영 확인}" "$NEW_KEY" "$NEW_URL" "$PARENT" "$REASON")"
      else
        gh_reply "$ROOT_ID" "🤖 resolve는 타당하나 Jira 서브태스크 생성 실패(HTTP ${CODE}). 스레드만 리졸브할게요."
      fi
    fi
  fi

  # ── 스레드 리졸브 ──
  local THREAD_ID=""
  [ -n "$ROOT_NODE_ID" ] && THREAD_ID=$(curl -s --max-time 15 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/graphql" \
    -d "$(jq -n --arg id "$ROOT_NODE_ID" '{query:"query($id:ID!){node(id:$id){... on PullRequestReviewComment{pullRequestReviewThread{id}}}}",variables:{id:$id}}')" \
    | jq -r '.data.node.pullRequestReviewThread.id // empty')
  [ -n "$THREAD_ID" ] && curl -s --max-time 10 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/graphql" \
    -d "$(jq -n --arg id "$THREAD_ID" '{query:"mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}",variables:{id:$id}}')" >/dev/null || true
  echo "::notice::resolve 완료"
}
```

- [ ] **Step 4: 통과 확인**

Run: `bash .github/scripts/review/tests/test_resolve_command.sh`
Expected: `6 passed, 0 failed`

- [ ] **Step 5: Commit**

```bash
git add .github/scripts/review/resolve_command.sh .github/scripts/review/tests/test_resolve_command.sh
git commit -m "feat(ci): /resolve AI 판단+Jira 서브태스크 resolve_command.sh 추가"
```

---

### Task 10: pr-review.yml 재구성 — 트리거/잡 + 스크립트 호출

전 잡을 스크립트 호출로 전환하고 트리거를 재정의한다. (스크립트 본문은 Task 1~9에서 완성됨)

**Files:**
- Modify: `.github/workflows/pr-review.yml`

- [ ] **Step 1: 헤더/트리거/공통 env 작성**

`.github/workflows/pr-review.yml` 상단을 아래로 교체(라인 1~33 영역):

```yaml
name: PR Code Review (Qodo / pr-agent)

# 공통 리뷰 설정은 .pr_agent.toml. 공유 스크립트는 .github/scripts/review/*.sh.
on:
  # 자동 전체 리뷰는 PR 최초 open 에서만 (push는 아래 review-on-push로 분리)
  pull_request_target:
    types: [opened, synchronize]
    branches: [dev]
  issue_comment:
    types: [created]
  pull_request_review_comment:
    types: [created]

concurrency:
  group: pr-review-${{ github.event.pull_request.number || github.event.issue.number }}
  cancel-in-progress: false

permissions:
  contents: read

env:
  PR_AGENT_IMAGE: "pragent/pr-agent@sha256:70e78800a3ee94ee31a25e43d3b8ef25e8e8fda7ccacfd765f911c9f66b6b048"
  PR_AGENT_FAIL_MARKERS: "Failed to review PR:|Failed to generate code suggestions for PR, error:|Failed to generate prediction with any model"
```

- [ ] **Step 2: resolve-gemini-model / resolve-openai-model 잡 유지**

기존 `resolve-gemini-model`(라인 38~95)과 `resolve-openai-model`(라인 608~657) 잡은 **그대로 유지**한다.
단 `resolve-gemini-model`의 `if:` 조건에서 `pull_request_target`은 그대로 두되, 잡 소비처가 open/push/manual로 나뉘므로 변경 불필요.

- [ ] **Step 3: review-gemini-auto 잡 — open 전용 + /improve 활성화 + 스크립트 호출**

기존 `review-gemini-auto` 잡(라인 100~435) 전체를 아래로 교체:

```yaml
  review-gemini-auto:
    name: Gemini Auto Review
    needs: resolve-gemini-model
    if: github.event_name == 'pull_request_target' && github.event.action == 'opened'
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
      - name: AI Review (Gemini, 공통 키, 라인별)
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          MODEL0: ${{ needs.resolve-gemini-model.outputs.model0 }}
          MODEL1: ${{ needs.resolve-gemini-model.outputs.model1 }}
          GEMINI_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}   # #1 공통 키
          PR_NUMBER: ${{ github.event.pull_request.number }}
          RETRY_CMD: "/gemini-review"
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_ai.sh
          . .github/scripts/review/lib_comments.sh
          . .github/scripts/review/run_pr_agent.sh
          cleanup_notices
          PROGRESS_ID=""; trap clear_progress EXIT; post_progress
          LOG=$(mktemp)
          # 라인별 리뷰 복원: AUTO_REVIEW + AUTO_IMPROVE 동시 활성화
          if run_pr_agent "${{ github.event_name }}" "$GITHUB_EVENT_PATH" "$LOG" -- \
              -e "CONFIG.AI_PROVIDER=google_ai_studio" \
              -e "CONFIG.MODEL=$MODEL0" -e "CONFIG.MODEL_WEAK=$MODEL1" -e "CONFIG.FALLBACK_MODELS=[]" \
              -e "GOOGLE_AI_STUDIO.GEMINI_API_KEY=$GEMINI_KEY" -e "GEMINI_API_KEY=$GEMINI_KEY" \
              -e "GITHUB_ACTION_CONFIG.AUTO_REVIEW=true" \
              -e "GITHUB_ACTION_CONFIG.AUTO_DESCRIBE=false" \
              -e "GITHUB_ACTION_CONFIG.AUTO_IMPROVE=true"; then
            localize_comments
          else
            notify_failure "$LOG" "$RETRY_CMD"; echo "::error::Gemini 자동 리뷰 실패"; exit 1
          fi
```

- [ ] **Step 4: review-on-push 잡 — synchronize 시 스레드 판단(#2)**

`review-gemini-auto` 잡 뒤에 신규 잡 추가:

```yaml
  review-on-push:
    name: Review On Push (thread resolve)
    needs: resolve-gemini-model
    if: github.event_name == 'pull_request_target' && github.event.action == 'synchronize'
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
      - name: Resolve author key
        id: whichkey
        env:
          LOGIN: ${{ github.event.pull_request.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_keys.sh
          echo "suffix=$(key_suffix_for "$LOGIN")" >> "$GITHUB_OUTPUT"
      - name: Judge & resolve threads (model1, 작성자 키)
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          MAPPED_KEY: ${{ secrets[format('GEMINI_KEY_{0}', steps.whichkey.outputs.suffix)] }}
          DEFAULT_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}
          GEMINI_MODEL: ${{ needs.resolve-gemini-model.outputs.model1 }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_ai.sh
          . .github/scripts/review/resolve_threads.sh
          export GEMINI_KEY="${MAPPED_KEY:-$DEFAULT_KEY}"
          resolve_threads
```

- [ ] **Step 5: review-gemini-manual 잡 — 스크립트 호출로 슬림화 + /improve**

기존 `review-gemini-manual` 잡(라인 441~603) 전체를 아래로 교체:

```yaml
  review-gemini-manual:
    name: Gemini Manual Review
    needs: resolve-gemini-model
    if: |
      github.event_name == 'issue_comment' &&
      github.event.comment.user.type != 'Bot' &&
      contains(fromJSON('["OWNER","MEMBER","COLLABORATOR"]'), github.event.comment.author_association) &&
      github.event.issue.pull_request != null &&
      contains(github.event.comment.body, '/gemini-review')
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
      - name: Resolve commenter key
        id: whichkey
        env:
          LOGIN: ${{ github.event.comment.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_keys.sh
          echo "suffix=$(key_suffix_for "$LOGIN")" >> "$GITHUB_OUTPUT"
      - name: AI Review (Gemini, 작성자 키, 라인별)
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          MODEL0: ${{ needs.resolve-gemini-model.outputs.model0 }}
          MODEL1: ${{ needs.resolve-gemini-model.outputs.model1 }}
          MAPPED_KEY: ${{ secrets[format('GEMINI_KEY_{0}', steps.whichkey.outputs.suffix)] }}
          DEFAULT_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}
          PR_NUMBER: ${{ github.event.issue.number }}
          RETRY_CMD: "/gemini-review"
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_ai.sh
          . .github/scripts/review/lib_comments.sh
          . .github/scripts/review/run_pr_agent.sh
          GEMINI_KEY="${MAPPED_KEY:-$DEFAULT_KEY}"
          # 코멘트 본문을 /review 로 바꿔 합성(이미지가 issue_comment 경로로 파싱)
          EVT=$(mktemp); jq '.comment.body = "/review"' "$GITHUB_EVENT_PATH" > "$EVT"
          cleanup_notices; PROGRESS_ID=""; trap clear_progress EXIT; post_progress
          LOG=$(mktemp)
          if run_pr_agent "${{ github.event_name }}" "$EVT" "$LOG" -- \
              -e "CONFIG.AI_PROVIDER=google_ai_studio" \
              -e "CONFIG.MODEL=$MODEL0" -e "CONFIG.MODEL_WEAK=$MODEL1" -e "CONFIG.FALLBACK_MODELS=[]" \
              -e "GOOGLE_AI_STUDIO.GEMINI_API_KEY=$GEMINI_KEY" -e "GEMINI_API_KEY=$GEMINI_KEY"; then
            # /improve 도 이어서 실행(라인별 커밋 제안)
            EVT2=$(mktemp); jq '.comment.body = "/improve"' "$GITHUB_EVENT_PATH" > "$EVT2"
            LOG2=$(mktemp)
            run_pr_agent "${{ github.event_name }}" "$EVT2" "$LOG2" -- \
              -e "CONFIG.AI_PROVIDER=google_ai_studio" \
              -e "CONFIG.MODEL=$MODEL0" -e "CONFIG.MODEL_WEAK=$MODEL1" -e "CONFIG.FALLBACK_MODELS=[]" \
              -e "GOOGLE_AI_STUDIO.GEMINI_API_KEY=$GEMINI_KEY" -e "GEMINI_API_KEY=$GEMINI_KEY" || true
            localize_comments
          else
            notify_failure "$LOG" "$RETRY_CMD"; echo "::error::Gemini 수동 리뷰 실패"; exit 1
          fi
```

- [ ] **Step 6: review-openai-manual 잡 — 스크립트 호출로 슬림화**

기존 `review-openai-manual` 잡(라인 662~818) 전체를 아래로 교체:

```yaml
  review-openai-manual:
    name: OpenAI Manual Review
    needs: resolve-openai-model
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - name: Checkout (scripts)
        uses: actions/checkout@v5
      - name: Generate OpenAI bot token
        id: bot-token
        uses: actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1
        with:
          app-id: ${{ secrets.OPENAI_BOT_APP_ID }}
          private-key: ${{ secrets.OPENAI_BOT_PRIVATE_KEY }}
      - name: Resolve commenter key
        id: whichkey
        env:
          LOGIN: ${{ github.event.comment.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_keys.sh
          echo "suffix=$(key_suffix_for "$LOGIN")" >> "$GITHUB_OUTPUT"
      - name: AI Review (OpenAI, 작성자 키, 라인별)
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          MODEL0: ${{ needs.resolve-openai-model.outputs.model0 }}
          MODEL1: ${{ needs.resolve-openai-model.outputs.model1 }}
          MAPPED_KEY: ${{ secrets[format('OPENAI_KEY_{0}', steps.whichkey.outputs.suffix)] }}
          DEFAULT_KEY: ${{ secrets.OPENAI_API_KEY }}
          PR_NUMBER: ${{ github.event.issue.number }}
          RETRY_CMD: "/openai-review"
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_ai.sh
          . .github/scripts/review/lib_comments.sh
          . .github/scripts/review/run_pr_agent.sh
          OPENAI_KEY="${MAPPED_KEY:-$DEFAULT_KEY}"
          EVT=$(mktemp); jq '.comment.body = "/review"' "$GITHUB_EVENT_PATH" > "$EVT"
          cleanup_notices; PROGRESS_ID=""; trap clear_progress EXIT; post_progress
          LOG=$(mktemp)
          if run_pr_agent "${{ github.event_name }}" "$EVT" "$LOG" -- \
              -e "CONFIG.AI_PROVIDER=openai" \
              -e "CONFIG.MODEL=$MODEL0" -e "CONFIG.MODEL_WEAK=$MODEL1" -e "CONFIG.FALLBACK_MODELS=[]" \
              -e "OPENAI.KEY=$OPENAI_KEY" -e "OPENAI_API_KEY=$OPENAI_KEY"; then
            EVT2=$(mktemp); jq '.comment.body = "/improve"' "$GITHUB_EVENT_PATH" > "$EVT2"
            LOG2=$(mktemp)
            run_pr_agent "${{ github.event_name }}" "$EVT2" "$LOG2" -- \
              -e "CONFIG.AI_PROVIDER=openai" \
              -e "CONFIG.MODEL=$MODEL0" -e "CONFIG.MODEL_WEAK=$MODEL1" -e "CONFIG.FALLBACK_MODELS=[]" \
              -e "OPENAI.KEY=$OPENAI_KEY" -e "OPENAI_API_KEY=$OPENAI_KEY" || true
            localize_comments
          else
            notify_failure "$LOG" "$RETRY_CMD"; echo "::error::OpenAI 수동 리뷰 실패"; exit 1
          fi
```

- [ ] **Step 7: ask-gemini 잡 유지**

기존 `ask-gemini` 잡(라인 823~908)은 동작 유지. 단 중복 `case` 매핑을 lib_keys로 통일하려면
`Resolve commenter key` 스텝의 인라인 case를 아래로 교체:

```yaml
      - name: Resolve commenter key
        id: whichkey
        env:
          LOGIN: ${{ github.event.comment.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_keys.sh
          echo "suffix=$(key_suffix_for "$LOGIN")" >> "$GITHUB_OUTPUT"
```

- [ ] **Step 8: resolve 잡 — resolve_command.sh 호출 + 트리거 조건**

기존 `resolve-to-jira-subtask` 잡(라인 917~1055) 전체를 아래로 교체:

```yaml
  resolve-command:
    name: Resolve Command
    needs: resolve-gemini-model
    if: |
      github.event_name == 'pull_request_review_comment' &&
      github.event.action == 'created' &&
      github.event.comment.user.type != 'Bot' &&
      contains(fromJSON('["OWNER","MEMBER","COLLABORATOR"]'), github.event.comment.author_association) &&
      startsWith(github.event.comment.body, '/resolve')
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - name: Checkout (scripts)
        uses: actions/checkout@v5
      - name: Generate bot token
        id: bot-token
        uses: actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1
        with:
          app-id: ${{ secrets.GEMINI_BOT_APP_ID }}
          private-key: ${{ secrets.GEMINI_BOT_PRIVATE_KEY }}
      - name: Resolve commenter key
        id: whichkey
        env:
          LOGIN: ${{ github.event.comment.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_keys.sh
          echo "suffix=$(key_suffix_for "$LOGIN")" >> "$GITHUB_OUTPUT"
      - name: AI judge + Jira subtask + resolve
        env:
          GITHUB_TOKEN: ${{ steps.bot-token.outputs.token }}
          MAPPED_KEY: ${{ secrets[format('GEMINI_KEY_{0}', steps.whichkey.outputs.suffix)] }}
          DEFAULT_KEY: ${{ secrets.GOOGLE_GEMINI_API_KEY }}
          GEMINI_MODEL: ${{ needs.resolve-gemini-model.outputs.model1 }}
          JIRA_BASE_URL: ${{ secrets.JIRA_BASE_URL }}
          JIRA_EMAIL: ${{ secrets.JIRA_EMAIL }}
          JIRA_TOKEN: ${{ secrets.JIRA_TOKEN }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          PR_TITLE: ${{ github.event.pull_request.title }}
          HEAD_REF: ${{ github.event.pull_request.head.ref }}
          PR_HTML_URL: ${{ github.event.pull_request.html_url }}
          COMMENT_BODY: ${{ github.event.comment.body }}
          COMMENT_ID: ${{ github.event.comment.id }}
          IN_REPLY_TO: ${{ github.event.comment.in_reply_to_id }}
          COMMENTER: ${{ github.event.comment.user.login }}
        run: |
          set -euo pipefail
          . .github/scripts/review/lib_ai.sh
          . .github/scripts/review/resolve_command.sh
          export GEMINI_KEY="${MAPPED_KEY:-$DEFAULT_KEY}"
          run_resolve_command
```

- [ ] **Step 9: resolve-gemini-model if 조건에 /resolve 포함 확인**

`resolve-gemini-model` 잡 `if:`에 `pull_request_review_comment` + `/resolve` 경로가 트리거되도록 아래 절을 OR로 추가(없으면):

```yaml
      (github.event_name == 'pull_request_review_comment' &&
       github.event.comment.user.type != 'Bot' &&
       contains(fromJSON('["OWNER","MEMBER","COLLABORATOR"]'), github.event.comment.author_association) &&
       (contains(github.event.comment.body, '/ask') || startsWith(github.event.comment.body, '/resolve')))
```

- [ ] **Step 10: YAML 파싱 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pr-review.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 11: 전체 단위 테스트 재실행**

Run: `for t in .github/scripts/review/tests/test_*.sh; do bash "$t" || exit 1; done; echo ALL_GREEN`
Expected: 각 테스트 `0 failed` + `ALL_GREEN`

- [ ] **Step 12: Commit**

```bash
git add .github/workflows/pr-review.yml
git commit -m "ci(review): 모놀리식 분해·트리거 재정의(open 전용 리뷰/push 스레드판단)·/improve 활성화"
```

---

### Task 11: pr-description.yml — 공통 키 / model1 (#0)

스펙 #0: description은 **공통 키 / model1**, **opened 시에만**.

**Files:**
- Modify: `.github/workflows/pr-description.yml`

- [ ] **Step 1: 트리거가 opened만인지 확인**

Run: `sed -n '3,6p' .github/workflows/pr-description.yml`
Expected: `types: [opened, reopened]` — `reopened`를 제거해 `types: [opened]`로 변경.

`.github/workflows/pr-description.yml` 4~5행:

```yaml
  pull_request_target:
    types: [opened]
```

- [ ] **Step 2: AI 요약이 model1을 쓰도록 모델 선택 순서 조정**

`AI Summary (general PR)` 스텝의 모델 루프는 `GEMINI_MODELS`를 순서대로 시도한다.
model1(2순위, 대화/describe용) 우선 사용을 위해, 해당 스텝 env에 명시적 우선순위를 준다.
`AI Summary (general PR)` 스텝(라인 235 근처) `run:` 직전 `env:`에 다음을 추가:

```yaml
          # describe는 약한 모델(model1) 우선: 2번째 모델부터 시도하도록 재정렬
          GEMINI_MODELS_DESC: ${{ vars.GEMINI_MODELS }}
```

그리고 해당 스텝 `run:` 내 `MODELS_CSV` 산출부를 아래로 교체(2순위를 앞으로):

```bash
          IFS=',' read -ra _M <<< "${GEMINI_MODELS_DESC:-gemini-2.5-flash}"
          if [ "${#_M[@]}" -ge 2 ]; then MODELS=("${_M[1]}" "${_M[@]}"); else MODELS=("${_M[@]}"); fi
```

> 키는 이미 `GOOGLE_GEMINI_API_KEY`(공통)를 사용 중이므로 #0 "공통 키" 요건 충족(변경 불필요).
> 정확한 교체 위치는 `grep -n 'MODELS_CSV\|read -ra MODELS' .github/workflows/pr-description.yml`로 확인.

- [ ] **Step 3: YAML 파싱 검증**

Run: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/pr-description.yml')); print('OK')"`
Expected: `OK`

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/pr-description.yml
git commit -m "ci(description): opened 전용 + describe 약한 모델(model1) 우선"
```

---

### Task 12: .pr_agent.toml — /improve 커밋 제안 확인

**Files:**
- Modify: `.pr_agent.toml`

- [ ] **Step 1: pr_code_suggestions 설정 확인**

Run: `grep -nA3 '\[pr_code_suggestions\]' .pr_agent.toml`
Expected: `commitable_code_suggestions = true` 존재. 없으면 `[pr_code_suggestions]` 아래에 추가:

```toml
[pr_code_suggestions]
commitable_code_suggestions = true
num_code_suggestions = 7
extra_instructions = "위 리뷰 페르소나와 동일한 친절한 한국어 톤으로, AS-IS/TO-BE를 포함해 작성하세요."
```

- [ ] **Step 2: num_max_findings 확인**

Run: `grep -n 'num_max_findings' .pr_agent.toml`
Expected: `num_max_findings = 7`

- [ ] **Step 3: Commit (변경 있을 때만)**

```bash
git add .pr_agent.toml
git commit -m "ci(review): /improve 커밋 제안 7건 설정 확인"
```

---

### Task 13: 통합 검증 (테스트 PR)

코드가 아닌 실제 동작을 GitHub에서 검증한다. 단위 테스트로는 잡히지 않는 트리거/권한/AI 경로 확인.

**Files:** (없음 — 운영 검증)

- [ ] **Step 1: 전체 단위 테스트 그린 확인**

Run: `for t in .github/scripts/review/tests/test_*.sh; do echo "== $t =="; bash "$t" || exit 1; done`
Expected: 모든 테스트 `0 failed`

- [ ] **Step 2: 브랜치 푸시 후 dev 대상 테스트 PR 생성**

Run:
```bash
git push -u origin HEAD
gh pr create --repo cash-chat-mvp/cash-chat-mvp --base dev --head "$(git branch --show-current)" \
  --title "[CC-343] 코드리뷰 시스템 재설계" --body "검증용 PR — 5개 플로우 동작 확인"
```
Expected: PR URL 출력

- [ ] **Step 3: open 시나리오 확인**

PR open 후 ~3분 내:
- `update-description` 잡 성공 + PR 본문 채워짐(#0).
- `Gemini Auto Review` 잡 성공 + "PR 리뷰 가이드" + **라인별 인라인 코멘트** + "PR 코드 개선 제안"(#1).
- 빌드: 변경 파일에 따라 `detect`→`build-check` 실행 또는 **Skipped**(#3).

Run: `gh pr checks <PR#> --repo cash-chat-mvp/cash-chat-mvp`
Expected: 위 잡들의 상태 확인. Auto Review pass, 무관 빌드 Skipped.

- [ ] **Step 4: push 시나리오 확인 (#2)**

문서 한 줄만 바꿔 푸시 → 확인:
- `Gemini Auto Review`는 **트리거 안 됨**(opened 전용).
- `Review On Push` 잡 실행 — 미해결 스레드 판단/리졸브 동작.
- 빌드 잡 Skipped(빌드 영향 없음).

`apps/frontend/shared/` 한 파일 바꿔 푸시 → Android·iOS 빌드 **둘 다** 실행 확인.

- [ ] **Step 5: 명령어 시나리오 확인 (#3, #4)**

- 코멘트로 `/gemini-review` → 전체 리뷰 재게시(라인별 포함).
- 코멘트로 `/openai-review` → OpenAI 리뷰.
- 라인 코멘트 답글로 `/resolve "이미 반영했습니다"` → AI 판단 답글 + (타당시) Jira 서브태스크 + 스레드 리졸브.

- [ ] **Step 6: 실패/멱등 확인**

- 동일 PR에 연속 푸시 → 중복 리뷰 코멘트 없음(멱등), concurrency로 직렬화.
- AI 실패 유도가 어려우면 로그에서 백오프 재시도 로그(`pr-agent 하드 실패 — 재시도`) 부재만 확인.

- [ ] **Step 7: 정리**

검증 완료 후 테스트 PR을 닫거나, 실제 CC-343 작업 PR로 승격.
문서/플랜 체크박스 완료 표시.

---

## Self-Review (작성자 점검 결과)

**1. 스펙 커버리지**
- #0 description 공통키/model1 → Task 11 ✓
- #1 open 전용 라인별 리뷰(+/improve) → Task 10 Step 3 ✓
- #2 push 스레드 판단(파생이슈 분기) → Task 8 + Task 10 Step 4 ✓
- #3 /gemini·/openai 수동 리뷰(라인별) → Task 10 Step 5~6 ✓
- #4 /resolve AI 판단 + Jira → Task 9 + Task 10 Step 8 ✓
- 공유 스크립트 추출 → Task 1~5,8,9 ✓
- 안정성(백오프/concurrency/멱등) → Task 2(lib_ai), Task 4(run_pr_agent), Task 10 Step 1(concurrency) ✓
- 빌드 detect+gate, 넓은 글로브 → Task 5~7 ✓

**2. Placeholder 스캔**: 모든 코드 스텝에 실제 코드 포함. TBD/TODO 없음. ✓

**3. 타입/시그니처 일관성**:
- `key_suffix_for`(Task1) 호출처 Task10 전 잡 동일. ✓
- `ai_retry`/`gemini_generate`(Task2) → resolve_threads/resolve_command/lib_comments에서 동일 시그니처 사용. ✓
- `run_pr_agent EVENT EVENT_FILE OUT -- <docker args>`(Task4) → Task10 호출 형식 일치. ✓
- `affects_android/affects_ios/changed_range`(Task5) → Task6~7 사용 일치. ✓
- `localize_comments`/`cleanup_notices`/`post_progress`/`clear_progress`/`notify_failure`(Task3) → Task10 사용 일치. ✓

**주의(실행자 메모)**: pr-agent 이미지가 `/improve` 합성 이벤트를 자동 리뷰(github_action) 경로에서
`AUTO_IMPROVE=true`로 처리하는지(Task10 Step3), 수동(issue_comment) 경로에서 `/improve` 합성 코멘트로
처리하는지(Step5~6)는 이미지 동작에 의존한다. Task13 Step3/Step5에서 **실제 라인별 제안 게시 여부**를
반드시 눈으로 확인하고, 안 되면 자동 경로도 합성 코멘트(`/review` + `/improve`) 방식으로 통일한다.
