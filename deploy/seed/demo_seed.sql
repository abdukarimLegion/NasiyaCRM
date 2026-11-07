-- =====================================================================
-- To'liq namoyish (demo) ma'lumotlari — barcha bo'limlar uchun.
--
-- Qamrov: mahsulotlar, mijozlar, arizalar (barcha holatlar), shartnomalar va to'lov
-- jadvallari, to'lovlar, kassa, undirish harakatlari, sud ishlari, hamkorlar orqali
-- savdolar, kredit tarixi, mijoz verifikatsiyasi (OTP/biometrik), ombor va ta'minot
-- (yetkazib beruvchilar, xarid buyurtmalari), hujjatlar jurnali, bildirishnomalar
-- jurnali, filiallar, xodimlar, KPI, shaxsiy rollar va (agar mavjud bo'lsa) audit log.
--
-- Flyway migratsiyasi EMAS — prod bazaga deploy qadamida bir marta yuklanadi:
--   deploy/seed-demo.sh   (docker compose'ni o'zi aniqlaydi)
-- yoki qo'lda:
--   docker compose -f docker-compose.prod.yml exec -T db \
--     sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < deploy/seed/demo_seed.sql
--
-- IDEMPOTENT: skriptni qayta ishga tushirish xavfsiz — mavjud demo yozuvlar
-- takrorlanmaydi (har bir bo'lim o'zining tabiiy kaliti yoki demo_seed_rows orqali
-- tekshiriladi). Sanalar ishga tushirilgan kunga nisbatan hisoblanadi.
-- Hisob-kitob MurabahaCalculator bilan bir xil. Qo'shilgan yozuvlar demo_seed_rows
-- jadvalida qayd etiladi — deploy/seed/demo_remove.sql ularni to'liq o'chiradi.
-- =====================================================================
\set ON_ERROR_STOP on
BEGIN;

CREATE TABLE IF NOT EXISTS demo_seed_rows (
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
    WHERE NOT EXISTS (SELECT 1 FROM products x WHERE x.tenant_id = 1 AND x.sku = p.sku)
    RETURNING id
)
INSERT INTO demo_seed_rows SELECT 'products', id FROM ins
ON CONFLICT DO NOTHING;

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
    ON CONFLICT (tenant_id, pinfl) DO NOTHING
    RETURNING id
)
INSERT INTO demo_seed_rows SELECT 'clients', id FROM ins
ON CONFLICT DO NOTHING;

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
    v_existing  INT;
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
    SELECT count(*) INTO v_existing FROM demo_seed_rows WHERE tbl = 'contracts';
    IF v_existing > 0 THEN
        RAISE NOTICE 'Demo shartnomalar allaqachon yuklangan (% ta) — qayta yaratilmadi', v_existing;
        RETURN;
    END IF;

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

-- ---------------------------------------------------------------- filiallar, xodimlar, KPI
-- Demo xodimlar tizimga kira olmaydi: parol xeshi mavjud bo'lmagan parolga mos keladi
-- (tasodifiy qiymat), shuning uchun login urinishi xato qaytaradi. Ular faqat KPI va filial
-- hisobotlarini to'ldirish uchun. demo_remove.sql ularni ham o'chiradi.
DO $$
DECLARE
    v_main BIGINT; v_chl BIGINT; v_sam BIGINT;
    v_off1 BIGINT; v_off2 BIGINT; v_off3 BIGINT; v_cash BIGINT; v_coll BIGINT;
    r RECORD;
    i INT := 0;
BEGIN
    SELECT id INTO v_main FROM branches WHERE tenant_id = 1 AND code = 'MAIN';

    INSERT INTO branches (tenant_id, code, name, address, phone, manager_name, monthly_target)
    SELECT 1, x.code, x.name, x.address, x.phone, x.manager, x.target
      FROM (VALUES ('DEMO-CHL', 'Chilonzor filiali (demo)', 'Toshkent sh., Chilonzor tumani', '+998 71 200-11-22', 'Rustam Aliyev', 30000000),
                   ('DEMO-SAM', 'Samarqand filiali (demo)', 'Samarqand sh., Registon ko''chasi', '+998 66 233-44-55', 'Nodira Karimova', 25000000))
           AS x(code, name, address, phone, manager, target)
     WHERE NOT EXISTS (SELECT 1 FROM branches b WHERE b.tenant_id = 1 AND b.code = x.code);
    INSERT INTO demo_seed_rows SELECT 'branches', id FROM branches WHERE code IN ('DEMO-CHL', 'DEMO-SAM')
    ON CONFLICT DO NOTHING;
    SELECT id INTO v_chl FROM branches WHERE tenant_id = 1 AND code = 'DEMO-CHL';
    SELECT id INTO v_sam FROM branches WHERE tenant_id = 1 AND code = 'DEMO-SAM';

    INSERT INTO users (tenant_id, username, password_hash, full_name, role, branch_id, active)
    SELECT 1, x.username, '$2a$10$ty5fpxLqTc0vYfiz9rLjN.HSpGbHm69rMIvk.7P5/7s3EMK2hSud.', x.full_name, x.role, x.branch, TRUE
      FROM (VALUES ('demo.operator1', 'Kamola Nazarova (demo)',  'CREDIT_OFFICER', v_main),
                   ('demo.operator2', 'Bekzod Rahimov (demo)',   'CREDIT_OFFICER', v_chl),
                   ('demo.operator3', 'Shahzod Ergashev (demo)', 'CREDIT_OFFICER', v_sam),
                   ('demo.kassir',    'Malika Tursunova (demo)', 'CASHIER',        v_main),
                   ('demo.undiruvchi','Jasur Mahmudov (demo)',   'COLLECTOR',      v_main))
           AS x(username, full_name, role, branch)
     WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.tenant_id = 1 AND u.username = x.username);
    INSERT INTO demo_seed_rows SELECT 'users', id FROM users WHERE username LIKE 'demo.%'
    ON CONFLICT DO NOTHING;
    SELECT id INTO v_off1 FROM users WHERE username = 'demo.operator1';
    SELECT id INTO v_off2 FROM users WHERE username = 'demo.operator2';
    SELECT id INTO v_off3 FROM users WHERE username = 'demo.operator3';
    SELECT id INTO v_cash FROM users WHERE username = 'demo.kassir';
    SELECT id INTO v_coll FROM users WHERE username = 'demo.undiruvchi';

    FOR r IN SELECT id FROM contracts WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts') ORDER BY id LOOP
        i := i + 1;
        UPDATE contracts SET branch_id = CASE i % 3 WHEN 0 THEN v_main WHEN 1 THEN v_chl ELSE v_sam END,
                             officer_id = CASE i % 3 WHEN 0 THEN v_off1 WHEN 1 THEN v_off2 ELSE v_off3 END
         WHERE id = r.id;
    END LOOP;
    UPDATE contracts SET collector_id = v_coll
     WHERE status = 'LATE' AND id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts');
    UPDATE payments p SET cashier_id = CASE WHEN c.branch_id = v_main THEN v_cash ELSE c.officer_id END
      FROM contracts c
     WHERE c.id = p.contract_id AND c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts');
    UPDATE collection_actions SET user_id = v_coll
     WHERE user_id IS NULL AND contract_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts');

    -- eng ko'p kechikkan demo shartnoma bo'yicha sud ishi (da'vo — faqat qolgan qarz, jarimasiz)
    INSERT INTO legal_cases (tenant_id, contract_id, status, claim_amount, court_name, filed_at, note, assigned_user_id)
    SELECT 1, x.id, 'FILED', x.rem, 'Tumanlararo fuqarolik ishlari sudi (demo)', current_date - 12,
           'Talabnoma javobsiz qoldi', v_coll
      FROM (SELECT c.id, sum(s.amount - s.paid_amount) AS rem,
                   min(s.due_date) FILTER (WHERE s.status <> 'PAID' AND s.due_date < current_date) AS first_late
              FROM contracts c JOIN schedule_items s ON s.contract_id = c.id
             WHERE c.status = 'LATE' AND c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
             GROUP BY c.id) x
     WHERE x.first_late IS NOT NULL AND x.first_late < current_date - 60
       AND NOT EXISTS (SELECT 1 FROM legal_cases l WHERE l.contract_id = x.id)
     ORDER BY x.first_late
     LIMIT 1;

    INSERT INTO kpi_targets (tenant_id, user_id, contracts_count, sales_volume, collected_amount, recovered_amount,
                             actions_count, bonus_amount) VALUES
        (1, v_off1, 3, 8000000, 4000000, 0, 0, 1500000),
        (1, v_off2, 3, 8000000, 4000000, 0, 0, 1500000),
        (1, v_off3, 3, 8000000, 4000000, 0, 0, 1200000),
        (1, v_cash, 0, 0, 12000000, 0, 0, 1000000),
        (1, v_coll, 0, 0, 0, 2000000, 25, 1800000)
    ON CONFLICT (tenant_id, user_id) DO NOTHING;
END $$;

-- ---------------------------------------------------------------- arizalar
-- 1) Shartnomaga aylangan arizalar: mavjud demo shartnomalardan hosil qilinadi.
INSERT INTO applications (
    tenant_id, application_no, client_id, branch_id, officer_id, request, items_summary,
    cost_price, down_payment, markup_pct, sale_price, installment_total, term_months, monthly_payment,
    score_total, risk_category, decision, score_details, status, decided_by, decided_at, contract_id,
    created_at, updated_at, verification_methods, verified_at)
SELECT 1,
       'DEMO-AR-C' || lpad(c.rn::text, 2, '0'),
       c.client_id, c.branch_id, c.officer_id,
       jsonb_build_object(
           'clientId', c.client_id,
           'terms', jsonb_build_object(
               'productId', c.product_id,
               'downPayment', c.down_payment,
               'markupPct', c.markup_pct,
               'termMonths', c.term_months,
               'firstDueDate', c.first_due_date),
           'scoring', jsonb_build_object(
               'points', jsonb_build_object('INCOME', 20, 'TENURE', 10, 'FAMILY', 7,
                                            'HISTORY', 15, 'DISCIPLINE', 12, 'GUARANTOR', 6),
               'incomeVerified', TRUE,
               'guarantorPresent', c.guarantor_name IS NOT NULL,
               'manualStopFactors', jsonb_build_array()),
           'guarantorName', c.guarantor_name,
           'guarantorPhone', c.guarantor_phone,
           'branchId', c.branch_id),
       c.product_name,
       c.cost_price, c.down_payment, c.markup_pct, c.sale_price, c.installment_total,
       c.term_months, c.monthly_payment, c.score_total, c.risk_category, c.decision,
       jsonb_build_object('points', jsonb_build_object('INCOME', 20, 'TENURE', 10, 'FAMILY', 7,
                                                       'HISTORY', 15, 'DISCIPLINE', 12, 'GUARANTOR', 6),
                          'incomeVerified', TRUE,
                          'guarantorPresent', c.guarantor_name IS NOT NULL,
                          'stopFactors', jsonb_build_array()),
       'CONTRACTED', c.officer_id, c.created_at - interval '1 day', c.id,
       c.created_at - interval '1 day', c.created_at - interval '1 day',
       CASE WHEN c.rn % 2 = 0 THEN 'BIOMETRIC' ELSE 'OTP' END,
       c.created_at - interval '1 day'
  FROM (SELECT ct.*, row_number() OVER (ORDER BY ct.created_at DESC, ct.id DESC) AS rn
          FROM contracts ct
         WHERE ct.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')) c
 WHERE c.rn <= 8
ON CONFLICT (tenant_id, application_no) DO NOTHING;

UPDATE contracts ct
   SET application_id = a.id
  FROM applications a
 WHERE a.contract_id = ct.id
   AND a.status = 'CONTRACTED'
   AND ct.application_id IS NULL
   AND ct.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts');

-- 2) Turli holatdagi arizalar: kutilayotgan, tasdiqlangan, rad etilgan va hamkorga topshirilgan.
CREATE TEMP TABLE seed_apps (key, client, sku, term, down, status, decision, risk, score, provider, reason, age) AS VALUES
        ('p01', 'c21', 'DEMO-P04', 12, 1500000, 'PENDING',  'REVIEW',   'B', 67, 'INTERNAL', NULL, 2),
        ('p02', 'c19', 'DEMO-P05',  9,       0, 'PENDING',  'REVIEW',   'C', 54, 'INTERNAL', NULL, 1),
        ('p03', 'c23', 'DEMO-P06', 12, 2000000, 'APPROVED', 'APPROVED', 'A', 84, 'INTERNAL', NULL, 4),
        ('p04', 'c30', 'DEMO-P14', 12, 1000000, 'APPROVED', 'APPROVED', 'A', 86, 'INTERNAL', NULL, 3),
        ('p05', 'c05', 'DEMO-P02',  6,       0, 'REJECTED', 'REJECTED', 'C', 48, 'INTERNAL',
         'Kredit tarixida oxirgi 6 oyda kechikishlar bor', 6),
        ('p06', 'c24', 'DEMO-P12', 12,  800000, 'PARTNER',  'APPROVED', 'B', 72, 'ALIF', NULL, 5),
        ('p07', 'c27', 'DEMO-P01', 12,       0, 'PARTNER',  'APPROVED', 'B', 70, 'UZUM', NULL, 8),
        ('p08', 'c15', 'DEMO-P07', 12, 1500000, 'PARTNER',  'APPROVED', 'A', 82, 'TBC',  NULL, 7),
        ('p09', 'c26', 'DEMO-P10',  6,       0, 'REJECTED', 'REJECTED', 'D', 32, 'INTERNAL',
         'PINFL bo''yicha sud qarzi aniqlandi', 9),
        ('p10', 'c01', 'DEMO-P03',  9,  900000, 'APPROVED', 'APPROVED', 'A', 90, 'INTERNAL', NULL, 0);

DO $$
DECLARE
    spec       RECORD;
    v_client   BIGINT;
    v_prod     RECORD;
    v_main     BIGINT; v_chl BIGINT; v_sam BIGINT;
    v_off      BIGINT; v_off1 BIGINT;
    v_branch   BIGINT;
    v_step     NUMERIC;
    v_fin NUMERIC; v_markup NUMERIC; v_sale NUMERIC; v_total NUMERIC;
    v_monthly NUMERIC; v_last NUMERIC;
    v_partner  BIGINT;
    v_pct      NUMERIC;
    v_commission NUMERIC;
    v_points   JSONB;
BEGIN
    SELECT rounding_step INTO v_step FROM tenant_settings WHERE tenant_id = 1;
    v_step := coalesce(v_step, 1000);
    SELECT id INTO v_main FROM branches WHERE tenant_id = 1 AND code = 'MAIN';
    SELECT id INTO v_chl  FROM branches WHERE tenant_id = 1 AND code = 'DEMO-CHL';
    SELECT id INTO v_sam  FROM branches WHERE tenant_id = 1 AND code = 'DEMO-SAM';
    SELECT id INTO v_off1 FROM users WHERE tenant_id = 1 AND username = 'demo.operator1';

    v_points := jsonb_build_object('INCOME', 20, 'TENURE', 10, 'FAMILY', 7,
                                   'HISTORY', 12, 'DISCIPLINE', 10, 'GUARANTOR', 5);

    FOR spec IN SELECT * FROM seed_apps ORDER BY key LOOP
        SELECT c.id INTO v_client
          FROM clients c JOIN seed_clients s ON s.pinfl = c.pinfl
         WHERE s.key = spec.client AND c.tenant_id = 1;
        SELECT p.id, p.name, p.price, p.markup_pct INTO v_prod
          FROM products p WHERE p.tenant_id = 1 AND p.sku = spec.sku;

        v_fin    := v_prod.price - spec.down;
        v_markup := round(v_fin * v_prod.markup_pct / 100);
        v_sale   := v_prod.price + v_markup;
        v_total  := v_fin + v_markup;
        v_monthly := round(v_total / spec.term / v_step) * v_step;
        v_last    := v_total - v_monthly * (spec.term - 1);
        IF v_last <= 0 THEN
            v_monthly := trunc(v_total / spec.term);
        END IF;

        v_branch := CASE (substr(spec.key, 2)::int % 3)
                        WHEN 0 THEN v_main WHEN 1 THEN v_chl ELSE v_sam END;
        v_off    := CASE (substr(spec.key, 2)::int % 3)
                        WHEN 0 THEN v_off1 WHEN 1 THEN
                            (SELECT id FROM users WHERE username = 'demo.operator2')
                        ELSE (SELECT id FROM users WHERE username = 'demo.operator3') END;

        v_partner := NULL;
        v_commission := NULL;
        IF spec.status = 'PARTNER' THEN
            SELECT id, commission_pct INTO v_partner, v_pct
              FROM partners WHERE tenant_id = 1 AND code = spec.provider;
            v_commission := round(v_sale * v_pct / 100);
        END IF;

        INSERT INTO applications (
            tenant_id, application_no, client_id, branch_id, officer_id, request, items_summary,
            cost_price, down_payment, markup_pct, sale_price, installment_total, term_months, monthly_payment,
            score_total, risk_category, decision, score_details, status, rejection_reason, decided_by, decided_at,
            contract_id, created_at, updated_at, verification_methods, verified_at,
            provider, partner_id, partner_tx_id, partner_handover_at, partner_notes, partner_commission)
        VALUES (1, 'DEMO-AR-' || upper(spec.key), v_client, v_branch, v_off,
            jsonb_build_object(
                'clientId', v_client,
                'terms', jsonb_build_object(
                    'productId', v_prod.id,
                    'downPayment', spec.down,
                    'markupPct', v_prod.markup_pct,
                    'termMonths', spec.term,
                    'firstDueDate', to_char(current_date + interval '1 month', 'YYYY-MM-DD')),
                'scoring', jsonb_build_object(
                    'points', v_points,
                    'incomeVerified', TRUE,
                    'guarantorPresent', FALSE,
                    'manualStopFactors', CASE WHEN spec.status = 'REJECTED'
                                              THEN jsonb_build_array('OVERDUE')
                                              ELSE jsonb_build_array() END),
                'guarantorName', NULL,
                'guarantorPhone', NULL,
                'branchId', v_branch),
            v_prod.name, v_prod.price, spec.down, v_prod.markup_pct, v_sale, v_total,
            spec.term, v_monthly, spec.score, spec.risk,
            CASE WHEN spec.status = 'PENDING' THEN 'REVIEW' ELSE spec.decision END,
            jsonb_build_object('points', v_points, 'incomeVerified', TRUE,
                               'guarantorPresent', FALSE,
                               'stopFactors', CASE WHEN spec.status = 'REJECTED'
                                                   THEN jsonb_build_array('OVERDUE')
                                                   ELSE jsonb_build_array() END),
            spec.status,
            CASE WHEN spec.status = 'REJECTED' THEN spec.reason END,
            CASE WHEN spec.status <> 'PENDING' THEN v_off1 END,
            CASE WHEN spec.status <> 'PENDING'
                 THEN ((current_date - spec.age) + time '11:05') AT TIME ZONE 'Asia/Tashkent' END,
            NULL,
            ((current_date - spec.age) + time '10:20') AT TIME ZONE 'Asia/Tashkent',
            ((current_date - spec.age) + time '10:20') AT TIME ZONE 'Asia/Tashkent',
            CASE WHEN spec.status <> 'PENDING' THEN 'OTP' END,
            CASE WHEN spec.status <> 'PENDING'
                 THEN ((current_date - spec.age) + time '10:10') AT TIME ZONE 'Asia/Tashkent' END,
            spec.provider, v_partner,
            CASE WHEN spec.status = 'PARTNER' THEN 'DEMO-TX-' || upper(spec.key) END,
            CASE WHEN spec.status = 'PARTNER'
                 THEN ((current_date - spec.age) + time '11:00') AT TIME ZONE 'Asia/Tashkent' END,
            CASE WHEN spec.status = 'PARTNER' THEN 'Hamkor orqali tasdiqlangan demo savdo' END,
            v_commission)
        ON CONFLICT (tenant_id, application_no) DO NOTHING;
    END LOOP;
END $$;

-- ---------------------------------------------------------------- kredit tarixi
INSERT INTO credit_reports (tenant_id, client_id, source, report_date, score, rating, active_loans,
                            total_debt, monthly_obligation, overdue_flag, max_overdue_days, note,
                            entered_by, created_at)
SELECT 1, c.id, x.source, current_date - x.days, x.score, x.rating, x.loans, x.debt, x.monthly,
       x.overdue, x.max_days, x.note,
       (SELECT id FROM users WHERE username = 'demo.operator1'),
       ((current_date - x.days) + time '09:40') AT TIME ZONE 'Asia/Tashkent'
  FROM (VALUES
        ('31403870261074', 'KATM', 10, 742, 'B', 1, 12500000,  950000, FALSE,  0, 'Faol kredit: 1 ta'),
        ('42208930269320', 'KATM', 20, 688, 'C', 2, 18400000, 2100000, FALSE,  0, 'Ikki faol kredit'),
        ('30211840187893', 'ICL',  15, 810, 'A', 0,        0,       0, FALSE,  0, 'Kechikish yo''q'),
        ('43005790264311', 'KATM', 25, 754, 'B', 1,  6200000,  620000, FALSE,  0, 'Faol kredit: 1 ta'),
        ('31701950301435', 'KATM', 30, 505, 'D', 3, 31200000, 4700000, TRUE,  96, '96 kun kechikish'),
        ('40909980269909', 'ICL',  18, 690, 'C', 1,  9800000, 1150000, FALSE,  0, 'Faol kredit: 1 ta'),
        ('41104880263121', 'KATM', 12, 780, 'B', 0,        0,       0, FALSE,  0, 'Kredit yo''q'),
        ('32807920033555', 'KATM', 22, 735, 'B', 1,  7400000,  810000, FALSE, 12, '12 kun kechikish (yopilgan)'),
        ('41902960265207', 'ICL',  16, 620, 'C', 2, 15600000, 1900000, FALSE,  0, 'Ikki faol kredit'),
        ('63001010263863', 'KATM', 28, 560, 'D', 2, 14300000, 2400000, TRUE,  45, '45 kun kechikish'),
        ('42703920268046', 'KATM', 14, 640, 'C', 1,  5100000,  680000, FALSE,  0, 'Faol kredit: 1 ta'),
        ('30803830144037', 'ICL',  19, 795, 'B', 0,        0,       0, FALSE,  0, 'Kechikish yo''q')
       ) AS x(pinfl, source, days, score, rating, loans, debt, monthly, overdue, max_days, note)
  JOIN clients c ON c.pinfl = x.pinfl AND c.tenant_id = 1
 WHERE NOT EXISTS (SELECT 1 FROM credit_reports r
                    WHERE r.client_id = c.id AND r.source = x.source);

-- ---------------------------------------------------------------- mijoz verifikatsiyasi
INSERT INTO client_verifications (tenant_id, client_id, method, status, phone, expires_at, attempts,
                                  match_pct, reference, verified_at, created_by, created_at)
SELECT 1, c.id,
       CASE WHEN c.id % 3 = 0 THEN 'BIOMETRIC' ELSE 'OTP' END,
       CASE WHEN c.id % 17 = 0 THEN 'FAILED'
            WHEN c.id % 11 = 0 THEN 'EXPIRED'
            ELSE 'VERIFIED' END,
       c.phone,
       now() + interval '5 minutes',
       CASE WHEN c.id % 17 = 0 THEN 3 ELSE 1 END,
       CASE WHEN c.id % 3 = 0 THEN 90 + (c.id % 9) END,
       'DEMO-VER-' || c.id,
       CASE WHEN c.id % 17 <> 0 AND c.id % 11 <> 0
            THEN now() - interval '10 days' END,
       (SELECT id FROM users WHERE username = 'demo.operator1'),
       now() - interval '10 days'
  FROM clients c
 WHERE c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients')
   AND NOT EXISTS (SELECT 1 FROM client_verifications v WHERE v.client_id = c.id);

-- ---------------------------------------------------------------- ombor va ta'minot
INSERT INTO suppliers (tenant_id, name, phone, contact_person, lead_time_days, note, active)
SELECT 1, x.name, x.phone, x.contact, x.lead, x.note, TRUE
  FROM (VALUES
        ('Artel Elektroniks (demo)',      '+998 71 200-30-40', 'Aziz Rahimov',     5,  'Maishiy texnika va televizorlar asosiy yetkazib beruvchisi'),
        ('Samsung Electronics UZ (demo)', '+998 71 120-10-10', 'Dilshod Karimov', 10, 'Telefon va televizorlar'),
        ('Mebel Trade (demo)',            '+998 90 123-45-67', 'Nargiza Yusupova', 14, 'Mebel va ofis jihozlari'),
        ('TechnoImport (demo)',           '+998 71 250-60-70', 'Sardor Aliyev',    7,  'Noutbuk va aksessuarlar')
       ) AS x(name, phone, contact, lead, note)
 WHERE NOT EXISTS (SELECT 1 FROM suppliers s WHERE s.tenant_id = 1 AND s.name = x.name);

INSERT INTO demo_seed_rows
SELECT 'suppliers', id FROM suppliers
 WHERE name IN ('Artel Elektroniks (demo)', 'Samsung Electronics UZ (demo)',
                'Mebel Trade (demo)', 'TechnoImport (demo)')
ON CONFLICT DO NOTHING;

UPDATE products p
   SET supplier_id = s.id
  FROM suppliers s
 WHERE p.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'products')
   AND p.supplier_id IS NULL
   AND s.name = CASE
       WHEN p.sku IN ('DEMO-P01', 'DEMO-P02', 'DEMO-P03', 'DEMO-P04', 'DEMO-P15')
           THEN 'Samsung Electronics UZ (demo)'
       WHEN p.sku = 'DEMO-P13' THEN 'Mebel Trade (demo)'
       WHEN p.sku = 'DEMO-P14' THEN 'TechnoImport (demo)'
       ELSE 'Artel Elektroniks (demo)'
   END;

INSERT INTO purchase_orders (tenant_id, order_no, supplier_id, status, eta, notes, created_by,
                             approved_by, approved_at, received_at, created_at, updated_at)
SELECT 1, x.order_no, s.id, x.status, current_date + x.eta_days, x.notes,
       (SELECT id FROM users WHERE username = 'demo.operator1'),
       CASE WHEN x.status IN ('APPROVED', 'RECEIVED')
            THEN (SELECT id FROM users WHERE username = 'demo.operator3') END,
       CASE WHEN x.status IN ('APPROVED', 'RECEIVED')
            THEN now() - make_interval(days => x.age) + interval '3 hours' END,
       CASE WHEN x.status = 'RECEIVED'
            THEN now() - make_interval(days => x.age) + interval '6 days' END,
       now() - make_interval(days => x.age),
       now() - make_interval(days => x.age)
  FROM (VALUES
        ('DEMO-PO-2026-01', 'Artel Elektroniks (demo)',      'RECEIVED',  -2, 18, 'Haftalik to''ldirish: TV va muzlatgich'),
        ('DEMO-PO-2026-02', 'Samsung Electronics UZ (demo)', 'APPROVED',   5,  6, 'Telefonlar zaxirasini to''ldirish'),
        ('DEMO-PO-2026-03', 'Mebel Trade (demo)',            'DRAFT',     12,  2, 'Yumshoq mebel yangi partiyasi'),
        ('DEMO-PO-2026-04', 'TechnoImport (demo)',           'CANCELLED',  0, 24, 'Yetkazib beruvchi narxni oshirdi — bekor qilindi')
       ) AS x(order_no, supplier, status, eta_days, age, notes)
  JOIN suppliers s ON s.tenant_id = 1 AND s.name = x.supplier
ON CONFLICT (tenant_id, order_no) DO NOTHING;

INSERT INTO demo_seed_rows
SELECT 'purchase_orders', id FROM purchase_orders WHERE order_no LIKE 'DEMO-PO-%'
ON CONFLICT DO NOTHING;

INSERT INTO purchase_order_items (tenant_id, order_id, product_id, quantity, unit_cost)
SELECT 1, o.id, p.id, x.qty, x.cost
  FROM (VALUES
        ('DEMO-PO-2026-01', 'DEMO-P05', 8, 2600000),
        ('DEMO-PO-2026-01', 'DEMO-P07', 6, 4300000),
        ('DEMO-PO-2026-01', 'DEMO-P09', 4, 2050000),
        ('DEMO-PO-2026-02', 'DEMO-P01', 15, 1750000),
        ('DEMO-PO-2026-02', 'DEMO-P02', 10, 2200000),
        ('DEMO-PO-2026-03', 'DEMO-P13', 3, 4100000),
        ('DEMO-PO-2026-04', 'DEMO-P14', 5, 4500000)
       ) AS x(order_no, sku, qty, cost)
  JOIN purchase_orders o ON o.tenant_id = 1 AND o.order_no = x.order_no
  JOIN products p ON p.tenant_id = 1 AND p.sku = x.sku
 WHERE NOT EXISTS (SELECT 1 FROM purchase_order_items i
                    WHERE i.order_id = o.id AND i.product_id = p.id);

-- ---------------------------------------------------------------- qo'shimcha sud ishlari
WITH late AS (
    SELECT c.id,
           sum(s.amount - s.paid_amount) AS rem,
           min(s.due_date) FILTER (WHERE s.status <> 'PAID' AND s.due_date < current_date) AS first_late
      FROM contracts c
      JOIN schedule_items s ON s.contract_id = c.id
     WHERE c.status = 'LATE'
       AND c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
     GROUP BY c.id
), ranked AS (
    SELECT l.*, row_number() OVER (ORDER BY l.first_late) AS rn
      FROM late l
     WHERE l.first_late IS NOT NULL AND l.first_late < current_date - 30
)
INSERT INTO legal_cases (tenant_id, contract_id, status, claim_amount, court_name, case_no,
                         filed_at, hearing_at, note, assigned_user_id, created_at, updated_at)
SELECT 1, r.id,
       CASE r.rn WHEN 1 THEN 'HEARING' WHEN 2 THEN 'PRETRIAL' ELSE 'FILED' END,
       r.rem, 'Tumanlararo fuqarolik ishlari sudi (demo)',
       'DEMO-2/' || to_char(current_date, 'YYYY') || '-' || lpad(r.rn::text, 3, '0'),
       current_date - (10 + r.rn * 8)::int,
       CASE WHEN r.rn = 1 THEN current_date + 7 END,
       'Demo sud ishi: talabnoma yuborilgan, qarz undirilmoqda',
       (SELECT id FROM users WHERE username = 'demo.undiruvchi'),
       now() - make_interval(days => (10 + r.rn * 8)::int),
       now()
  FROM ranked r
 WHERE r.rn <= 3
   AND NOT EXISTS (SELECT 1 FROM legal_cases l WHERE l.contract_id = r.id);

-- ---------------------------------------------------------------- hujjatlar jurnali
INSERT INTO document_log (tenant_id, contract_id, code, doc_no, lang, amount, user_id, created_at)
SELECT 1, c.id, 'CONTRACT', c.contract_no, x.lang, c.sale_price, c.officer_id, c.signed_at
  FROM (SELECT ct.*, row_number() OVER (ORDER BY ct.created_at DESC, ct.id DESC) AS rn
          FROM contracts ct
         WHERE ct.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')) c
 CROSS JOIN (VALUES ('uz'), ('ru')) AS x(lang)
 WHERE c.rn <= 10
   AND NOT EXISTS (SELECT 1 FROM document_log d
                    WHERE d.contract_id = c.id AND d.code = 'CONTRACT' AND d.lang = x.lang);

INSERT INTO document_log (tenant_id, contract_id, code, doc_no, lang, amount, user_id, created_at)
SELECT 1, l.contract_id, 'DEMAND',
       'TAL-' || to_char(coalesce(l.filed_at, current_date), 'YYYY') || '-' || lpad(l.id::text, 4, '0'),
       'uz', l.claim_amount, l.assigned_user_id,
       (coalesce(l.filed_at, current_date) - 15)::timestamp AT TIME ZONE 'Asia/Tashkent'
  FROM legal_cases l
 WHERE l.contract_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
   AND NOT EXISTS (SELECT 1 FROM document_log d
                    WHERE d.contract_id = l.contract_id AND d.code = 'DEMAND');

INSERT INTO document_log (tenant_id, contract_id, code, doc_no, lang, amount, user_id, created_at)
SELECT 1, l.contract_id, 'CLAIM',
       'DV-' || to_char(coalesce(l.filed_at, current_date), 'YYYY') || '-' || lpad(l.id::text, 4, '0'),
       'uz', l.claim_amount, l.assigned_user_id,
       (coalesce(l.filed_at, current_date) - 10)::timestamp AT TIME ZONE 'Asia/Tashkent'
  FROM legal_cases l
 WHERE l.contract_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
   AND NOT EXISTS (SELECT 1 FROM document_log d
                    WHERE d.contract_id = l.contract_id AND d.code = 'CLAIM');

-- ---------------------------------------------------------------- bildirishnomalar jurnali
WITH demo_c AS (
    SELECT c.*, cl.full_name, cl.phone,
           row_number() OVER (ORDER BY c.created_at DESC, c.id DESC) AS rn
      FROM contracts c
      JOIN clients cl ON cl.id = c.client_id
     WHERE c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
)
INSERT INTO notification_log (tenant_id, event_key, channel, client_id, contract_id, schedule_item_id,
                              recipient, message, status, provider_message_id, error, created_at)
SELECT 1, 'CONTRACT', 'SMS', d.client_id, d.id, NULL, d.phone,
       'Hurmatli ' || split_part(d.full_name, ' ', 1) || '! ' || d.contract_no ||
       ' shartnomangiz rasmiylashtirildi. Oylik to''lov: ' ||
       to_char(d.monthly_payment, 'FM999G999G999') || ' so''m.',
       CASE WHEN d.rn % 17 = 0 THEN 'FAILED' ELSE 'SENT' END,
       CASE WHEN d.rn % 17 = 0 THEN NULL ELSE 'demo-sms-' || d.id END,
       CASE WHEN d.rn % 17 = 0 THEN 'Operator xizmati javob bermadi' END,
       d.created_at + interval '2 minutes'
  FROM demo_c d
 WHERE NOT EXISTS (SELECT 1 FROM notification_log n
                    WHERE n.contract_id = d.id AND n.event_key = 'CONTRACT');

INSERT INTO notification_log (tenant_id, event_key, channel, client_id, contract_id, schedule_item_id,
                              recipient, message, status, provider_message_id, error, created_at)
SELECT 1, 'LATE_7', 'SMS', c.client_id, c.id, s.id, cl.phone,
       'Hurmatli ' || split_part(cl.full_name, ' ', 1) || '! ' || c.contract_no ||
       ' shartnomasi bo''yicha ' || to_char(s.amount - s.paid_amount, 'FM999G999G999') ||
       ' so''m to''lov muddati o''tgan. Iltimos, to''lovni amalga oshiring.',
       'SENT', 'demo-late-' || s.id, NULL,
       ((s.due_date + 7) + time '09:30') AT TIME ZONE 'Asia/Tashkent'
  FROM contracts c
  JOIN clients cl ON cl.id = c.client_id
  JOIN schedule_items s ON s.contract_id = c.id
 WHERE c.status = 'LATE' AND s.status = 'LATE'
   AND c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
   AND NOT EXISTS (SELECT 1 FROM notification_log n
                    WHERE n.schedule_item_id = s.id AND n.event_key = 'LATE_7');

-- ---------------------------------------------------------------- shaxsiy rollar
INSERT INTO custom_roles (tenant_id, name, description, color, base_role, permissions)
VALUES
    (1, 'Katta kredit inspektori (demo)', 'Limitdan yuqori arizalarni ko''rib chiqadi',
     '#2563eb', 'CREDIT_OFFICER',
     '["DASHBOARD_VIEW","CLIENTS_VIEW","CLIENTS_EDIT","CONTRACTS_VIEW","CONTRACTS_CREATE","CONTRACTS_APPROVE","PAYMENTS_VIEW","PRODUCTS_VIEW","COLLECTION_VIEW","COLLECTION_WORK","NOTIFICATIONS_VIEW","BRANCHES_VIEW","KPI_VIEW_ALL"]'::jsonb),
    (1, 'Filial nazoratchisi (demo)', 'Filial bo''yicha to''liq nazorat',
     '#0f766e', 'BRANCH_MANAGER',
     '["DASHBOARD_VIEW","CLIENTS_VIEW","CONTRACTS_VIEW","CONTRACTS_CREATE","CONTRACTS_APPROVE","CONTRACTS_CANCEL","PAYMENTS_VIEW","PAYMENTS_ACCEPT","PRODUCTS_VIEW","PRODUCTS_EDIT","COLLECTION_VIEW","COLLECTION_WORK","COLLECTION_ASSIGN","NOTIFICATIONS_VIEW","KPI_VIEW_ALL","FORECAST_VIEW","ANALYTICS_VIEW","BRANCHES_VIEW"]'::jsonb),
    (1, 'Undirish guruhi (demo)', 'Kechikkan to''lovlar bilan ishlash',
     '#b45309', 'COLLECTOR',
     '["DASHBOARD_VIEW","CLIENTS_VIEW","CONTRACTS_VIEW","PAYMENTS_VIEW","COLLECTION_VIEW","COLLECTION_WORK","COLLECTION_LEGAL","NOTIFICATIONS_VIEW","BRANCHES_VIEW"]'::jsonb)
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO demo_seed_rows
SELECT 'custom_roles', id FROM custom_roles
 WHERE name IN ('Katta kredit inspektori (demo)', 'Filial nazoratchisi (demo)', 'Undirish guruhi (demo)')
ON CONFLICT DO NOTHING;

UPDATE users u
   SET custom_role_id = r.id
  FROM custom_roles r
 WHERE u.tenant_id = 1
   AND u.username = 'demo.operator2'
   AND r.tenant_id = 1
   AND r.name = 'Katta kredit inspektori (demo)'
   AND u.custom_role_id IS NULL;

-- ---------------------------------------------------------------- audit log (P1 migratsiyasi bo'lsa)
DO $do$
BEGIN
    IF to_regclass('public.audit_log') IS NOT NULL THEN
        EXECUTE $sql$
            INSERT INTO audit_log (tenant_id, user_id, username, action, entity, entity_id, details, created_at)
            SELECT 1, c.officer_id, u.username, 'CONTRACT_CREATE', 'CONTRACT', c.id,
                   jsonb_build_object('contractNo', c.contract_no, 'seed', TRUE), c.created_at
              FROM contracts c
              JOIN users u ON u.id = c.officer_id
             WHERE c.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts')
               AND NOT EXISTS (SELECT 1 FROM audit_log a
                                WHERE a.entity = 'CONTRACT' AND a.entity_id = c.id)
        $sql$;
        EXECUTE $sql$
            INSERT INTO audit_log (tenant_id, user_id, username, action, entity, entity_id, details, created_at)
            SELECT 1, u.id, u.username, 'USER_CREATE', 'USER', u.id,
                   jsonb_build_object('role', u.role, 'seed', TRUE), now() - interval '30 days'
              FROM users u
             WHERE u.id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'users')
               AND NOT EXISTS (SELECT 1 FROM audit_log a
                                WHERE a.entity = 'USER' AND a.entity_id = u.id)
        $sql$;
    END IF;
END $do$;

-- ---------------------------------------------------------------- natija
SELECT count(*)                                  AS shartnomalar,
       count(*) FILTER (WHERE status = 'ACTIVE') AS faol,
       count(*) FILTER (WHERE status = 'LATE')   AS kechikkan,
       count(*) FILTER (WHERE status = 'CLOSED') AS yopilgan,
       to_char(sum(sale_price), 'FM999G999G999')                AS jami_savdo_narxi,
       to_char(sum(cost_price - down_payment), 'FM999G999G999') AS moliyalashtirilgan,
       to_char(sum(markup_amount), 'FM999G999G999')             AS ustama_foyda
  FROM contracts WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts');

SELECT (SELECT count(*) FROM clients  WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients'))  AS mijozlar,
       (SELECT count(*) FROM products WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'products')) AS mahsulotlar,
       (SELECT count(*) FROM applications
         WHERE client_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients'))                       AS arizalar,
       (SELECT count(*) FROM credit_reports
         WHERE client_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients'))                       AS kredit_tarixi,
       (SELECT count(*) FROM client_verifications
         WHERE client_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'clients'))                       AS verifikatsiya,
       (SELECT count(*) FROM suppliers
         WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'suppliers'))                            AS yetkazib_beruvchilar,
       (SELECT count(*) FROM purchase_orders
         WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'purchase_orders'))                      AS xarid_buyurtmalari,
       (SELECT count(*) FROM notification_log
         WHERE contract_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts'))                   AS bildirishnomalar,
       (SELECT count(*) FROM document_log
         WHERE contract_id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'contracts'))                   AS hujjatlar,
       (SELECT count(*) FROM custom_roles
         WHERE id IN (SELECT id FROM demo_seed_rows WHERE tbl = 'custom_roles'))                         AS shaxsiy_rollar;

COMMIT;

