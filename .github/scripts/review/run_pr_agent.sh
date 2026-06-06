#!/usr/bin/env bash
# pr-agent docker 실행 래퍼. 하드 실패(코멘트 미게시) 마커에서만 재시도(멱등).
# 필요 env: PR_AGENT_IMAGE, GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_API_URL, GITHUB_SERVER_URL
PR_AGENT_FAIL_MARKERS="${PR_AGENT_FAIL_MARKERS:-Failed to review PR:|Failed to generate code suggestions for PR, error:|Failed to generate prediction with any model}"

has_fail_marker() { grep -qE "$PR_AGENT_FAIL_MARKERS" "$1"; }

# run_pr_agent EVENT_NAME EVENT_FILE OUT_LOG -- DOCKER_ENV_ARGS...
# docker run 후 status/마커 판정. 하드 실패면 1회 재실행(중복 코멘트 방지).
# 성공 rc0 / 최종 실패 rc1(로그는 OUT_LOG에 남음).
run_pr_agent() {
  local event_name="$1" event_file="$2" out="$3"; shift 3
  [ "$1" = "--" ] && shift
  local _had_e; case $- in *e*) _had_e=1;; *) _had_e=0;; esac
  local attempt st
  for attempt in 1 2; do
    set +e
    docker run --rm \
      -e GITHUB_TOKEN \
      -e GITHUB_EVENT_NAME="$event_name" \
      -e GITHUB_REPOSITORY="$GITHUB_REPOSITORY" \
      -e GITHUB_API_URL="$GITHUB_API_URL" \
      -e GITHUB_SERVER_URL="$GITHUB_SERVER_URL" \
      -e GITHUB_EVENT_PATH=/github/workflow/event.json \
      "$@" \
      -v "${event_file}:/github/workflow/event.json:ro" \
      "$PR_AGENT_IMAGE" 2>&1 | tee "$out"
    st=${PIPESTATUS[0]}
    [ "$_had_e" -eq 1 ] && set -e
    if [ "$st" -eq 0 ] && ! has_fail_marker "$out"; then return 0; fi
    # 하드 실패 → 첫 시도면 백오프 후 재실행
    [ "$attempt" -eq 1 ] && { echo "::warning::pr-agent 하드 실패 — 30초 후 1회 재시도"; sleep 30; }
  done
  return 1
}
