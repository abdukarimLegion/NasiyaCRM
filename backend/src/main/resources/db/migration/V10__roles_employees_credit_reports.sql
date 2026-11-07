-- =====================================================================
-- V10: qo'shimcha rollar, shaxsiy rollar (ruxsatlar to'plami), xodim ma'lumotlari va
--      mijozning kredit tarixi (KATM / tashqi byuro) yozuvlari.
-- =====================================================================
ALTER TABLE users DROP CONSTRAINT ck_users_role;
ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN
    ('ADMIN','CREDIT_OFFICER','COLLECTOR','CASHIER','RISK_ANALYST','LEGAL_OFFICER','BRANCH_MANAGER'));
ALTER TABLE role_permissions DROP CONSTRAINT ck_role_permissions_role;
ALTER TABLE role_permissions ADD CONSTRAINT ck_role_permissions_role CHECK (role IN
    ('CREDIT_OFFICER','COLLECTOR','CASHIER','RISK_ANALYST','LEGAL_OFFICER','BRANCH_MANAGER'));

-- Shaxsiy rol: nom, rang va ruxsatlar to'plami. Xodimga biriktirilsa, ruxsatlari shu to'plamdan olinadi
-- (to'plam o'zgarsa, barcha shu roldagi xodimlarda o'zgaradi). base_role — tizim darajasidagi asosiy rol.
CREATE TABLE custom_roles (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    name        VARCHAR(80)  NOT NULL,
    description VARCHAR(200),
    color       VARCHAR(20)  NOT NULL DEFAULT '#64748b',
    base_role   VARCHAR(20)  NOT NULL DEFAULT 'CREDIT_OFFICER',
    permissions JSONB        NOT NULL DEFAULT '[]'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_custom_roles_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_custom_roles_base CHECK (base_role IN
        ('CREDIT_OFFICER','COLLECTOR','CASHIER','RISK_ANALYST','LEGAL_OFFICER','BRANCH_MANAGER'))
);

-- Xodim shaxsiy ma'lumotlari (komplayens): xodim o'zi nasiya olishi cheklanadi (allow_nasiya = false)
ALTER TABLE users
    ADD COLUMN custom_role_id BIGINT REFERENCES custom_roles (id),
    ADD COLUMN pinfl           VARCHAR(14),
    ADD COLUMN passport_series VARCHAR(20),
    ADD COLUMN birth_date      DATE,
    ADD COLUMN home_address    VARCHAR(300),
    ADD COLUMN phone           VARCHAR(20),
    ADD COLUMN email           VARCHAR(150),
    ADD COLUMN allow_nasiya    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN nasiya_note     VARCHAR(300),
    ADD CONSTRAINT ck_users_pinfl CHECK (pinfl IS NULL OR pinfl ~ '^[0-9]{14}$');
CREATE UNIQUE INDEX uq_users_pinfl ON users (tenant_id, pinfl) WHERE pinfl IS NOT NULL;

-- Kredit tarixi: KATM yoki boshqa kredit byurosi hisobotidan olingan ma'lumot (qo'lda kiritiladi)
CREATE TABLE credit_reports (
    id                 BIGSERIAL PRIMARY KEY,
    tenant_id          BIGINT        NOT NULL DEFAULT 1,
    client_id          BIGINT        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    source             VARCHAR(30)   NOT NULL DEFAULT 'KATM',
    report_date        DATE          NOT NULL,
    score              INTEGER,
    rating             VARCHAR(2),
    active_loans       INTEGER       NOT NULL DEFAULT 0,
    total_debt         NUMERIC(18,2) NOT NULL DEFAULT 0,
    monthly_obligation NUMERIC(18,2) NOT NULL DEFAULT 0,
    overdue_flag       BOOLEAN       NOT NULL DEFAULT FALSE,
    max_overdue_days   INTEGER       NOT NULL DEFAULT 0,
    note               VARCHAR(500),
    entered_by         BIGINT        REFERENCES users (id),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_credit_reports CHECK (active_loans >= 0 AND total_debt >= 0 AND monthly_obligation >= 0
        AND max_overdue_days >= 0 AND (score IS NULL OR score BETWEEN 0 AND 1000)
        AND (rating IS NULL OR rating IN ('A','B','C','D','E')))
);
CREATE INDEX ix_credit_reports_client ON credit_reports (client_id, report_date DESC);
