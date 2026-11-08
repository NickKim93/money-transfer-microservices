#!/usr/bin/env bash
# Приёмка W3 Day 1: путь transfer.requested на Kafka.
# Каждый сценарий сам проверяет результат и падает с exit 1 при расхождении.
#
#   scripts/acceptance-w3d1.sh               — все сценарии по порядку (up → replay)
#   scripts/acceptance-w3d1.sh <scenario>... — выбранные сценарии
#
# Сценарии:
#   up          docker compose up --build, ожидание сервисов, засев данных
#   happy       POST /transfers → 202 PENDING → COMPLETED, балансы сдвинулись, сумма не изменилась
#   message     сообщение видно в топике: партиция, оффсет, ключ
#   durability  account-service остановлен → 3 перевода → старт → все COMPLETED
#   duplicate   одно и то же TransferRequested дважды → деньги двигаются один раз
#   replay      сброс оффсетов группы на начало → всё переигрывается, ни одного двойного списания
#   failed-replay  (не входит в all) демонстрация known gap: FAILED-перевод исполняется при replay
#
# Требования: заполненный .env (cp .env.example .env), запущенный Docker, Git Bash / bash.

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

AMOUNT=10

scenario_up() {
    step "up: поднять стек"
    [[ -f .env ]] || fail ".env не найден: cp .env.example .env"
    set -a
    source .env
    set +a
    docker compose up --build -d --wait
    docker compose ps
    wait_services
    step "up: засеять данные (идемпотентно)"
    docker exec -i account-db psql -q -U "$POSTGRES_USER" -d "$ACCOUNT_DB_NAME" \
        <seed/account_seed.sql
    docker exec -i transfer-db psql -q -U "$POSTGRES_USER" -d "$TRANSFER_DB_NAME" \
        <seed/transfer_seed.sql
    pass "seed applied"
}

scenario_happy() {
    step "happy: POST /transfers → COMPLETED"
    local a0 b0 s0 id
    a0="$(balance "$ALICE")"
    b0="$(balance "$BOB")"
    s0="$(balance_sum)"
    info "before: alice=$a0 bob=$b0 sum=$s0"

    id="$(create_transfer "$ALICE" "$BOB" "$AMOUNT")"
    pass "202 + PENDING, transferId=$id"
    wait_status "$id" COMPLETED

    assert_num_eq "$(balance "$ALICE")" "$(num_add "$a0" "-$AMOUNT")" "alice debited"
    assert_num_eq "$(balance "$BOB")" "$(num_add "$b0" "$AMOUNT")" "bob credited"
    assert_num_eq "$(balance_sum)" "$s0" "balance_sum unchanged"
    LAST_TRANSFER_ID="$id"
}

scenario_message() {
    step "message: сообщение в топике $TOPIC"
    local id="${LAST_TRANSFER_ID:-}" line
    if [[ -z "$id" ]]; then
        id="$(create_transfer "$ALICE" "$BOB" "$AMOUNT")"
        wait_status "$id" COMPLETED
    fi
    line="$(scripts/kafka.sh consume 2>/dev/null | grep "$id" || true)"
    [[ -n "$line" ]] || fail "сообщение с transferId=$id не найдено в топике"
    info "$line"
    [[ "$line" == *$'\t'"$ALICE"$'\t'* ]] || fail "ключ сообщения != fromAccountId ($ALICE)"
    pass "ключ = fromAccountId, партиция и оффсет — в строке выше"
    step "message: партиции топика"
    scripts/kafka.sh describe
}

scenario_durability() {
    step "durability: консьюмер остановлен, сообщения ждут в логе"
    local a0 s0 ids=() id lag
    a0="$(balance "$ALICE")"
    s0="$(balance_sum)"
    stop_account_service

    for _ in 1 2 3; do ids+=("$(create_transfer "$ALICE" "$BOB" "$AMOUNT")"); done
    pass "3 переводы созданы: ${ids[*]}"
    sleep 3
    for id in "${ids[@]}"; do assert_eq "$(transfer_status "$id")" PENDING "$id пока PENDING"; done
    lag="$(group_lag)"
    assert_eq "$lag" 3 "lag группы $GROUP при остановленном сервисе"

    start_account_service
    for id in "${ids[@]}"; do wait_status "$id" COMPLETED; done
    wait_lag_zero
    assert_num_eq "$(balance "$ALICE")" "$(num_add "$a0" "-$((AMOUNT * 3))")" "alice debited ×3"
    assert_num_eq "$(balance_sum)" "$s0" "balance_sum unchanged"
}

scenario_duplicate() {
    step "duplicate: одно TransferRequested дважды"
    local a0 s0 id record since dups
    a0="$(balance "$ALICE")"
    s0="$(balance_sum)"
    stop_account_service

    id="$(create_transfer "$ALICE" "$BOB" "$AMOUNT")"
    pass "transfer $id создан, сообщение лежит в топике"
    record="$(topic_records | grep "$id" | head -n 1)"
    [[ -n "$record" ]] || fail "не нашёл запись transferId=$id в топике"
    printf '%s\n' "$record" | produce_records
    pass "та же запись (тот же key + value) отправлена повторно"

    since="$(now_utc)"
    start_account_service
    wait_status "$id" COMPLETED
    wait_lag_zero

    assert_num_eq "$(balance "$ALICE")" "$(num_add "$a0" "-$AMOUNT")" "alice debited ровно один раз"
    assert_num_eq "$(balance_sum)" "$s0" "balance_sum unchanged"
    dups="$(docker compose logs account-service --since "$since" 2>/dev/null |
        grep "$id" | grep -c "already committed" || true)"
    assert_eq "$dups" 1 "дубль распознан дедупом (лог 'already committed')"
}

scenario_replay() {
    step "replay: сброс оффсетов группы $GROUP на начало"
    local a0 b0 s0 since dups
    a0="$(balance "$ALICE")"
    b0="$(balance "$BOB")"
    s0="$(balance_sum)"
    stop_account_service # reset работает только для неактивной группы

    scripts/kafka.sh reset-earliest
    info "lag после reset: $(group_lag)"

    since="$(now_utc)"
    start_account_service
    wait_lag_zero 120
    sleep 2

    assert_num_eq "$(balance "$ALICE")" "$a0" "alice не изменилась"
    assert_num_eq "$(balance "$BOB")" "$b0" "bob не изменился"
    assert_num_eq "$(balance_sum)" "$s0" "balance_sum unchanged"
    dups="$(docker compose logs account-service --since "$since" 2>/dev/null |
        grep -c "already committed" || true)"
    pass "переиграно и отсечено дедупом: $dups сообщений"
}

# Known gap (README → Known issues): маркер ProcessedTransfer откатывается вместе с бизнес-отказом,
# поэтому при replay ранее FAILED-перевод исполняется заново, если денег стало достаточно.
scenario_failed_replay() {
    step "failed-replay: демонстрация known gap (меняет балансы!)"
    local b0 id
    b0="$(balance "$BOB")"
    id="$(create_transfer "$BOB" "$ALICE" "$(num_add "$b0" 1)")"
    wait_status "$id" FAILED
    deposit "$BOB" 1
    info "bob пополнен на 1 — теперь денег на перевод $id хватает"

    stop_account_service
    scripts/kafka.sh reset-earliest >/dev/null
    start_account_service
    wait_lag_zero 120
    sleep 2

    info "transfer $id status: $(transfer_status "$id") (ожидаем FAILED — гвард F4)"
    info "bob balance: $(balance "$BOB") (было $(num_add "$b0" 1) до replay)"
    if awk -v a="$(balance "$BOB")" 'BEGIN { exit !(a + 0 == 0) }'; then
        printf '    %s⚠ gap подтверждён: деньги ушли, а перевод в transfer-service FAILED%s\n' \
            "$C_RED" "$C_RESET"
    else
        pass "gap не воспроизвёлся"
    fi
}

ALL=(up happy message durability duplicate replay)
scenarios=("$@")
[[ ${#scenarios[@]} -gt 0 ]] || scenarios=("${ALL[@]}")

for s in "${scenarios[@]}"; do
    fn="scenario_${s//-/_}"
    declare -F "$fn" >/dev/null || fail "неизвестный сценарий: $s (см. шапку скрипта)"
    "$fn"
done

step "готово: ${scenarios[*]}"
