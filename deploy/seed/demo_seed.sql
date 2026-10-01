-- =====================================================================
-- Namoyish (demo) ma'lumotlari: 40 ta shartnoma, oxirgi 6 oy, jami ~100 mln so'm.
--
-- Flyway migratsiyasi EMAS — qo'lda (yoki "Demo ma'lumotlar" workflow'i orqali)
-- bir marta ishga tushiriladi:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < deploy/seed/demo_seed.sql
--
-- Sanalar ishga tushirilgan kunga nisbatan hisoblanadi (bugun - N oy - M kun).
-- Hisob-kitob MurabahaCalculator bilan bir xil. Qo'shilgan har bir mijoz, mahsulot
-- va shartnoma demo_seed_rows jadvalida qayd etiladi — deploy/seed/demo_remove.sql
-- ularni (to'lovlari bilan birga) to'liq o'chiradi.
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

DO $$
BEGIN
    IF to_regclass('public.demo_seed_rows') IS NOT NULL THEN
        RAISE EXCEPTION 'Demo ma''lumotlar allaqachon yuklangan. Qayta yuklash uchun avval demo_remove.sql ni ishga tushiring.';
    END IF;
END $$;

CREATE TABLE demo_seed_rows (
    tbl VARCHAR(30) NOT NULL,
    id  BIGINT      NOT NULL,
    PRIMARY KEY (tbl, id)
);

-- ---------------------------------------------------------------- mahsulotlar
CREATE TEMP TABLE seed_products (sku, cat, name, price, markup, tmin, tmax, stock) AS VALUES
    -- sku, kategoriya, nomi, narx, ustama%, min, max muddat, ombor
        ('DEMO-P01', 'phone', 'Samsung Galaxy A16 4/128', 2300000, 24, 3, 12, 25),
        ('DEMO-P02', 'phone', 'Redmi Note 14 8/256', 2900000, 24, 3, 12, 18),
        ('DEMO-P03', 'phone', 'Samsung Galaxy A55 8/256', 4700000, 22, 6, 12, 9),
        ('DEMO-P04', 'phone', 'iPhone 13 128GB', 7600000, 20, 6, 12, 4),
        ('DEMO-P05', 'tv', 'Artel 43" Smart TV', 3400000, 22, 3, 12, 11),
        ('DEMO-P06', 'tv', 'Samsung 55" Crystal UHD', 6900000, 20, 6, 12, 5),
        ('DEMO-P07', 'fridge', 'Artel muzlatgich HD 455', 5600000, 20, 6, 12, 7),
        ('DEMO-P08', 'fridge', 'Samsung kir yuvish mashinasi 7kg', 4900000, 20, 6, 12, 6),
        ('DEMO-P09', 'fridge', 'Artel gaz plita 4 konforka', 2700000, 22, 3, 9, 10),
        ('DEMO-P10', 'fridge', 'Changhong mikroto''lqinli pech', 1150000, 25, 3, 6, 20),
        ('DEMO-P11', 'fridge', 'Artel changyutgich 2000W', 1400000, 25, 3, 6, 15),
        ('DEMO-P12', 'ac', 'Artel konditsioner 12 inverter', 4800000, 22, 6, 12, 8),
        ('DEMO-P13', 'furn', 'Yumshoq mebel to''plami', 5400000, 24, 6, 12, 3),
        ('DEMO-P14', 'laptop', 'Lenovo IdeaPad 3 i5', 5800000, 21, 6, 12, 5),
        ('DEMO-P15', 'phone', 'Samsung Galaxy A06 4/64', 1650000, 25, 3, 9, 30),
        ('DEMO-P16', 'fridge', 'Artel multivarka 5L', 950000, 25, 3, 6, 22);

WITH ins AS (
    INSERT INTO products (tenant_id, category_id, name, sku, price, markup_pct, term_min, term_max, stock, active)
    SELECT 1, c.id, p.name, p.sku, p.price, p.markup, p.tmin, p.tmax, p.stock, TRUE
    FROM seed_products p
    JOIN categories c ON c.code = p.cat AND c.tenant_id = 1
    RETURNING id
)
INSERT INTO demo_seed_rows SELECT 'products', id FROM ins;

-- ---------------------------------------------------------------- mijozlar
CREATE TEMP TABLE seed_clients (key, full_name, pinfl, passport, passport_expiry, birth_date, phone, extra_phone,
                                region, district, address, workplace, income, family) AS VALUES
        ('c01', 'Akmal Karimov', '31403870261074', 'AE6230501', '2035-04-14', '1987-03-14', '+998985835253', '+998934912869', 'Toshkent sh.', 'Chilonzor', 'Bunyodkor ko''chasi, 34-uy', '"Artel" zavodi · usta', 8500000, 'Uylangan'),
        ('c02', 'Dilnoza Yusupova', '42208930269320', 'AA1777154', '2034-11-09', '1993-08-22', '+998339969465', NULL, 'Toshkent sh.', 'Yunusobod', 'Ipak yo''li ko''chasi, 19-uy', '"Korzinka" · sotuvchi', 5200000, 'Turmushda'),
        ('c03', 'Sardor To''xtayev', '30211840187893', 'AA7925545', '2030-11-21', '1984-11-02', '+998995635625', '+998986379931', 'Samarqand', 'Urgut', 'Amir Temur ko''chasi, 116-uy', 'Tadbirkor', 14000000, 'Uylangan'),
        ('c04', 'Gulnora Rashidova', '43005790264311', 'AA4839792', '2031-10-20', '1979-05-30', '+998943049259', '+998935880899', 'Toshkent sh.', 'Mirzo Ulug''bek', 'Fayzli ko''chasi, 4-uy', 'Maktab · o''qituvchi', 4800000, 'Beva'),
        ('c05', 'Jasur Abdullayev', '31701950301435', 'AA5793017', '2032-01-11', '1995-01-17', '+998956396829', NULL, 'Farg''ona', 'Marg''ilon', 'Bobur ko''chasi, 111-uy', 'Bozor · savdo', 4200000, 'Uylangan'),
        ('c06', 'Madina Saidova', '40909980269909', 'AA5922607', '2030-08-10', '1998-09-09', '+998935195098', '+998936558412', 'Toshkent sh.', 'Yashnobod', 'Navro''z ko''chasi, 47-uy', 'IT kompaniya · dizayner', 11500000, 'Turmushda emas'),
        ('c07', 'Bekzod Rahimov', '30512900271457', 'AB7126885', '2032-05-19', '1990-12-05', '+998918370680', NULL, 'Toshkent vil.', 'Chirchiq', 'Bobur ko''chasi, 118-uy', 'Kimyo zavodi · operator', 6900000, 'Uylangan'),
        ('c08', 'Nilufar Ahmedova', '41104880263121', 'AA2043697', '2027-12-06', '1988-04-11', '+998883510551', NULL, 'Toshkent sh.', 'Sergeli', 'Fayzli ko''chasi, 75-uy', 'Klinika · hamshira', 5100000, 'Turmushda'),
        ('c09', 'Otabek Nurmatov', '32807920033555', 'AA3051404', '2035-05-25', '1992-07-28', '+998984359829', NULL, 'Andijon', 'Asaka', 'Amir Temur ko''chasi, 57-uy', '"UzAuto" · yig''uvchi', 7300000, 'Uylangan'),
        ('c10', 'Shahnoza Karimova', '41902960265207', 'AA4675242', '2033-08-08', '1996-02-19', '+998778177912', NULL, 'Toshkent sh.', 'Olmazor', 'Fayzli ko''chasi, 25-uy', 'Bank · operator', 6400000, 'Turmushda'),
        ('c11', 'Shohruh Ismoilov', '30310990261320', 'AC5251134', '2030-09-07', '1999-10-03', '+998947998359', NULL, 'Toshkent sh.', 'Uchtepa', 'Bog''ishamol ko''chasi, 42-uy', '"Uzum Market" · kuryer', 5600000, 'Turmushda emas'),
        ('c12', 'Feruza Normatova', '42506850181525', 'AE2960518', '2033-11-21', '1985-06-25', '+998909292776', '+998984538672', 'Samarqand', 'Samarqand sh.', 'Bog''ishamol ko''chasi, 44-uy', 'Tikuvchilik sexi', 3900000, 'Turmushda'),
        ('c13', 'Doniyor Xolmatov', '30803830144037', 'AC8046248', '2035-04-21', '1983-03-08', '+998775501311', '+998989328049', 'Namangan', 'Chust', 'Mustaqillik ko''chasi, 110-uy', 'Fermer', 9800000, 'Uylangan'),
        ('c14', 'Zarina Aliyeva', '63001010263863', 'AA7623641', '2029-05-22', '2001-01-30', '+998903806251', NULL, 'Toshkent sh.', 'Yakkasaroy', 'Fayzli ko''chasi, 73-uy', 'Kafe · ofitsiant', 4100000, 'Turmushda emas'),
        ('c15', 'Ulug''bek Qodirov', '31608860065836', 'AD4666517', '2027-04-06', '1986-08-16', '+998905317432', '+998984730168', 'Buxoro', 'G''ijduvon', 'Navro''z ko''chasi, 113-uy', 'Qurilish · prorab', 10200000, 'Uylangan'),
        ('c16', 'Malika Tojiyeva', '42111910273062', 'AE6547270', '2035-08-17', '1991-11-21', '+998991442808', '+998882894424', 'Toshkent vil.', 'Zangiota', 'Fayzli ko''chasi, 72-uy', 'Do''kon · sotuvchi', 4600000, 'Turmushda'),
        ('c17', 'Farrux Ergashev', '30705940269854', 'AE3328035', '2027-06-03', '1994-05-07', '+998331177020', '+998972253839', 'Toshkent sh.', 'Chilonzor', 'Mustaqillik ko''chasi, 70-uy', 'Taksi haydovchisi', 6700000, 'Uylangan'),
        ('c18', 'Mohira Usmonova', '41302890305646', 'AB6220555', '2033-04-25', '1989-02-13', '+998997711858', '+998917138488', 'Farg''ona', 'Qo''qon', 'Bobur ko''chasi, 54-uy', 'Maktab · o''qituvchi', 4500000, 'Turmushda'),
        ('c19', 'Sherzod Mirzayev', '32909810109787', 'AA4285862', '2031-08-14', '1981-09-29', '+998913808119', '+998933958677', 'Qashqadaryo', 'Qarshi', 'Bog''ishamol ko''chasi, 42-uy', 'Avtoservis · usta', 7800000, 'Uylangan'),
        ('c20', 'Nodira Jo''rayeva', '40112970266065', 'AC1081857', '2029-01-21', '1997-12-01', '+998953029252', NULL, 'Toshkent sh.', 'Mirzo Ulug''bek', 'Fayzli ko''chasi, 101-uy', 'Dorixona · farmatsevt', 5300000, 'Turmushda emas'),
        ('c21', 'Aziz Sobirov', '31804930268329', 'AD9830008', '2028-09-08', '1993-04-18', '+998985923290', '+998931028395', 'Toshkent sh.', 'Yunusobod', 'Navro''z ko''chasi, 79-uy', 'IT kompaniya · dasturchi', 16500000, 'Uylangan'),
        ('c22', 'Sevara Raximova', '40407870034208', 'AD6127962', '2035-08-20', '1987-07-04', '+998888416628', '+998957076364', 'Andijon', 'Andijon sh.', 'Ipak yo''li ko''chasi, 18-uy', 'Davlat xizmati', 5900000, 'Turmushda'),
        ('c23', 'Rustam Hamidov', '32601780275431', 'AE3414883', '2029-05-12', '1978-01-26', '+998946879421', NULL, 'Toshkent vil.', 'Qibray', 'Mustaqillik ko''chasi, 10-uy', 'Tadbirkor', 12500000, 'Uylangan'),
        ('c24', 'Kamola Xasanova', '41410950265122', 'AC7239339', '2032-03-10', '1995-10-14', '+998901337221', NULL, 'Toshkent sh.', 'Sergeli', 'Mustaqillik ko''chasi, 103-uy', 'Sartaroshxona', 4700000, 'Turmushda'),
        ('c25', 'Anvar Yo''ldoshev', '30906900184671', 'AB4060189', '2034-11-19', '1990-06-09', '+998912959987', '+998994727428', 'Samarqand', 'Kattaqo''rg''on', 'Bunyodkor ko''chasi, 110-uy', 'Bozor · savdo', 6100000, 'Uylangan'),
        ('c26', 'Dildora Sharipova', '42703920268046', 'AE4983475', '2034-12-08', '1992-03-27', '+998957167593', '+998979887910', 'Toshkent sh.', 'Olmazor', 'Fayzli ko''chasi, 116-uy', 'Oshxona · oshpaz', 4400000, 'Ajrashgan'),
        ('c27', 'Javohir Tursunov', '51208000269226', 'AE7721959', '2032-05-15', '2000-08-12', '+998981221179', '+998338692941', 'Toshkent sh.', 'Yashnobod', 'Ipak yo''li ko''chasi, 52-uy', 'Call-markaz · operator', 4900000, 'Turmushda emas'),
        ('c28', 'Munisa Azimova', '40505980144979', 'AA3618127', '2035-02-15', '1998-05-05', '+998776828472', NULL, 'Namangan', 'Namangan sh.', 'Bunyodkor ko''chasi, 117-uy', 'Klinika · laborant', 4300000, 'Turmushda'),
        ('c29', 'Islom Bakirov', '31912850261934', 'AD4727252', '2034-10-25', '1985-12-19', '+998913495225', NULL, 'Toshkent sh.', 'Uchtepa', 'Mustaqillik ko''chasi, 39-uy', 'Qurilish · elektrik', 7600000, 'Uylangan'),
        ('c30', 'Mansur Olimov', '30202820302350', 'AB7675776', '2035-12-18', '1982-02-02', '+998951310836', NULL, 'Farg''ona', 'Farg''ona sh.', 'Bog''ishamol ko''chasi, 97-uy', 'Tadbirkor', 11000000, 'Uylangan'),
        ('c31', 'Bobur Sultonov', '30811960261993', 'AA6310297', '2028-02-09', '1996-11-08', '+998905849563', NULL, 'Toshkent sh.', 'Chilonzor', 'Bog''ishamol ko''chasi, 94-uy', 'Logistika · haydovchi', 6800000, 'Uylangan'),
        ('c32', 'Dilshod Qurbonov', '31509890062526', 'AB2364117', '2035-06-23', '1989-09-15', '+998903613578', NULL, 'Buxoro', 'Buxoro sh.', 'Bog''ishamol ko''chasi, 28-uy', 'Mehmonxona · administrator', 5500000, 'Uylangan');

WITH ins AS (
    INSERT INTO clients (tenant_id, full_name, pinfl, passport_series, passport_expiry, birth_date, phone, extra_phone,
                         region, district, address, workplace, monthly_income, family_status)
    SELECT 1, full_name, pinfl, passport, passport_expiry::date, birth_date::date, phone, extra_phone,
           region, district, address, workplace, income, family
    FROM seed_clients
    RETURNING id
)
INSERT INTO demo_seed_rows SELECT 'clients', id FROM ins;

-- ---------------------------------------------------------------- shartnomalar
-- m, d     — shartnoma bugundan m oy + d kun oldin tuzilgan
-- profile  — GOOD vaqtida to'laydi, EARLY keyingi oyni oldindan to'lagan,
--            PARTIAL keyingi oyni qisman to'lagan, LATE1/LATE2 1-2 oy kechikkan,
--            LATEP oxirgi oyni qisman to'lab kechikkan, PAYOFF muddatidan oldin yopgan,
--            NEW bugun tuzilgan
-- delay    — to'lov muddatdan necha kun keyin (manfiy: oldin) qilinadi
CREATE TEMP TABLE seed_contracts (client, sku, m, d, term, down, profile, delay, risk, score,
                                  guarantor_name, guarantor_phone) AS VALUES
        ('c13', 'DEMO-P05', 5, 12, 9, 0, 'PAYOFF', 1, 'A', 80, NULL, NULL),
        ('c17', 'DEMO-P11', 5, 9, 3, 0, 'LATE1', -3, 'C', 59, 'Ergasheva Laylo', '+998999516004'),
        ('c03', 'DEMO-P02', 5, 7, 9, 0, 'GOOD', 2, 'B', 75, NULL, NULL),
        ('c31', 'DEMO-P01', 5, 3, 9, 600000, 'LATEP', 2, 'C', 55, 'Yusupova Mohira', '+998983819154'),
        ('c08', 'DEMO-P16', 4, 16, 6, 200000, 'PAYOFF', 1, 'A', 84, NULL, NULL),
        ('c10', 'DEMO-P12', 4, 5, 12, 1000000, 'GOOD', -2, 'A', 90, 'Toshmatov Alisher', '+998959637527'),
        ('c05', 'DEMO-P01', 4, 3, 9, 300000, 'GOOD', 2, 'A', 88, NULL, NULL),
        ('c16', 'DEMO-P05', 4, 3, 6, 1000000, 'LATE2', 1, 'C', 63, 'Qodirova Zuhra', '+998336081143'),
        ('c28', 'DEMO-P15', 4, 1, 6, 0, 'EARLY', 2, 'A', 88, NULL, NULL),
        ('c22', 'DEMO-P16', 3, 18, 6, 0, 'PAYOFF', 1, 'A', 80, NULL, NULL),
        ('c16', 'DEMO-P16', 3, 13, 3, 0, 'EARLY', 5, 'A', 83, NULL, NULL),
        ('c11', 'DEMO-P15', 3, 10, 6, 0, 'GOOD', 2, 'B', 76, NULL, NULL),
        ('c20', 'DEMO-P01', 3, 2, 6, 600000, 'PARTIAL', 0, 'B', 76, NULL, NULL),
        ('c32', 'DEMO-P15', 3, 0, 4, 300000, 'GOOD', 5, 'A', 83, NULL, NULL),
        ('c14', 'DEMO-P11', 3, 0, 4, 0, 'GOOD', -1, 'A', 85, NULL, NULL),
        ('c03', 'DEMO-P02', 2, 25, 12, 600000, 'GOOD', 2, 'A', 88, NULL, NULL),
        ('c12', 'DEMO-P09', 2, 18, 6, 0, 'GOOD', 3, 'A', 93, NULL, NULL),
        ('c13', 'DEMO-P16', 2, 17, 3, 200000, 'LATE2', 5, 'C', 56, 'Qodirova Zuhra', '+998973856550'),
        ('c10', 'DEMO-P01', 2, 12, 6, 0, 'LATE1', 5, 'B', 70, NULL, NULL),
        ('c30', 'DEMO-P15', 2, 9, 6, 400000, 'LATEP', 0, 'C', 57, 'Qodirova Zuhra', '+998908098209'),
        ('c32', 'DEMO-P10', 2, 8, 3, 0, 'GOOD', 0, 'A', 94, NULL, NULL),
        ('c23', 'DEMO-P09', 2, 6, 6, 700000, 'GOOD', 3, 'B', 74, NULL, NULL),
        ('c29', 'DEMO-P03', 1, 27, 12, 700000, 'LATE1', -1, 'C', 57, 'Karimov Rustam', '+998951318759'),
        ('c21', 'DEMO-P10', 1, 27, 6, 0, 'LATE1', 5, 'C', 61, 'Ergasheva Laylo', '+998932439816'),
        ('c01', 'DEMO-P15', 1, 26, 9, 200000, 'GOOD', 1, 'A', 93, NULL, NULL),
        ('c07', 'DEMO-P10', 1, 21, 4, 0, 'GOOD', 0, 'A', 81, NULL, NULL),
        ('c09', 'DEMO-P15', 1, 9, 6, 500000, 'GOOD', 1, 'A', 84, NULL, NULL),
        ('c04', 'DEMO-P01', 1, 7, 12, 700000, 'GOOD', 0, 'B', 79, NULL, NULL),
        ('c27', 'DEMO-P10', 1, 4, 3, 0, 'PARTIAL', 3, 'B', 74, NULL, NULL),
        ('c06', 'DEMO-P02', 1, 1, 6, 600000, 'PARTIAL', 3, 'B', 78, NULL, NULL),
        ('c25', 'DEMO-P11', 0, 26, 3, 100000, 'GOOD', 5, 'A', 89, NULL, NULL),
        ('c18', 'DEMO-P16', 0, 24, 4, 0, 'GOOD', -2, 'A', 83, NULL, NULL),
        ('c24', 'DEMO-P08', 0, 21, 12, 700000, 'GOOD', 5, 'A', 93, 'Toshmatov Alisher', '+998911257973'),
        ('c15', 'DEMO-P15', 0, 12, 9, 500000, 'GOOD', 0, 'B', 79, NULL, NULL),
        ('c04', 'DEMO-P11', 0, 10, 3, 0, 'GOOD', -2, 'B', 77, NULL, NULL),
        ('c19', 'DEMO-P10', 0, 10, 4, 200000, 'GOOD', -1, 'A', 94, NULL, NULL),
        ('c24', 'DEMO-P01', 0, 9, 12, 0, 'GOOD', -3, 'A', 91, NULL, NULL),
        ('c26', 'DEMO-P16', 0, 9, 6, 100000, 'GOOD', 0, 'A', 89, NULL, NULL),
        ('c02', 'DEMO-P01', 0, 7, 12, 300000, 'GOOD', 0, 'A', 90, NULL, NULL),
        ('c05', 'DEMO-P02', 0, 0, 6, 0, 'NEW', -1, 'B', 78, NULL, NULL);

DO $$
DECLARE
    spec        RECORD;
    prod        RECORD;
    v_n         INT := 0;
    v_today     DATE := (now() AT TIME ZONE 'Asia/Tashkent')::date;
    v_step      NUMERIC;
    v_prefix    TEXT;
    v_client    BIGINT;
    v_cdate     DATE;
    v_created   TIMESTAMPTZ;
    v_first     DATE;
    v_fin NUMERIC; v_markup NUMERIC; v_total NUMERIC; v_monthly NUMERIC; v_last NUMERIC;
    v_amount NUMERIC; v_prin NUMERIC; v_mark NUMERIC; v_prin_left NUMERIC; v_mark_left NUMERIC;
    v_contract  BIGINT;
    v_sched     BIGINT;
    v_payment   BIGINT;
    v_payoff    BIGINT;
    v_payoff_ts TIMESTAMPTZ;
    v_n_due     INT;
    v_paid_upto INT;
    v_due       DATE;
    v_paid      NUMERIC;
    v_pay_date  DATE;
    v_pay_ts    TIMESTAMPTZ;
    v_status    TEXT;
    v_item      TEXT;
    v_method    TEXT;
    v_methods   TEXT[] := ARRAY['CASH','CASH','CASH','CASH','CLICK','CLICK','PAYME','PAYME','CARD','UZUM'];
    v_unpaid    INT;
    v_late      INT;
    v_overdue   NUMERIC;
    v_first_late DATE;
    i           INT;
BEGIN
    SELECT rounding_step, contract_prefix INTO v_step, v_prefix FROM tenant_settings WHERE tenant_id = 1;
    v_step   := coalesce(v_step, 1000);
    v_prefix := coalesce(v_prefix, 'NS');

    FOR spec IN SELECT * FROM seed_contracts ORDER BY m DESC, d DESC, client LOOP
        v_n := v_n + 1;
        SELECT c.id INTO v_client
          FROM clients c JOIN seed_clients s ON s.pinfl = c.pinfl
         WHERE s.key = spec.client AND c.tenant_id = 1;
        SELECT p.id, p.name, p.price, p.markup_pct INTO prod
          FROM products p JOIN demo_seed_rows r ON r.tbl = 'products' AND r.id = p.id
         WHERE p.sku = spec.sku;

        -- murobaha (MurabahaCalculator bilan bir xil)
        v_fin    := prod.price - spec.down;
        v_markup := round(v_fin * prod.markup_pct / 100);
        v_total  := v_fin + v_markup;
        v_monthly := round(v_total / spec.term / v_step) * v_step;
        v_last    := v_total - v_monthly * (spec.term - 1);
        IF v_last <= 0 THEN
            v_monthly := trunc(v_total / spec.term);
            v_last    := v_total - v_monthly * (spec.term - 1);
        END IF;

        v_cdate := (v_today - make_interval(months => spec.m, days => spec.d))::date;
        IF spec.profile = 'NEW' THEN
            v_created := now() - interval '40 minutes';
        ELSE
            v_created := (v_cdate + time '09:30' + make_interval(mins => (v_n * 37) % 480)) AT TIME ZONE 'Asia/Tashkent';
        END IF;
        v_first := (v_cdate + interval '1 month')::date;

        -- muddati o'tgan (bugundan oldingi) oylar soni
        SELECT count(*) INTO v_n_due
          FROM generate_series(0, spec.term - 1) g
         WHERE (v_first + make_interval(months => g))::date < v_today;

        v_paid_upto := CASE spec.profile
                           WHEN 'LATE1' THEN v_n_due - 1
                           WHEN 'LATEP' THEN v_n_due - 1
                           WHEN 'LATE2' THEN v_n_due - 2
                           WHEN 'PAYOFF' THEN greatest(v_n_due - 1, 1)
                           WHEN 'EARLY' THEN v_n_due + 1
                           ELSE v_n_due
                       END;

        INSERT INTO contracts (tenant_id, contract_no, client_id, product_id, product_name,
                               cost_price, down_payment, markup_pct, markup_amount, sale_price,
                               installment_total, term_months, monthly_payment, first_due_date,
                               status, score_total, risk_category, decision,
                               guarantor_name, guarantor_phone, signed_at, created_at, updated_at)
        VALUES (1, v_prefix || '-' || to_char(v_cdate, 'YYYY') || '-' || lpad(nextval('contract_no_seq')::text, 4, '0'),
                v_client, prod.id, prod.name,
                prod.price, spec.down, prod.markup_pct, v_markup, prod.price + v_markup,
                v_total, spec.term, v_monthly, v_first,
                'ACTIVE', spec.score, spec.risk, 'APPROVED',
                spec.guarantor_name, spec.guarantor_phone, v_created, v_created, v_created)
        RETURNING id INTO v_contract;
        INSERT INTO demo_seed_rows VALUES ('contracts', v_contract);

        v_prin_left := v_fin;
        v_mark_left := v_markup;
        v_payoff := NULL;

        FOR i IN 1..spec.term LOOP
            v_due    := (v_first + make_interval(months => i - 1))::date;
            v_amount := CASE WHEN i = spec.term THEN v_last ELSE v_monthly END;
            IF i = spec.term THEN
                v_prin := v_prin_left;
                v_mark := v_mark_left;
            ELSE
                v_prin := round(v_amount * v_fin / v_total);
                v_mark := v_amount - v_prin;
            END IF;
            v_prin_left := v_prin_left - v_prin;
            v_mark_left := v_mark_left - v_mark;

            -- shu oy uchun qancha va qachon to'langan
            v_paid := 0;
            v_pay_date := NULL;
            IF i <= v_paid_upto THEN
                v_paid := v_amount;
                IF spec.profile = 'EARLY' AND i = v_paid_upto THEN
                    v_pay_date := least(v_due - 6, v_today - 1);           -- oldindan
                ELSE
                    v_pay_date := least(v_due + spec.delay, v_today - 1);
                END IF;
            ELSIF spec.profile = 'PAYOFF' THEN
                -- qolgan barcha oylar bitta to'lov bilan yopilgan
                v_paid := v_amount;
                v_pay_date := least((v_first + make_interval(months => v_paid_upto - 1))::date + 12, v_today - 2);
            ELSIF spec.profile = 'PARTIAL' AND i = v_paid_upto + 1 THEN
                v_paid := round(v_amount * 0.5 / 1000) * 1000;
                v_pay_date := v_today - 2;
            ELSIF spec.profile = 'LATEP' AND i = v_paid_upto + 1 THEN
                v_paid := round(v_amount * 0.4 / 1000) * 1000;
                v_pay_date := least(v_due + 2, v_today - 1);
            END IF;
            IF v_pay_date IS NOT NULL THEN
                v_pay_date := greatest(v_pay_date, v_cdate + 1);
                v_pay_ts := (v_pay_date + time '10:15' + make_interval(mins => ((v_n * 53 + i * 29) % 450)))
                            AT TIME ZONE 'Asia/Tashkent';
            END IF;

            IF v_paid = v_amount THEN
                v_item := 'PAID';
            ELSIF v_paid > 0 THEN
                v_item := CASE WHEN v_due < v_today THEN 'LATE' ELSE 'PARTIAL' END;
            ELSE
                v_item := CASE WHEN v_due < v_today THEN 'LATE' ELSE 'PENDING' END;
            END IF;

            INSERT INTO schedule_items (tenant_id, contract_id, seq, due_date, amount,
                                        principal_part, markup_part, paid_amount, status, paid_at)
            VALUES (1, v_contract, i, v_due, v_amount, v_prin, v_mark, v_paid, v_item,
                    CASE WHEN v_item = 'PAID' THEN v_pay_ts END)
            RETURNING id INTO v_sched;

            IF v_paid > 0 THEN
                v_method := v_methods[1 + ((v_n * 3 + i * 7) % 10)];
                IF spec.profile = 'PAYOFF' AND i > v_paid_upto THEN
                    IF v_payoff IS NULL THEN
                        INSERT INTO payments (tenant_id, contract_id, amount, method, paid_at, note, created_at)
                        VALUES (1, v_contract, v_paid, 'CASH', v_pay_ts, 'Muddatidan oldin to''liq yopildi', v_pay_ts)
                        RETURNING id INTO v_payoff;
                        v_payoff_ts := v_pay_ts;
                    ELSE
                        UPDATE payments SET amount = amount + v_paid WHERE id = v_payoff;
                    END IF;
                    v_payment := v_payoff;
                ELSE
                    INSERT INTO payments (tenant_id, contract_id, amount, method, paid_at, created_at)
                    VALUES (1, v_contract, v_paid, v_method, v_pay_ts, v_pay_ts)
                    RETURNING id INTO v_payment;
                END IF;
                INSERT INTO payment_allocations (payment_id, schedule_item_id, amount)
                VALUES (v_payment, v_sched, v_paid);
            END IF;
        END LOOP;

        -- shartnoma holati jadvaldan kelib chiqadi (DailyJobs bilan bir xil qoida)
        SELECT count(*) FILTER (WHERE status <> 'PAID'),
               count(*) FILTER (WHERE status <> 'PAID' AND due_date < v_today),
               coalesce(sum(amount - paid_amount) FILTER (WHERE status <> 'PAID' AND due_date < v_today), 0),
               min(due_date) FILTER (WHERE status <> 'PAID' AND due_date < v_today)
          INTO v_unpaid, v_late, v_overdue, v_first_late
          FROM schedule_items WHERE contract_id = v_contract;

        IF v_unpaid = 0 THEN
            v_status := 'CLOSED';
            UPDATE contracts SET status = 'CLOSED',
                   closed_at = (SELECT max(paid_at) FROM schedule_items WHERE contract_id = v_contract),
                   updated_at = (SELECT max(paid_at) FROM schedule_items WHERE contract_id = v_contract)
             WHERE id = v_contract;
        ELSIF v_late > 0 THEN
            v_status := 'LATE';
            UPDATE contracts SET status = 'LATE' WHERE id = v_contract;
            -- undiruvchi harakatlari
            INSERT INTO collection_actions (tenant_id, contract_id, action_type, result, note, created_at)
            VALUES (1, v_contract, 'CALL', 'Javob bermadi', 'Ikki marta qo''ng''iroq qilindi, telefonni ko''tarmadi',
                    (least(v_first_late + 1, v_today - 1) + time '11:20') AT TIME ZONE 'Asia/Tashkent');
            INSERT INTO collection_actions (tenant_id, contract_id, action_type, result, note,
                                            promised_date, promised_amount, created_at)
            VALUES (1, v_contract, 'CALL', 'Va''da berdi', 'Maosh kechikkanini aytdi, hafta oxirigacha to''lashini aytdi',
                    v_today + 3, v_overdue,
                    (least(v_first_late + 3, v_today - 1) + time '15:40') AT TIME ZONE 'Asia/Tashkent');
            IF v_late >= 2 THEN
                INSERT INTO collection_actions (tenant_id, contract_id, action_type, result, note, created_at)
                VALUES (1, v_contract, 'VISIT', 'Uyda gaplashildi',
                        'Kafil bilan birga uchrashildi, qarzni bo''lib to''lash kelishildi',
                        (least(v_first_late + 33, v_today - 1) + time '17:05') AT TIME ZONE 'Asia/Tashkent');
            END IF;
        END IF;
    END LOOP;

    -- mijoz birinchi shartnomasidan biroz oldin ro'yxatga olingan
    UPDATE clients c
       SET created_at = x.first_at - interval '25 minutes',
           updated_at = x.first_at - interval '25 minutes'
      FROM (SELECT client_id, min(created_at) AS first_at FROM contracts GROUP BY client_id) x
     WHERE x.client_id = c.id
       AND c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients');
END $$;

-- ---------------------------------------------------------------- natija
SELECT count(*)                                  AS shartnomalar,
       count(*) FILTER (WHERE status = 'ACTIVE') AS faol,
       count(*) FILTER (WHERE status = 'LATE')   AS kechikkan,
       count(*) FILTER (WHERE status = 'CLOSED') AS yopilgan,
       to_char(sum(sale_price), 'FM999G999G999')                AS jami_savdo_narxi,
       to_char(sum(cost_price - down_payment), 'FM999G999G999') AS moliyalashtirilgan,
       to_char(sum(markup_amount), 'FM999G999G999')             AS ustama_foyda
  FROM contracts WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts');

COMMIT;

