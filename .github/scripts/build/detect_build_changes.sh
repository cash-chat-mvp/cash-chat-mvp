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
