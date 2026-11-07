-- =====================================================================
-- V8: Hamkor nasiya provayderlari (Alif, Uzum, TBC).
-- Mijoz ichki limitga sig'masa yoki do'kon xavfni o'zi olishni istamasa, savdo hamkor orqali
-- amalga oshiriladi: hamkor do'konga pulni darhol (komissiyani ushlab qolib) to'laydi, qarz — hamkorda.
-- =====================================================================
CREATE TABLE partners (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL DEFAULT 1,
    code           VARCHAR(20)   NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    commission_pct NUMERIC(5,2)  NOT NULL,   -- hamkor do'kondan ushlab qoladigan komissiya, savdo summasidan %
    max_term       INTEGER       NOT NULL,   -- maksimal muddat, oy
    min_down_pct   NUMERIC(5,2)  NOT NULL DEFAULT 0,  -- minimal boshlang'ich to'lov, narxdan %
    dti_pct        NUMERIC(5,2)  NOT NULL,   -- hamkorning oylik to'lov chegarasi, daromaddan % (A toifa uchun)
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    sort_order     INTEGER       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_partners_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_partners CHECK (commission_pct BETWEEN 0 AND 50 AND max_term BETWEEN 1 AND 60
        AND min_down_pct BETWEEN 0 AND 90 AND dti_pct BETWEEN 0 AND 100)
);

INSERT INTO partners (tenant_id, code, name, commission_pct, max_term, min_down_pct, dti_pct, sort_order) VALUES
    (1, 'ALIF', 'Alif Nasiya', 12, 12, 0, 45, 1),
    (1, 'UZUM', 'Uzum Nasiya', 10, 12, 0, 40, 2),
    (1, 'TBC',  'TBC Nasiya',   9, 18, 20, 35, 3);

ALTER TABLE applications DROP CONSTRAINT ck_applications_status;
ALTER TABLE applications ADD CONSTRAINT ck_applications_status
    CHECK (status IN ('PENDING','APPROVED','CONTRACTED','REJECTED','PARTNER'));
ALTER TABLE applications
    ADD COLUMN provider           VARCHAR(20)   NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN partner_id         BIGINT        REFERENCES partners (id),
    ADD COLUMN partner_tx_id      VARCHAR(100),
    ADD COLUMN partner_handover_at TIMESTAMPTZ,
    ADD COLUMN partner_notes      VARCHAR(500),
    ADD COLUMN partner_commission NUMERIC(18,2);
