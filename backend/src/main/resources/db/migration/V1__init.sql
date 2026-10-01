-- =====================================================================
-- V1: Asosiy sxema.
-- Har bir biznes jadvalda tenant_id bor. Hozircha doim 1 (bitta mijoz
-- serverida), SaaS bosqichida Row-Level Security / Hibernate filtri
-- bilan yoqiladi.
-- Pul summalari: NUMERIC(18,2), so'mda.
-- =====================================================================

CREATE TABLE tenant_settings (
    tenant_id            BIGINT PRIMARY KEY,
    company_name         VARCHAR(200) NOT NULL,
    brand_name           VARCHAR(100) NOT NULL,
    primary_color        VARCHAR(20)  NOT NULL DEFAULT '#1f8a5b',
    logo_url             VARCHAR(500),
    contract_prefix      VARCHAR(10)  NOT NULL DEFAULT 'NS',
    default_lang         VARCHAR(5)   NOT NULL DEFAULT 'uz',
    rounding_step        INTEGER      NOT NULL DEFAULT 1000,
    max_active_contracts INTEGER      NOT NULL DEFAULT 3,
    sms_sender           VARCHAR(20),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_settings_rounding CHECK (rounding_step >= 1)
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_username UNIQUE (tenant_id, username),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN','CREDIT_OFFICER','COLLECTOR','CASHIER'))
);

CREATE TABLE clients (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL DEFAULT 1,
    full_name        VARCHAR(150) NOT NULL,
    pinfl            VARCHAR(14)  NOT NULL,
    passport_series  VARCHAR(20),
    passport_expiry  DATE,
    birth_date       DATE,
    phone            VARCHAR(20)  NOT NULL,
    extra_phone      VARCHAR(20),
    region           VARCHAR(100),
    district         VARCHAR(100),
    address          VARCHAR(300),
    workplace        VARCHAR(200),
    monthly_income   NUMERIC(18,2),
    family_status    VARCHAR(50),
    telegram_chat_id BIGINT,
    blacklisted      BOOLEAN      NOT NULL DEFAULT FALSE,
    note             TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_clients_pinfl UNIQUE (tenant_id, pinfl),
    CONSTRAINT ck_clients_pinfl CHECK (pinfl ~ '^[0-9]{14}$')
);
CREATE INDEX ix_clients_name  ON clients (tenant_id, lower(full_name));
CREATE INDEX ix_clients_phone ON clients (tenant_id, phone);

CREATE TABLE categories (
    id        BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT       NOT NULL DEFAULT 1,
    code      VARCHAR(30)  NOT NULL,
    name_uz   VARCHAR(100) NOT NULL,
    name_ru   VARCHAR(100) NOT NULL,
    CONSTRAINT uq_categories_code UNIQUE (tenant_id, code)
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL DEFAULT 1,
    category_id BIGINT        NOT NULL REFERENCES categories (id),
    name        VARCHAR(200)  NOT NULL,
    sku         VARCHAR(50),
    price       NUMERIC(18,2) NOT NULL,
    markup_pct  NUMERIC(5,2)  NOT NULL,
    term_min    INTEGER       NOT NULL,
    term_max    INTEGER       NOT NULL,
    stock       INTEGER       NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_products_price  CHECK (price > 0),
    CONSTRAINT ck_products_markup CHECK (markup_pct >= 0 AND markup_pct <= 100),
    CONSTRAINT ck_products_term   CHECK (term_min >= 1 AND term_max >= term_min AND term_max <= 60)
);
CREATE INDEX ix_products_category ON products (category_id);

CREATE SEQUENCE contract_no_seq START 1;

CREATE TABLE contracts (
    id                BIGSERIAL PRIMARY KEY,
    tenant_id         BIGINT        NOT NULL DEFAULT 1,
    contract_no       VARCHAR(30)   NOT NULL,
    client_id         BIGINT        NOT NULL REFERENCES clients (id),
    product_id        BIGINT        REFERENCES products (id),
    product_name      VARCHAR(200)  NOT NULL,
    cost_price        NUMERIC(18,2) NOT NULL,   -- sotuvchining tannarxi (mahsulot narxi)
    down_payment      NUMERIC(18,2) NOT NULL DEFAULT 0,
    markup_pct        NUMERIC(5,2)  NOT NULL,
    markup_amount     NUMERIC(18,2) NOT NULL,   -- ustama (murobaha foydasi)
    sale_price        NUMERIC(18,2) NOT NULL,   -- cost_price + markup_amount
    installment_total NUMERIC(18,2) NOT NULL,   -- sale_price - down_payment
    term_months       INTEGER       NOT NULL,
    monthly_payment   NUMERIC(18,2) NOT NULL,
    first_due_date    DATE          NOT NULL,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    score_total       INTEGER,
    risk_category     VARCHAR(1),
    decision          VARCHAR(20),
    score_details     JSONB,
    officer_id        BIGINT        REFERENCES users (id),
    guarantor_name    VARCHAR(150),
    guarantor_phone   VARCHAR(20),
    signed_at         TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_contracts_no UNIQUE (tenant_id, contract_no),
    CONSTRAINT ck_contracts_status CHECK (status IN ('DRAFT','ACTIVE','LATE','CLOSED','CANCELLED')),
    CONSTRAINT ck_contracts_risk CHECK (risk_category IS NULL OR risk_category IN ('A','B','C','D')),
    CONSTRAINT ck_contracts_amounts CHECK (
        cost_price > 0 AND down_payment >= 0 AND down_payment < cost_price
        AND sale_price = cost_price + markup_amount
        AND installment_total = sale_price - down_payment),
    CONSTRAINT ck_contracts_term CHECK (term_months BETWEEN 1 AND 60)
);
CREATE INDEX ix_contracts_client ON contracts (client_id);
CREATE INDEX ix_contracts_status ON contracts (tenant_id, status);

CREATE TABLE schedule_items (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL DEFAULT 1,
    contract_id    BIGINT        NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    seq            INTEGER       NOT NULL,
    due_date       DATE          NOT NULL,
    amount         NUMERIC(18,2) NOT NULL,
    principal_part NUMERIC(18,2) NOT NULL,
    markup_part    NUMERIC(18,2) NOT NULL,
    paid_amount    NUMERIC(18,2) NOT NULL DEFAULT 0,
    status         VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    paid_at        TIMESTAMPTZ,
    CONSTRAINT uq_schedule_seq UNIQUE (contract_id, seq),
    CONSTRAINT ck_schedule_status CHECK (status IN ('PENDING','PARTIAL','PAID','LATE')),
    CONSTRAINT ck_schedule_parts CHECK (amount = principal_part + markup_part AND paid_amount >= 0 AND paid_amount <= amount)
);
CREATE INDEX ix_schedule_due ON schedule_items (tenant_id, due_date) WHERE status <> 'PAID';

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL DEFAULT 1,
    contract_id BIGINT        NOT NULL REFERENCES contracts (id),
    amount      NUMERIC(18,2) NOT NULL,
    method      VARCHAR(20)   NOT NULL,
    paid_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    external_id VARCHAR(100),
    cashier_id  BIGINT        REFERENCES users (id),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (method IN ('CASH','CARD','PAYME','CLICK','UZUM','BANK_TRANSFER'))
);
CREATE INDEX ix_payments_contract ON payments (contract_id);
-- Payme/Click callback'lari ikki marta kelsa, ikki marta yozilmasin
CREATE UNIQUE INDEX uq_payments_external ON payments (tenant_id, method, external_id) WHERE external_id IS NOT NULL;

CREATE TABLE payment_allocations (
    id               BIGSERIAL PRIMARY KEY,
    payment_id       BIGINT        NOT NULL REFERENCES payments (id) ON DELETE CASCADE,
    schedule_item_id BIGINT        NOT NULL REFERENCES schedule_items (id),
    amount           NUMERIC(18,2) NOT NULL CHECK (amount > 0)
);
CREATE INDEX ix_alloc_payment ON payment_allocations (payment_id);

CREATE TABLE collection_actions (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 1,
    contract_id     BIGINT       NOT NULL REFERENCES contracts (id),
    action_type     VARCHAR(20)  NOT NULL,
    result          VARCHAR(30),
    note            VARCHAR(1000),
    promised_date   DATE,
    promised_amount NUMERIC(18,2),
    user_id         BIGINT       REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_collection_type CHECK (action_type IN ('CALL','SMS','VISIT','LETTER','LEGAL','NOTE'))
);
CREATE INDEX ix_collection_contract ON collection_actions (contract_id);

CREATE TABLE notification_templates (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    event_key   VARCHAR(30)  NOT NULL,
    channel     VARCHAR(10)  NOT NULL DEFAULT 'SMS',
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    offset_days INTEGER      NOT NULL DEFAULT 0,  -- manfiy: to'lovdan oldin, musbat: kechikishdan keyin
    text_uz     VARCHAR(500) NOT NULL,
    text_ru     VARCHAR(500) NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_templates_event UNIQUE (tenant_id, event_key),
    CONSTRAINT ck_templates_channel CHECK (channel IN ('SMS','TELEGRAM','BOTH'))
);

CREATE TABLE notification_log (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 1,
    event_key           VARCHAR(30)  NOT NULL,
    channel             VARCHAR(10)  NOT NULL,
    client_id           BIGINT       REFERENCES clients (id),
    contract_id         BIGINT       REFERENCES contracts (id),
    schedule_item_id    BIGINT       REFERENCES schedule_items (id),
    recipient           VARCHAR(50)  NOT NULL,
    message             VARCHAR(1000) NOT NULL,
    status              VARCHAR(10)  NOT NULL,
    provider_message_id VARCHAR(100),
    error               VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_status CHECK (status IN ('SENT','FAILED','SKIPPED'))
);
-- Bitta to'lov uchun bitta eslatma turi faqat bir marta yuboriladi (job qayta ishlasa ham)
CREATE UNIQUE INDEX uq_notification_once
    ON notification_log (event_key, schedule_item_id, channel)
    WHERE schedule_item_id IS NOT NULL AND status = 'SENT';
CREATE INDEX ix_notification_created ON notification_log (tenant_id, created_at DESC);
