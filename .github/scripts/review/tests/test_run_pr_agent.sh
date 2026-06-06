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
