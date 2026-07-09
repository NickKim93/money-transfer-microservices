# account-service

Spring Boot сервис управления счетами (Неделя 2, событийная версия). Java 21 / Maven,
своя БД PostgreSQL `account_db`. Порт приложения — **8081**.

Помимо REST-CRUD по счетам, слушает событие `TransferRequested` из RabbitMQ, двигает деньги
между двумя счетами **одной локальной `@Transactional`** и публикует `TransferResult`.

> Часть стека `java-prep`. Запуск всего стека в контейнерах — в [корневом README](../README.md).
> Этот файл описывает сам модуль и локальную разработку (профиль `local`).

## Стек

- Java 21
- Spring Boot 3.5.x
- Spring Web
- Spring Data JPA
- Spring Validation
- PostgreSQL 16
- Docker (multi-stage) + docker-compose
- Maven Wrapper

## Структура

```text
src/main/java/com/myapp/account_service
+-- controller      # REST-контроллеры
+-- dto             # request / response DTO
+-- exception       # иерархия исключений + GlobalExceptionHandler
+-- messaging       # события (dto/status), RabbitMQ config, имена очередей/ключей
+-- model           # JPA-сущности и базовые классы
+-- repository       # слой persistence
+-- service         # бизнес-логика (+ service/messaging: listener и publisher)
```

## Профили и конфигурация

| Профиль | Где активен | datasource |
|---|---|---|
| `docker` | в контейнере (`SPRING_PROFILES_ACTIVE=docker` из compose) | `account-db:5432/account_db` |
| `local` | запуск через `mvnw` на хосте | `localhost:5435/account_db` |

Креды БД (`DB_USER`/`DB_PASSWORD`) приходят из окружения; дефолт — `postgres/postgres`.
Значения env для compose лежат в корневом `.env`. Реальные секреты не коммить.

## Запуск

**Весь стек в контейнерах** (рекомендуется) — из корня `java-prep`:

```bash
docker compose up --build      # account-service → :8081
```

**Локально (профиль `local`)** — БД поднимает корневой compose, приложение запускается на хосте:

```bash
cd ..
docker compose up -d account-db          # только БД, :5435
cd account-service
./mvnw spring-boot:run -Dspring-boot.run.profiles=local      # :8081
```

Windows: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"`.

## API

Базовый путь: `/api/v1`

Создать счёт:

```http
POST /api/v1/accounts
Content-Type: application/json

{ "ownerName": "Alice", "currency": "USD", "balance": "100.00" }
```

Получить счёт:

```http
GET /api/v1/accounts/{id}
```

Списание / зачисление:

```http
POST /api/v1/accounts/{id}/withdraw
POST /api/v1/accounts/{id}/deposit
Content-Type: application/json

{ "amount": "25.00" }
```

Оба возвращают обновлённый счёт. Коды ответов:

- `200 OK` — операция применена, в теле новый баланс
- `400 Bad Request` — невалидный ввод (`amount` отсутствует или не положителен)
- `404 Not Found` — счёт не найден
- `409 Conflict` — конкурентное изменение (оптимистичная блокировка через `@Version`)
- `422 Unprocessable Entity` — нарушено бизнес-правило (например, нехватка средств на `withdraw`)

### Известный долг (норма на сейчас)

- **Операции не идемпотентны.** Повтор того же `withdraw`/`deposit` применится снова.
  Idempotency-ключи — Неделя 2.

## Сборка и тесты

```bash
./mvnw compile
./mvnw test
```

Windows: `.\mvnw.cmd compile` / `.\mvnw.cmd test`.

## База данных

В контейнере данные живут в **named-volume** `account_data` (переживают `docker compose down`,
сносятся через `docker compose down -v`). Bind-mount в `./data` не используется.

- Образ: `postgres:16`
- Порт на хосте: `5435` → контейнер `5432`
- Имя БД: `account_db`

## Ревью

Находки и шаги — в `review/` (`DAY1_FINDINGS.md` … `DAY3_FINDINGS.md`),
сводное ревью стека Дня 5 — в корневом `review/DAY5_FINDINGS.md`.
