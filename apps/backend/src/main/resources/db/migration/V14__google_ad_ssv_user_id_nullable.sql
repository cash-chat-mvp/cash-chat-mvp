-- V14: FE 는 SSV user_id 를 보내지 않으므로(nonce 는 custom_data) user_id 를 nullable 로 완화한다.
ALTER TABLE google_ad_ssv_events
    MODIFY COLUMN user_id VARCHAR(128) NULL;
