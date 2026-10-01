-- =====================================================================
-- demo_seed.sql qo'shgan namoyish ma'lumotlarini to'liq o'chiradi:
-- demo shartnomalar (jadval, to'lovlar, undirish harakatlari, SMS jurnali bilan),
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
DELETE FROM payments WHERE contract_id IN (SELECT id FROM demo_contracts);   -- taqsimotlar cascade
DELETE FROM contracts WHERE id IN (SELECT id FROM demo_contracts);           -- jadval cascade

-- keyin haqiqiy shartnoma ochilgan mijoz/mahsulot qoldiriladi
DELETE FROM clients c
 WHERE c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients')
   AND NOT EXISTS (SELECT 1 FROM contracts x WHERE x.client_id = c.id);
DELETE FROM products p
 WHERE p.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'products')
   AND NOT EXISTS (SELECT 1 FROM contracts x WHERE x.product_id = p.id);

DROP TABLE demo_seed_rows;
COMMIT;

SELECT 'Demo ma''lumotlar o''chirildi' AS natija;
