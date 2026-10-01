-- =====================================================================
-- DEMO shartnomalar, to'lov jadvallari va to'lovlar. Faqat `dev` profilida.
-- Oxirgi 6 oyga taqsimlangan: dashboard grafiklari bo'sh ko'rinmasin.
-- Hisob-kitob MurabahaCalculator bilan bir xil:
--   financed = cost - down;  markup = round(financed * pct / 100)
--   total    = financed + markup
--   monthly  = round(total / term, rounding_step);  last = total - monthly * (term-1)
-- =====================================================================

DO $$
DECLARE
    spec        RECORD;
    prod        RECORD;
    v_client    BIGINT;
    v_step      NUMERIC;
    v_cost      NUMERIC; v_down NUMERIC; v_fin NUMERIC; v_markup NUMERIC; v_total NUMERIC;
    v_monthly   NUMERIC; v_last NUMERIC; v_amount NUMERIC;
    v_prin      NUMERIC; v_mark NUMERIC; v_prin_left NUMERIC; v_mark_left NUMERIC;
    v_created   TIMESTAMPTZ; v_first DATE; v_due DATE;
    v_contract  BIGINT; v_sched BIGINT; v_payment BIGINT;
    v_status    TEXT; v_item_status TEXT; v_paid NUMERIC;
    v_closed    TIMESTAMPTZ;
    i           INT;
BEGIN
    SELECT coalesce(rounding_step, 1000) INTO v_step FROM tenant_settings WHERE tenant_id = 1;
    v_step := coalesce(v_step, 1000);

    FOR spec IN
        SELECT * FROM (VALUES
            ('Akmal Karimov',     'iPhone 15',                    6, 12, 2000000,  6, 0.0,  'A', 86),
            ('Dilnoza Yusupova',  'Samsung Galaxy A55',           6,  6,  500000,  6, 0.0,  'B', 72),
            ('Sardor To''xtayev', 'Oshxona to''plami',            5, 18, 2000000,  5, 0.5,  'A', 91),
            ('Gulnora Rashidova', 'Roison kir yuvish mashinasi',  5,  9,  600000,  5, 0.0,  'B', 68),
            ('Madina Saidova',    'LED TV Samsung 55"',           5, 12, 1500000,  2, 0.0,  'A', 84),
            ('Akmal Karimov',     'Midea konditsioner 12',        4,  6,  800000,  4, 0.0,  'A', 86),
            ('Jasur Abdullayev',  'Samsung Galaxy A55',           4,  9,  400000,  1, 0.0,  'C', 58),
            ('Dilnoza Yusupova',  'Artel muzlatgich INVERTER',    4, 12, 1000000,  4, 0.0,  'B', 70),
            ('Sardor To''xtayev', 'HP Pavilion 15',               3, 12, 2500000,  3, 0.0,  'A', 91),
            ('Gulnora Rashidova', 'Midea konditsioner 12',        3,  9,  500000,  0, 0.4,  'C', 61),
            ('Madina Saidova',    'iPhone 15',                    3, 12, 3000000,  3, 0.0,  'A', 88),
            ('Akmal Karimov',     'LED TV Samsung 55"',           2, 15, 1200000,  2, 0.0,  'B', 79),
            ('Dilnoza Yusupova',  'Oshxona to''plami',            2, 24, 1500000,  2, 0.0,  'B', 74),
            ('Jasur Abdullayev',  'Roison kir yuvish mashinasi',  2, 12,  700000,  0, 0.0,  'C', 57),
            ('Sardor To''xtayev', 'Artel muzlatgich INVERTER',    1, 12, 1000000,  1, 0.0,  'A', 90),
            ('Madina Saidova',    'Samsung Galaxy A55',           1,  6,  600000,  1, 0.0,  'A', 85),
            ('Gulnora Rashidova', 'LED TV Samsung 55"',           1, 15, 1000000,  0, 0.0,  'B', 69),
            ('Akmal Karimov',     'Artel muzlatgich INVERTER',    0, 12, 1200000,  0, 0.0,  'A', 87)
        ) AS t(client, product, months_ago, term, down, paid_months, part, risk, score)
    LOOP
        SELECT id INTO v_client FROM clients WHERE full_name = spec.client AND tenant_id = 1;
        SELECT id, name, price, markup_pct INTO prod FROM products WHERE name = spec.product AND tenant_id = 1;
        CONTINUE WHEN v_client IS NULL OR prod.id IS NULL;

        v_cost   := prod.price;
        v_down   := spec.down;
        v_fin    := v_cost - v_down;
        v_markup := round(v_fin * prod.markup_pct / 100);
        v_total  := v_fin + v_markup;

        v_monthly := round(v_total / spec.term / v_step) * v_step;
        v_last    := v_total - v_monthly * (spec.term - 1);
        IF v_last <= 0 THEN
            v_monthly := trunc(v_total / spec.term);
            v_last    := v_total - v_monthly * (spec.term - 1);
        END IF;

        v_created := date_trunc('month', now()) - make_interval(months => spec.months_ago)
                     + interval '8 days 11 hours';
        v_first   := (v_created + interval '1 month')::date;

        -- oxirgi to'langan oydan keyingi muddati o'tgan bo'lsa — LATE
        IF spec.paid_months >= spec.term THEN
            v_status := 'CLOSED';
            v_closed := (v_first + make_interval(months => spec.term - 1) + interval '2 days')::timestamptz;
        ELSIF (v_first + make_interval(months => spec.paid_months)) < current_date THEN
            v_status := 'LATE';
            v_closed := NULL;
        ELSE
            v_status := 'ACTIVE';
            v_closed := NULL;
        END IF;

        INSERT INTO contracts (tenant_id, contract_no, client_id, product_id, product_name,
                               cost_price, down_payment, markup_pct, markup_amount, sale_price,
                               installment_total, term_months, monthly_payment, first_due_date,
                               status, score_total, risk_category, decision,
                               signed_at, closed_at, created_at, updated_at)
        VALUES (1, 'NS-' || to_char(v_created, 'YYYY') || '-' || lpad(nextval('contract_no_seq')::text, 4, '0'),
                v_client, prod.id, prod.name,
                v_cost, v_down, prod.markup_pct, v_markup, v_cost + v_markup,
                v_total, spec.term, v_monthly, v_first,
                v_status, spec.score, spec.risk, 'APPROVED',
                v_created, v_closed, v_created, coalesce(v_closed, v_created))
        RETURNING id INTO v_contract;

        v_prin_left := v_fin;
        v_mark_left := v_markup;

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

            IF i <= spec.paid_months THEN
                v_paid := v_amount;
                v_item_status := 'PAID';
            ELSIF i = spec.paid_months + 1 AND spec.part > 0 THEN
                v_paid := round(v_amount * spec.part);
                v_item_status := 'PARTIAL';
            ELSE
                v_paid := 0;
                v_item_status := CASE WHEN v_due < current_date THEN 'LATE' ELSE 'PENDING' END;
            END IF;

            INSERT INTO schedule_items (tenant_id, contract_id, seq, due_date, amount,
                                        principal_part, markup_part, paid_amount, status, paid_at)
            VALUES (1, v_contract, i, v_due, v_amount, v_prin, v_mark, v_paid, v_item_status,
                    CASE WHEN v_item_status = 'PAID' THEN (v_due + interval '2 days')::timestamptz END)
            RETURNING id INTO v_sched;

            IF v_paid > 0 THEN
                INSERT INTO payments (tenant_id, contract_id, amount, method, paid_at, note, created_at)
                VALUES (1, v_contract, v_paid,
                        (ARRAY['CASH','CARD','PAYME','CLICK'])[1 + (i % 4)],
                        (v_due + interval '2 days')::timestamptz,
                        'Demo to''lov', (v_due + interval '2 days')::timestamptz)
                RETURNING id INTO v_payment;

                INSERT INTO payment_allocations (payment_id, schedule_item_id, amount)
                VALUES (v_payment, v_sched, v_paid);
            END IF;
        END LOOP;
    END LOOP;
END $$;
