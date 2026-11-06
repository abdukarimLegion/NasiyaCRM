-- =====================================================================
-- V4: Filiallar, bo'lim darajasidagi ruxsatlar, operator KPI,
--     undirish (mas'ul xodim, sud ishlari), hujjat shablonlari va prognoz sozlamalari.
-- =====================================================================

-- ---------------------------------------------------------------- filiallar
CREATE TABLE branches (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL DEFAULT 1,
    code           VARCHAR(20)   NOT NULL,
    name           VARCHAR(150)  NOT NULL,
    address        VARCHAR(300),
    phone          VARCHAR(30),
    manager_name   VARCHAR(150),
    monthly_target NUMERIC(18,2),               -- oylik savdo rejasi (moliyalashtirilgan summa)
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_branches_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_branches_target CHECK (monthly_target IS NULL OR monthly_target >= 0)
);

INSERT INTO branches (tenant_id, code, name) VALUES (1, 'MAIN', 'Bosh filial');

-- Foydalanuvchi filiali va shaxsiy ruxsatlari.
-- permissions NULL bo'lsa — rolning ruxsatlari ishlatiladi.
ALTER TABLE users ADD COLUMN branch_id   BIGINT REFERENCES branches (id);
ALTER TABLE users ADD COLUMN permissions JSONB;
UPDATE users SET branch_id = (SELECT id FROM branches WHERE tenant_id = 1 AND code = 'MAIN');

-- Shartnoma qaysi filialda rasmiylashtirilgan va undirish bo'yicha mas'ul xodim
ALTER TABLE contracts ADD COLUMN branch_id    BIGINT REFERENCES branches (id);
ALTER TABLE contracts ADD COLUMN collector_id BIGINT REFERENCES users (id);
UPDATE contracts c
   SET branch_id = COALESCE((SELECT u.branch_id FROM users u WHERE u.id = c.officer_id),
                            (SELECT id FROM branches WHERE tenant_id = 1 AND code = 'MAIN'));
CREATE INDEX ix_contracts_branch    ON contracts (tenant_id, branch_id);
CREATE INDEX ix_contracts_officer   ON contracts (officer_id);
CREATE INDEX ix_contracts_collector ON contracts (collector_id);
CREATE INDEX ix_payments_cashier    ON payments (cashier_id, paid_at);
CREATE INDEX ix_payments_paid_at    ON payments (tenant_id, paid_at);
CREATE INDEX ix_collection_user     ON collection_actions (user_id, created_at);

-- ---------------------------------------------------------------- rollar ruxsatlari
-- Qator bo'lmasa — koddagi standart ruxsatlar (Role.defaultPermissions) ishlatiladi.
CREATE TABLE role_permissions (
    tenant_id   BIGINT      NOT NULL DEFAULT 1,
    role        VARCHAR(20) NOT NULL,
    permissions JSONB       NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, role),
    CONSTRAINT ck_role_permissions_role CHECK (role IN ('CREDIT_OFFICER','COLLECTOR','CASHIER'))
);

-- ---------------------------------------------------------------- operator KPI
CREATE TABLE kpi_targets (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT        NOT NULL DEFAULT 1,
    user_id          BIGINT        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    contracts_count  INTEGER       NOT NULL DEFAULT 0,
    sales_volume     NUMERIC(18,2) NOT NULL DEFAULT 0,
    collected_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    recovered_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    actions_count    INTEGER       NOT NULL DEFAULT 0,
    bonus_amount     NUMERIC(18,2) NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_kpi_targets_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_kpi_targets_nonneg CHECK (contracts_count >= 0 AND sales_volume >= 0 AND collected_amount >= 0
        AND recovered_amount >= 0 AND actions_count >= 0 AND bonus_amount >= 0)
);

-- KPI vaznlari va prognoz uchun mavsumiy koeffitsiyentlar (NULL — standart qiymatlar)
ALTER TABLE tenant_settings ADD COLUMN kpi_config  JSONB;
ALTER TABLE tenant_settings ADD COLUMN seasonality JSONB;

-- ---------------------------------------------------------------- undirish
-- Rejalashtirilgan sana (masalan, tashrif yoki qayta qo'ng'iroq kuni)
ALTER TABLE collection_actions ADD COLUMN planned_date DATE;

-- Sud va MIB jarayoni. Da'vo summasi — faqat qolgan asosiy qarz (savdo narxining to'lanmagan qismi):
-- murobaha qoidasiga ko'ra kechikish uchun jarima yoki foiz qo'shilmaydi.
CREATE TABLE legal_cases (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT        NOT NULL DEFAULT 1,
    contract_id      BIGINT        NOT NULL REFERENCES contracts (id),
    status           VARCHAR(20)   NOT NULL DEFAULT 'PRETRIAL',
    claim_amount     NUMERIC(18,2) NOT NULL,
    court_name       VARCHAR(200),
    case_no          VARCHAR(60),
    filed_at         DATE,
    hearing_at       DATE,
    decision_at      DATE,
    enforcement_no   VARCHAR(60),
    note             VARCHAR(1000),
    assigned_user_id BIGINT        REFERENCES users (id),
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_legal_status CHECK (status IN ('PRETRIAL','FILED','HEARING','DECIDED','ENFORCEMENT','CLOSED')),
    CONSTRAINT ck_legal_amount CHECK (claim_amount >= 0)
);
-- Bitta shartnoma bo'yicha bir vaqtda faqat bitta ochiq ish
CREATE UNIQUE INDEX uq_legal_open ON legal_cases (contract_id) WHERE status <> 'CLOSED';

-- ---------------------------------------------------------------- hujjatlar
CREATE TABLE document_templates (
    tenant_id  BIGINT        NOT NULL DEFAULT 1,
    code       VARCHAR(20)   NOT NULL,
    lang       VARCHAR(5)    NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    body       TEXT          NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, code, lang),
    CONSTRAINT ck_doc_code CHECK (code IN ('CONTRACT','DEMAND','CLAIM')),
    CONSTRAINT ck_doc_lang CHECK (lang IN ('uz','ru'))
);

-- Chiqarilgan hujjatlar jurnali (talabnoma / da'vo raqami shu yerdan olinadi)
CREATE SEQUENCE document_no_seq START 1;
CREATE TABLE document_log (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 1,
    contract_id BIGINT       NOT NULL REFERENCES contracts (id),
    code        VARCHAR(20)  NOT NULL,
    doc_no      VARCHAR(40)  NOT NULL,
    lang        VARCHAR(5)   NOT NULL,
    amount      NUMERIC(18,2),
    user_id     BIGINT       REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_document_log_contract ON document_log (contract_id);

-- Placeholder'lar: {company} {client} {pinfl} {passport} {address} {phone} {contract_no} {contract_date}
-- {product} {sale_price} {down_payment} {installment_total} {monthly} {term} {paid} {remaining}
-- {overdue} {days_late} {deadline} {doc_no} {today} {branch}
INSERT INTO document_templates (tenant_id, code, lang, title, body) VALUES
(1, 'CONTRACT', 'uz', 'MUROBAHA ASOSIDA NASIYA SAVDO SHARTNOMASI № {contract_no}',
'{company} (keyingi o''rinlarda — "Sotuvchi") bir tomondan va {client}, JShShIR {pinfl}, pasport {passport}, manzil: {address} (keyingi o''rinlarda — "Xaridor") ikkinchi tomondan quyidagilar haqida ushbu shartnomani tuzdilar.

1. Sotuvchi o''ziga tegishli bo''lgan {product} tovarini Xaridorga muddatli to''lov sharti bilan sotadi. Tovarning savdo narxi {sale_price}, shundan boshlang''ich to''lov {down_payment}.

2. Savdo narxi tovarning tannarxi va kelishilgan ustamadan iborat bo''lib, shartnoma imzolangan kunda qat''iy belgilanadi va keyinchalik o''zgarmaydi.

3. Qolgan {installment_total} Xaridor tomonidan {term} oy davomida, quyidagi jadval bo''yicha har oy {monthly} miqdorida to''lanadi.

4. To''lov kechiktirilgan taqdirda qarz summasiga hech qanday foiz, jarima yoki penya qo''shilmaydi. Sotuvchi faqat to''lanmagan savdo narxini talab qilish huquqiga ega.

5. Xaridor to''lovlarni muddatidan oldin to''lashi mumkin.

6. Nizolar muzokaralar yo''li bilan, kelishilmagan taqdirda O''zbekiston Respublikasi qonunchiligiga muvofiq sud tartibida hal qilinadi.'),
(1, 'CONTRACT', 'ru', 'ДОГОВОР КУПЛИ-ПРОДАЖИ В РАССРОЧКУ НА ОСНОВЕ МУРАБАХА № {contract_no}',
'{company} (далее — «Продавец») с одной стороны и {client}, ПИНФЛ {pinfl}, паспорт {passport}, адрес: {address} (далее — «Покупатель») с другой стороны заключили настоящий договор о нижеследующем.

1. Продавец продаёт принадлежащий ему товар {product} Покупателю с условием оплаты в рассрочку. Цена продажи товара {sale_price}, из них первоначальный взнос {down_payment}.

2. Цена продажи состоит из себестоимости товара и согласованной наценки, фиксируется в день подписания договора и в дальнейшем не меняется.

3. Оставшаяся сумма {installment_total} оплачивается Покупателем в течение {term} мес. по графику ниже ежемесячно в размере {monthly}.

4. При просрочке платежа к сумме долга не начисляются никакие проценты, штрафы или пени. Продавец вправе требовать только неоплаченную часть цены продажи.

5. Покупатель вправе погасить задолженность досрочно.

6. Споры решаются путём переговоров, а при недостижении согласия — в судебном порядке в соответствии с законодательством Республики Узбекистан.'),
(1, 'DEMAND', 'uz', 'TALABNOMA № {doc_no}',
'Hurmatli {client}!

{company} bilan {contract_date} sanada tuzilgan {contract_no} raqamli nasiya shartnomasi asosida Siz {product} tovarini muddatli to''lov sharti bilan xarid qilgansiz.

Bugungi kunga qadar to''lov jadvali bo''yicha {overdue} miqdoridagi to''lovlar {days_late} kundan beri amalga oshirilmagan. Shartnoma bo''yicha jami qolgan qarzingiz {remaining}.

Shartnoma shartlariga ko''ra qarzga hech qanday foiz, jarima yoki penya qo''shilmaydi. Shu bois Sizdan faqat muddati o''tgan {overdue} ni {deadline} sanasiga qadar to''lashingizni so''raymiz.

Belgilangan muddatda to''lov amalga oshirilmasa, kompaniya qarzni undirish uchun qonunchilikda belgilangan tartibda sudga murojaat qilishga majbur bo''ladi.

To''lov bo''yicha savollar uchun {branch} ga murojaat qilishingiz mumkin.'),
(1, 'DEMAND', 'ru', 'ТРЕБОВАНИЕ № {doc_no}',
'Уважаемый(ая) {client}!

На основании договора рассрочки № {contract_no} от {contract_date}, заключённого с {company}, Вы приобрели товар {product} с оплатой в рассрочку.

На сегодняшний день платежи по графику в размере {overdue} не поступают уже {days_late} дн. Общий остаток долга по договору составляет {remaining}.

По условиям договора к долгу не начисляются никакие проценты, штрафы или пени. Поэтому просим Вас оплатить только просроченную сумму {overdue} в срок до {deadline}.

Если оплата не поступит в установленный срок, компания будет вынуждена обратиться в суд для взыскания задолженности в порядке, установленном законодательством.

По вопросам оплаты Вы можете обратиться в {branch}.'),
(1, 'CLAIM', 'uz', 'DA''VO ARIZASI № {doc_no}',
'Da''vogar: {company}
Javobgar: {client}, JShShIR {pinfl}, manzil: {address}, tel.: {phone}
Da''vo bahosi: {remaining}

{company} va {client} o''rtasida {contract_date} sanada {contract_no} raqamli murobaha asosidagi nasiya savdo shartnomasi tuzilgan. Unga ko''ra javobgarga {product} tovari {sale_price} savdo narxida, {term} oy muddatga bo''lib to''lash sharti bilan topshirilgan.

Javobgar shu kungacha {paid} to''lagan, to''lov majburiyatlari {days_late} kundan beri bajarilmayapti. Javobgarga talabnoma yuborilgan, biroq qarz to''lanmagan.

Shartnomaga ko''ra kechikish uchun foiz, jarima yoki penya hisoblanmagan; da''vo summasi faqat to''lanmagan savdo narxidan iborat.

Yuqoridagilarga asosan, javobgardan da''vogar foydasiga {remaining} miqdoridagi qarzni undirishingizni so''raymiz.

Ilovalar: shartnoma nusxasi, to''lov jadvali, to''lovlar ko''chirmasi, talabnoma nusxasi.'),
(1, 'CLAIM', 'ru', 'ИСКОВОЕ ЗАЯВЛЕНИЕ № {doc_no}',
'Истец: {company}
Ответчик: {client}, ПИНФЛ {pinfl}, адрес: {address}, тел.: {phone}
Цена иска: {remaining}

Между {company} и {client} {contract_date} заключён договор рассрочки на основе мурабаха № {contract_no}. По нему ответчику передан товар {product} по цене продажи {sale_price} с оплатой в рассрочку на {term} мес.

Ответчик на сегодняшний день оплатил {paid}, обязательства по оплате не исполняются {days_late} дн. Ответчику направлено требование, однако долг не погашен.

По условиям договора проценты, штрафы или пени за просрочку не начислялись; сумма иска состоит только из неоплаченной части цены продажи.

На основании изложенного просим взыскать с ответчика в пользу истца задолженность в размере {remaining}.

Приложения: копия договора, график платежей, выписка о платежах, копия требования.');
