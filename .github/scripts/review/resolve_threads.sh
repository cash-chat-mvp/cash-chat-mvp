#!/usr/bin/env bash
# push(synchronize) 시: 변경 라인에 걸린 미해결 리뷰 스레드를 model1로 판단.
# yes → 근거 답글 + resolve / no → 코멘트만(파생이슈 가능). lib_ai.sh를 먼저 source할 것.
# 필요 env: GITHUB_TOKEN, GITHUB_API_URL, GITHUB_REPOSITORY, PR_NUMBER, GEMINI_KEY, GEMINI_MODEL(=model1)

parse_verdict() { printf '%s' "${1:-}" | head -1 | tr '[:upper:]' '[:lower:]' | tr -d ' *`' ; }
parse_reason()  { printf '%s' "${1:-}" | sed -n '2p' | sed 's/^[[:space:]]*//' ; }

resolve_threads() {
  local api="${GITHUB_API_URL}/repos/${GITHUB_REPOSITORY}"
  local owner="${GITHUB_REPOSITORY%%/*}" name="${GITHUB_REPOSITORY##*/}"

  local pr_files_json changed_files
  pr_files_json=$(curl -s --max-time 15 -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
    "$api/pulls/${PR_NUMBER}/files?per_page=100" 2>/dev/null || echo '[]')
  changed_files=$(printf '%s' "$pr_files_json" | jq -r '.[].filename' 2>/dev/null || true)
  [ -z "$changed_files" ] && { echo "::notice::변경 파일 없음"; return 0; }

  local threads_json unresolved
  threads_json=$(curl -s --max-time 15 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
    "https://api.github.com/graphql" \
    -d "$(jq -n --arg owner "$owner" --arg name "$name" --argjson number "$PR_NUMBER" \
      '{query:"query($owner:String!,$name:String!,$number:Int!){repository(owner:$owner,name:$name){pullRequest(number:$number){reviewThreads(first:100){nodes{id isResolved comments(first:1){nodes{body path databaseId}}}}}}}",variables:{owner:$owner,name:$name,number:$number}}')" \
    2>/dev/null || echo '{}')
  unresolved=$(printf '%s' "$threads_json" | jq -c '.data.repository.pullRequest.reviewThreads.nodes // [] | map(select(.isResolved==false)) | .[]' 2>/dev/null || true)
  [ -z "$unresolved" ] && { echo "::notice::미해결 스레드 없음"; return 0; }

  local model="${GEMINI_MODEL##gemini/}" resolved=0
  while IFS= read -r node; do
    local tid body fpath cdb fdiff prompt payload out raw verdict reason
    tid=$(printf '%s' "$node" | jq -r '.id // empty')
    body=$(printf '%s' "$node" | jq -r '.comments.nodes[0].body // empty')
    fpath=$(printf '%s' "$node" | jq -r '.comments.nodes[0].path // empty')
    cdb=$(printf '%s' "$node" | jq -r '.comments.nodes[0].databaseId // empty')
    [ -z "$tid" ] && continue
    printf '%s' "$changed_files" | grep -qxF "$fpath" || continue   # 이번에 바뀐 파일만

    fdiff=$(printf '%s' "$pr_files_json" | jq -r --arg p "$fpath" '.[]|select(.filename==$p)|.patch // ""' 2>/dev/null | head -80 || true)
    prompt=$(printf '코드 리뷰 코멘트가 아래 diff로 해결되었나요?\n첫 줄에 "yes" 또는 "no"만 쓰고, 둘째 줄에 한국어 한 문장으로 근거(yes면 해결 이유, no면 남은 이슈/파생 우려)를 쓰세요.\n\n파일: %s\n코멘트: %s\n\nDiff:\n%s' "$fpath" "$body" "$fdiff")
    payload=$(mktemp); out=$(mktemp)
    jq -n --arg p "$prompt" '{contents:[{role:"user",parts:[{text:$p}]}],generationConfig:{maxOutputTokens:256,temperature:0}}' > "$payload"
    if ! ai_retry gemini_generate "$GEMINI_KEY" "$model" "$payload" "$out"; then
      echo "::warning::스레드 판단 실패(쿼터/오류) — 건너뜀: $fpath"; rm -f "$payload" "$out"; sleep 3; continue
    fi
    raw=$(jq -r '.candidates[0].content.parts[0].text // "no"' "$out" 2>/dev/null || echo "no")
    sleep 3   # RPM 보호
    verdict=$(parse_verdict "$raw"); reason=$(parse_reason "$raw")

    if [[ "$verdict" == yes* ]]; then
      [ -n "$cdb" ] && [ "$cdb" != "null" ] && curl -s --max-time 10 -X POST \
        -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
        "$api/pulls/${PR_NUMBER}/comments/${cdb}/replies" \
        -d "$(jq -n --arg b "$(printf '🤖 **자동 리졸브 판단**\n\n%s\n\n_최신 변경에서 해결된 것으로 판단되어 자동 리졸브합니다. (%s)_' "${reason:-최신 변경에서 해결된 것으로 판단됩니다.}" "$model")" '{body:$b}')" >/dev/null 2>&1 || true
      curl -s --max-time 10 -X POST -H "Authorization: Bearer $GITHUB_TOKEN" -H "Content-Type: application/json" \
        "https://api.github.com/graphql" \
        -d "$(jq -n --arg id "$tid" '{query:"mutation($id:ID!){resolveReviewThread(input:{threadId:$id}){thread{isResolved}}}",variables:{id:$id}}')" >/dev/null 2>&1 || true
      resolved=$((resolved+1)); echo "::notice::✅ 리졸브: $fpath"
    else
      # 미해결: 파생이슈/남은 우려를 답글로만 남기고 resolve하지 않음
      [ -n "$cdb" ] && [ "$cdb" != "null" ] && curl -s --max-time 10 -X POST \
        -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" \
        "$api/pulls/${PR_NUMBER}/comments/${cdb}/replies" \
        -d "$(jq -n --arg b "$(printf '🤖 **변경 검토**\n\n%s\n\n_아직 해결되지 않았거나 추가 확인이 필요해 보여 리졸브하지 않았습니다._' "${reason:-남은 우려가 있어 보입니다.}")" '{body:$b}')" >/dev/null 2>&1 || true
      echo "::notice::↺ 미해결 유지: $fpath"
    fi
    rm -f "$payload" "$out"
  done <<< "$unresolved"
  echo "::notice::총 ${resolved}개 자동 리졸브"
  return 0
}
