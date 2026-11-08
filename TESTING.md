# Прогон стека

## W3 Day 1 — путь `transfer.requested` на Kafka

Приёмка автоматизирована: каждый сценарий сам проверяет результат и падает с `exit 1`.

```bash
cp .env.example .env                       # один раз
scripts/acceptance-w3d1.sh                 # все: up happy message durability duplicate replay
scripts/acceptance-w3d1.sh duplicate       # отдельный сценарий
scripts/acceptance-w3d1.sh failed-replay   # опционально: демонстрация known gap (меняет балансы)
```

Отладка руками — `scripts/kafka.sh topics | describe | consume | group | reset-earliest | produce`,
глазами — kafka-ui на http://localhost:8090.

| # | Сценарий | Что проверяется | Результат |
|---|---|---|---|
| 1 | `up` | `docker compose up --build --wait`: все контейнеры healthy/running, сервисы отвечают | ⬜ |
| 2 | `happy` | `POST /transfers` → 202 + PENDING → COMPLETED; Alice −10, Bob +10, `balance_sum` не изменилась | ⬜ |
| 3 | `message` | запись в `transfer.requested.v1`: ключ = `fromAccountId`, видны партиция и оффсет | ⬜ |
| 4 | `durability` | account-service остановлен → 3 перевода PENDING, lag = 3 → старт → все COMPLETED, lag = 0 | ⬜ |
| 5 | `duplicate` | та же запись (key + value) отправлена дважды → деньги сдвинулись один раз, в логе один `already committed` | ⬜ |
| 6 | `replay` | `--reset-offsets --to-earliest` → все сообщения переиграны, балансы и сумма не изменились | ⬜ |
| — | `failed-replay` | FAILED-перевод при replay исполняется, если денег стало хватать (known gap) | ⬜ |

### Почему это работает (впиши своими словами после прогона)

- **Durability:** где лежали сообщения, пока консьюмер был остановлен, и откуда он узнал, с какого
  места читать после старта (лог топика на диске брокера + закоммиченный оффсет группы в
  `__consumer_offsets`).
- **Duplicate / replay:** почему повтор не двигает деньги (insert маркера `ProcessedTransfer` по PK
  `transferId` в той же транзакции, что и движение денег → `DuplicateTransferMessageException` →
  переиздаём COMPLETED → transfer-service: гвард, no-op).

### Фактический вывод

<!-- вставь ключевые строки вывода скрипта / kafka.sh consume / group -->

---

## Архив: W1 Day 5 — синхронная версия (неактуально после W2 Day 2)

Проверяем, что весь стек поднимается одной командой и перевод проходит **через контейнеры**:
`transfer-service` → `account-service` → две отдельные БД.

| Компонент | В сети compose | На хосте |
|---|---|---|
| account-service | `account-service:8081` | `localhost:8081` |
| transfer-service | `transfer-service:8082` | `localhost:8082` |
| account-db | `account-db:5432` | `localhost:5435` / `account_db` |
| transfer-db | `transfer-db:5432` | `localhost:5436` / `transfer_db` |

> На Windows вызывай `curl.exe`, а не `curl` (последнее — алиас `Invoke-WebRequest`).

## Шаг 1. Поднять весь стек

Из корня `java-prep`:

```bash
docker compose up --build
docker compose ps        # 4 сервиса; обе БД — healthy, оба приложения — running
```

Приложения стартуют только после healthcheck своих БД (`depends_on: service_healthy`).
Первый старт создаёт таблицы (`ddl-auto=update`).

## Шаг 2. Засеять данные

```bash
# bash / git-bash
docker exec -i account-db  psql -U postgres -d account_db  < seed/account_seed.sql
docker exec -i transfer-db psql -U postgres -d transfer_db < seed/transfer_seed.sql
```

```powershell
# PowerShell (нет <-редиректа — через pipe)
Get-Content seed/account_seed.sql  | docker exec -i account-db  psql -U postgres -d account_db
Get-Content seed/transfer_seed.sql | docker exec -i transfer-db psql -U postgres -d transfer_db
```

Сид: Alice `1111…1111` (баланс 1000), Bob `2222…2222` (баланс 0).

Проверка, что счёт виден напрямую в account-service:

```bash
curl.exe -i http://localhost:8081/api/v1/accounts/11111111-1111-1111-1111-111111111111
```

## Шаг 3. Сценарии перевода (через transfer-service)

### ✅ Сценарий 1 — успешный перевод (200 → COMPLETED)
Alice → Bob, 100. Денег хватает, оба сервиса живы.

```bash
curl.exe -i -X POST http://localhost:8082/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{ "fromAccountId":"11111111-1111-1111-1111-111111111111", "toAccountId":"22222222-2222-2222-2222-222222222222", "amount":100.00 }'
```

Ожидаем: `200 OK`, статус `COMPLETED`. Проверь балансы: у Alice 900, у Bob 100.
Статус перевода: `GET http://localhost:8082/api/v1/transfers/{transferId}`.

### ✅ Сценарий 2 — нехватка средств (422 → FAILED)
Bob (баланс 0) → Alice, 100. `withdraw` падает на стороне account-service.

```bash
curl.exe -i -X POST http://localhost:8082/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{ "fromAccountId":"22222222-2222-2222-2222-222222222222", "toAccountId":"11111111-1111-1111-1111-111111111111", "amount":100.00 }'
```

Ожидаем: `422 Unprocessable Entity`, перевод `FAILED` (`failureReason = Insufficient funds`),
`deposit` НЕ вызван, балансы не тронуты.

### ✅ Сценарий 3 — account-service недоступен (503 → FAILED)
Останавливаем account-service контейнером (НЕ Ctrl+C — стек крутится в compose):

```bash
docker compose stop account-service
curl.exe -i -X POST http://localhost:8082/api/v1/transfers \
  -H "Content-Type: application/json" \
  -d '{ "fromAccountId":"11111111-1111-1111-1111-111111111111", "toAccountId":"22222222-2222-2222-2222-222222222222", "amount":10.00 }'
docker compose start account-service     # вернуть назад
```

Ожидаем: `503 Service Unavailable` (connection refused → `ResourceAccessException` →
`AccountClientResourceAccessException`), перевод `FAILED`.

### Прочие исходы
- `404 Not Found` — несуществующий `fromAccountId`/`toAccountId`.
- `400 Bad Request` — `fromAccountId == toAccountId`.
- `502 Bad Gateway` — account-service вернул 5xx (нужен искусственный 5xx; на штатном пути не возникает).

Диагностический эндпоинт (только GET к account-service, без записи в таблицу transfer):
`GET http://localhost:8082/api/v1/transfers/check/{accountId}`.

## Шаг 4. Персистентность

```bash
docker compose down        # данные в named-volume остаются
docker compose up          # балансы после сценариев 1-3 на месте
```

`docker compose down -v` — снести вместе с данными БД (вернуться к чистому состоянию).

## Что зафиксировать
- Все три сценария прошли через контейнеры (200 / 422 / 503).
- Данные пережили `down`/`up`.
- Реальные тела ответов (200/404/422/503) вписать в таблицу исходов в `transfer_service/README.md`
  (там сейчас плейсхолдеры `TODO`).
