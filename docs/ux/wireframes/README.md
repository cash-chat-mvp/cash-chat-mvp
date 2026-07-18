# 와이어프레임 — Cash Chat Admin v0.2 (페이지 단위)

- 구성: 관리자 콘솔 **페이지 단위** lo-fi HTML. 모든 페이지는 공용 사이드바 셸을 포함하며 서로 링크된다.
- 뷰어: [index.html](./index.html) 을 브라우저로 열면 전체 페이지 목록 + 미리보기.
- REQ-007(위험 작업 이중 확인)은 별도 페이지가 아니라 **공통 모달**로, 제재·조정·취소 등 위험 작업 페이지 안에 표현되어 있다.
- 수정 요청은 페이지 번호로 요청하면 해당 페이지만 재생성한다.

## 페이지 ↔ REQ ↔ Jira 스토리 매핑 (34페이지)

| 페이지 | 메뉴 그룹 | 제목 | REQ (Jira 스토리) |
|---|---|---|---|
| 00-login | (셸 밖) | 로그인 | REQ-001(CC-582), REQ-004(CC-585) |
| 01-dashboard-overview | 대시보드 | 운영 현황 | REQ-008(CC-589) |
| 02-dashboard-users | 대시보드 | 회원·활동 지표 | REQ-009(CC-590) |
| 03-dashboard-economy | 대시보드 | 보상 경제·재무 지표 | REQ-010(CC-591) |
| 04-dashboard-ai-cost | 대시보드 | AI 비용 지표 | REQ-011(CC-592), REQ-031(CC-612) |
| 05-members | 회원 관리 | 회원 목록·검색 | REQ-012(CC-593) |
| 06-member-detail | 회원 관리 | 회원 상세 | REQ-013(CC-594), REQ-015(CC-596), REQ-016(CC-597), REQ-017(CC-598) |
| 07-sanctions | 회원 관리 | 이용 제재 | REQ-014(CC-595) |
| 08-withdrawals | 회원 관리 | 탈퇴 처리 | REQ-018(CC-599) |
| 09-ledger | 보상·포인트 | 자산·원장 조회 | REQ-019(CC-600) |
| 10-manual-adjust | 보상·포인트 | 포인트·Energy 수동 조정 | REQ-020(CC-601), REQ-021(CC-602) |
| 11-reward-channels | 보상·포인트 | 채널별 보상·실패 재처리 | REQ-022(CC-603), REQ-023(CC-604) |
| 12-ad-ssv | 광고 운영 | 광고 시청·SSV 이력 | REQ-024(CC-605), REQ-025(CC-606) |
| 13-offerwall | 광고 운영 | 오퍼월 이력 | REQ-026(CC-607) |
| 14-ad-policy | 광고 운영 | 광고 정책·노출 관리 | REQ-027(CC-608), REQ-028(CC-609) |
| 15-chat-history | 채팅·AI | 채팅 이력 조회 | REQ-029(CC-610) |
| 16-chat-analytics | 채팅·AI | 채팅 성공·실패 분석 | REQ-030(CC-611) |
| 17-ai-policy | 채팅·AI | AI 라우팅·품질 풀 정책 | REQ-032(CC-613), REQ-033(CC-614) |
| 18-products | 상점·주문 | 상품·재고 관리 | REQ-034(CC-615), REQ-035(CC-616) |
| 19-orders | 상점·주문 | 주문 관리 | REQ-036(CC-617), REQ-037(CC-618), REQ-038(CC-619) |
| 20-evolution | 콘텐츠·리텐션 | 진화 정책·이력 | REQ-039(CC-620), REQ-040(CC-621) |
| 21-retention-policy | 콘텐츠·리텐션 | 출석·초대·룰렛 정책 | REQ-041(CC-622) |
| 22-notices-terms | 고객지원·공지 | 공지·약관 관리 | REQ-042(CC-623), REQ-043(CC-624) |
| 23-voc | 고객지원·공지 | 문의·VoC 관리 | REQ-044(CC-625), REQ-045(CC-626) |
| 24-app-settings-push | 고객지원·공지 | 앱 설정·푸시 발송 | REQ-046(CC-627), REQ-047(CC-628) |
| 25-system-status | 시스템 운영 | 서비스 상태·성능 | REQ-048(CC-629), REQ-049(CC-630) |
| 26-deploy | 시스템 운영 | 배포·재기동 요청 | REQ-050(CC-631) |
| 27-marketing | 마케팅·정산 | 마케팅 비용·유입 분석 | REQ-051(CC-632), REQ-052(CC-633), REQ-053(CC-634) |
| 28-revenue-settlement | 마케팅·정산 | 수익·정산 | REQ-054(CC-635), REQ-055(CC-636) |
| 29-abuse | 어뷰징·위험 | 이상 적립·식별자 차단 | REQ-056(CC-637), REQ-057(CC-638) |
| 30-i18n | 국제화 | 다국어·국가별 지표 | REQ-058(CC-639), REQ-059(CC-640) |
| 31-admin-accounts | 관리자·보안 | 관리자 계정 관리 | REQ-002(CC-583) |
| 32-roles | 관리자·보안 | 역할·권한 설정 | REQ-003(CC-584) |
| 33-audit-log | 관리자·보안 | 감사 로그 조회 | REQ-005(CC-586), REQ-006(CC-587) |
