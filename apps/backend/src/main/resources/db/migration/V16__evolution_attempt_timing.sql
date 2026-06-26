-- V16: 진화 시도 타이밍 보너스 판정 컬럼 (CC-352).
-- 모두 nullable — 레거시/타이밍 미사용 시도는 NULL. 멱등 재시도 시 최초 판정값을 그대로 재현하기 위해 영속한다.
-- timing_grade 는 TimingGrade enum(NORMAL/GREAT/PERFECT)을 STRING 으로 저장.

ALTER TABLE evolution_attempt ADD COLUMN timing_grade VARCHAR(255) NULL;
ALTER TABLE evolution_attempt ADD COLUMN timing_bonus_rate DOUBLE NULL;
ALTER TABLE evolution_attempt ADD COLUMN base_success_rate DOUBLE NULL;
ALTER TABLE evolution_attempt ADD COLUMN final_success_rate DOUBLE NULL;
