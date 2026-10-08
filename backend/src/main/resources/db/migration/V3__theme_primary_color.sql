-- =====================================================================
-- V3: Yangi interfeys (slate + zumrad) asosiy rangi.
-- Brend rangi hali o'zgartirilmagan bo'lsa, eski standart (#1f8a5b) yangi
-- standart zumrad rangga (#059669) almashtiriladi. Qo'lda tanlangan rang qoladi.
-- =====================================================================

ALTER TABLE tenant_settings ALTER COLUMN primary_color SET DEFAULT '#059669';

UPDATE tenant_settings SET primary_color = '#059669' WHERE primary_color = '#1f8a5b';
