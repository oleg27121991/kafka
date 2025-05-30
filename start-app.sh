#!/bin/bash

# Проверяем, запущена ли база данных
if ! docker ps | grep -q kafkareader-postgres; then
    echo "PostgreSQL is not running. Please start it first using ./start-db.sh"
    exit 1
fi

# Останавливаем существующий контейнер если есть
docker stop kafkareader-app 2>/dev/null
docker rm kafkareader-app 2>/dev/null

# Запускаем приложение
docker run -d \
  --name kafkareader-app \
  -p 5252:8080 \
  -v ./logs:/app/logs \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/kafkareader \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  kafkareader

echo "Application is starting..."
sleep 5
docker logs kafkareader-app 