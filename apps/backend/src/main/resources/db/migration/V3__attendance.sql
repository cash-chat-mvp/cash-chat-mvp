-- V3: 출석 도메인 — 로그 + 보상 시드 (Phase 1 가설값)

CREATE TABLE attendance_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    check_in_date    DATE         NOT NULL,
    streak_day_count INT          NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_attendance_log_user_date UNIQUE (user_id, check_in_date),
    CONSTRAINT fk_attendance_log_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE attendance_reward (
    day_count INT    NOT NULL,
    coin      BIGINT NOT NULL,
    PRIMARY KEY (day_count)
);

CREATE TABLE attendance_reward_bonus (
    id        BIGINT      NOT NULL AUTO_INCREMENT,
    day_count INT         NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    quantity  INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendance_reward_bonus_day FOREIGN KEY (day_count) REFERENCES attendance_reward (day_count)
);
CREATE INDEX idx_attendance_reward_bonus_day ON attendance_reward_bonus (day_count);

-- 시드: day_count=0 은 기본 일일 보상(코인 20, 보너스 없음). 7/14/30 마일스톤만 오버라이드.
INSERT INTO attendance_reward (day_count, coin) VALUES (0, 20), (7, 50), (14, 100), (30, 300);

INSERT INTO attendance_reward_bonus (day_count, item_code, quantity) VALUES
    (7,  'EVO_STONE',      1),
    (14, 'EVO_STONE',      2),
    (14, 'LUCK_CHARM',     1),
    (30, 'PROTECT_TICKET', 1);
