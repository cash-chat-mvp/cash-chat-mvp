-- V8: 확률형 진화 시도 기록

CREATE TABLE evolution_attempt (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    user_id            BIGINT       NOT NULL,
    attempt_key        VARCHAR(255) NOT NULL,
    level_before       INT          NOT NULL,
    level_after        INT          NOT NULL,
    required_exp       BIGINT       NOT NULL,
    base_success_rate  DOUBLE       NOT NULL,
    fail_stack_before  INT          NOT NULL,
    final_success_rate DOUBLE       NOT NULL,
    roll_value         DOUBLE       NOT NULL,
    result             VARCHAR(20)  NOT NULL,
    exp_after          BIGINT       NOT NULL,
    fail_stack_after   INT          NOT NULL,
    policy_version     INT          NOT NULL,
    created_at         TIMESTAMP(6) NOT NULL,
    updated_at         TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_evolution_attempt_user_key UNIQUE (user_id, attempt_key),
    CONSTRAINT fk_evolution_attempt_user FOREIGN KEY (user_id) REFERENCES users (id)
);
