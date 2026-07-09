# DAY4 FINDINGS — transfer-service (оркестрация перевода)

Режим: ревью + обучение. Код не исправлялся. Ниже — *где / почему / направление фикса*.

---

## 1. Резюме

Уровень: **черновик, не сдаётся**. Архитектура транзакционных границ выбрана правильно (короткие `@Transactional` по краям в `TransferStatusService`, сеть — вне транзакции, разнесение оркестрации и работы со статусом по двум бинам — это хорошо). НО фактически **перевод не работает ни в одном сценарии**: `AccountClient.withdraw/deposit` не отправляют тело запроса, поэтому `account-service` отвечает `400`, а это исключение нигде не ловится → каждый перевод падает в `500` и навсегда зависает в `PENDING`. Плюс README не обновлён и в нём нет записи про мину Дня 6.

Главное к фиксу (по порядку): (1) тело HTTP-запросов withdraw/deposit; (2) согласовать иерархию/обработку исключений клиента (400, Unavailable, ExternalServiceException, TransferInvalid сейчас все → 500); (3) README (эндпоинты + мина).

---

## 2. Таблица находок

| Приоритет | Где | Проблема | Почему это проблема | Направление фикса |
|---|---|---|---|---|
| 🔴 | `client/AccountClient.java` `withdraw()` (стр. 77‑105) и `deposit()` (стр. 117‑145) | POST-вызовы не передают тело: цепочка `post().uri(...).retrieve()` без `.body(...)`. Параметр `amount` и DTO `ExternalWithdrawRequestDto`/`ExternalDepositRequestDto` не используются вообще. | `account-service` объявляет `@RequestBody @Valid WithdrawRequestDto` (см. `account-service/.../AccountController.java:44‑59`). Пустое тело → `HttpMessageNotReadableException` → `400`. То есть **сумма перевода вообще не передаётся**, деньги не двигаются, а вызов всегда падает. Это срывает весь deliverable Дня 4. | Прочитать как RestClient отправляет payload: между `.uri(...)` и `.retrieve()` добавить `.body(new ExternalWithdrawRequestDto(amount))` (и аналогично для deposit). Проверить, что поле в DTO совпадает с тем, что ждёт `account-service`. |
| 🔴 | `client/exception/AccountClientIllegalArgumentException.java` + `service/TransferServiceImpl.java` `createTransfer()` catch-цепочка (стр. 71‑105) | На `400` клиент кидает `AccountClientIllegalArgumentException`, который наследует **`RuntimeException`, а не `AccountClientException`**. В `createTransfer` его не ловит ни один `catch`. | Исключение пролетает мимо всех catch → `Transfer` остаётся в `PENDING` навсегда (никто не вызвал `markFailed`), `GlobalExceptionHandler` его тоже не знает → клиент получает `500`. В связке с находкой выше это и есть реальное поведение каждого перевода: осиротевший `PENDING` + `500`. | Решить, частный это случай `AccountClientException` (тогда наследовать от него — и он попадёт в общий catch) или отдельный класс (тогда добавить ему `catch` в оркестрации и обработчик в `GlobalExceptionHandler`). Заодно подумать: при `400` от account-service надо ли `markFailed`. |
| 🔴 | `service/TransferServiceImpl.java:86‑95` ↔ `exception/GlobalExceptionHandler.java` | В catch `AccountClientResourceAccessException` сервис бросает наружу `AccountClientUnavailableException`, но `GlobalExceptionHandler` зарегистрирован на `AccountClientResourceAccessException`, а **не** на `Unavailable`. | Нет `@ExceptionHandler(AccountClientUnavailableException.class)` → недоступность account-service возвращает `500` вместо `503`. Контракт ошибок врёт. | Либо обрабатывать в handler именно тот тип, что реально летит наружу, либо не подменять тип в сервисе. Выбрать один класс под «сервис недоступен» и провести его сквозь оба слоя. |
| 🔴 | `README.md` (весь) | README застрял на Дне 3: «Полной оркестрации ещё НЕТ», нет `POST /transfers`, нет `GET /transfers/{id}`, **нет записи про мину Дня 6** (withdraw прошёл / deposit упал = деньги потеряны, компенсации нет). | Рубрика требует честной фиксации мины как known issue. Сейчас её нет — со стороны выглядит так, будто проблему не осознали. Также отсутствие новых эндпоинтов в README — пробел гигиены. | Обновить статус до Дня 4, описать оба эндпоинта, добавить раздел «Known issues»: *money loss possible when withdraw succeeds and deposit fails; no compensation/idempotency yet; addressed Day 6 / Week 3*. |
| 🟡 | `exception/ExternalServiceException.java` + `GlobalExceptionHandler` | `ExternalServiceException` бросается в catch `AccountClientException` (`TransferServiceImpl:104`), но обработчика для него нет. | → `500` вместо осмысленного `502`. При этом для самого `AccountClientException` обработчик (502) есть — то есть подмена типа только ухудшает: был бы 502, стал 500. | Либо не оборачивать (отдать `AccountClientException` наружу как есть), либо добавить handler на `ExternalServiceException`. |
| 🟡 | `exception/GlobalExceptionHandler` (нет записи) + `TransferServiceImpl:58‑60` | `TransferInvalidException` (проверка «нельзя переводить самому себе») не имеет обработчика. | → `500` вместо `400/422`. Валидный по смыслу клиентский запрос-ошибка выглядит как падение сервера. | Добавить `@ExceptionHandler(TransferInvalidException.class)` → `400` (или `422`). Заодно решить: проверку `from != to` оставить в сервисе или вынести в bean-валидацию на DTO. |
| 🟡 | `dto/response/TransferResponseDto.java:9‑18` | Поле называется `accountId`, но в `fromEntity` в него кладётся `transfer.getId()` — это **id перевода**, не счёта. | Публичный JSON-ключ вводит в заблуждение, а переименовать его позже (когда на него завяжутся клиенты) дорого. Плюс в DTO вообще нет `fromAccountId/toAccountId`/`amount`, хотя это ответ `GET /transfers/{id}`. | Переименовать в `transferId`. Подумать, какой набор полей реально нужен потребителю статуса перевода (сумма, обе стороны, статус, причина). |
| 🟡 | `controller/TransferController.java:40‑43` | `POST /transfers` возвращает `200 OK`. | Создаётся ресурс — конвенция REST это `201 Created` (+ опц. `Location`). Не критично функционально, но это собесный момент. | `ResponseEntity.status(HttpStatus.CREATED)` или `.created(uri)`. |
| 🟡 | `service/TransferServiceImpl.java` `findById()` + `controller` `getClientAccount` / `GET /transfers/check/{id}` | Диагностический эндпоинт Дня 3 («проверка связи») остался в продакшн-контроллере перевода. Метод `findById` возвращает `ExternalAccountResponse` — имя generic и врёт (это не поиск перевода). | Мусорный публичный эндпоинт под `/transfers/...`, путается с реальным `GET /transfers/{id}`; имя метода не отражает домен. | Решить судьбу диагностики: убрать, либо вынести в отдельный `diagnostics`/`health` контроллер. Метод назвать `getAccountById`/`checkAccount`. |
| 🟢 | `service/TransferServiceImpl.java` (импорты + локальные переменные) | Куча неиспользуемого: `javax.security.auth.login.AccountNotFoundException`, `HttpStatusCode`, `ErrorResponseException`, `Status`, `Transactional`, оба `External*RequestDto`, `ExternalAccountResponse`; локальные `completedTransfer`/`failedTransfer` частью не используются. | Мёртвый код, шум, особенно подозрительный импорт `AccountNotFoundException` из javax.security — явный «случайный» автоимпорт. | Почистить импорты (IDE → Organize imports), убрать неиспользуемые переменные. |
| 🟢 | `client/AccountClient.java` deposit `onStatus(422 → InsufficientFunds)` (стр. 128‑135) | На `deposit` маппится `422 → "Not enough funds"`. | У зачисления не бывает «недостатка средств» — это копипаст из withdraw. Семантически бессмысленный маппинг. | Пересмотреть, какие статусы реально возможны на deposit; убрать неприменимое. |
| 🟢 | `service/TransferStatusService.java` `markCompleted/markFailed` | Переходы статусов не защищены: можно пометить COMPLETED уже завершённый/проваленный перевод. | Сейчас в линейном потоке «оживления» не случится, но инвариант «PENDING → терминальный, однонаправленно» не выражен в коде. | Для понимания: добавить проверку текущего статуса перед сменой (guard) или комментарий-инвариант. Не обязательно для Дня 4, но проговорить словами. |
| 🟢 | `dto/response/*` — `TransferCreateResponseDto.status` (String) vs `TransferResponseDto.status` (enum `Status`) | Один и тот же концепт (статус) сериализуется по-разному в двух ответах. | Несогласованность представления одного домена-понятия между эндпоинтами. | Привести к одному виду (везде enum как STRING). |
| 🟢 | Кросс-сервисный нейминг DTO | Здесь суффикс `Dto` (`TransferRequestDto`), в `account-service` — `DTO`/`RequestDTO`. | Один и тот же слой называется по-разному в двух сервисах. Рубрика просит согласованность концептов между сервисами. | Выбрать единый стиль (рубрика предлагает `*Request`/`*Response` без `Dto`-суффикса) и применить в обоих модулях. |
| 🟢 | Порядок операций — обоснование | withdraw → deposit реализован верно, но нигде (README/коммент) не объяснено *почему* именно так. | Рубрика отмечает отсутствие обоснования как пробел в понимании, а не в коде. | Одной строкой в README зафиксировать: сначала списываем у отправителя, потом зачисляем; обратный порядок при сбое 2-го шага создаёт деньги из воздуха. |

---

## 3. Что сделано хорошо (реально)

- **Транзакционные границы — в точку.** `createTransfer` (метод с сетевыми вызовами) НЕ помечен `@Transactional`; сохранение `PENDING`, `markCompleted`, `markFailed` — отдельные короткие `@Transactional` в `TransferStatusService`. БД-соединение/блокировки не держатся на время HTTP. Это главная тема дня — сделано правильно.
- **Прокси сработает.** `TransferStatusService` — отдельный бин, его `@Transactional`-методы вызываются из `TransferServiceImpl` (внешний вызов), а не self-invocation. Распространённую ловушку обошли.
- **Фиксация намерения.** `Transfer` сохраняется в `PENDING` ДО вызовов account-service — след незавершённого перевода остаётся.
- **Порядок withdraw → deposit** корректен (см. оговорку про обоснование выше).
- **Мина Дня 6 НЕ залатана криво.** Нет слепого retry deposit, нет ручного отката через БД account, нет имитации распределённой транзакции. Наивное поведение оставлено — это правильный выбор. (Осталось только честно записать его в README — см. 🔴 про README.)
- **Persistence/DTO-база здоровая:** `amount` = `BigDecimal` + `@Column(precision=19, scale=4)`; `status` = `@Enumerated(EnumType.STRING)` (не ORDINAL); наружу отдаются `record`-DTO, а не entity; на entity `@Getter/@Builder`, без `@Data`. Оркестрация дёргает доменные методы `AccountClient`, а не `RestClient` напрямую.
- **Разнесение ответственности:** оркестрация (`TransferServiceImpl`) отделена от работы со статусом/персистентностью (`TransferStatusService`), маппинг — в `fromEntity` DTO. «Божественного» сервиса нет.

---

## 4. Контрольные вопросы (ответь словами, для себя)

1. **Порядок операций.** Почему withdraw обязан идти строго до deposit? Что именно произойдёт с балансами, если поменять местами и упадёт второй шаг?
2. **Транзакционные границы.** Что плохого, если обернуть весь `createTransfer` (вместе с HTTP-вызовами) в один `@Transactional`? Назови два разных последствия — про БД-ресурсы и про то, даёт ли это вообще атомарность между двумя сервисами.
3. **Зачем PENDING первым.** Если бы запись `Transfer` создавалась только в самом конце (после deposit), что именно ты потеряешь при падении процесса между withdraw и deposit? Как `PENDING`-запись помогает Дню 6?
4. (бонус) После фикса тела запросов: сценарий «withdraw ок, deposit вернул 422/5xx» сейчас пометит перевод `FAILED`. Честно ли это слово, если деньги уже списаны? Что точнее отражало бы состояние «деньги ушли, получатель не зачислен»?

---

## 5. Чек-лист готовности к Дню 5 (docker-compose: оба сервиса + 2 Postgres)

- [ ] Сначала закрыть 🔴-блокеры: без рабочего тела withdraw/deposit перевод не пройдёт и в контейнерах.
- [ ] Dockerfile для **обоих** сервисов (multi-stage: build `mvnw` → slim JRE).
- [ ] `docker-compose` расширить с 2 БД до **2 БД + 2 приложения** (сейчас в `java-prep/docker-compose.yaml` только `account-db` + `transfer-db`).
- [ ] `base-url` клиента в контейнере должен указывать на **имя сервиса** (`http://account-service:8081/...`), а не `localhost`. Сейчас в `application.yaml` зашит `localhost:8081` — вынести в env/профиль `docker`.
- [ ] `depends_on` с `condition: service_healthy` на БД (healthcheck-и уже есть — переиспользовать).
- [ ] Прогнать сквозной сценарий **через контейнеры**: создать 2 счёта → `POST /transfers` → `GET /transfers/{id}` = `COMPLETED`, балансы изменились.
- [ ] Проверить негативы в контейнерах: нехватка средств → `FAILED`+reason+`422`; account-service выключен → `503` (а не 500 — см. находку про Unavailable).
- [ ] Секреты только через env/`.env`, не в образах; `.gitignore` покрывает оба модуля (проверить наличие отдельного `.gitignore` в `account-service`).

---

## Что показать, если нужно копнуть глубже

- `account-service/.../dto/request/WithdrawRequestDto.java` и `DepositRequestDto.java` — точное имя поля суммы (чтобы тело клиента совпало 1-в-1).
- `account-service` сервис-слой withdraw/deposit — какие коды реально возвращает (особенно есть ли отдельный код для «счёт не найден» vs «нет средств»).
- Корневой/`account-service` `.gitignore` и факт, что `.env` не закоммичен (текущий модуль — не git-репозиторий, проверить нельзя).
