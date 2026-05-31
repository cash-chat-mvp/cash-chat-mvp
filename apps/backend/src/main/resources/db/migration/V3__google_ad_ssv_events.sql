-- V3: Google AdMob SSV 검증 이벤트 (transaction_id 유니크)

CREATE TABLE google_ad_ssv_events (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id   VARCHAR(128) NOT NULL,
    user_id          VARCHAR(128) NOT NULL,
    reward_amount    INT          NOT NULL,
    reward_item      VARCHAR(128) NOT NULL,
    ad_unit          VARCHAR(255) NOT NULL,
    key_id           BIGINT       NOT NULL,
    reward_status    VARCHAR(32)  NOT NULL,
    raw_query_string TEXT         NOT NULL,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_google_ad_ssv_events_transaction_id UNIQUE (transaction_id)
);
