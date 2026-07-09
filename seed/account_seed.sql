-- Seed data for account-service (DB: account_db)
-- Запускать ПОСЛЕ первого старта account-service (таблица `account` создаётся через ddl-auto=update).
-- Идемпотентно: ON CONFLICT DO NOTHING — повторный запуск не падает и не плодит дубли.
--
--   docker exec -i account-db psql -U postgres -d account_db < seed/account_seed.sql

INSERT INTO account (id, owner_name, balance, currency, version, created_at, updated_at) VALUES
    -- «богатый» счёт — для сценария успеха (200)
    ('11111111-1111-1111-1111-111111111111', 'Alice', 1000.0000, 'USD', 0, now(), now()),
    -- счёт с нулём — пригодится в День 4 для проверки нехватки средств
    ('22222222-2222-2222-2222-222222222222', 'Bob',      0.0000, 'USD', 0, now(), now())
ON CONFLICT (id) DO NOTHING;

-- Заведомо НЕсуществующий id для сценария 404 (его НЕ вставляем намеренно):
--   99999999-9999-9999-9999-999999999999
