-- =====================================================================
-- V9: Ombor tahlili va ta'minot: yetkazib beruvchilar va xarid buyurtmalari.
-- =====================================================================
CREATE TABLE suppliers (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT       NOT NULL DEFAULT 1,
    name           VARCHAR(150) NOT NULL,
    phone          VARCHAR(30),
    contact_person VARCHAR(150),
    lead_time_days INTEGER      NOT NULL DEFAULT 7,
    note           VARCHAR(500),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_suppliers_lead CHECK (lead_time_days BETWEEN 0 AND 180)
);

ALTER TABLE products ADD COLUMN supplier_id BIGINT REFERENCES suppliers (id);

CREATE SEQUENCE purchase_order_no_seq START 1;

CREATE TABLE purchase_orders (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT       NOT NULL DEFAULT 1,
    order_no      VARCHAR(30)  NOT NULL,
    supplier_id   BIGINT       NOT NULL REFERENCES suppliers (id),
    status        VARCHAR(12)  NOT NULL DEFAULT 'DRAFT',
    eta           DATE,
    notes         VARCHAR(500),
    created_by    BIGINT       REFERENCES users (id),
    approved_by   BIGINT       REFERENCES users (id),
    approved_at   TIMESTAMPTZ,
    received_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_po_no UNIQUE (tenant_id, order_no),
    CONSTRAINT ck_po_status CHECK (status IN ('DRAFT','APPROVED','RECEIVED','CANCELLED'))
);

CREATE TABLE purchase_order_items (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL DEFAULT 1,
    order_id    BIGINT        NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
    product_id  BIGINT        NOT NULL REFERENCES products (id),
    quantity    INTEGER       NOT NULL,
    unit_cost   NUMERIC(18,2) NOT NULL,
    CONSTRAINT ck_poi CHECK (quantity >= 1 AND unit_cost >= 0)
);
CREATE INDEX ix_poi_order ON purchase_order_items (order_id);
