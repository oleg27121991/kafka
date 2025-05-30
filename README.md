# Kafka Reader

Сервис для чтения и обработки логов из Kafka.

## Требования

- Java 21
- Docker и Docker Compose
- Доступ к Kafka кластеру

## Быстрый старт

### 1. Сборка и публикация Docker образа

```bash
# Установите переменную окружения с вашим Docker Hub username
export DOCKER_USERNAME=your_username

# Запустите скрипт сборки и публикации
./build-and-save.sh
```

### 2. Запуск приложения

```bash
# Запуск с настройками по умолчанию (Kafka на localhost:9092)
docker compose up -d

# Или с указанием конкретного Kafka кластера
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092,kafka2:9092 docker compose up -d

# Для защищенного Kafka кластера
KAFKA_BOOTSTRAP_SERVERS=kafka1:9092 \
KAFKA_SECURITY_PROTOCOL=SASL_SSL \
KAFKA_SASL_MECHANISM=PLAIN \
KAFKA_SASL_JAAS_CONFIG="org.apache.kafka.common.security.plain.PlainLoginModule required username=\"user\" password=\"pass\";" \
docker compose up -d
```

### 3. Проверка статуса

```bash
# Проверка статуса контейнеров
docker ps -a

# Просмотр логов приложения
docker logs -f kafkareader-app
```

## Конфигурация

### Переменные окружения

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| KAFKA_BOOTSTRAP_SERVERS | Адреса брокеров Kafka | localhost:9092 |
| KAFKA_SECURITY_PROTOCOL | Протокол безопасности | PLAINTEXT |
| KAFKA_SASL_MECHANISM | Механизм SASL | PLAIN |
| KAFKA_SASL_JAAS_CONFIG | Конфигурация JAAS для SASL | - |

### Порты

- 5252 - HTTP порт приложения
- 5432 - PostgreSQL

## Разработка

### Сборка проекта

```bash
./gradlew clean build
```

### Запуск тестов

```bash
./gradlew test
```

## Структура проекта

```
src/
├── main/
│   ├── java/
│   │   └── kafkareader/
│   │       ├── config/      # Конфигурации Spring
│   │       ├── controller/  # REST контроллеры
│   │       ├── dto/         # Data Transfer Objects
│   │       ├── entity/      # JPA сущности
│   │       ├── repository/  # Spring Data репозитории
│   │       └── service/     # Бизнес-логика
│   └── resources/
│       └── application.yml  # Конфигурация приложения
└── test/                    # Тесты
```

## Лицензия

MIT

