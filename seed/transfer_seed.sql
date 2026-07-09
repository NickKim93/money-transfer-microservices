-- Seed data for transfer-service (DB: transfer_db)
-- ВНИМАНИЕ: для Дня 3 НЕ обязательно. Эндпоинт /transfers/check/{id} не читает таблицу transfer —
-- он только дёргает account-service. Эти строки — задел под День 4 (оркестрация перевода).
-- Запускать ПОСЛЕ первого старта transfer-service (таблица `transfer` создаётся через ddl-auto=update).
--
--   docker exec -i transfer-db psql -U postgres -d transfer_db < seed/transfer_seed.sql

INSERT INTO transfer (id, from_account_id, to_account_id, amount, status, created_at, failure_reason) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111',
     '22222222-2222-2222-2222-222222222222',
     100.0000, 'COMPLETED', now(), NULL),
    ('aaaaaaaa-0000-0000-0000-000000000002',
     '22222222-2222-2222-2222-222222222222',
     '11111111-1111-1111-1111-111111111111',
      50.0000, 'FAILED', now(), 'Insufficient funds')
ON CONFLICT (id) DO NOTHING;
