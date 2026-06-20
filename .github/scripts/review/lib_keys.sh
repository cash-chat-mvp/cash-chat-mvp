#!/usr/bin/env bash
# GitHub login → 작성자별 API 키 SUFFIX 매핑(단일 소스).
# 사용처: secrets[format('GEMINI_KEY_{0}', <suffix>)]
key_suffix_for() {
  local login_lc
  login_lc=$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')
  case "$login_lc" in
    gudals-kim) echo "GUDALS" ;;
    seedplan005|jwchoi42) echo "CHOI" ;;
    jeonj95|unistuj) echo "JEON" ;;
    *) echo "" ;;
  esac
}
