# 도메인별 유저 스토리 카탈로그

제품이 **무엇을 해야 하는가**에 대한 직군(BE/FE/Infra) 공통의 단일 진실 원천이자 검증 기준선.
각 스토리 파일은 구현에 독립적인 **스토리 + 인수 조건(Given-When-Then)** 만 담는다. 각 직군은 자기 방식(JUnit/Compose/스모크 등)으로 검증하되, 같은 AC ID를 역참조한다.

## 규칙

- **파일명 = `US-<DOMAIN>-<NNN>-<slug>.md`**. `US`는 유저 스토리 표식, `<DOMAIN>`은 번호 스코프.
- **번호(NNN)는 도메인 내 생성 순서일 뿐 의미 없는 불변 식별자.** 우선순위/플로우 순서를 인코딩하지 않으며, deprecate해도 결번으로 비워둔다.
- **정식 참조는 slug 없이 ID로** (`US-REWARD-002`). 파일은 `US-REWARD-002-*` 글롭으로 찾으므로 slug을 개선해도 참조가 안 깨진다.
- **워킹 아이템(담당자·스프린트·우선순위)은 Jira**, **응결된 계약(스토리·AC)은 여기(repo)**. 사람이 보는 순서는 도메인 `README.md` 인덱스 표에서 관리.
- 여러 스토리가 공유하는 용어·전제는 도메인 `_glossary.md`에 한 번만.

## 도메인

| 도메인 | 범위 | 인덱스 |
| ------ | ---- | ------ |
| **reward** | 혜택존 코인/밥 적립 채널 (출석·리워드 광고·오퍼월·친구 초대) | [reward/README.md](./reward/README.md) |
| **shop** | 상점 카탈로그·구매·인벤토리 | [shop/README.md](./shop/README.md) |
| **auth** | 소셜 로그인·토큰 발급 | [auth/README.md](./auth/README.md) |
| **chat** | AI 채팅(서버 SSE 스트리밍) | [chat/README.md](./chat/README.md) |

## 원본 spec과의 관계

이 카탈로그는 `docs/features/*`, `docs/specs/*`의 유저 스토리·인수 조건을 도메인·스토리 단위로 재조직한 것이다.
API 계약·데이터 모델·시퀀스 다이어그램 등 **기술 구현 상세는 원본 spec에 남아 있으며 각 US 파일이 링크**한다.
