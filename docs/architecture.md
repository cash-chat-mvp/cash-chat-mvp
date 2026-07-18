# Architecture — Cash Chat 관리자 운영 시스템 v.0.2

- 상태: DRAFT (사용자 확정: 프런트 스택, 백엔드 배치, 데이터 접근)
- 작성일: 2026-07-18
- 기준 PRD: [prd.md](./prd.md)

## 1. 확정 결정

| 결정 | 내용 | 근거 |
|---|---|---|
| 프런트엔드 | **React + TypeScript (Vite)** 관리자 웹 콘솔, `apps/admin-frontend/` | 사용자 확정 (2026-07-18). 관리자 콘솔 생태계·AI 생성 품질 |
| 백엔드 | **별도 admin 서버 신설** — Kotlin + Spring Boot, `apps/admin-backend/` | 사용자 확정 (2026-07-18). 서비스 서버와 격리 |
| 언어·프레임워크 관례 | 기존 `apps/backend/`와 동일: Kotlin 1.9+, Spring Boot 3.5+, Java 21, Gradle Kotlin DSL, 도메인 레이어링(`persistence/{entity,repository}`·`service`·`web`) | 모노레포 일관성 (AGENTS.md) |
| 인증 | 관리자 전용 ID/PW + 세션(유휴 만료·잠금) + **MFA=TOTP** — 서비스 OAuth와 분리 | REQ-001~004, UNR-009 확정 |
| 인가 | 역할×(조회·변경·다운로드) RBAC, **서버 측 검증**(403), 메뉴는 파생 | REQ-003 |
| 감사 | 모든 변경 API는 감사 기록과 **원자적** 커밋. 감사 실패 = 변경 롤백 | REQ-005 |
| 배포 | 기존 인프라 관례: Docker Compose(OCI), GHCR 이미지, GitHub Actions CI/CD | infra/ 관례 |

## 2. 불변 규칙 (Invariants)

1. 관리자 API는 인증·RBAC 검증을 통과하지 않으면 어떤 데이터도 반환·변경하지 않는다.
2. 자산(포인트·Energy) 변경은 원장(ledger) 기록 없이 일어나지 않는다.
3. 변경 API는 감사 이벤트와 같은 트랜잭션 경계에서 커밋된다.
4. 자동 제재 금지: 검토 완료 전 자동 몰수·영구 정지를 실행하는 코드 경로를 만들지 않는다.
5. 민감정보는 마스킹 기본, 원본 조회는 권한+조회 감사 필수.
6. 클라이언트(React) 측 제한은 UX 편의일 뿐 보안 경계가 아니다.

## 3. 시스템 구성

```text
apps/
├─ admin-frontend/   # React+TS(Vite) 관리자 콘솔  ← 신규
├─ admin-backend/    # Kotlin Spring Boot admin 서버 ← 신규
├─ backend/          # 기존 서비스 API (변경 최소화)
└─ frontend/         # 기존 KMM 앱 (범위 외)
```

- 데이터 접근 (**UNR-013 확정, 2026-07-18**): admin-backend가 **운영 MySQL을 직접 공유**한다(관리자 전용 테이블은 별도 스키마). 자산 변경은 원장·감사 불변 규칙(§2)이 보호한다.
- 서비스 서버 연동 확정 필요 지점: 제재 상태 검증(REQ-014), health·metric(REQ-048·049), 재기동 파이프라인(REQ-050).

### 공용 모듈 (UNR-002 확정)

- `domain/policy/` — 동적 운영 정책 저장소(저장·버전·롤백·유효시각). REQ-027·028·032·039·041·046이 공용 사용, 정책 내용은 각 도메인 소유.

## 4. 도메인 배치

admin-backend는 기존 백엔드와 동일한 도메인 레이어링을 따르며, PRD §3의 14개 도메인을 `domain/<name>/` 모듈로 배치한다. 상세 테이블·컬럼·API 스키마는 4단계 구현 시 확정한다.
