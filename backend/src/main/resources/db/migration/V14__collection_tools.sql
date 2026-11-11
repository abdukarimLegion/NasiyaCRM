-- =====================================================================
-- V14: Undirish vositalari: ish joyiga/kafilga so'rovnoma xati, bank hisoblariga taqiq (arest),
--      E-SUD orqali topshirish belgisi.
-- =====================================================================

-- Sud ishi: bank hisoblariga taqiq so'rovi va elektron sudga topshirish
ALTER TABLE legal_cases
    ADD COLUMN arrest_requested_at DATE,
    ADD COLUMN arrest_note         VARCHAR(500),
    ADD COLUMN esud_submitted_at   TIMESTAMPTZ,
    ADD COLUMN esud_ref            VARCHAR(100);

-- Yangi hujjat turi: qarzdorning ish joyiga va kafiliga so'rovnoma xati
ALTER TABLE document_templates DROP CONSTRAINT ck_doc_code;
ALTER TABLE document_templates ADD CONSTRAINT ck_doc_code CHECK (code IN ('CONTRACT','DEMAND','CLAIM','EMPLOYER'));

INSERT INTO document_templates (tenant_id, code, lang, title, body) VALUES
(1, 'EMPLOYER', 'uz', 'SO''ROVNOMA XATI № {doc_no}',
'{workplace} rahbariyati va bosh buxgalteriga.
Nusxasi: kafil {guarantor}.

Sizning tashkilotingiz xodimi {client} (JShShIR {pinfl}) bilan {company} o''rtasida {contract_date} sanada {contract_no} raqamli nasiya savdo shartnomasi tuzilgan. Unga ko''ra xodimga «{product}» {term} oy davomida bo''lib to''lash sharti bilan topshirilgan.

Bugungi kunga kelib xodim to''lov majburiyatlarini {days_late} kundan beri bajarmayapti. Muddati o''tgan qarz {overdue}, shartnoma bo''yicha jami qoldiq {remaining} ni tashkil etadi.

Shu munosabat bilan Sizdan:
1. Xodimning ushbu tashkilotda ishlashi va lavozimini tasdiqlashingizni;
2. Xodimni qarzdorlik haqida xabardor qilishingizni va uni {deadline} sanagacha to''lashiga ko''maklashishingizni so''raymiz.

Belgilangan muddatda qarz to''lanmasa, {company} qonunchilikda belgilangan tartibda sudga murojaat qilishga majbur bo''ladi. Aloqa uchun: {branch}.'),
(1, 'EMPLOYER', 'ru', 'ПИСЬМО-ЗАПРОС № {doc_no}',
'Руководству и главному бухгалтеру {workplace}.
Копия: поручителю {guarantor}.

Между сотрудником Вашей организации {client} (ПИНФЛ {pinfl}) и {company} {contract_date} заключён договор продажи в рассрочку № {contract_no}. По нему сотруднику передан товар «{product}» с оплатой в течение {term} мес.

На сегодняшний день сотрудник не исполняет платёжные обязательства в течение {days_late} дн. Просроченная задолженность составляет {overdue}, общий остаток по договору — {remaining}.

В связи с этим просим Вас:
1. Подтвердить факт работы и должность сотрудника в Вашей организации;
2. Уведомить сотрудника о задолженности и содействовать её погашению до {deadline}.

Если задолженность не будет погашена в указанный срок, {company} будет вынуждена обратиться в суд в установленном законом порядке. Контакты: {branch}.')
ON CONFLICT (tenant_id, code, lang) DO NOTHING;
