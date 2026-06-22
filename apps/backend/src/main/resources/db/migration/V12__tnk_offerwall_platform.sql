-- V12: TNK 오퍼월 콜백을 플랫폼(Android/iOS) 인지로 확장.
-- TNK 미연동 상태라 tnk_offerwall_callbacks 가 비어 있어 NOT NULL 컬럼 추가가 안전하다.

-- DEFAULT 미지정: 테이블이 비어 있어 NOT NULL 추가가 안전하고, 빈 문자열('')이 백필되면
-- OfferwallPlatform enum 매핑이 실패하므로 의도적으로 기본값을 두지 않는다.
ALTER TABLE tnk_offerwall_callbacks
    ADD COLUMN platform VARCHAR(16) NOT NULL;

-- seq_id 단독 유니크 → (platform, seq_id) 복합 유니크로 교체.
-- 동일 seq_id 라도 플랫폼이 다르면 독립 콜백으로 처리한다.
ALTER TABLE tnk_offerwall_callbacks
    DROP CONSTRAINT uk_tnk_offerwall_callbacks_seq_id;

ALTER TABLE tnk_offerwall_callbacks
    ADD CONSTRAINT uk_tnk_offerwall_callbacks_platform_seq_id UNIQUE (platform, seq_id);
