# DAY 3 — Findings (transfer-service: граница сервиса + межсервисный вызов)

> Обновлено 2026-06-27, финальный проход. Компилируется (BUILD SUCCESS).
> **Вердикт: день можно закрывать.** Всё ядро дня закрыто; остался один ручной шаг — вписать
> реальные тела ответов в README после живого прогона.

## 1. Резюме
Граница сервиса выдержана, межсервисный вызов изолирован в клиенте, исходы вызова различаются
и доходят до HTTP-ответа осмысленными кодами. Persistence-практики Дней 1–2 перенесены. Инфра
(единый docker-compose + datasource) поднимается. Критичных и важных замечаний не осталось.

## 2. Закрыто ✅

| Было (приоритет) | Где | Стало |
|---|---|---|
| 🔴 datasource не настроен | `application-local.yaml` | подключён `localhost:5436/transfer_db` (и account на :5435) |
| 🔴 таймауты не применены | `client/AccountClient` | `SimpleClientHttpRequestFactory` с connect+read из `AccountClientProperties` |
| 🔴 исходы вызова не различались | `client/AccountClient` + `client/exception/*` | 404→`AccountClientNotFoundException`, 422→`...InsufficientFunds`, 5xx→`AccountClientException`, недоступность/таймаут→`...ResourceAccessException` |
| 🔴 различение не доходило до HTTP | `GlobalExceptionHandler` | 404→404, 422→422, недоступен→503, 5xx апстрима→502 |
| 🔴 нет `amount` | `model/Transfer` | `BigDecimal`, `@Column(precision=19, scale=4)` |
| 🟡 `Status` как ORDINAL | `model/Transfer` | `@Enumerated(EnumType.STRING)` |
| 🟡 `createdAt` = null | `model/Transfer` | `@CreationTimestamp` + `@Column(updatable=false)` |
| 🟡 check-эндпоинт лез в свою БД по чужому id | `service/TransferServiceImpl` | упрощён, возвращает `ExternalAccountResponse`; лишний репозиторий убран |
| 🟡 docker-compose не отражал День 3 | `java-prep/docker-compose.yaml` | единый compose на обе БД, починены порты/healthcheck, named volumes, сеть |
| 🟡 нет README | `README.md` | есть: запуск + таблица исходов вызова |
| 🟢 дубли/мёртвый код | — | удалены `AccountServiceDto`, пустой `model/AccountClient`, дублирующая пара `ExternalService*` |
| 🟢 `.env` не в `.gitignore` | `.gitignore` | добавлен |

## 3. Что хорошо
- **Точка интеграции изолирована**: бизнес-код зовёт `accountClient.getAccountById(...)`; завтра менять на события — одно место.
- **Исходы вызова разнесены семантически** и транслируются в HTTP с разделением «наша вина / вина апстрима / транзиентно» (404/422/502/503) — готовая опора для оркестратора Дня 4.
- **Слои DTO честные**: `ExternalAccountResponse` (контракт соседа) ≠ `TransferResponseDto` (ответ перевода, заготовка под День 4); Entity наружу не течёт.
- **Entity зрелая**: `amount` NUMERIC(19,4), enum STRING, авто-`createdAt`, `@Getter` (не `@Data`), без межсервисных FK.
- **Инфра согласована**: один compose, фиксированная версия Postgres, раздельные БД и volume.

## 4. Остаётся (не блокирует закрытие дня)
- [ ] **Ручной шаг:** прогнать вживую три сценария (успех / account-service выключен / счёт не найден) и вписать реальные тела ответов в таблицу README. Сейчас там плейсхолдеры `TODO` (Docker Desktop при ревью был выключен).
- 🟢 **На День 4, не сейчас:** прочие 4xx от account-service (например 400) пойдут через дефолтный обработчик `RestClient` → станут 500. Добавить catch-all на остальные 4xx, когда появится оркестрация.
- 🟢 `TransferResponseDto` и `TransferNotFoundException` — заготовки под День 4, пока не используются (осознанно).

## 5. Контрольные вопросы (для самопроверки перед собесом)
1. Почему `fromAccountId/toAccountId` — `UUID`, а не `@ManyToOne` на сущность account? Что ломает межсервисный FK?
2. Почему недоступность апстрима = 503, а его 5xx = 502, а «нет счёта» = 404? Что каждый код говорит вызывающему?
3. Почему таймаут важнее ретраев на старте, и чем ретрай опасен для `withdraw` без идемпотентности?

## 6. Готовность к Дню 4 (оркестрация withdraw→deposit)
- [x] БД подключена; приложение конфигурируется на `transfer_db`.
- [x] Таймауты (connect + read) применены к клиенту.
- [x] Исходы вызова различаются и маппятся в HTTP-коды.
- [x] `Transfer.amount` (NUMERIC 19,4); `Status` STRING; `createdAt` авто.
- [x] docker-compose поднимает обе БД; мёртвый код подчищен.
- [ ] Добавить методы клиента `withdraw`/`deposit` (DTO `WithdrawRequestDto`/`DepositRequestDto` уже есть) — определить вход/выход.
- [ ] Вписать реальные тела ответов в README.

> Идемпотентность/ретраи — Неделя 2. Не добавляй retry на `withdraw`, пока её нет: повтор не-идемпотентной операции = двойное списание.
