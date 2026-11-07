-- =====================================================================
-- V6: KPI — arizalar rejasi, oklad (asosiy maosh) va savdo bonusi foizi.
-- Jami to'lov = oklad + savdo bonusi (savdo summasi × foiz) + KPI bonusi.
-- =====================================================================
ALTER TABLE kpi_targets
    ADD COLUMN applications_count INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN base_salary        NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN sales_bonus_pct    NUMERIC(5,2)  NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_kpi_targets_comp CHECK (applications_count >= 0 AND base_salary >= 0
        AND sales_bonus_pct >= 0 AND sales_bonus_pct <= 100);
