-- =====================================================================
-- V2: Majburiy boshlang'ich ma'lumotlar (har bir o'rnatishda kerak).
-- Kompaniya nomi / brend keyin Sozlamalar sahifasidan o'zgartiriladi.
-- =====================================================================

INSERT INTO tenant_settings (tenant_id, company_name, brand_name, contract_prefix)
VALUES (1, 'Kompaniya nomi', 'Nasiya CRM', 'NS');

INSERT INTO categories (tenant_id, code, name_uz, name_ru) VALUES
    (1, 'phone',  'Telefonlar',      'Телефоны'),
    (1, 'tv',     'Televizorlar',    'Телевизоры'),
    (1, 'fridge', 'Maishiy texnika', 'Бытовая техника'),
    (1, 'ac',     'Konditsionerlar', 'Кондиционеры'),
    (1, 'furn',   'Mebel',           'Мебель'),
    (1, 'laptop', 'Noutbuklar',      'Ноутбуки');

-- O'zgaruvchilar: {ism} {summa} {sana} {shartnoma} {kun}
INSERT INTO notification_templates (tenant_id, event_key, channel, enabled, offset_days, text_uz, text_ru) VALUES
    (1, 'CONTRACT', 'SMS', TRUE, 0,
     'Hurmatli {ism}! {shartnoma} shartnomangiz rasmiylashtirildi. Oylik to''lov: {summa}.',
     'Уважаемый(ая) {ism}! Договор {shartnoma} оформлен. Ежемесячный платёж: {summa}.'),
    (1, 'BEFORE_3', 'SMS', TRUE, -3,
     'Hurmatli {ism}! {sana} sanasida {summa} miqdorida to''lovingiz bor. Iltimos, o''z vaqtida to''lang.',
     'Уважаемый(ая) {ism}! {sana} у вас платёж {summa}. Просим оплатить вовремя.'),
    (1, 'PAYDAY', 'BOTH', TRUE, 0,
     'Hurmatli {ism}! Bugun {summa} miqdorida to''lov kuni. {shartnoma}.',
     'Уважаемый(ая) {ism}! Сегодня день платежа {summa}. {shartnoma}.'),
    (1, 'LATE_1', 'SMS', TRUE, 1,
     'Hurmatli {ism}! To''lovingiz {kun} kun kechikdi. {summa} ni to''lashingizni so''raymiz.',
     'Уважаемый(ая) {ism}! Платёж просрочен на {kun} дн. Просим оплатить {summa}.'),
    (1, 'LATE_7', 'BOTH', TRUE, 7,
     'Hurmatli {ism}! {kun} kun kechikish. {summa} qarzingizni zudlik bilan to''lang.',
     'Уважаемый(ая) {ism}! Просрочка {kun} дн. Срочно оплатите {summa}.'),
    (1, 'LATE_30', 'BOTH', FALSE, 30,
     'Hurmatli {ism}! {kun} kun kechikish. Masala undirish bo''limiga o''tkazildi. {summa}.',
     'Уважаемый(ая) {ism}! Просрочка {kun} дн. Дело передано в отдел взыскания. {summa}.');
