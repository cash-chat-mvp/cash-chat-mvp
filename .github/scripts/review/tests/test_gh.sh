#!/usr/bin/env bash
DIR="$(cd "$(dirname "$0")/.." && pwd)"
. "$DIR/tests/_assert.sh"
. "$DIR/lib_gh.sh"
echo "test_gh"

export GITHUB_TOKEN="t"
export RESOLVE_RETRY_SLEEP=0   # 테스트 백오프 단축

# 빈 threadId → 호출 없이 실패
gh_resolve_thread ""; assert_rc $? 1 "빈 threadId → rc1"

# 성공: 응답에 isResolved=true → rc0
curl() { printf '{"data":{"resolveReviewThread":{"thread":{"isResolved":true}}}}'; }
gh_resolve_thread "TID"; assert_rc $? 0 "isResolved=true → rc0(검증 성공)"

# GraphQL 오류(권한 등): errors 존재, data 없음 → rc1 (조용히 성공 처리 안 함)
curl() { printf '{"data":null,"errors":[{"type":"FORBIDDEN","message":"Resource not accessible by integration"}]}'; }
gh_resolve_thread "TID"; assert_rc $? 1 "errors 응답 → rc1(거짓 성공 방지)"

# 일시 실패 후 성공: 1차 빈 응답, 2차 성공 → rc0 (재시도 동작)
CNT="$(mktemp)"; echo 0 > "$CNT"
curl() {
  local n; n=$(cat "$CNT"); n=$((n+1)); echo "$n" > "$CNT"
  if [ "$n" -lt 2 ]; then printf '{}'; else printf '{"data":{"resolveReviewThread":{"thread":{"isResolved":true}}}}'; fi
}
gh_resolve_thread "TID"; assert_rc $? 0 "1차 실패→2차 성공 → rc0(재시도)"
assert_eq "$(cat "$CNT")" "2" "재시도로 curl 2회 호출"
rm -f "$CNT"
unset -f curl

t_summary
