-- =====================================================================
-- DEMO: filiallar, xodimlar, KPI rejalari va undirish jarayoni. Faqat `dev` profilida.
-- Demo xodimlar paroli: demo12345 (operator1..3, kassir1, undiruvchi1).
-- =====================================================================

DO $$
DECLARE
    v_main BIGINT; v_chl BIGINT; v_sam BIGINT;
    v_off1 BIGINT; v_off2 BIGINT; v_off3 BIGINT; v_cash BIGINT; v_coll BIGINT;
    v_hash TEXT := '$2a$10$dSg3hehKusj849okqSNl2eHi.3AC42djjSFCKoBA4UfQSbNzsOQ1i';
    r RECORD;
    i INT := 0;
BEGIN
    SELECT id INTO v_main FROM branches WHERE tenant_id = 1 AND code = 'MAIN';
    UPDATE branches SET address = 'Toshkent sh., Yunusobod tumani, Amir Temur ko''chasi 108',
                        phone = '+998 71 200-00-01', manager_name = 'Dilshod Qodirov', monthly_target = 40000000
     WHERE id = v_main AND address IS NULL;

    INSERT INTO branches (tenant_id, code, name, address, phone, manager_name, monthly_target) VALUES
        (1, 'CHL', 'Chilonzor filiali', 'Toshkent sh., Chilonzor tumani, Bunyodkor ko''chasi 15',
         '+998 71 200-11-22', 'Rustam Aliyev', 30000000),
        (1, 'SAM', 'Samarqand filiali', 'Samarqand sh., Registon ko''chasi 42',
         '+998 66 233-44-55', 'Nodira Karimova', 25000000)
    ON CONFLICT (tenant_id, code) DO NOTHING;
    SELECT id INTO v_chl FROM branches WHERE tenant_id = 1 AND code = 'CHL';
    SELECT id INTO v_sam FROM branches WHERE tenant_id = 1 AND code = 'SAM';

    INSERT INTO users (tenant_id, username, password_hash, full_name, role, branch_id) VALUES
        (1, 'operator1',   v_hash, 'Kamola Nazarova',   'CREDIT_OFFICER', v_main),
        (1, 'operator2',   v_hash, 'Bekzod Rahimov',    'CREDIT_OFFICER', v_chl),
        (1, 'operator3',   v_hash, 'Shahzod Ergashev',  'CREDIT_OFFICER', v_sam),
        (1, 'kassir1',     v_hash, 'Malika Tursunova',  'CASHIER',        v_main),
        (1, 'undiruvchi1', v_hash, 'Jasur Mahmudov',    'COLLECTOR',      v_main)
    ON CONFLICT (tenant_id, username) DO NOTHING;
    SELECT id INTO v_off1 FROM users WHERE username = 'operator1';
    SELECT id INTO v_off2 FROM users WHERE username = 'operator2';
    SELECT id INTO v_off3 FROM users WHERE username = 'operator3';
    SELECT id INTO v_cash FROM users WHERE username = 'kassir1';
    SELECT id INTO v_coll FROM users WHERE username = 'undiruvchi1';

    -- shartnomalarni filial va operatorlarga taqsimlash
    FOR r IN SELECT id FROM contracts WHERE officer_id IS NULL ORDER BY id LOOP
        i := i + 1;
        IF i % 3 = 0 THEN
            UPDATE contracts SET branch_id = v_main, officer_id = v_off1 WHERE id = r.id;
        ELSIF i % 3 = 1 THEN
            UPDATE contracts SET branch_id = v_chl, officer_id = v_off2 WHERE id = r.id;
        ELSE
            UPDATE contracts SET branch_id = v_sam, officer_id = v_off3 WHERE id = r.id;
        END IF;
    END LOOP;
    UPDATE contracts SET branch_id = v_main WHERE branch_id IS NULL;
    UPDATE contracts SET collector_id = v_coll WHERE status = 'LATE' AND collector_id IS NULL;

    -- to'lovlar: bosh filialda kassir, boshqalarida shartnomani rasmiylashtirgan operator qabul qilgan
    UPDATE payments p SET cashier_id = CASE WHEN c.branch_id = v_main THEN v_cash ELSE c.officer_id END
      FROM contracts c WHERE c.id = p.contract_id AND p.cashier_id IS NULL;

    -- undirish harakatlari: qo'ng'iroq va va'dalar
    INSERT INTO collection_actions (tenant_id, contract_id, action_type, result, note, promised_date, promised_amount,
                                    user_id, created_at)
    SELECT 1, c.id, 'CALL', 'PTP', 'Maosh kunida to''lashini aytdi',
           current_date + (c.id % 5)::int - 2,
           (SELECT sum(s.amount - s.paid_amount) FROM schedule_items s
             WHERE s.contract_id = c.id AND s.status <> 'PAID' AND s.due_date < current_date),
           v_coll, now() - interval '4 days'
      FROM contracts c WHERE c.status = 'LATE';
    INSERT INTO collection_actions (tenant_id, contract_id, action_type, result, note, planned_date, user_id, created_at)
    SELECT 1, c.id, 'VISIT', 'PLANNED', 'Uyga tashrif rejalashtirildi', current_date + 2, v_coll, now() - interval '1 day'
      FROM contracts c WHERE c.status = 'LATE' AND c.id % 2 = 0;

    -- eng ko'p kechikkan shartnoma bo'yicha sud ishi (da'vo — faqat qolgan qarz, jarimasiz)
    INSERT INTO legal_cases (tenant_id, contract_id, status, claim_amount, court_name, case_no, filed_at,
                             note, assigned_user_id)
    SELECT 1, x.id, 'FILED', x.rem, 'Chilonzor tumanlararo fuqarolik ishlari sudi', '2-1001-2601/' || x.id,
           current_date - 12, 'Talabnoma javobsiz qoldi', v_coll
      FROM (SELECT c.id, sum(s.amount - s.paid_amount) AS rem,
                   min(s.due_date) FILTER (WHERE s.status <> 'PAID' AND s.due_date < current_date) AS first_late
              FROM contracts c JOIN schedule_items s ON s.contract_id = c.id
             WHERE c.status = 'LATE' GROUP BY c.id) x
     WHERE x.first_late IS NOT NULL
     ORDER BY x.first_late
     LIMIT 1;

    -- KPI rejalari
    INSERT INTO kpi_targets (tenant_id, user_id, contracts_count, sales_volume, collected_amount, recovered_amount,
                             actions_count, bonus_amount) VALUES
        (1, v_off1, 4, 15000000, 5000000, 0, 0, 1500000),
        (1, v_off2, 4, 15000000, 5000000, 0, 0, 1500000),
        (1, v_off3, 3, 12000000, 4000000, 0, 0, 1200000),
        (1, v_cash, 0, 0, 20000000, 0, 0, 1000000),
        (1, v_coll, 0, 0, 0, 3000000, 25, 1800000)
    ON CONFLICT (tenant_id, user_id) DO NOTHING;
END $$;
