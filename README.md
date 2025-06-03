# Kafka Reader

Сервис для чтения и обработки логов из Kafka, с возможностью отправки логов в Kafka с сохранением стектрейсов.

## Требования

- Java 21
- Docker и Docker Compose
- Gradle

## Развертывание Kafka

### 1. Запуск Kafka через Docker Compose

```bash
# Создайте файл docker-compose.yml со следующим содержимым:
version: '3'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  kafka:
    image: confluentinc/cp-kafka:latest
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_MESSAGE_MAX_BYTES: 10485760
      KAFKA_REPLICA_FETCH_MAX_BYTES: 10485760
      KAFKA_MAX_REQUEST_SIZE: 10485760

# Запустите Kafka
docker-compose up -d
```

## Запуск приложения

### 1. Сборка проекта

```bash
# Сборка через Gradle
./gradlew clean build

# Или через Maven
mvn clean package
```

### 2. Запуск приложения

```bash
# Запуск с настройками по умолчанию (Kafka на localhost:9092)
java -jar build/libs/kafkareader-0.0.1-SNAPSHOT.jar

# Или с указанием конкретного Kafka кластера
java -jar build/libs/kafkareader-0.0.1-SNAPSHOT.jar \
  --spring.kafka.bootstrap-servers=kafka1:9092,kafka2:9092
```

## Использование

### 1. Настройка подключения к Kafka

1. Откройте веб-интерфейс по адресу `http://localhost:5252`
2. В разделе "Настройки Kafka" укажите:
   - Адреса брокеров Kafka
   - Нажмите кнопку "Проверить подключение"
   - Выбирите топик
   - Настройки безопасности (если требуется)

### 2. Запуск процесса записи логов

1. В веб-интерфейсе перейдите в раздел "Запись логов"
2. Выберите файл с логами для обработки
3. Укажите параметры:
   - Целевая скорость (сообщений в секунду)
   - Целевая скорость данных (МБ/сек)
4. Нажмите "Начать запись"

### 3. Мониторинг процесса

В процессе записи вы увидите:
- Количество обработанных сообщений
- Общий объем данных
- Текущую скорость записи
- Статистику по времени обработки

## Конфигурация

### Основные настройки приложения

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      properties:
        max.request.size: 10485760
        buffer.memory: 134217728
        batch.size: 2097152
        compression.type: lz4
```

### Переменные окружения

| Переменная | Описание | Значение по умолчанию |
|------------|----------|----------------------|
| SERVER_PORT | Порт приложения | 5252 |
| SPRING_KAFKA_BOOTSTRAP_SERVERS | Адреса брокеров Kafka | localhost:9092 |
| SPRING_KAFKA_PRODUCER_PROPERTIES_MAX_REQUEST_SIZE | Максимальный размер сообщения | 10485760 |
| SPRING_KAFKA_PRODUCER_PROPERTIES_BUFFER_MEMORY | Размер буфера | 134217728 |
| SPRING_KAFKA_PRODUCER_PROPERTIES_BATCH_SIZE | Размер батча | 2097152 |
| SPRING_KAFKA_PRODUCER_PROPERTIES_COMPRESSION_TYPE | Тип сжатия | lz4 |

## Структура проекта

```
src/
├── main/
│   ├── java/
│   │   └── kafkareader/
│   │       ├── config/      # Конфигурации Spring
│   │       ├── controller/  # REST контроллеры
│   │       ├── dto/         # Data Transfer Objects
│   │       ├── service/     # Бизнес-логика
│   │       └── util/        # Утилиты
│   └── resources/
│       ├── static/         # Веб-интерфейс
│       └── application.yml # Конфигурация приложения
└── test/                   # Тесты
```

## Особенности реализации

- Сохранение полных стектрейсов в сообщениях Kafka
- Оптимизированная отправка сообщений одним батчем
- Веб-интерфейс для управления процессом записи
- Мониторинг производительности в реальном времени
- Поддержка больших файлов логов
- Оптимизированные настройки Kafka Producer

## Лицензия

MIT

