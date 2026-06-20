#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/../../.." && pwd)"
. "$DIR/scripts/review/tests/_assert.sh"
. "$DIR/scripts/build/detect_build_changes.sh"
echo "test_notify_decision"
# build-check 잡 결론 → 알림 행동. 실제로 빌드가 돈 경우만 알림.
assert_eq "$(notify_decision success)"   "pass" "success → pass"
assert_eq "$(notify_decision failure)"   "fail" "failure → fail"
assert_eq "$(notify_decision cancelled)" "fail" "cancelled → fail"
assert_eq "$(notify_decision timed_out)" "fail" "timed_out → fail"
assert_eq "$(notify_decision skipped)"   "skip" "skipped → skip(알림 안 함)"
assert_eq "$(notify_decision '')"        "skip" "빈 값(잡 없음) → skip"
assert_eq "$(notify_decision neutral)"   "skip" "neutral → skip"
t_summary
