-- =====================================================================
-- V4: Audit log.
-- Moliyaviy tizimda kim, qachon va qanday o'zgarish qilgani yozilib boradi.
-- Yozuvlar biznes tranzaksiyasi bilan birga commit qilinadi.
-- =====================================================================

CREATE TABLE audit_log (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT      NOT NULL DEFAULT 1,
    user_id    BIGINT      REFERENCES users (id),
    username   VARCHAR(50),
    action     VARCHAR(50) NOT NULL,
    entity     VARCHAR(50) NOT NULL,
    entity_id  BIGINT,
    details    JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX ix_audit_entity  ON audit_log (entity, entity_id);
CREATE INDEX ix_audit_user    ON audit_log (user_id, created_at DESC);
