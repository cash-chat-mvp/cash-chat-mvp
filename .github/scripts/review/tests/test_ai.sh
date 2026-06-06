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
