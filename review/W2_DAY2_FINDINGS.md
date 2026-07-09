# W2 · Day 2 — Findings (ревью #2, событийный перевод)

> Повторное ревью после правок. Проверено `mvnw -o clean compile` для **обоих** сервисов — оба **BUILD SUCCESS**.

## 1. Резюме

С прошлого раза починили главное: оба сервиса **компилируются** (сигнатура `transfer(BigDecimal)`, импорты exception в listener), и появился обратный путь — `AccountTransferResultListener` → `updateTransferStatus` → `markCompleted/markFailed`. Код замкнутого потока теперь есть. Но **на рантайме поток всё ещё не закроется**: result-очередь нигде не объявлена и не привязана к exchange, а listener слушает не очередь, а строку-роутинг-кей. Результат из account-service будет отброшен → перевод остаётся `PENDING`. Ядро дня (атомарное движение денег) — по-прежнему правильно. README всё ещё описывает синхронный поток Недели 1.

## 2. Таблица находок

| # | Приоритет | Где | Проблема | Почему это плохо | Направление фикса |
|---|-----------|-----|----------|------------------|-------------------|
| 1 | 🔴 | `transfer_service/.../RabbitTransferConfig` + `AccountTransferResultListener` (стр. 25) | **Result-канал не связан.** account-service шлёт на `transfer.exchange` / rk `transfer.result`. В transfer-service нет ни `Queue`-бина для результата, ни `Binding` на `transfer.result`. Listener же объявлен `@RabbitListener(queues = TRANSFER_RESULT_ROUTING_KEY)` — т.е. **routing key используется как имя очереди**. | Очередь `transfer.result` как бин не объявлена → `RabbitAdmin` её не создаёт; `@RabbitListener` сам произвольную очередь не декларирует (при дефолтном `missing-queues-fatal=true` контейнер вообще не поднимет консьюмера). Даже была бы — без binding'а topic-exchange отбрасывает сообщение. Итог: терминальный статус не выставляется, перевод вечно `PENDING`. Обратный путь есть в коде, но не работает. | Развести в `RabbitNames` понятия queue и routing key. Объявить `transfer.result.queue` (durable) + `Binding` к `transfer.exchange` с rk `transfer.result`. `@RabbitListener(queues = TRANSFER_RESULT_QUEUE)` — по имени **очереди**. |
| 2 | 🟡 | `TransferRequestedListener` (стр. 26-44), `AccountTransferResultListener` (стр. 24) | `@Transactional` на listener-методе **+ catch внутри** той же транзакции (account-side). Вложенный `@Transactional transfer()` при бизнес-ошибке метит tx `rollback-only`; catch «глотает»; на commit → `UnexpectedRollbackException`. Publish результата не скоординирован с БД-tx. | Ack-семантика не осознана: «обработанная» ошибка всё равно валит транзакцию; на Day 4 (requeue/DLQ) — poison-loop + дубли result-событий. | Граница транзакции — только на доменном `transfer()`; listener ловит **вне** tx; result-publish — вне БД-tx. |
| 3 | 🟡 | `TransferController.makeTransfer` (стр. 41-42) | POST возвращает `ResponseEntity.ok()` — **200**, не **202 Accepted**. | Контракт притворяется синхронным. Смысл дня — «принял, обработаю асинхронно» = 202 + `transferId` + `PENDING`. Тело уже правильное. | `ResponseEntity.accepted().body(...)`. |
| 4 | 🟡 | `TransferResultMessage` (оба сервиса), `updateTransferStatus`, `AccountTransferResultPublisher` | Результат — `boolean success`, а не факты `TransferCompleted`/`TransferFailed`. Нет `failureReason` → `updateTransferStatus` хардкодит `"Transfer failed"`. Нет `eventId`/`occurredAt` (в отличие от `TransferRequested`). Плюс `transferId` — `UUID` в account-service, но `String` в transfer-service. | `boolean` — флаг, не событие-факт. Реальную причину отказа клиент не увидит. Без `eventId` на результате нет стабильного ключа дедупа результата (асимметрия с requested). Разнотипный `transferId` работает лишь потому, что JSON строковый; `UUID.fromString` на кривом входе бросит и (без catch в result-listener) уйдёт в redelivery. | Два факт-события (или enum-статус) + `failureReason` + `eventId`/`occurredAt`; единый тип `transferId` (UUID) в обоих сервисах. |
| 5 | 🟡 | `transfer_service/.../RabbitTransferConfig` (стр. 20-31) | transfer-service объявляет **чужую** consumer-очередь `account.transfer.requested.queue` + binding (владелец — account-service, который её тоже объявляет), а **свою** result-очередь не объявляет. | Размытое владение инфраструктурой + прямой корень находки #1 (нет result-queue/binding). | Убрать декларацию чужой очереди; добавить свою result-queue+binding. |
| 6 | 🟢 | `AccountTransferResultListener` (стр. 20) | Инжектится `RabbitTemplate`, но нигде не используется. | Мёртвая зависимость, сбивает при чтении. | Удалить поле. |
| 7 | 🟢 | `transfer_service/.../dto/messaging/PingMessage.java` | Demo-пинг из Day 1 не удалён (в файле: «Удалить в Day 2»). | Мёртвый демо-код «прирастает». | Удалить record и остатки ping-инфры. |
| 8 | 🟢 | `TransferServiceImpl` (стр. 66-150) | Большой закомментированный синхронный блок `createTransfer`. | Мёртвый код, шум; история — в git. | Удалить. |
| 9 | 🟡 | `README.md`, `transfer_service/README.md` | README всё ещё описывает **синхронный** поток Недели 1 (`RestClient`, `withdraw→deposit`, «200 OK», статус «День 5»). Событийного потока, `202`+polling, новых рисков нет. | Прямое требование дня: описать новый поток + known issues (`lost result → PENDING навсегда`; нет идемпотентности событий; publish-после-commit). | Переписать раздел «поток» под события; добавить блок known issues. |
| 10 | 🟢 | `TransferServiceImpl.updateTransferStatus` / `TransferService` | Контракт метода — `String transferId` + `UUID.fromString` внутри. | Лишняя конвертация и точка отказа; тип должен быть `UUID` сквозняком. | `UUID transferId` в сигнатуре, парсинг — на границе десериализации. |
| 11 | 🟢 | `TransferServiceImpl` (`/transfers/check/{id}` + `AccountClient`) | Синхронный `AccountClient` из потока перевода убран (хорошо), но остался диагностический эндпоинт. Судьба клиента для Day 2 явно не помечена. | Не ошибка, но «на всякий» без пометки путает: часть потока это или нет. | Пометить эндпоинт diagnostic/deprecated или удалить осознанно. |

## 3. Что сделано хорошо

- **Компиляция почин­ена.** Сигнатура `transfer(...BigDecimal)` в интерфейсе и impl совпадает; импорты `AccountNotFoundException`/`InsufficientFundsException` на месте. Оба модуля собираются.
- **Обратный путь появился в коде.** `AccountTransferResultListener` → `TransferService.updateTransferStatus` → `markCompleted`/`markFailed`. Осталось починить инфраструктуру канала (#1), логика доведения статуса уже есть.
- **Атомарность денег — в точку.** `AccountServiceImpl.transfer()` — один `@Transactional`, оба счёта в одной БД, `withdraw` строго перед `deposit`, `BigDecimal(19,4)` + `compareTo`, `@Version`. Мину «списал/не зачислил» убрали корректно.
- **События — record'ы, не JPA-entity**; `Jackson2JsonMessageConverter`; JSON. `transferId` протащен сквозь весь поток; `eventId`+`occurredAt` на `TransferRequested` — готовый стабильный ключ дедупа.
- **Имена requested-канала — константы** (`RabbitNames`), консистентны publisher↔listener. Listener'ы тонкие (зовут доменный сервис), messaging-пакет отделён от бизнес-логики. `createPending` сохраняет `PENDING` до publish, отдельной короткой транзакцией.

## 4. Контрольные вопросы (ответы)

**Что такое eventual consistency на этом примере.**
Между сохранением `Transfer=PENDING` и приходом результата система рассогласована: деньги в account-service могли уже сдвинуться, а `Transfer` ещё `PENDING`. Статус «догоняет» реальность асинхронно, через result-событие. До терминального статуса наблюдатель видит промежуточное состояние — это by design.

**Почему co-located счета позволяют не распределять транзакцию.**
Оба счёта лежат в одной БД account-service. Списание и зачисление — одна локальная ACID-транзакция (`@Transactional`): либо оба, либо ни одного. 2PC/сага/компенсация не нужны. Распределённый край остаётся только между transfer-service и account-service (разные БД) — и он закрывается событием+статусом, а не общей транзакцией.

**При каком условии 202 стал честным.**
Когда фоновый обработчик реально доводит `PENDING` до терминального статуса. Код обработчика теперь есть, но канал результата не связан (#1), поэтому сейчас 202 всё ещё был бы нечестен — а контроллер к тому же отдаёт 200. Честным станет после починки result-queue/binding.

**Где новый распределённый край и чем лечится.**
Доставка result-события и обновление статуса в БД transfer-service. Риски: потеря события; падение account-service после commit денег, но до publish; падение transfer-service после приёма, но до `markCompleted`. Итог — вечный `PENDING`. Лечится outbox (Week 3) + идемпотентный консьюмер (Week 3) + reconciliation незакрытых `PENDING`. На Day 2 — фиксируется как known issue, не решается.

## 5. Чек-лист готовности к Day 3 (идемпотентность)

- [x] **Стабильный ключ дедупа входящего** — `transferId` + `eventId` на `TransferRequested` есть.
- [ ] **Ключ на result-стороне** — у `TransferResultMessage` нет `eventId`/`messageId`; для симметричного дедупа добавить (#4).
- [ ] **Осознанная ack-семантика** — пока не осознана: `@Transactional`+catch на listener даёт неявный rollback/redelivery (#2). Развести границу транзакции и ack до навешивания дедупа.
- [x] **Атомарное движение денег как фундамент** — `transfer()` один `@Transactional`; проверку «этот `transferId` уже обработан?» можно положить в ту же транзакцию.
- [ ] **Предусловие**: сначала реально замкнуть поток (result-queue/binding, #1). Пока результат не доходит и перевод не выходит из `PENDING`, дедуплицировать на терминальном шаге нечего.

## 6. Не проверено / нужны данные

- Компилировал оба сервиса (`mvnw -o clean compile` → BUILD SUCCESS ×2). Рантайм (реальная доставка через RabbitMQ) не запускал — вывод про необъявленную result-очередь сделан по конфигу (`RabbitTransferConfig` без result-`Queue`/`Binding`) и по `@RabbitListener(queues = <routing key>)`. Подтверждается прогоном `docker compose up` + отправкой одного перевода: статус останется `PENDING`.
- `application*.yaml` (в частности `spring.rabbitmq.listener.simple.missing-queues-fatal` и `acknowledge-mode`) детально не разбирал — поведение контейнера при отсутствующей очереди зависит от этих настроек.
