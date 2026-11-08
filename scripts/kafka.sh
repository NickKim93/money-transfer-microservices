#!/usr/bin/env bash
# Ручные Kafka-команды для отладки (обёртки над CLI внутри контейнера kafka).
#
#   scripts/kafka.sh topics            — список топиков
#   scripts/kafka.sh describe          — партиции / лидер / ISR топика transfer.requested.v1
#   scripts/kafka.sh consume           — все сообщения: партиция, оффсет, ключ, значение
#   scripts/kafka.sh group             — оффсеты и lag группы account-service
#   scripts/kafka.sh reset-earliest    — сбросить оффсеты группы на начало (сервис должен быть остановлен)
#   scripts/kafka.sh produce           — console producer, ввод "key|json" построчно (Ctrl+D — выход)

source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

cmd="${1:-help}"

case "$cmd" in
topics)
    kafka_exec "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" --list
    ;;
describe)
    kafka_exec "$KAFKA_BIN/kafka-topics.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --describe --topic "$TOPIC"
    ;;
consume)
    kafka_exec "$KAFKA_BIN/kafka-console-consumer.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --topic "$TOPIC" --from-beginning --timeout-ms 5000 \
        --property print.partition=true --property print.offset=true --property print.key=true
    ;;
group)
    kafka_exec "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --describe --group "$GROUP"
    ;;
reset-earliest)
    kafka_exec "$KAFKA_BIN/kafka-consumer-groups.sh" --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --group "$GROUP" --topic "$TOPIC" --reset-offsets --to-earliest --execute
    ;;
produce)
    produce_records
    ;;
*)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
