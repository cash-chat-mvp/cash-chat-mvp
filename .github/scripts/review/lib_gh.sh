#!/usr/bin/env bash
# GitHub GraphQL 공용 헬퍼. 필요 env: GITHUB_TOKEN.
# RESOLVE_RETRY_SLEEP(초, 기본 3)로 재시도 간격 조정(테스트에서 0).

# gh_resolve_thread THREAD_NODE_ID
#   리뷰 스레드를 resolve 하고 결과를 "검증"한다.
#   - 응답을 버리지 않고 .data.resolveReviewThread.thread.isResolved 로 실제 해결 여부 확인
#   - 실패 시 .errors(권한/일시 오류)를 로그로 노출 → 원인 추적 가능
#   - 일시 실패는 1회 재시도
#   rc0 = 실제로 resolved 확인됨 / rc1 = 실패(거짓 성공 보고 금지)
gh_resolve_thread() {
  local id="${1:-}"
  if [ -z "$id" ] || [ "$id" = "null" ]; then
    echo "::warning::resolve 건너뜀 — 빈 threadId"
    return 1
  fi
  local sleep_s="${RESOLVE_RETRY_SLEEP:-3}" attempt resp ok err
  for attempt in 1 2; do
    resp=$(curl -s --max-time 15 -X POST \
      -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
      "https://api.github.com/graphql" \
      -d "$(jq -n --arg id "$id" '{query:"mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}",variables:{id:$id}}')" \
      2>/dev/null || echo '{}')
    ok=$(printf '%s' "$resp" | jq -r '.data.resolveReviewThread.thread.isResolved // empty' 2>/dev/null || true)
    if [ "$ok" = "true" ]; then
      echo "::notice::✅ resolve 확인됨 (threadId=$id)"
      return 0
    fi
    err=$(printf '%s' "$resp" | jq -rc '.errors // empty' 2>/dev/null || true)
    echo "::warning::resolve 미확인 (attempt ${attempt}/2) errors=${err:-none} resp=$(printf '%s' "$resp" | head -c 300)"
    [ "$attempt" -eq 1 ] && [ "$sleep_s" -gt 0 ] && sleep "$sleep_s"
  done
  echo "::error::resolveReviewThread 실패 — 스레드가 실제로 해결되지 않았습니다 (threadId=$id). 권한(App pull_requests:write) 또는 일시 오류 가능."
  return 1
}
