#!/usr/bin/env bash
# /resolve "사유": AI(model1)가 사유+원본 코멘트+diff로 resolve 타당성 판단.
# 타당 → 근거 답글 + Jira 서브태스크 + 스레드 resolve / 부당 → 코멘트만.
# lib_ai.sh를 먼저 source. 필요 env: GITHUB_TOKEN, GITHUB_API_URL, GITHUB_REPOSITORY,
#   PR_NUMBER, PR_TITLE, HEAD_REF, PR_HTML_URL, COMMENT_BODY, COMMENT_ID, IN_REPLY_TO, COMMENTER,
#   GEMINI_KEY, GEMINI_MODEL(=model1), JIRA_BASE_URL, JIRA_EMAIL, JIRA_TOKEN

. "$(dirname "${BASH_SOURCE[0]}")/lib_cards.sh"

parse_resolve_reason() {
  local r
  r=$(printf '%s' "${1:-}" | sed -E 's#^/resolve[[:space:]]*##' | head -1)
  r=$(printf '%s' "$r" | LC_ALL=C.UTF-8 sed -E 's/^["“”'"'"']//; s/["“”'"'"']$//; s/[[:space:]]*$//')
  [ -z "$r" ] && r="추후 일괄 수정 예정"
  printf '%s' "$r"
}
extract_jira_parent() { printf '%s %s' "${1:-}" "${2:-}" | grep -oE 'CC-[0-9]+' | head -1 || true; }

# AI 판단: rc0=resolve 타당(yes), rc1=부당(no). 근거는 전역 RESOLVE_REASON_AI에 저장.
ai_judge_resolve() { # $1=사유 $2=원본코멘트 $3=diff
  RESOLVE_REASON_AI=""
  local model="${GEMINI_MODEL##gemini/}" payload out prompt raw verdict
  prompt=$(printf '리뷰어가 아래 사유로 이 코드리뷰 스레드를 resolve 요청했습니다.\n사유가 타당한지(코드가 실제로 반영되었거나, 추후 처리로 분류하는 게 합리적인지) 판단하세요.\n첫 줄에 "yes"(리졸브 타당) 또는 "no"(아직 이르다)만, 둘째 줄에 한국어 한 문장 근거.\n\n사유: %s\n원본 코멘트: %s\n\nDiff:\n%s' "$1" "$2" "$3")
  payload=$(mktemp); out=$(mktemp)
  jq -n --arg p "$prompt" '{contents:[{role:"user",parts:[{text:$p}]}],generationConfig:{maxOutputTokens:256,temperature:0}}' > "$payload"
  if ! ai_retry gemini_generate "$GEMINI_KEY" "$model" "$payload" "$out"; then
    RESOLVE_REASON_AI="AI 판단을 가져오지 못해 사유를 신뢰해 처리합니다."; rm -f "$payload" "$out"; return 0   # 폴백: 타당 처리
  fi
  raw=$(jq -r '.candidates[0].content.parts[0].text // "yes"' "$out" 2>/dev/null || echo "yes")
  verdict=$(printf '%s' "$raw" | head -1 | tr '[:upper:]' '[:lower:]' | tr -d ' *`')
  RESOLVE_REASON_AI=$(printf '%s' "$raw" | sed -n '2p' | sed 's/^[[:space:]]*//')
  rm -f "$payload" "$out"
  [[ "$verdict" == yes* ]]
}

gh_reply() { curl -s --max-time 15 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
  "${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}/pulls/${PR_NUMBER}/comments/$1/replies" \
  -d "$(jq -n --arg b "$2" '{body:$b}')" >/dev/null || true; }

run_resolve_command() {
  local API="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
  # Jira 인증을 config 파일로 전달 — --user/-H 는 argv(ps)에 노출되므로 회피
  local JIRA_CFG; JIRA_CFG=$(mktemp); chmod 600 "$JIRA_CFG"
  printf 'user = "%s:%s"\n' "$JIRA_EMAIL" "$JIRA_TOKEN" > "$JIRA_CFG"
  trap 'rm -f "$JIRA_CFG"' RETURN
  local REASON ROOT_ID ROOT_JSON ORIG_BODY ORIG_PATH ROOT_NODE_ID FDIFF
  REASON="$(parse_resolve_reason "$COMMENT_BODY")"
  ROOT_ID="${IN_REPLY_TO:-$COMMENT_ID}"
  ROOT_JSON=$(curl -s --max-time 15 -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$API/pulls/comments/${ROOT_ID}")
  ORIG_BODY=$(printf '%s' "$ROOT_JSON" | jq -r '.body // ""'); [ -z "$ORIG_BODY" ] && ORIG_BODY="(원본 코멘트를 불러오지 못했습니다)"
  ORIG_PATH=$(printf '%s' "$ROOT_JSON" | jq -r '.path // ""')
  ROOT_NODE_ID=$(printf '%s' "$ROOT_JSON" | jq -r '.node_id // ""')
  FDIFF=$(printf '%s' "$ROOT_JSON" | jq -r '.diff_hunk // ""' | head -60)

  # ── AI 판단 ──
  if ! ai_judge_resolve "$REASON" "$ORIG_BODY" "$FDIFF"; then
    gh_reply "$ROOT_ID" "$(render_card hold 'resolve 보류' "$(printf '%s\n아직 리졸브하기 이르다고 판단했어요. 반영 후 다시 `/resolve \"사유\"` 해주세요.' "${RESOLVE_REASON_AI:-제시한 사유만으로는 해결을 확인하기 어렵습니다.}")" '')"
    echo "::notice::AI 판단: 보류"; return 0
  fi

  # ── Jira 상위 티켓 ──
  local PARENT PROJECT_KEY SUBTASK_TYPE_ID
  PARENT="$(extract_jira_parent "$PR_TITLE" "$HEAD_REF")"
  if [ -z "$PARENT" ]; then
    gh_reply "$ROOT_ID" "🤖 resolve는 타당하나 Jira 티켓(CC-###)을 못 찾아 서브태스크 없이 스레드만 리졸브할게요."
  else
    PROJECT_KEY="${PARENT%%-*}"
    SUBTASK_TYPE_ID=$(curl -s --max-time 20 --config "$JIRA_CFG" -H "Accept: application/json" \
      "${JIRA_BASE_URL}/rest/api/3/issue/createmeta/${PROJECT_KEY}/issuetypes" \
      | jq -r '[(.values // .issueTypes // [])[]|select(.subtask==true)][0].id // empty')
    [ -z "$SUBTASK_TYPE_ID" ] && SUBTASK_TYPE_ID=$(curl -s --max-time 20 --config "$JIRA_CFG" -H "Accept: application/json" \
      "${JIRA_BASE_URL}/rest/api/3/issuetype" | jq -r '[.[]|select(.subtask==true)][0].id // empty')
    if [ -n "$SUBTASK_TYPE_ID" ]; then
      local SUMMARY DESCRIPTION PAYLOAD RESP CODE RBODY NEW_KEY NEW_URL
      SUMMARY=$(printf '[리뷰 후속] %s' "$(printf '%s' "$ORIG_BODY" | tr '\n' ' ' | cut -c1-180)")
      DESCRIPTION=$(jq -n --arg reason "$REASON" --arg orig "$ORIG_BODY" --arg path "$ORIG_PATH" --arg pr "$PR_HTML_URL" --arg who "$COMMENTER" '
        {type:"doc",version:1,content:[
          {type:"paragraph",content:[{type:"text",text:"GitHub PR 리뷰에서 추후 처리로 분류된 항목입니다."}]},
          {type:"paragraph",content:[{type:"text",text:("사유: " + $reason)}]},
          {type:"paragraph",content:[{type:"text",text:("요청자: @" + $who)}]},
          ($path|if .=="" then empty else {type:"paragraph",content:[{type:"text",text:("파일: " + .)}]} end),
          {type:"paragraph",content:[{type:"text",text:"원본 리뷰 코멘트:"}]},
          {type:"blockquote",content:($orig|split("\n")|map(if .=="" then {type:"paragraph"} else {type:"paragraph",content:[{type:"text",text:.}]} end))},
          {type:"paragraph",content:[{type:"text",text:"PR: "},{type:"text",text:$pr,marks:[{type:"link",attrs:{href:$pr}}]}]}
        ]}')
      PAYLOAD=$(jq -n --arg pk "$PROJECT_KEY" --arg parent "$PARENT" --arg tid "$SUBTASK_TYPE_ID" --arg summary "$SUMMARY" --argjson desc "$DESCRIPTION" \
        '{fields:{project:{key:$pk},parent:{key:$parent},issuetype:{id:$tid},summary:$summary,description:$desc}}')
      RESP=$(curl -s --max-time 25 --config "$JIRA_CFG" -w $'\n%{http_code}' -X POST \
        -H "Content-Type: application/json" -H "Accept: application/json" "${JIRA_BASE_URL}/rest/api/3/issue" --data "$PAYLOAD")
      CODE=$(printf '%s' "$RESP" | tail -1); RBODY=$(printf '%s' "$RESP" | sed '$d')
      if [ "$CODE" = "201" ]; then
        NEW_KEY=$(printf '%s' "$RBODY" | jq -r '.key'); NEW_URL="${JIRA_BASE_URL}/browse/${NEW_KEY}"
      gh_reply "$ROOT_ID" "$(render_card approve 'resolve 승인 — 추후 처리 항목 등록' "$(printf '%s\n\n- 서브태스크: [%s](%s)\n- 상위 티켓: %s\n- 사유: %s\n\n이 스레드는 리졸브됩니다. 🙏' "${RESOLVE_REASON_AI:-반영 확인}" "$NEW_KEY" "$NEW_URL" "$PARENT" "$REASON")" '')"
      else
        gh_reply "$ROOT_ID" "🤖 resolve는 타당하나 Jira 서브태스크 생성 실패(HTTP ${CODE}). 스레드만 리졸브할게요."
      fi
    else
      gh_reply "$ROOT_ID" "🤖 resolve는 타당하나 Jira 서브태스크 이슈 타입을 찾지 못해 스레드만 리졸브할게요."
    fi
  fi

  # ── 스레드 리졸브 ──
  local THREAD_ID=""
  [ -n "$ROOT_NODE_ID" ] && THREAD_ID=$(curl -s --max-time 15 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/graphql" \
    -d "$(jq -n --arg id "$ROOT_NODE_ID" '{query:"query($id:ID!){node(id:$id){... on PullRequestReviewComment{pullRequestReviewThread{id}}}}",variables:{id:$id}}')" \
    | jq -r '.data.node.pullRequestReviewThread.id // empty')
  [ -n "$THREAD_ID" ] && curl -s --max-time 10 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/graphql" \
    -d "$(jq -n --arg id "$THREAD_ID" '{query:"mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}",variables:{id:$id}}')" >/dev/null || true
  echo "::notice::resolve 완료"
}
