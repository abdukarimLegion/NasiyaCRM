-- =====================================================================
-- DEMO ma'lumotlar. Faqat `dev` profilida yuklanadi
-- (spring.flyway.locations ga classpath:db/demo qo'shilganda).
-- Mijoz serveriga (prod) tushmaydi.
-- =====================================================================

INSERT INTO products (tenant_id, category_id, name, price, markup_pct, term_min, term_max, stock, active)
SELECT 1, c.id, p.name, p.price, p.markup, p.tmin, p.tmax, p.stock, p.active
FROM (VALUES
    ('phone',  'Samsung Galaxy A55',          4200000, 22, 3, 12, 34, TRUE),
    ('fridge', 'Artel muzlatgich INVERTER',   6800000, 18, 6, 18, 12, TRUE),
    ('phone',  'iPhone 15',                  11500000, 20, 6, 12,  8, TRUE),
    ('ac',     'Midea konditsioner 12',       5300000, 25, 3,  9, 21, TRUE),
    ('tv',     'LED TV Samsung 55"',          7900000, 19, 6, 15, 16, TRUE),
    ('furn',   'Oshxona to''plami',           9200000, 24, 6, 24,  5, TRUE),
    ('laptop', 'HP Pavilion 15',              8700000, 21, 6, 18,  0, FALSE),
    ('fridge', 'Roison kir yuvish mashinasi', 4600000, 20, 3, 12, 27, TRUE)
) AS p(cat, name, price, markup, tmin, tmax, stock, active)
JOIN categories c ON c.code = p.cat AND c.tenant_id = 1;

INSERT INTO clients (tenant_id, full_name, pinfl, passport_series, passport_expiry, phone, region, district, workplace, monthly_income, family_status) VALUES
    (1, 'Akmal Karimov',      '31708896540021', 'AB1234567', '2031-05-01', '+998901234567', 'Toshkent sh.', 'Chilonzor',    '"Artel" zavodi · usta',   9500000, 'Uylangan'),
    (1, 'Dilnoza Yusupova',   '52810901230044', 'AC7654321', '2029-11-12', '+998938842109', 'Toshkent sh.', 'Yunusobod',    '"Korzinka" · sotuvchi',   6200000, 'Turmushda'),
    (1, 'Sardor To''xtayev',  '30412887760018', 'AD1112223', '2030-02-20', '+998997015512', 'Samarqand',    'Urgut',        'Tadbirkor',              14000000, 'Uylangan'),
    (1, 'Gulnora Rashidova',  '61905923410067', 'AE3334445', '2028-07-07', '+998912203040', 'Toshkent sh.', 'Mirzo Ulug''bek', 'Maktab · o''qituvchi', 4800000, 'Beva'),
    (1, 'Jasur Abdullayev',   '30207889120033', 'AF5556667', '2025-01-10', '+998971456789', 'Farg''ona',    'Marg''ilon',   'Bozor · savdo',           3200000, 'Uylangan'),
    (1, 'Madina Saidova',     '52911934550071', 'AG7778889', '2032-09-09', '+998909090909', 'Toshkent sh.', 'Yashnobod',    'IT kompaniya · dizayner',11500000, 'Turmushda emas');
