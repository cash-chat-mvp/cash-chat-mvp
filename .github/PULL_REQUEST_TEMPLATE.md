> 확인 목록 (Check List)

병합 요청(Merge Request)을 하기 전에, 아래 항목을 확인해 주세요.

- [ ] **Local** 환경에서 **Build** 성공 및 **Test** 검증
- [ ] 제목에 **Jira**에서 **Ticket**의 **ID** 포함

---

> AI 코드 리뷰 명령어 · 자세한 사용법: [docs/review/ai-code-review.md](../docs/review/ai-code-review.md)
<!-- 동기화 유지: 같은 표가 .github/workflows/pr-description.yml, .github/scripts/review/lib_help.sh, docs/review/ai-code-review.md 에도 있음 -->

| 명령어 | 설명 | 사용 위치 |
|---|---|---|
| `/gemini-review` | Gemini 코드 리뷰 (PR을 열면 자동 1회 실행, 재요청 시 입력) | PR 코멘트 |
| `/openai-review` | OpenAI 심층 리뷰 (수동 · 비용 발생) | PR 코멘트 |
| `/ask 질문내용` | AI 답변/코드에 후속 질문 (저비용 모델) | PR · 라인 코멘트 |
| `/resolve` | AI가 반영 여부 판단 → Jira 서브태스크 생성 + 스레드 해결 | 라인 코멘트 답글 |
| `/help` | 명령어 도움말 표시 | PR 코멘트 |
| `@coderabbitai review` | CodeRabbit 리뷰 (수동 · 비용 발생) | PR 코멘트 |

> ℹ️ 자동 리뷰는 PR을 열 때 **공통 키로 1회만** 실행돼요. 이후 푸시에는 자동 재리뷰가 없으니(해결된 코멘트만 자동 정리), 다시 받으려면 위 명령어로 요청하세요. 명령어 리뷰는 **요청자 개인 키**(미등록 시 공통 키)로 동작해 공통 일일 한도를 아낍니다.
