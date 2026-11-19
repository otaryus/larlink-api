-- Dados de demonstração: acrescenta 21 candidatas aprovadas (30 perfis no total
-- quando executado na base LarLink actual). Os emails são reservados para demo.
-- Senha inicial comum: LarLink.Demo2026! (a conta exige troca no primeiro acesso).
DO $$
DECLARE
    names text[] := ARRAY[
        'Marta Chissano', 'Judite Mucavele', 'Alzira Manjate', 'Noémia Cossa',
        'Benvinda Sitoe', 'Ivone Bila', 'Salomé Nhantumbo', 'Teresa Macuácua',
        'Rute Tembe', 'Esperança Mbanze', 'Célia Chongo', 'Lurdes Mahumane',
        'Mónica Chauque', 'Helena Macamo', 'Adélia Jamisse', 'Carolina Baloi',
        'Paulina Zandamela', 'Amélia Cumbane', 'Eunice Matusse', 'Rosa Chivambo',
        'Felicidade Mondlane'
    ];
    cities text[] := ARRAY['Maputo', 'Matola', 'Beira', 'Nampula', 'Xai-Xai', 'Inhambane'];
    neighborhoods text[][] := ARRAY[
        ARRAY['Polana', 'Maxaquene', 'Malhangalene', 'Costa do Sol'],
        ARRAY['Matola A', 'Fomento', 'Liberdade', 'Machava'],
        ARRAY['Ponta-Gêa', 'Macúti', 'Munhava', 'Chingussura'],
        ARRAY['Central', 'Namutequeliua', 'Muahivire', 'Muatala'],
        ARRAY['Praia', 'Chiconelane', 'Patrice Lumumba', 'Inhamissa'],
        ARRAY['Conguiana', 'Balane', 'Morrumbene', 'Maxixe']
    ];
    all_skills text[] := ARRAY['CLEANING', 'COOKING', 'CHILDCARE', 'ELDERLY_CARE', 'LAUNDRY', 'GARDENING', 'OTHER'];
    all_days text[] := ARRAY['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
    demo_password_hash text := '$2a$10$xYcRyDGXgPp6oGr8T0iAWOM5oOaVnCTKAB./9RAusth2lCiFklVS6';
    account_id uuid;
    profile_id uuid;
    city_index integer;
    i integer;
    k integer;
    salary integer;
BEGIN
    FOR i IN 1..array_length(names, 1) LOOP
        IF NOT EXISTS (SELECT 1 FROM accounts WHERE email = 'demo.candidata' || lpad(i::text, 2, '0') || '@larlink.invalid') THEN
            account_id := gen_random_uuid();
            city_index := ((i - 1) % array_length(cities, 1)) + 1;
            salary := 8000 + (((i * 7) % 121) * 50);

            INSERT INTO accounts (id, name, email, password_hash, role, must_change_password, created_at)
            VALUES (
                account_id,
                names[i],
                'demo.candidata' || lpad(i::text, 2, '0') || '@larlink.invalid',
                demo_password_hash,
                'CANDIDATE',
                true,
                now()
            );

            profile_id := gen_random_uuid();
            INSERT INTO housekeeper_profiles
                (id, account_id, birth_date, phone, city, neighborhood, expected_salary, years_of_experience, status)
            VALUES (
                profile_id,
                account_id,
                (current_date - make_interval(years => 23 + (i % 22), months => (i * 3) % 12))::date,
                '+258' || (84 + (i % 4))::text || lpad((1000000 + i * 7919)::text, 7, '0'),
                cities[city_index],
                neighborhoods[city_index][((i - 1) % 4) + 1],
                salary,
                i % 16,
                'APPROVED'
            );

            -- Perfis combinam 2 a 5 competências, com padrões variados.
            FOR k IN 1..array_length(all_skills, 1) LOOP
                IF k = ((i - 1) % 7) + 1 OR k = ((i + 1) % 7) + 1 OR ((i + k) % 4 = 0) THEN
                    INSERT INTO housekeeper_skills (profile_id, skill) VALUES (profile_id, all_skills[k]);
                END IF;
            END LOOP;

            -- Disponibilidades variam entre dias úteis e fins de semana.
            FOR k IN 1..array_length(all_days, 1) LOOP
                IF ((i + k) % 4 <> 0) OR (i % 5 = 0 AND k IN (6, 7)) THEN
                    INSERT INTO housekeeper_availability (profile_id, weekday) VALUES (profile_id, all_days[k]);
                END IF;
            END LOOP;
        END IF;
    END LOOP;
END $$;
