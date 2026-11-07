-- =====================================================================
-- V12: MIB (Majburiy ijro byurosi) bo'yicha qarzdorlik tekshiruvi.
-- Mijoz JShShIR orqali tekshiriladi; natija mijoz kartasida saqlanadi va skoringga (stop-faktor) ta'sir qiladi.
-- =====================================================================
CREATE TABLE mib_checks (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL DEFAULT 1,
    client_id   BIGINT        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    pinfl       VARCHAR(14)   NOT NULL,
    -- CLEAR — qarz yo'q, DEBT — qarz bor, ERROR — tekshirib bo'lmadi (xizmat javob bermadi yoki sozlanmagan)
    status      VARCHAR(10)   NOT NULL,
    -- API — MIB xizmatidan avtomatik; MANUAL — xodim MIB portalidan ko'rib qo'lda kiritgan
    source      VARCHAR(10)   NOT NULL,
    total_debt  NUMERIC(18,2) NOT NULL DEFAULT 0,
    case_count  INTEGER       NOT NULL DEFAULT 0,
    cases       JSONB,
    message     VARCHAR(500),
    checked_by  BIGINT        REFERENCES users (id),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_mib_status CHECK (status IN ('CLEAR','DEBT','ERROR')),
    CONSTRAINT ck_mib_source CHECK (source IN ('API','MANUAL')),
    CONSTRAINT ck_mib_amounts CHECK (total_debt >= 0 AND case_count >= 0)
);
CREATE INDEX ix_mib_client ON mib_checks (client_id, created_at DESC);

-- OFF — tekshirilmaydi; OPTIONAL — tekshiriladi, xizmat ishlamasa ariza davom etadi; REQUIRED — natijasiz ariza yuborilmaydi
ALTER TABLE tenant_settings
    ADD COLUMN mib_mode          VARCHAR(10) NOT NULL DEFAULT 'OFF',
    ADD COLUMN mib_validity_days INTEGER     NOT NULL DEFAULT 7,
    ADD CONSTRAINT ck_settings_mib CHECK (mib_mode IN ('OFF','OPTIONAL','REQUIRED') AND mib_validity_days BETWEEN 1 AND 90);
