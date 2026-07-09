# java-prep — событийная версия (Неделя 2)

Учебная двухсервисная система перевода денег под собес в финтех.

- **account-service** (`:8081`) — счета: создание, баланс, `withdraw`/`deposit`, атомарный
  перевод между двумя счетами одной БД. Своя БД `account_db`.
- **transfer-service** (`:8082`) — оркестрация перевода: принимает запрос, ведёт статус,
  общается с account-service **через события** (RabbitMQ). Своя БД `transfer_db`.

> Статус: **Неделя 2, День 2 — синхронная оркестрация заменена событийным потоком.**
> Синхронный `RestClient` в потоке перевода больше не используется (остался только за
> диагностическим `GET /transfers/check/{id}`). Идемпотентности, retry/DLQ, outbox,
> компенсаций — пока нет (Неделя 2-3). См. «Известный долг» ниже.

## Событийный поток перевода

```
POST /transfers
  └─ transfer-service: сохранить Transfer=PENDING, вернуть 202 + transferId
     └─ publish TransferRequested ─────► [transfer.exchange | rk transfer.requested]
                                              └─► account.transfer.requested.queue
        account-service: двигает деньги ОДНОЙ локальной @Transactional
          (withdraw + deposit в одной БД → атомарно), затем
          publish TransferResult(outcome, failureReason) ─► [transfer.exchange | rk transfer.result]
                                                                 └─► transfer.result.queue
             transfer-service: markCompleted / markFailed
```

Статус перевода клиент опрашивает через `GET /transfers/{id}` (202 + polling), т.к. обработка
асинхронная. Между `PENDING` и терминальным статусом система **eventual consistent** — это by design.

## Стек

- Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, **AMQP / RabbitMQ**)
- PostgreSQL 16
- RabbitMQ 4 (topic exchange `transfer.exchange`)
- Docker (multi-stage) + docker-compose

## Карта портов

| Компонент | Внутри сети | На хосте |
|---|---|---|
| account-service | `account-service:8081` | `localhost:8081` |
| transfer-service | `transfer-service:8082` | `localhost:8082` |
| account-db (Postgres) | `account-db:5432` | `localhost:5435` |
| transfer-db (Postgres) | `transfer-db:5432` | `localhost:5436` |
| rabbitmq (AMQP) | `rabbitmq:5672` | `localhost:5672` |
| rabbitmq (Management UI) | `rabbitmq:15672` | `localhost:15672` |

Внутри docker-сети сервисы ходят друг к другу и к БД **по именам сервисов**, не по `localhost`.

## Конфигурация (env)

Compose читает корневой `.env` и подставляет значения в `docker-compose.yaml`:

```env
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
ACCOUNT_DB_NAME=account_db
ACCOUNT_DB_PORT=5435
TRANSFER_DB_NAME=transfer_db
TRANSFER_DB_PORT=5436
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest
```

Креды RabbitMQ приходят в приложения через `RABBITMQ_USER`/`RABBITMQ_PASSWORD` (тем же путём, что и БД).

Креды БД в приложения приходят **через env** (`DB_USER`/`DB_PASSWORD` → `application-docker.yaml`),
не зашиты в образ. Реальные секреты не коммить — для шаринга используется `.env.example`.

Профиль в контейнерах переключается через `SPRING_PROFILES_ACTIVE=docker` (задаётся в compose).

## Запуск всего стека (одна команда)

```bash
docker compose up --build
```

Поднимутся 5 контейнеров: `account-db`, `transfer-db`, `rabbitmq`, `account-service`, `transfer-service`.
Приложения стартуют **после** того, как их БД и `rabbitmq` прошли healthcheck (`depends_on: service_healthy`).
Таблицы создаются на первом старте (`ddl-auto=update`).

Остановить / снести с данными:

```bash
docker compose down            # остановить, данные в volume сохранятся
docker compose down -v         # снести вместе с данными БД
```

## Засев данных

После первого старта (таблицы уже созданы):

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

Сид заводит Alice (`1111…1111`, баланс 1000) и Bob (`2222…2222`, баланс 0).

## Прогон сценариев

Полные шаги (успех / нехватка средств → 422 / остановленный account-service → 503) —
в [TESTING.md](TESTING.md).

## Известный долг (норма на сейчас)

- **Идемпотентности нет (Day 3).** Повторная доставка `TransferRequested` спишет/зачислит
  деньги ещё раз. Дизайн к дедупу готов: сквозной `transferId` + `eventId` на каждом событии —
  стабильный ключ, по которому повтор можно будет отсечь.
- **⚠️ Потеря result-события → `PENDING` навсегда.** Если `TransferResult` потеряется, или
  account-service упадёт после коммита денег, но до публикации, или transfer-service упадёт
  после приёма, но до `markCompleted` — перевод останется `PENDING`. Это новый распределённый
  край событийной модели. Лечится **outbox** + идемпотентным консьюмером + reconciliation (Неделя 3).
- **Publish не в одной транзакции с БД (Day 2).** `Transfer=PENDING` коммитится, затем
  публикуется событие — между ними окно рассинхрона. Мотивация outbox (Неделя 3).
- **retry/DLQ нет (Day 4).** Ошибочное сообщение сейчас просто логируется; ретраев и
  dead-letter-очереди пока нет.
- **Компенсаций/саг нет (Неделя 3).** В рамках одной БД account-service они не нужны (перевод
  атомарен одной локальной транзакцией); понадобятся, когда счета разъедутся по разным БД.

## Локальная разработка без контейнеров

Профиль `local` (БД из compose, сами приложения через `mvnw`) — см. README модулей:
[account-service](account-service/README.md), [transfer-service](transfer_service/README.md).
