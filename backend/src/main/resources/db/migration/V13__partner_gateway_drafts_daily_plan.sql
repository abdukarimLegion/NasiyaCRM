-- =====================================================================
-- V13: Nasiya beruvchi hamkorlar ro'yxati kengaytirildi (hozircha mock integratsiya),
--      hamkorga topshirishda seriya raqamlari, ariza qoralamalari va kunlik reja.
-- =====================================================================

-- Hamkor shartlari namunaviy (mock): haqiqiy shartnoma shartlari Sozlamalar → Hamkorlar'da tahrirlanadi.
UPDATE partners SET name = 'Alif' WHERE tenant_id = 1 AND code = 'ALIF' AND name = 'Alif Nasiya';
INSERT INTO partners (tenant_id, code, name, commission_pct, max_term, min_down_pct, dti_pct, sort_order) VALUES
    (1, 'UZUM_BANK', 'Uzum Bank',                      9, 24,  0, 40, 4),
    (1, 'ANOR',      'Anor Bank',                     10, 12,  0, 40, 5),
    (1, 'OPEN',      'Open kart',                     11, 12,  0, 35, 6),
    (1, 'CHAKANA',   'Chakana',                       12,  9,  0, 35, 7),
    (1, 'PAYME',     'Payme Nasiya',                  11, 12,  0, 40, 8),
    (1, 'INFIN',     'Infin muddatli to''lov kartasi', 10, 12,  0, 35, 9),
    (1, 'PAYNET',    'Paynet Nasiya',                 12, 12, 10, 35, 10)
ON CONFLICT (tenant_id, code) DO NOTHING;

-- Hamkorga topshirilgan tovarlarning seriya raqami / IMEI ro'yxati: [{"name": "...", "serial": "..."}]
ALTER TABLE applications ADD COLUMN partner_serials JSONB;

-- Ariza qoralamasi: konveyerning istalgan qadamida saqlanadi va keyin davom ettiriladi.
CREATE TABLE application_drafts (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    client_id   BIGINT       NOT NULL REFERENCES clients (id),
    officer_id  BIGINT       NOT NULL REFERENCES users (id),
    branch_id   BIGINT       REFERENCES branches (id),
    step        INTEGER      NOT NULL DEFAULT 0,
    summary     VARCHAR(300),
    amount      NUMERIC(18,2),
    payload     JSONB        NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_application_drafts_officer ON application_drafts (tenant_id, officer_id);

-- Kunlik reja (boshqaruv panelidagi monitoring): filial bo'yicha, branch_id NULL — butun kompaniya.
CREATE TABLE daily_plans (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    branch_id         BIGINT        REFERENCES branches (id),
    sales_target      NUMERIC(18,2) NOT NULL DEFAULT 0,
    contracts_target  INTEGER       NOT NULL DEFAULT 0,
    collection_target NUMERIC(18,2) NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_daily_plans_branch ON daily_plans (tenant_id, coalesce(branch_id, 0));
