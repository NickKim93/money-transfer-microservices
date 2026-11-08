#!/usr/bin/env bash
# Общие хелперы для скриптов приёмки. Подключается через `source`, сам по себе не запускается.
# Зависимости: bash, curl, docker (compose v2). jq НЕ нужен.

set -euo pipefail

# Git Bash (MSYS) переписывает аргументы вида /opt/... в C:/Program Files/Git/opt/...,
# из-за чего `docker exec kafka /opt/kafka/bin/...` ломается. Отключаем конвертацию путей.
export MSYS_NO_PATHCONV=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ACCOUNT_URL="${ACCOUNT_URL:-http://localhost:8081/api/v1}"
TRANSFER_URL="${TRANSFER_URL:-http://localhost:8082/api/v1}"

# Счета из seed/account_seed.sql
ALICE="11111111-1111-1111-1111-111111111111"
BOB="22222222-2222-2222-2222-222222222222"

KAFKA_BIN="/opt/kafka/bin"
KAFKA_BOOTSTRAP="localhost:9092" # изнутри контейнера kafka
TOPIC="transfer.requested.v1"
GROUP="account-service"

# ---------- вывод ----------

if [[ -t 1 ]]; then
    C_BLUE=$'\e[34m' C_GREEN=$'\e[32m' C_RED=$'\e[31m' C_DIM=$'\e[2m' C_RESET=$'\e[0m'
else
    C_BLUE='' C_GREEN='' C_RED='' C_DIM='' C_RESET=''
fi

step() { printf '\n%s==> %s%s\n' "$C_BLUE" "$*" "$C_RESET"; }
info() { printf '    %s\n' "$*"; }
pass() { printf '    %s✔ %s%s\n' "$C_GREEN" "$*" "$C_RESET"; }
fail() {
    printf '    %s✘ %s%s\n' "$C_RED" "$*" "$C_RESET" >&2
    exit 1
}

# ---------- проверки ----------

assert_eq() { # assert_eq <actual> <expected> <message>
    if [[ "$1" == "$2" ]]; then pass "$3 ($1)"; else fail "$3: expected '$2', got '$1'"; fi
}

assert_num_eq() { # сравнение десятичных чисел: 1000 == 1000.0000
    if awk -v a="$1" -v b="$2" 'BEGIN { exit !(a + 0 == b + 0) }'; then
        pass "$3 ($1)"
    else
        fail "$3: expected $2, got $1"
    fi
}

num_add() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.4f", a + b }'; }

# ---------- JSON без jq ----------

json_field() { # json_field <json> <field> — плоские поля: строки и числа
    local json="$1" field="$2"
    printf '%s' "$json" | sed -n -E "s/.*\"$field\":\"?([^\",}]*)\"?.*/\1/p"
}

# ---------- HTTP ----------

wait_http() { # wait_http <url> <name> [timeout_sec] — «живой» = ответил любым HTTP-кодом
    local url="$1" name="$2" timeout="${3:-120}" i code
    for ((i = 0; i < timeout; i++)); do
        code="$(curl -s -o /dev/null -w '%{http_code}' "$url" || true)"
        if [[ "$code" != "000" ]]; then
            pass "$name is up (HTTP $code)"
            return 0
        fi
        sleep 1
    done
    fail "$name did not respond at $url within ${timeout}s"
}

wait_services() {
    wait_http "$ACCOUNT_URL/accounts/balance_sum" "account-service"
    wait_http "$TRANSFER_URL/transfers/00000000-0000-0000-0000-000000000000" "transfer-service"
}

balance() { # balance <accountId>
    json_field "$(curl -fs "$ACCOUNT_URL/accounts/$1")" balance
}

balance_sum() {
    json_field "$(curl -fs "$ACCOUNT_URL/accounts/balance_sum")" balanceSum
}

deposit() { # deposit <accountId> <amount>
    curl -fs -o /dev/null -X POST "$ACCOUNT_URL/accounts/$1/deposit" \
        -H 'Content-Type: application/json' -d "{\"amount\": $2}"
}

# create_transfer <from> <to> <amount> → печатает transferId; проверяет 202 + PENDING
create_transfer() {
    local body code json id status
    body="{\"fromAccountId\":\"$1\",\"toAccountId\":\"$2\",\"amount\":$3}"
    json="$(curl -s -w '\n%{http_code}' -X POST "$TRANSFER_URL/transfers" \
        -H 'Content-Type: application/json' -d "$body")"
    code="${json##*$'\n'}"
    json="${json%$'\n'*}"
    [[ "$code" == "202" ]] || fail "POST /transfers: expected 202, got $code: $json"
    id="$(json_field "$json" transferId)"
    status="$(json_field "$json" status)"
    [[ "$status" == "PENDING" ]] || fail "POST /transfers: expected PENDING, got $status"
    printf '%s' "$id"
}

transfer_status() {
    json_field "$(curl -fs "$TRANSFER_URL/transfers/$1")" status
}

wait_status() { # wait_status <transferId> <expected> [timeout_sec]
    local id="$1" expected="$2" timeout="${3:-60}" i status=""
    for ((i = 0; i < timeout; i++)); do
        status="$(transfer_status "$id")"
        if [[ "$status" == "$expected" ]]; then
            pass "transfer $id → $expected"
            return 0
        fi
        sleep 1
    done
    fail "transfer $id: expected $expected within ${timeout}s, last status '$status'"
}

# ---------- docker / kafka ----------

kafka_exec() { docker exec -i kafka "$@"; }

# Суммарный lag группы по топику (0, если группа ещё ничего не коммитила — см. LAG '-')
group_lag() {
    kafka_exec "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --describe --group "$GROUP" 2>/dev/null |
        awk -v t="$TOPIC" '$2 == t && $6 ~ /^[0-9]+$/ { s += $6 } END { print s + 0 }'
}

wait_lag_zero() { # wait_lag_zero [timeout_sec]
    local timeout="${1:-60}" i lag=""
    for ((i = 0; i < timeout; i++)); do
        lag="$(group_lag)"
        if [[ "$lag" == "0" ]]; then
            pass "consumer group $GROUP lag = 0"
            return 0
        fi
        sleep 1
    done
    fail "consumer group $GROUP lag did not reach 0 within ${timeout}s (lag=$lag)"
}

# Все записи топика в виде "key|value" (для повторной отправки через console producer)
topic_records() {
    kafka_exec "$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --topic "$TOPIC" --from-beginning --timeout-ms 5000 \
        --property print.key=true --property key.separator='|' 2>/dev/null || true
}

produce_records() { # stdin: строки "key|value"
    kafka_exec "$KAFKA_BIN/kafka-console-producer.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --topic "$TOPIC" --property parse.key=true --property key.separator='|'
}

stop_account_service() {
    docker compose stop account-service >/dev/null
    pass "account-service stopped"
}

start_account_service() {
    docker compose start account-service >/dev/null
    wait_http "$ACCOUNT_URL/accounts/balance_sum" "account-service"
}

now_utc() { date -u +%Y-%m-%dT%H:%M:%SZ; }
