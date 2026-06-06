#!/usr/bin/env bash
# 코멘트 본문 명령어 앵커 파싱(순수 함수). contains() 의 부분 문자열 오발동을 막는다.
# 규칙: 어떤 줄이든 "맨 앞(선행 공백 허용)에 /명령" 으로 시작하고, 그 뒤가 공백 또는 줄끝.

# detect_command BODY CMD  → 매칭되면 rc0
# CMD 예: gemini-review / openai-review / ask / resolve / help
detect_command() {
  local body="${1:-}" cmd="${2:-}"
  [ -z "$cmd" ] && return 1
  printf '%s' "$body" | grep -qE "^[[:space:]]*/${cmd}([[:space:]]|$)"
}
