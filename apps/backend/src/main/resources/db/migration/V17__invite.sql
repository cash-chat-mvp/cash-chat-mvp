-- V13: 친구 초대(추천 코드) — 코드 발급(invite_codes) + redeem 원장(invite_redemptions)

CREATE TABLE invite_codes (
    user_id    BIGINT       NOT NULL,
    code       VARCHAR(16)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uq_invite_codes_code UNIQUE (code),
    CONSTRAINT fk_invite_codes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE invite_redemptions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    invitee_user_id BIGINT       NOT NULL,
    inviter_user_id BIGINT       NOT NULL,
    code            VARCHAR(16)  NOT NULL,
    awarded_energy  INT          NOT NULL,
    awarded_coin    BIGINT       NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_invite_redemptions_invitee UNIQUE (invitee_user_id),
    CONSTRAINT fk_invite_redemptions_invitee FOREIGN KEY (invitee_user_id) REFERENCES users (id),
    CONSTRAINT fk_invite_redemptions_inviter FOREIGN KEY (inviter_user_id) REFERENCES users (id)
);

-- (inviter_user_id, status) 복합 인덱스: cap 카운트 countByInviterUserIdAndStatus(GRANTED) 가속.
-- 선두 컬럼이 inviter_user_id 이므로 invitedCount(countByInviterUserId) 조회도 함께 커버한다.
CREATE INDEX idx_invite_redemptions_inviter_status ON invite_redemptions (inviter_user_id, status);
