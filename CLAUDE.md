# CLAUDE.md

## Git

- Коммиты — **без** трейлера `Co-Authored-By` (и любых других строк атрибуции AI).

## Сборка и проверки

- JDK 21 обязателен (maven-enforcer `[21,22)`): если `JAVA_HOME` указывает на другой JDK, сборка
  падает на enforcer.
- Полная проверка, как в CI: `./mvnw verify -Pintegration` (spotless + checkstyle + unit + ArchUnit +
  Testcontainers IT). Форматирование: `./mvnw spotless:apply`.

## Скрипты

- Всё, что прогоняется руками (приёмка, отладка брокеров), — bash-скрипты в `scripts/`, не
  разовые команды в markdown. Скрипты сами проверяют результат и падают с `exit 1`.
