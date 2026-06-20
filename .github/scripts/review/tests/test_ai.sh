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

# 4) ai_generate: 개인키 우선, 실패 유형별 공용키 폴백
export AI_RETRY_SLEEP=0 AI_RETRY_MAX=2
GG_CALLS="$(mktemp)"; : > "$GG_CALLS"
# gemini_generate 스텁: (key, model, payload, out). 키별 동작 분기.
gemini_generate() {
  local k="$1" o="$4"
  echo "$k" >> "$GG_CALLS"
  case "$k" in
    GOOD)    echo '{"candidates":[]}' > "$o"; return 0;;
    LIMITED) echo '{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}' > "$o"; return 1;;
    ERR)     echo 'boom 500' > "$o"; return 1;;
    SHARED)  echo '{"ok":1}' > "$o"; return 0;;
  esac; return 1
}
O=$(mktemp)
# 개인키 성공 → 폴백 없음
: > "$GG_CALLS"; ai_generate GOOD SHARED m p "$O"; assert_rc $? 0 "ai_generate: 개인키 성공 → rc0"
assert_eq "$(grep -c SHARED "$GG_CALLS")" "0" "ai_generate: 성공 시 공용키 미사용"
# 개인키 rate-limit → 즉시 공용키 폴백(개인키 재시도 없이)
: > "$GG_CALLS"; ai_generate LIMITED SHARED m p "$O"; assert_rc $? 0 "ai_generate: 개인키 429 → 공용키 폴백 rc0"
assert_eq "$(cat "$O")" '{"ok":1}' "ai_generate: 폴백 응답이 out에 기록"
assert_eq "$(grep -c LIMITED "$GG_CALLS")" "1" "ai_generate: 429 시 개인키 재시도 안 함(1회)"
# 개인키 일시오류 → 개인키 백오프 재시도 후 공용키 폴백
: > "$GG_CALLS"; ai_generate ERR SHARED m p "$O"; assert_rc $? 0 "ai_generate: 개인키 일시오류 → 공용키 폴백 rc0"
assert_eq "$(grep -c SHARED "$GG_CALLS")" "1" "ai_generate: 폴백 1회"
# 폴백 키 없음 → 개인키 실패 시 rc1
: > "$GG_CALLS"; ai_generate ERR "" m p "$O"; assert_rc $? 1 "ai_generate: 폴백 없으면 rc1"
rm -f "$O" "$GG_CALLS"
t_summary
