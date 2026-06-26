-- V13: 개정 경제 모델(CC-283) R1 — 채팅 완료 보상 루프
-- 밥 예약(reserved) 컬럼 + 진화 경험치(evolution_exp) 컬럼 추가.
-- 광고/오퍼월/ledger/quality/상점은 건드리지 않는다.

ALTER TABLE user_energy ADD COLUMN reserved_energy INT NOT NULL DEFAULT 0;
ALTER TABLE user_evolution ADD COLUMN evolution_exp BIGINT NOT NULL DEFAULT 0;
