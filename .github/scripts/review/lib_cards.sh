#!/usr/bin/env bash
# 봇 코멘트 "카드" 렌더(순수 함수, 네트워크 없음). lib_comments/lib_help 등이 source.
# 모든 카드 = GitHub Alert 상태줄 → 본문(blockquote) → 푸터(다음 동작·문서 링크) → 숨김 마커.

CARD_DOCS_PATH="docs/review/ai-code-review.md"

# 상태(KIND) → GitHub Alert 토큰
_card_alert() {
  case "${1:-}" in
    progress|help|hold|autostop) echo "NOTE" ;;
    clean|approve|autostart)     echo "TIP" ;;
    quota)                       echo "WARNING" ;;
    error)                       echo "CAUTION" ;;
    *)                           echo "NOTE" ;;
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
