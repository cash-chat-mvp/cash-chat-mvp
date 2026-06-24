-- V13: SSV nonce 를 custom_data 로 정렬. FE 가 setCustomData(nonce) 로 보내므로 nonce 를 담을 컬럼 추가.
ALTER TABLE google_ad_ssv_events
    ADD COLUMN custom_data VARCHAR(1024) NULL;
