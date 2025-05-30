#!/bin/bash

# Останавливаем существующий контейнер если есть
docker stop kafkareader-postgres 2>/dev/null
docker rm kafkareader-postgres 2>/dev/null

# Запускаем PostgreSQL
docker run -d \
  --name kafkareader-postgres \
  -e POSTGRES_DB=kafkareader \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5433:5432 \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:15

# Ждем пока база данных будет готова
echo "Waiting for PostgreSQL to be ready..."
until docker exec kafkareader-postgres pg_isready -U postgres; do
  echo "PostgreSQL is unavailable - sleeping"
  sleep 1
done

echo "PostgreSQL is up and ready!" 