-- =====================================================================
-- V7: Mijoz verifikatsiyasi (SMS OTP / biometrik natija) va moliyaviy sozlamalar
-- =====================================================================
ALTER TABLE tenant_settings
    ADD COLUMN verification_mode   VARCHAR(10)   NOT NULL DEFAULT 'NONE',
    ADD COLUMN otp_expiry_seconds  INTEGER       NOT NULL DEFAULT 120,
    ADD COLUMN min_face_match_pct  INTEGER       NOT NULL DEFAULT 85,
    -- prognoz va analitika uchun: oylik operatsion xarajat, o'z kapitali, jalb qilingan mablag' narxi (yillik %)
    ADD COLUMN monthly_opex        NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN own_capital         NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN funding_cost_pct    NUMERIC(5,2)  NOT NULL DEFAULT 22,
    ADD CONSTRAINT ck_settings_verification CHECK (verification_mode IN ('NONE','OTP','BIOMETRIC','ANY','BOTH')),
    ADD CONSTRAINT ck_settings_fin CHECK (otp_expiry_seconds BETWEEN 30 AND 900 AND min_face_match_pct BETWEEN 50 AND 100
        AND monthly_opex >= 0 AND own_capital >= 0 AND funding_cost_pct >= 0 AND funding_cost_pct <= 100);

CREATE TABLE client_verifications (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    client_id     BIGINT       NOT NULL REFERENCES clients (id),
    method        VARCHAR(10)  NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    phone         VARCHAR(20),
    code_hash     VARCHAR(100),
    expires_at    TIMESTAMPTZ,
    attempts      INTEGER      NOT NULL DEFAULT 0,
    match_pct     INTEGER,
    reference     VARCHAR(100),
    verified_at   TIMESTAMPTZ,
    created_by    BIGINT       REFERENCES users (id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_verif_method CHECK (method IN ('OTP','BIOMETRIC')),
    CONSTRAINT ck_verif_status CHECK (status IN ('PENDING','VERIFIED','FAILED','EXPIRED'))
);
CREATE INDEX ix_verif_client ON client_verifications (client_id, created_at DESC);

ALTER TABLE applications
    ADD COLUMN verification_methods VARCHAR(40),
    ADD COLUMN verified_at TIMESTAMPTZ;
