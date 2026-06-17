#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/run_pr_agent.sh"
export PR_AGENT_IMAGE=dummy
echo "test_run_pr_agent"
log=$(mktemp)
trap 'rm -f "$log" "${out:-}"' EXIT
printf 'Failed to review PR: boom\n' > "$log"
has_fail_marker "$log"; assert_rc $? 0 "하드 실패 마커 감지(rc0)"
printf 'INFO Reviewing PR ... published review\n' > "$log"
has_fail_marker "$log"; assert_rc $? 1 "정상 로그 → 마커 없음(rc1)"
printf 'transient 429 but litellm recovered, review posted\n' > "$log"
has_fail_marker "$log"; assert_rc $? 1 "일시 429 복구 → 마커 아님(rc1)"
# run_pr_agent 테스트용 env 스텁
export GITHUB_TOKEN=stub GITHUB_REPOSITORY=stub/stub GITHUB_API_URL=https://api.github.com GITHUB_SERVER_URL=https://github.com
# run_pr_agent: docker 성공 + 마커 없음 → rc0, 1회 호출
_call_file=$(mktemp)
echo 0 > "$_call_file"
docker() { echo $(( $(cat "$_call_file") + 1 )) > "$_call_file"; echo "published review ok"; return 0; }
export -f docker
out=$(mktemp)
run_pr_agent issue_comment /dev/null "$out" -- -e X=1 >/dev/null 2>&1; rc=$?
assert_rc "$rc" 0 "run_pr_agent: 정상 → rc0"
assert_eq "$(cat "$_call_file")" "1" "run_pr_agent: 정상 시 docker 1회"
# docker 성공이지만 하드 실패 마커 → 재시도 후 최종 rc1, 2회 호출
echo 0 > "$_call_file"
docker() { echo $(( $(cat "$_call_file") + 1 )) > "$_call_file"; echo "Failed to review PR: boom"; return 0; }
export -f docker
# 재시도 sleep 30s를 건너뛰기 위해 sleep을 모킹
sleep() { :; }
export -f sleep
run_pr_agent issue_comment /dev/null "$out" -- -e X=1 >/dev/null 2>&1; rc=$?
assert_rc "$rc" 1 "run_pr_agent: 하드 실패 → 재시도 후 rc1"
assert_eq "$(cat "$_call_file")" "2" "run_pr_agent: 하드 실패 시 docker 2회"
rm -f "$out" "$_call_file"

# ── run_pr_agent_fallback: 개인키 우선, quota 시 공용키로 전체 재실행 ──
CALLS=$(mktemp)
# docker 스텁: 전달된 GEMINI_API_KEY 값으로 동작 분기, 호출 키를 기록.
docker() {
  local a key=""
  for a in "$@"; do case "$a" in GEMINI_API_KEY=*) key="${a#GEMINI_API_KEY=}";; esac; done
  echo "$key" >> "$CALLS"
  case "$key" in
    PRIMARY_OK)    echo "published review";;
    PRIMARY_QUOTA) echo "Failed to generate prediction with any model: RESOURCE_EXHAUSTED";;
    PRIMARY_HARD)  echo "Failed to review PR: boom";;
    SHARED)        echo "published review";;
  esac
  return 0
}
export -f docker
out=$(mktemp)
# 개인키 성공 → 폴백 없음, 1회
: > "$CALLS"; run_pr_agent_fallback issue_comment /dev/null "$out" PRIMARY_OK SHARED -- -e X=1 >/dev/null 2>&1; rc=$?
assert_rc "$rc" 0 "fallback: 개인키 성공 → rc0"
assert_eq "$(grep -c . "$CALLS")" "1" "fallback: 성공 시 docker 1회"
assert_eq "$(grep -c SHARED "$CALLS")" "0" "fallback: 성공 시 공용키 미사용"
# 개인키 quota → 공용키로 재실행 성공
: > "$CALLS"; run_pr_agent_fallback issue_comment /dev/null "$out" PRIMARY_QUOTA SHARED -- -e X=1 >/dev/null 2>&1; rc=$?
assert_rc "$rc" 0 "fallback: 개인키 quota → 공용키 재실행 rc0"
assert_eq "$(grep -c SHARED "$CALLS")" "1" "fallback: quota 시 공용키 1회 사용"
# 개인키 하드실패(quota 아님) → 폴백 안 함
: > "$CALLS"; run_pr_agent_fallback issue_comment /dev/null "$out" PRIMARY_HARD SHARED -- -e X=1 >/dev/null 2>&1; rc=$?
assert_rc "$rc" 1 "fallback: 비-quota 하드실패 → rc1"
assert_eq "$(grep -c SHARED "$CALLS")" "0" "fallback: 비-quota는 공용키 미사용"
# 폴백 키 없음 → 개인키 quota여도 rc1
: > "$CALLS"; run_pr_agent_fallback issue_comment /dev/null "$out" PRIMARY_QUOTA "" -- -e X=1 >/dev/null 2>&1; rc=$?
assert_rc "$rc" 1 "fallback: 폴백키 없으면 rc1"
rm -f "$out" "$CALLS"
t_summary
