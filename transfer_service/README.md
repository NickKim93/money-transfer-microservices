# transfer-service

Второй сервис системы (Неделя 2, событийная версия). Оркеструет перевод: принимает запрос,
ведёт статус и общается с `account-service` **через события** (RabbitMQ). Своя БД `transfer_db`.

> Статус: **Неделя 2, День 2 — событийная оркестрация.** Перевод идёт через события
> (`TransferRequested` → обработка в account-service → `TransferResult` → статус), а не через
> синхронный `withdraw → deposit`. `RestClient` остался только за диагностическим
> `GET /transfers/check/{id}`, из потока перевода убран.
> Идемпотентности/retry/DLQ/outbox нет — это Неделя 2-3. Компенсаций/саг нет — это Неделя 3.

## Стек
- Java 21, Spring Boot 3.5
- Spring AMQP (RabbitMQ), Spring Data JPA, PostgreSQL
- Spring Web (`RestClient`) — только для диагностического эндпоинта
- Docker (multi-stage) + docker-compose
- Порт приложения: 8082 · БД: `transfer_db` (в docker — `transfer-db:5432`, на хосте — `localhost:5436`)

> Запуск всего стека в контейнерах одной командой — в [корневом README](../README.md)
> (`docker compose up --build`). Ниже — локальная разработка (профиль `local`).

## Профили и адрес account-service

`base-url` клиента задаётся **в профиле**, потому что адрес account-service разный:

| Профиль | `clients.account-service.base-url` |
|---|---|
| `docker` | `http://account-service:8081/api/v1` (имя сервиса в сети compose) |
| `local` | `http://localhost:8081/api/v1` |

Таймауты (`connect-timeout`, `read-timeout`) — общие, в базовом `application.yaml`.

## Запуск (local-профиль)

БД обоих сервисов поднимает **единый корневой** `docker-compose` из `java-prep/`:

```bash
cd ../          # java-prep/
docker compose up -d account-db transfer-db    # только БД: 5435 / 5436
```

Затем сами приложения (каждое из своей папки, профиль `local`):

```bash
# account-service
./mvnw spring-boot:run -Dspring-boot.run.profiles=local     # :8081

# transfer-service
./mvnw spring-boot:run -Dspring-boot.run.profiles=local     # :8082
```

## Перевод (событийная оркестрация)

```
POST http://localhost:8082/api/v1/transfers
Content-Type: application/json

{ "fromAccountId": "<uuid>", "toAccountId": "<uuid>", "amount": 100.00 }
```

Ответ — **`202 Accepted`** + `{ transferId, status: PENDING }`. Запрос принят, обработка
асинхронная; синхронно результат НЕ ждётся.

Поток:
1. transfer-service сохраняет `Transfer=PENDING` (**до** публикации) и публикует
   `TransferRequested` (rk `transfer.requested`).
2. account-service слушает, двигает деньги **одной локальной `@Transactional`**
   (`withdraw` + `deposit` в одной БД → атомарно), публикует `TransferResult`
   (`outcome` = `COMPLETED`/`FAILED`, при отказе — `failureReason`), rk `transfer.result`.
3. transfer-service слушает результат → `markCompleted` / `markFailed` (короткая транзакция,
   переход `PENDING` → терминальный статус однонаправлен).

Нехватка средств / нет счёта: деньги **не тронуты** (`withdraw` бросает до `deposit`,
локальная транзакция откатывается целиком), результат — `FAILED` с `failureReason`.

Статус перевода клиент опрашивает (polling):

```
GET http://localhost:8082/api/v1/transfers/{transferId}
```

| Сценарий | Итоговый статус перевода |
|---|---|
| Деньги сдвинуты | `COMPLETED` |
| Нехватка средств у отправителя | `FAILED` (`failureReason` = insufficient funds) |
| Счёт не найден | `FAILED` (`failureReason` = account not found) |
| `fromAccountId == toAccountId` | перевод не создаётся, `POST` → `400 Bad Request` |

> До прихода результата статус будет `PENDING` — это нормальное промежуточное состояние
> (eventual consistency), а не ошибка.

## Проверка связи (диагностика)

```
GET http://localhost:8082/api/v1/transfers/check/{accountId}
```

Эндпоинт диагностический: дёргает `account-service GET /accounts/{id}` и возвращает его ответ.

### Наблюдаемые исходы вызова

Клиент (`AccountClient`) различает классы исходов, `GlobalExceptionHandler` транслирует их в HTTP:

| Сценарий | Что на клиенте | Ожидаемый HTTP наружу | Наблюдаемое тело (заполнить после прогона) |
|---|---|---|---|
| Счёт существует, account-service поднят | `ExternalAccountResponse` | `200 OK` | _TODO: вставить реальный JSON_ |
| Счёт не найден (account-service → 404) | `AccountClientNotFoundException` | `404 Not Found` | _TODO_ |
| Нет средств (account-service → 422) | `AccountClientInsufficientFundsException` | `422 Unprocessable Entity` | _TODO_ |
| account-service выключен / недоступен | `AccountClientResourceAccessException` | `503 Service Unavailable` | _TODO_ |
| account-service вернул 5xx | `AccountClientException` | `502 Bad Gateway` | _TODO_ |

> Воспроизведи минимум три (успех / выключенный account-service / несуществующий счёт),
> впиши фактические тела ответов в таблицу.

## Известные долги (норма на сейчас)
- **⚠️ Потеря result-события → `PENDING` навсегда.** Если `TransferResult` потеряется, или
  account-service упадёт после коммита денег но до публикации, или transfer-service упадёт
  после приёма но до `markCompleted` — перевод застрянет в `PENDING`. Это новый распределённый
  край событийной модели. Лечится **outbox** + идемпотентным консьюмером + reconciliation (Неделя 3).
- **Publish не в одной транзакции с БД.** `Transfer=PENDING` коммитится, потом публикуется
  событие — между ними окно рассинхрона (мотивация outbox, Неделя 3).
- **Идемпотентности нет (Day 3).** Повтор `TransferRequested`/`TransferResult` обработается
  снова. Дизайн готов: сквозной `transferId` + `eventId` на каждом событии — будущий ключ дедупа.
- **retry/DLQ нет (Day 4).** Ошибочное сообщение сейчас логируется, ретраев/dead-letter нет.
- Движение денег атомарно внутри одной БД account-service, поэтому распределённой транзакции /
  саги на этом шаге не требуется (понадобится, когда счета разъедутся по БД — Неделя 3).
