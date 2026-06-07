#!/usr/bin/env bash
# /help 명령어 레퍼런스 카드(명령어 표 단일 소스). lib_cards.sh 를 source 한 뒤 사용.
# ⚠️ 동기화 유지: 같은 표가 .github/PULL_REQUEST_TEMPLATE.md, .github/workflows/pr-description.yml,
#                docs/review/ai-code-review.md 에도 있다. 바꾸면 네 곳을 함께 맞출 것.
render_help_card() {
  local body
  body=$(cat <<'MD'
PR 코멘트에 아래 명령어를 입력하면 AI 리뷰를 사용할 수 있어요.

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 이 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

ℹ️ 자동 리뷰는 PR을 열 때 공통 키로 1회만 실행돼요. 이후 푸시에는 자동 재리뷰가 없으니(해결된 코멘트만 자동 정리), 다시 받으려면 위 명령어로 요청하세요. 명령어 리뷰는 요청자 개인 키(미등록 시 공통 키)로 동작해 공통 일일 한도를 아낍니다.
MD
)
  render_card help '🛟 AI 코드 리뷰 명령어 도움말' "$body" ''
}
