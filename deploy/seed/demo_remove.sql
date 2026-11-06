-- =====================================================================
-- demo_seed.sql qo'shgan namoyish ma'lumotlarini to'liq o'chiradi:
-- demo shartnomalar (jadval, to'lovlar, undirish harakatlari, sud ishlari, SMS jurnali bilan),
-- demo filiallar va xodimlar (KPI rejalari bilan),
-- demo mijozlar va demo mahsulotlar. Haqiqiy ma'lumotlarga tegmaydi.
--   docker compose -f docker-compose.prod.yml exec -T db \
--     sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < deploy/seed/demo_remove.sql
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.demo_seed_rows') IS NULL THEN
        RAISE EXCEPTION 'Demo ma''lumotlar topilmadi (demo_seed_rows jadvali yo''q) — o''chiriladigan narsa yo''q.';
    END IF;
END $$;

CREATE TEMP TABLE demo_contracts AS SELECT id FROM demo_seed_rows WHERE tbl = 'contracts';

DELETE FROM notification_log
 WHERE contract_id IN (SELECT id FROM demo_contracts)
    OR client_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients');
DELETE FROM collection_actions WHERE contract_id IN (SELECT id FROM demo_contracts);
DELETE FROM legal_cases WHERE contract_id IN (SELECT id FROM demo_contracts);
DELETE FROM document_log WHERE contract_id IN (SELECT id FROM demo_contracts);
DELETE FROM payments WHERE contract_id IN (SELECT id FROM demo_contracts);   -- taqsimotlar cascade
DELETE FROM contracts WHERE id IN (SELECT id FROM demo_contracts);           -- jadval cascade

-- keyin haqiqiy shartnoma ochilgan mijoz/mahsulot qoldiriladi
DELETE FROM clients c
 WHERE c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients')
   AND NOT EXISTS (SELECT 1 FROM contracts x WHERE x.client_id = c.id);
DELETE FROM products p
 WHERE p.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'products')
   AND NOT EXISTS (SELECT 1 FROM contracts x WHERE x.product_id = p.id);

-- demo xodimlar va filiallar (haqiqiy yozuvlardagi havolalar bo'shatiladi)
CREATE TEMP TABLE demo_users AS SELECT id FROM demo_seed_rows WHERE tbl = 'users';
DELETE FROM kpi_targets WHERE user_id IN (SELECT id FROM demo_users);
UPDATE contracts SET officer_id = NULL WHERE officer_id IN (SELECT id FROM demo_users);
UPDATE contracts SET collector_id = NULL WHERE collector_id IN (SELECT id FROM demo_users);
UPDATE payments SET cashier_id = NULL WHERE cashier_id IN (SELECT id FROM demo_users);
UPDATE collection_actions SET user_id = NULL WHERE user_id IN (SELECT id FROM demo_users);
UPDATE legal_cases SET assigned_user_id = NULL WHERE assigned_user_id IN (SELECT id FROM demo_users);
UPDATE document_log SET user_id = NULL WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM users WHERE id IN (SELECT id FROM demo_users);
UPDATE users SET branch_id = NULL WHERE branch_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'branches');
UPDATE contracts SET branch_id = NULL WHERE branch_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'branches');
DELETE FROM branches WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'branches');

DROP TABLE demo_seed_rows;
COMMIT;

SELECT 'Demo ma''lumotlar o''chirildi' AS natija;
