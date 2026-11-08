# java-prep — событийная версия (Неделя 3)

Учебная двухсервисная система перевода денег под собес в финтех.

- **account-service** (`:8081`) — счета: создание, баланс, `withdraw`/`deposit`, атомарный
  перевод между двумя счетами одной БД. Своя БД `account_db`.
- **transfer-service** (`:8082`) — оркестрация перевода: принимает запрос, ведёт статус,
  общается с account-service **через события**. Своя БД `transfer_db`.

> Статус: **Неделя 3, День 1 — путь `TransferRequested` переехал на Kafka.**
> Путь результата (`TransferResult`) пока на RabbitMQ — переезжает на Day 2, после чего RabbitMQ
> удаляется. Консьюмер идемпотентен (дедуп по `transferId`), переходы статуса защищены гвардом.
> Retry/DLT — Day 3, outbox — Day 4–5. См. «Известный долг» ниже.

## Событийный поток перевода

```
POST /transfers
  └─ transfer-service: сохранить Transfer=PENDING, вернуть 202 + transferId
     └─ send TransferRequested ──► Kafka topic transfer.requested.v1 (3 партиции, key = fromAccountId)
        account-service (consumer group account-service):
          дедуп по transferId + withdraw/deposit ОДНОЙ локальной @Transactional, затем
          publish TransferResult(outcome, failureReason) ─► RabbitMQ [transfer.exchange | rk transfer.result]
                                                                 └─► transfer.result.queue
             transfer-service: markCompleted / markFailed (из терминального статуса не уходим)
```

Статус перевода клиент опрашивает через `GET /transfers/{id}` (202 + polling), т.к. обработка
асинхронная. Между `PENDING` и терминальным статусом система **eventual consistent** — это by design.

## Kafka design

| Решение | Значение | Почему |
|---|---|---|
| Топик | `transfer.requested.v1` | Суффикс версии — задел на несовместимую эволюцию схемы (`v2` рядом, а не ломать `v1`). |
| Партиции / RF | 3 / 1 | Один брокер → RF > 1 невозможен. 3 партиции = до 3 параллельных консьюмеров в группе. |
| Кто создаёт топик | transfer-service, бин `NewTopic` (`KafkaTopicConfig`) | Владелец события = продьюсер. У брокера `auto.create.topics.enable=false`: опечатка в имени или ранний консьюмер не создадут молча топик с дефолтами брокера. |
| Ключ | `fromAccountId` | См. ниже. |
| Consumer group | `account-service` | Один логический потребитель = одна группа; инстансы сервиса делят партиции. |
| Имена | `KafkaNames` в обоих сервисах | Без магических строк; общего модуля нет намеренно (см. «Сериализация»). |

### Ключ сообщения: `fromAccountId`, а не `transferId`

- **Что гарантирует про порядок.** Kafka упорядочивает записи только внутри партиции. Один ключ →
  всегда одна партиция → в группе её читает ровно один консьюмер. Значит, все списания с одного
  счёта обрабатываются **последовательно и в порядке отправки**.
- **Влияние на `@Version` (optimistic locking).** Параллельные переводы *с одного счёта* больше не
  конкурируют за его версию — они идут одним потоком. Конфликт остаётся по **счёту получателя**:
  `A→C` и `B→C` могут лежать в разных партициях и обновлять `C` одновременно. Ключ снижает число
  конфликтов, но не устраняет их.
- **Почему не `transferId`.** На один перевод в этом топике одно событие — упорядочивать по
  `transferId` нечего; такой ключ дал бы только равномерное распределение без пользы для счетов.
- **Если увеличить число партиций.** Партиция = `hash(key) % N`. При смене `N` ключ переезжает в
  другую партицию: старые сообщения счёта — в старой, новые — в новой, и на переходе порядок по
  ключу нарушается. Поэтому партиции закладывают с запасом, а расширяют при нулевом lag и
  остановленных продьюсерах.
- **Цена.** «Горячий» счёт → перекос нагрузки на одну партицию.
- **Нюанс.** По умолчанию `@KafkaListener` имеет `concurrency=1`: один инстанс читает все 3
  партиции одним потоком. Параллелизм появится при `concurrency=3` или трёх инстансах.

### Listeners

- `listeners` — где брокер **слушает** (bind): `INTERNAL://:9092`, `EXTERNAL://:29092`, `CONTROLLER://:9093`.
- `advertised.listeners` — какой адрес брокер **отдаёт клиенту в метаданных**; клиент использует
  bootstrap только для первого запроса, дальше ходит по адвертайзнутому адресу. Контейнерам —
  `kafka:9092`, сервисам из IDE — `localhost:29092`.
- Классика «подключается, но не получает сообщения»: если наружу адвертайзить `kafka:9092`, клиент
  из IDE подключится к `localhost:29092`, получит `kafka:9092` и не сможет его разрезолвить.

### Продьюсер (transfer-service)

- `StringSerializer` для ключа, `JsonSerializer` для значения.
- `acks=all` + `enable.idempotence=true` (в kafka-clients 3.x+ это и так дефолт; явно — как документация).
- **Что гарантирует идемпотентный продьюсер:** брокер по `(PID, sequence)` отбрасывает дубли,
  возникшие из-за *внутренних ретраев клиента* (ack потерялся в сети), и сохраняет порядок при
  ретраях — в пределах одной сессии продьюсера и одной партиции.
- **Чего не гарантирует:** после рестарта PID новый, повторный `send()` из приложения — новое
  сообщение для брокера. От падения после `send()` / повторной отправки защищает **дедуп консьюмера**,
  от потери между коммитом в БД и `send()` — **outbox**.
- `send()` асинхронный: результат обрабатывается в `whenComplete` (debug с partition/offset или error
  с `transferId`). Если брокер недоступен, `send()` блокирует поток запроса до `max.block.ms` (60 с).

### Консьюмер (account-service)

- `auto-offset-reset: earliest` срабатывает **только** когда у группы нет закоммиченного оффсета для
  партиции (новая группа/партиция) или оффсет вне диапазона (данные удалены retention). При обычном
  рестарте консьюмер продолжает с закоммиченного оффсета. Важно на первом запуске: если группа
  подписалась позже первых сообщений, `latest` их бы пропустил.
- **AckMode по умолчанию — `BATCH`**, `enable.auto.commit=false`: Spring коммитит оффсеты после того,
  как листенер обработал все записи одного `poll()`. Исключение из листенера → `DefaultErrorHandler`
  (10 попыток без паузы, затем лог и пропуск с коммитом). Retry/DLT настраиваются на Day 3.
- `ErrorHandlingDeserializer` поверх `JsonDeserializer` — защита от poison pill: битый JSON не
  зацикливает партицию на ошибке десериализации.

### Сериализация: без type headers + тип по умолчанию

У DTO в сервисах разные пакеты (`com.myapp.transfer_service…` / `com.myapp.account_service…`). По
умолчанию `JsonSerializer` кладёт FQCN класса в заголовок `__TypeId__`, и консьюмер падает, не найдя
класс. Решение: продьюсер `spring.json.add.type.headers=false`, консьюмер
`spring.json.use.type.headers=false` + `spring.json.value.default.type=<свой DTO>`.

- **Почему не type mapping:** в топике один тип события; контракт — имя топика + JSON-схема, а не
  Java-имена. Mapping понадобится, если в один топик пойдут несколько типов.
- **Почему не общий модуль с DTO:** это shared kernel — сервисы связываются версией артефакта и
  синхронными релизами. Каждый сервис владеет своей моделью события и читает толерантно.

## Стек

- Java 21, Spring Boot 3.5 (Web, Data JPA, Validation, **Spring for Apache Kafka**, AMQP)
- PostgreSQL 16
- Apache Kafka 4.4 (KRaft, один узел) — путь `TransferRequested`
- RabbitMQ 4 (topic exchange `transfer.exchange`) — путь `TransferResult` (до W3 Day 2)
- Docker (multi-stage) + docker-compose

## Карта портов

| Компонент | Внутри сети | На хосте |
|---|---|---|
| account-service | `account-service:8081` | `localhost:8081` |
| transfer-service | `transfer-service:8082` | `localhost:8082` |
| account-db (Postgres) | `account-db:5432` | `localhost:5435` |
| transfer-db (Postgres) | `transfer-db:5432` | `localhost:5436` |
| kafka | `kafka:9092` | `localhost:29092` |
| kafka-ui | `kafka-ui:8080` | `localhost:8090` |
| rabbitmq (AMQP) | `rabbitmq:5672` | `localhost:5672` |
| rabbitmq (Management UI) | `rabbitmq:15672` | `localhost:15672` |

Внутри docker-сети сервисы ходят друг к другу и к БД **по именам сервисов**, не по `localhost`.

## Конфигурация (env)

Compose читает корневой `.env` и подставляет значения в `docker-compose.yaml`. Шаблон —
[.env.example](.env.example):

```bash
cp .env.example .env
```

Креды БД и RabbitMQ приходят в приложения **через env** (`DB_USER`/`DB_PASSWORD`,
`RABBITMQ_USER`/`RABBITMQ_PASSWORD`), не зашиты в образ. Kafka-переменные: теги образов
(`KAFKA_IMAGE_TAG`, `KAFKA_UI_IMAGE_TAG`), `KAFKA_CLUSTER_ID` (генерируется один раз,
см. комментарий в шаблоне), порты `KAFKA_EXTERNAL_PORT` / `KAFKA_UI_PORT`.

Профиль в контейнерах переключается через `SPRING_PROFILES_ACTIVE=docker` (задаётся в compose).

## Запуск всего стека (одна команда)

```bash
docker compose up --build
```

Поднимутся `account-db`, `transfer-db`, `kafka`, `kafka-ui`, `rabbitmq`, `account-service`,
`transfer-service`. Приложения стартуют **после** того, как их БД, `kafka` и `rabbitmq` прошли
healthcheck (`depends_on: service_healthy`). Таблицы создаются на первом старте (`ddl-auto=update`),
топик `transfer.requested.v1` — на старте transfer-service.

Остановить / снести с данными:

```bash
docker compose down            # остановить, данные БД в volume сохранятся (топики Kafka — нет)
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

## Скрипты

Всё, что прогоняется руками, лежит в [scripts/](scripts/) (bash; на Windows — Git Bash):

| Скрипт | Назначение |
|---|---|
| `scripts/acceptance-w3d1.sh [scenario…]` | приёмка W3 Day 1 с автоматическими проверками: `up happy message durability duplicate replay` (+ опциональный `failed-replay`) |
| `scripts/kafka.sh <cmd>` | отладка Kafka: `topics`, `describe`, `consume`, `group`, `reset-earliest`, `produce` |
| `scripts/lib.sh` | общие хелперы (подключается через `source`) |

## Прогон сценариев

Шаги и результаты — в [TESTING.md](TESTING.md).

## Известный долг (норма на сейчас)

- **Dual write: publish не в одной транзакции с БД.** `Transfer=PENDING` коммитится, затем
  `kafkaTemplate.send()`; если отправка не удалась (или сервис упал между ними) — перевод навсегда
  `PENDING`. В коде помечено `// known gap: dual write, see outbox`. Лечится **outbox** (W3 Day 4–5).
- **⚠️ Потеря result-события → `PENDING` навсегда.** Если `TransferResult` потеряется, или
  account-service упадёт после коммита денег, но до публикации, или transfer-service упадёт
  после приёма, но до `markCompleted` — перевод останется `PENDING`. Лечится outbox +
  идемпотентным консьюмером + reconciliation.
- **⚠️ Replay переисполняет бизнес-отказы.** Маркер `ProcessedTransfer` пишется в той же транзакции,
  что и движение денег, и откатывается вместе с `InsufficientFunds`/`AccountNotFound`. При повторной
  доставке (replay, rebalance) такой перевод исполняется заново; если денег стало достаточно — деньги
  уйдут, а в transfer-service перевод останется `FAILED` (гвард не пускает из терминального статуса).
  Воспроизведение: `scripts/acceptance-w3d1.sh failed-replay`. Решение — фиксировать исход (включая
  отказ) как обработанный — задача W3 Day 3 (классификация ошибок).
- **Retry/DLT нет (W3 Day 3).** Инфраструктурные ошибки пробрасываются из листенера и попадают в
  дефолтный `DefaultErrorHandler` (10 быстрых попыток, затем пропуск). W2 Day 4 (retry/DLQ на
  RabbitMQ) в репозиторий не попал — заменяется Kafka-версией на Day 3.
- **Старая очередь `account.transfer.requested.queue`** остаётся в RabbitMQ (volume
  `rabbitmq_data`), хотя приложение её больше не объявляет. Удалить в Management UI или `down -v`.
- **Компенсаций/саг нет.** В рамках одной БД account-service они не нужны (перевод атомарен одной
  локальной транзакцией); понадобятся, когда счета разъедутся по разным БД.

## Локальная разработка без контейнеров

Профиль `local` (БД и брокеры из compose, сами приложения через `mvnw`; Kafka — `localhost:29092`) —
см. README модулей: [account-service](account-service/README.md),
[transfer-service](transfer_service/README.md).
