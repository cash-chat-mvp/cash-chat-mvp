# v.0.2 요구사항 분석·Story 분류

## 1. 문서 정보

- 프로젝트: Cash Chat 관리자 운영 시스템 (Jira: CC)
- 대상 버전: v.0.2
- 문서 경로: `docs/requirements/v.0.2/classification/00-domain-index.md`
- 원문 요구사항 경로: `docs/requirements/v.0.2/raw/`
- 문서 상태: REVIEW
- 작성 일시: 2026-07-12
- 작성 AI: Claude (Fable 5)
- 검토자:
- 승인자:
- 승인 일시:

---

## 2. 원문 요구사항 추적

| 접수 ID | 출처 | 접수 일시 | 원문·첨부 링크 | 담당자 | 반영 상태 |
|---|---|---|---|---|---|
| INTAKE-001 | Confluence FCTC 「관리자 기능 정리」 (페이지 31784962) | 2026-07-12 | [INTAKE-001.md](../raw/INTAKE-001.md) | 최지웅 | 반영 (Story 59건 전수 추출) |

---

## 3. 도메인 분류

| 번호 | 도메인 | 책임 요약 | Story 수 | P0 수 | 검토 상태 | 문서 |
|---:|---|---|---:|---:|---|---|
| 01 | 관리자 인증·권한 | 관리자 계정·세션·RBAC 소유, 접근 통제 정책 판단 | 4 | 3 | AI_PROPOSED | [01](./01-admin-auth.md) |
| 02 | 감사·보안 | 감사 로그 소유, 불변성·원자성·위험 작업 통제 | 3 | 3 | AI_PROPOSED | [02](./02-audit-security.md) |
| 03 | 대시보드 | 집계 정의(활성 기준·임계치) 소유, 지표 제공 | 4 | 0 | AI_PROPOSED | [03](./03-dashboard.md) |
| 04 | 회원·제재 | 회원 상태·제재·위험표시·메모·로그인 이력 소유 | 7 | 3 | AI_PROPOSED | [04](./04-member-sanction.md) |
| 05 | 보상·경제 | 포인트·Energy 지갑과 원장 소유 | 5 | 3 | AI_PROPOSED | [05](./05-reward-economy.md) |
| 06 | 광고·오퍼월 | SSV·TNK 콜백·quota·노출 정책 소유 | 5 | 1 | AI_PROPOSED | [06](./06-ads-offerwall.md) |
| 07 | 채팅·AI 운영 | 대화·메시지·토큰 usage·모델 정책 소유 | 5 | 0 | AI_PROPOSED | [07](./07-chat-ai-ops.md) |
| 08 | 상점·주문 | 상품·재고·주문·인벤토리 소유 | 5 | 0 | AI_PROPOSED | [08](./08-store-order.md) |
| 09 | 진화·리텐션 콘텐츠 | 진화·출석·초대·룰렛 정책과 이력 소유 | 3 | 0 | AI_PROPOSED | [09](./09-evolution-retention.md) |
| 10 | 공지·약관·고객지원 | 공지·약관·문의·앱 설정·푸시 관리 | 6 | 0 | AI_PROPOSED | [10](./10-notice-policy-support.md) |
| 11 | 서버·배포 운영 | 서비스 상태·메트릭·승인된 운영 작업 | 3 | 1 | AI_PROPOSED | [11](./11-server-deploy-ops.md) |
| 12 | 마케팅·매출·정산 | 캠페인 비용·전환·수익·정산 | 5 | 0 | AI_PROPOSED | [12](./12-marketing-sales-settlement.md) |
| 13 | 어뷰징·위험 관리 | 탐지 규칙·위험 후보·차단 목록 소유 | 2 | 0 | AI_PROPOSED | [13](./13-abuse-risk.md) |
| 14 | 국제화 | 다국어 콘텐츠·국가별 지표 | 2 | 0 | AI_PROPOSED | [14](./14-internationalization.md) |

합계: Story 59건 (P0 10, P1 21, P2 15, P3 13). Story 상세는 각 도메인 문서에서 관리한다.

---

## 4. 중복·의존·충돌

| Story ID | 대상 Story | 유형 | 내용 | 처리 상태 |
|---|---|---|---|---|
| REQ-013 | REQ-006 | 유사 | 회원 상세 AC-04 조회 감사가 민감정보 조회 감사와 중복 명세 | OPEN |
| REQ-011 | REQ-031 | 유사 | AI 비용 대시보드와 토큰·비용 기록이 동일 데이터 사용 (소유: 07) | OPEN |
| REQ-019 | - | 선행 | Energy 변동 원장은 신규 도입 필요 (원문 §2.2) | OPEN |
| REQ-023 | REQ-024, REQ-026 | 후행 | 실패 보상 재처리는 SSV·TNK 이력 조회를 선행으로 요구 | OPEN |
| REQ-014 | - | 선행 | 제재 상태의 서비스 서버 검증 연동 필요 (원문 §3.1-6) | OPEN |

---

## 5. 결정 필요

전체 목록은 [99-unresolved.md](./99-unresolved.md)에서 관리한다. 핵심:

- REQ-058 다국어 콘텐츠의 주 도메인 (복수 후보)
- 동적 운영 정책 플랫폼 신설 여부 (REQ-027·028·032·039·041·046 공통 메커니즘)
- REQ-011·031 AI 단가 데이터 소유 도메인
- REQ-055 휴면 Ledger 회계 정책, REQ-051~053 어트리뷰션 연동 방식 (원문 §11)

---

## 6. AI 검사 결과

| 검사 항목 | 결과 | 발견 내용 | 조치 |
|---|---|---|---|
| 원문 누락 | PASS | US-ADM 59건 전수 반영 | - |
| Story 문장 누락 | PASS | 전 Story 정규화 완료 | - |
| 중복·유사 Story | FLAG | 2건 (§4) | 사람 검토 |
| 주 도메인 복수 지정 | FLAG | REQ-058 1건 | 99로 분리 |
| 미분류 Story | PASS | 없음 (REQ-058은 잠정 14 배정 + 99 기록) | - |
| 근거 없는 우선순위 | PASS | 원문 [P0~P3] 표기 그대로 사용 | - |
| AI의 정책 임의 확정 | PASS | 정책 결정 항목은 전부 OPEN 유지 | - |

---

## 7. 승인

- [ ] 모든 원문 요구사항의 반영 여부를 추적할 수 있다.
- [ ] 모든 도메인 Story 문서가 `CONFIRMED`다.
- [ ] 모든 승인 대상 Story에 주 도메인이 하나만 지정됐다.
- [ ] 중복·충돌 Story의 처리 방향이 결정됐다.
- [ ] 미결정 사항에 결정권자와 기한이 있다.
- [ ] Jira Epic과 Story 명세 Task를 만들 대상이 확정됐다.

- 승인 상태: REVIEW
- 승인 Jira Task:
- 검토 PR:
- 승인자:
- 승인 일시:
