-- =====================================================================
-- V5: Nasiya arizalari va ko'p mahsulotli savat.
--  * contract_items — shartnomadagi mahsulotlar (har bir qator: narx × miqdor)
--  * applications   — ariza (PENDING → APPROVED → CONTRACTED yoki REJECTED)
-- =====================================================================

CREATE TABLE contract_items (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT        NOT NULL DEFAULT 1,
    contract_id  BIGINT        NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    product_id   BIGINT        REFERENCES products (id),
    product_name VARCHAR(200)  NOT NULL,
    unit_price   NUMERIC(18,2) NOT NULL,
    quantity     INTEGER       NOT NULL DEFAULT 1,
    line_total   NUMERIC(18,2) NOT NULL,
    CONSTRAINT ck_contract_items_qty   CHECK (quantity >= 1),
    CONSTRAINT ck_contract_items_price CHECK (unit_price > 0),
    CONSTRAINT ck_contract_items_total CHECK (line_total = unit_price * quantity)
);
CREATE INDEX ix_contract_items_contract ON contract_items (contract_id);
CREATE INDEX ix_contract_items_product  ON contract_items (product_id);

-- Mavjud shartnomalar: bitta qator (mahsulot tannarxi)
INSERT INTO contract_items (tenant_id, contract_id, product_id, product_name, unit_price, quantity, line_total)
SELECT tenant_id, id, product_id, product_name, cost_price, 1, cost_price FROM contracts;

CREATE SEQUENCE application_no_seq START 1;

CREATE TABLE applications (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT        NOT NULL DEFAULT 1,
    application_no   VARCHAR(30)   NOT NULL,
    client_id        BIGINT        NOT NULL REFERENCES clients (id),
    branch_id        BIGINT        REFERENCES branches (id),
    officer_id       BIGINT        REFERENCES users (id),
    -- to'liq so'rov (savat, shartlar, skoring) — ariza tasdiqlangach shartnoma shu asosda tuziladi
    request          JSONB         NOT NULL,
    items_summary    VARCHAR(300)  NOT NULL,
    cost_price       NUMERIC(18,2) NOT NULL,
    down_payment     NUMERIC(18,2) NOT NULL DEFAULT 0,
    markup_pct       NUMERIC(5,2)  NOT NULL,
    sale_price       NUMERIC(18,2) NOT NULL,
    installment_total NUMERIC(18,2) NOT NULL,
    term_months      INTEGER       NOT NULL,
    monthly_payment  NUMERIC(18,2) NOT NULL,
    score_total      INTEGER,
    risk_category    VARCHAR(1),
    decision         VARCHAR(20),
    score_details    JSONB,
    status           VARCHAR(20)   NOT NULL,
    rejection_reason VARCHAR(500),
    decided_by       BIGINT        REFERENCES users (id),
    decided_at       TIMESTAMPTZ,
    contract_id      BIGINT        REFERENCES contracts (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_applications_no UNIQUE (tenant_id, application_no),
    CONSTRAINT ck_applications_status CHECK (status IN ('PENDING','APPROVED','CONTRACTED','REJECTED')),
    CONSTRAINT ck_applications_risk CHECK (risk_category IS NULL OR risk_category IN ('A','B','C','D'))
);
CREATE INDEX ix_applications_status ON applications (tenant_id, status);
CREATE INDEX ix_applications_client ON applications (client_id);
CREATE INDEX ix_applications_officer ON applications (officer_id, created_at);

-- Shartnoma qaysi arizadan tuzilgani
ALTER TABLE contracts ADD COLUMN application_id BIGINT REFERENCES applications (id);
