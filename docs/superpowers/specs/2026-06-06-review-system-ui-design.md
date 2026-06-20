# 리뷰 시스템 개발자 UI 개선 + 사용 가이드 설계

- 작성일: 2026-06-06
- 범위: AI 코드 리뷰 시스템의 **개발자 노출 UI**(명령어 표·봇 상태 코멘트) 개선 및 종합 사용 가이드 신설
- 비용 영향: 전부 마크다운/텍스트 변경 → AI 호출 추가 없음(0원)

## 배경

AI 코드 리뷰 시스템은 GitHub Actions + pr-agent(Qodo) 기반으로, 개발자는 PR 코멘트에
슬래시 명령어를 입력해 상호작용한다. 현재 개발자 노출 UI에 다음 문제가 있다.

1. **명령어 표가 두 곳에 따로 존재하며 불일치**
   - `.github/PULL_REQUEST_TEMPLATE.md` — 오래된 버전(`/resolve` 없음). PR **작성 시** 노출.
   - `.github/workflows/pr-description.yml`(341~349줄) — `/resolve` 포함 최신 버전. PR을
     **열면 본문을 덮어써서** 실제로 노출됨.
2. **자동/수동·키 전략이 어디에도 안내되지 않음** — 자동 리뷰는 PR open 시 공통 키로 1회만
   실행되고, 이후 재리뷰는 명령어(개인 키)로 해야 한다는 핵심 동작을 개발자가 알 수 없음.
3. **봇 상태 코멘트의 시각 언어가 제각각** — `## 헤딩+이모지`, `🤖 **굵게**`, 헤더 없는 평문이
   섞여 일관성이 없음. GitHub Alerts(`> [!NOTE]` 등) 같은 최신 표준을 쓰지 않음.
4. **공통 푸터 부재** — 다음 동작 명령어/문서 링크가 메시지마다 들쭉날쭉.
5. **`/ask` 실패 메시지만 구조가 없음**(평문).
6. **사용법 문서가 docs 어디에도 없음**.
7. **명령어 매칭이 느슨함** — 모든 명령 트리거가 `contains(body, '/cmd')` 라 본문
   어디든 부분 문자열이 있으면 발동(산문 속 언급·코드블록·유사어로 오발동 가능).
8. **PR 안에서 명령어를 탐색할 방법이 없음** — `/help` 같은 인입형 안내가 없음.

## 현재 동작(확인된 사실)

| 항목 | 트리거 | 키 | 비고 |
|---|---|---|---|
| 자동 리뷰 (`review-gemini-auto`) | `pull_request_target` `opened` **1회** | 공통(`GOOGLE_GEMINI_API_KEY`) | `/review`+`/improve` 동시 |
| 푸시 후 (`review-on-push`) | `synchronize` | 작성자 개인→공통 폴백 | **전체 재리뷰 없음**, 스레드 자동 리졸브 판단만 |
| `/gemini-review` (수동) | issue_comment | 요청자 개인→공통 폴백 | |
| `/openai-review` (수동) | issue_comment | 요청자 개인→공통 폴백 | 항상 비용 발생 |
| `/ask` | issue_comment / review_comment | 요청자 개인→공통 폴백 | 저비용 모델(model_weak) |
| `/resolve` | review_comment(답글) | 요청자 개인→공통 폴백 | AI 판단→Jira 서브태스크→스레드 리졸브 |

- 개인 키 매핑(`lib_keys.sh`): `gudals-kim`→GUDALS, `seedplan005`/`jwchoi42`→CHOI,
  `jeonj95`/`unistuj`→JEON. 그 외 로그인은 공통 키 사용.
- 권한: OWNER/MEMBER/COLLABORATOR 의 비봇 코멘트만 명령어 트리거.
- 자동 리뷰가 공통 키 1회로 제한된 이유: 공통 키 무료 등급 **일일 한도(RPD)** 절약.

## 변경 설계

### A. 명령어 표 통일 (2곳 동기화)

`PULL_REQUEST_TEMPLATE.md` 와 `pr-description.yml`(341줄 `aiReviewCommands`)의 표를 **동일 내용**으로 맞춘다.

- 열 구성: `명령어 | 설명 | 사용 위치`
- 포함 명령어: `/gemini-review`, `/openai-review`, `/ask 질문내용`, `/resolve`, `@coderabbitai review`
- `/ask` 는 따옴표 불필요 — `질문내용` 형태로 표기
- 표 위/아래에 **상세 문서 링크**(`docs/review/ai-code-review.md`)
- 표 아래 **자동/키 안내 문구** 추가:
  > ℹ️ 자동 리뷰는 PR을 열 때 **공통 키로 1회만** 실행돼요. 이후 푸시에는 자동 재리뷰가
  > 없으니(해결된 코멘트만 자동 정리), 다시 받으려면 위 명령어로 요청하세요.
  > 명령어 리뷰는 **요청자 개인 키**(미등록 시 공통 키)로 동작해 공통 일일 한도를 아낍니다.
- 두 파일에 "다른 쪽과 동기화 유지" 주석 표기.

### B. 봇 메시지 카드 시스템 (`lib_comments.sh`)

모든 봇 코멘트가 동일 구조의 "카드"를 갖도록 공통 렌더 헬퍼를 도입한다.

구조:
```
> [!NOTE]
> **🔍 AI 코드 리뷰 진행 중**
> 본문…

<sub>다음: `/gemini-review` 재요청 · <a href="...docs 링크...">사용법</a></sub>
<!-- cashchat-ai-review:progress -->
```

상태별 Alert 매핑:

| 종류(KIND) | Alert | 용도 |
|---|---|---|
| `progress` | `[!NOTE]` | 리뷰 진행 중 |
| `clean` | `[!TIP]` | 개선점 없음(정상 완료) |
| `quota` | `[!WARNING]` | 일일 사용량 소진 |
| `error` | `[!CAUTION]` | 일시적 실패 |

**핵심: 숨김 마커 기반 탐지.** 현재 `cleanup_notices`/`is_notice_body` 는 한국어 본문 텍스트로
코멘트를 식별 → 문구만 바꿔도 깨진다. 각 카드에 `<!-- cashchat-ai-review:KIND -->` 마커를 심고,
탐지 로직을 **마커 기반**으로 전환한다(표시 텍스트와 탐지 분리).
단, pr-agent 자체 영문 notice(`Preparing review...` 등)는 마커가 없으므로 기존 문자열 매칭도 함께 유지.

푸터: `GITHUB_SERVER_URL`/`GITHUB_REPOSITORY` 로 docs blob URL을 만들어 링크.

### C. `/ask` 실패 메시지 정렬 (`pr-review.yml`)

`ask-gemini` 잡의 평문 실패 메시지를 동일 카드(`error`/`quota`, 마커 포함)로 통일.
라인 댓글(`pull_request_review_comment`)이면 기존대로 해당 스레드 답글로 게시(맥락 유지).
가능하면 카드 렌더는 `lib_comments.sh` 헬퍼를 재사용.

### D. 진행 코멘트 제자리 업데이트 (`notify_failure`)

실패 시 `cleanup_notices` + 신규 POST 대신, 진행 카드(`PROGRESS_ID`)를 실패 카드로 **PATCH**한다.
- 성공 시: 결과 코멘트가 별도로 게시되므로 진행 카드는 기존대로 삭제(`clear_progress`).
- `PROGRESS_ID` 가 없으면 신규 POST 로 폴백.
- 효과: "진행 중 → 실패/사용량소진" 전환이 한 코멘트에서 일어나 알림 소음 감소.

### E. resolve 계열 메시지 정렬 (`resolve_command.sh`, `resolve_threads.sh`)

`🤖 **굵게**` 표기를 카드 시각 언어로 정렬한다.
- resolve 승인 / 자동 리졸브 → `[!TIP]`
- resolve 보류 / 변경 검토(미해결) → `[!NOTE]`
- 라인 스레드 답글이라는 맥락은 유지(전체 PR 코멘트로 바꾸지 않음).

### F. 종합 사용 가이드 (`docs/review/ai-code-review.md`, 신규, 한국어)

구성:
1. 개요 — pr-agent(Qodo) 기반, Gemini 자동 + OpenAI 수동 + CodeRabbit
2. 명령어 한눈에 보기(표)
3. 명령어별 상세 — 동작 / 사용 위치 / 비용(호출 횟수·모델 등급) / 예시
   - 자동 리뷰(PR open 1회), `/gemini-review`, `/openai-review`, `/ask`, `/resolve`,
     `/help`, `@coderabbitai`
4. 리뷰 결과 읽는 법 — 리뷰 가이드 · 코드 개선 제안 · 인라인 코멘트 ·
   Conventional Comments 접두사([Suggestion]/[Nitpick]/[Question]/[Compliment])
5. 비용 & 키 전략 — 자동 1회(공통 키) vs 명령어(개인 키 폴백), 등록된 개인 키 보유자,
   무료 등급 일일 한도(RPD)와 "사용량 소진" 메시지 연결, OpenAI는 항상 비용
6. FAQ / 트러블슈팅 — 사용량 소진 시 대처, 실패 재시도, 권한(OWNER/MEMBER/COLLABORATOR),
   왜 푸시해도 재리뷰가 안 도는가
7. 참고 — 관련 파일 경로(워크플로·스크립트·설정)

### G. 명령어 매칭 견고성 (`pr-review.yml`)

모든 명령 트리거의 `contains(body, '/cmd')` 를 **앵커 매칭**으로 교체한다.

- 댓글 이벤트(`issue_comment`/`pull_request_review_comment`) 전용 **`detect-command` 잡** 신설.
  - 코멘트 본문을 줄 앵커 정규식 `^[[:space:]]*/cmd([[:space:]]|$)` 로 파싱(셸, 테스트 가능).
  - 출력: 명령별 boolean(`is_gemini_review`/`is_openai_review`/`is_ask`/`is_resolve`/`is_help`)
    + 권한 게이트(`authorized` = 비봇 & OWNER/MEMBER/COLLABORATOR & PR 코멘트).
- 댓글로 트리거되는 워커 잡(`review-gemini-manual`/`review-openai-manual`/`ask-gemini`/
  `resolve-command`/`resolve-openai-model`/신설 `help`)은 `contains()` 대신
  `needs.detect-command.outputs.*` 로 게이트. 권한·봇·PR 검사도 detect-command 로 일원화.
- **공유 잡 `resolve-gemini-model`** 은 `pull_request_target`(자동·푸시)에서도 필요하므로,
  코멘트 분기 매칭만 앵커 정합(`contains`→앵커). 이 잡은 모델 존재 확인(HTTP)만 하는
  저비용 게이트라 약간 느슨해도 워커는 detect-command 가 최종 차단한다.
  `pull_request_target` 경로는 영향 없음.
- 합성 `/review`·`/improve` 본문(jq rewrite)은 내부용이라 무관.
- 정확한 `needs`/`always()` 배선은 구현 계획에서 테스트와 함께 확정.

### H. `/help` 명령어 신설 (`pr-review.yml` + `lib_help.sh`)

PR 안에서 명령어를 탐색할 수 있는 인입형 안내.

- 트리거: `issue_comment` 본문 `/help`(앵커 매칭) — detect-command 의 `is_help`.
- 동작: **AI 호출 없이(0원)** 명령어 레퍼런스 카드를 PR 코멘트로 게시.
  카드 시스템·마커(`<!-- cashchat-ai-review:help -->`) 재사용.
- **단일 소스**: 명령어 레퍼런스 본문을 `lib_help.sh::render_help_card`(또는 lib_comments 내
  헬퍼) 한 곳에 정의하고 `/help` 가 사용.
- 중복 게시 방지: 게시 전 기존 help 마커 코멘트를 삭제 후 새로 게시(자기 정리).
  단, 리뷰 흐름의 `cleanup_notices` 대상에는 **넣지 않음**(리뷰 실행이 help 카드를 지우지 않게).

## 검증

- 변경한 셸 스크립트는 `.github/scripts/review/tests/` 의 기존 테스트 체계에 맞춰 실행·보강.
- 마커 기반 `cleanup_notices`/`is_notice_body` 정규식이 **신규 카드**와 **기존 pr-agent 영문
  notice** 를 모두 잡는지 테스트.
- 두 명령어 표(`PULL_REQUEST_TEMPLATE.md` / `pr-description.yml`)의 내용 일치 확인.
- `detect-command` 앵커 매칭 테스트:
  - 양성: 명령어가 코멘트 맨 앞(`/ask 질문`, `  /gemini-review`).
  - 음성: 산문 속 언급(`/ask 가 왜 안되죠?`처럼 문장 중간), 유사어(`/asking`),
    코드블록 안의 `/review`.
- `/help` 카드 렌더 + 단일 소스 동작, 중복 게시 방지 테스트.

## 비범위 (Out of scope)

- pr-agent 리뷰 본문 자체의 한국어화 규칙 변경(`localize_*` 로직은 그대로).
- PR 본문 주입(`pr-description.yml`)까지 포함한 명령어 표의 **완전 프로그래밍적 단일화** —
  PR 템플릿(정적 md)·본문 주입(JS 배열)·`lib_help`·docs 는 수동 동기화(상호 주석으로 관리).
