#!/bin/bash

# Проверка наличия Docker
if ! command -v docker &> /dev/null; then
    echo "Docker не установлен. Пожалуйста, установите Docker Desktop для Mac."
    exit 1
fi

# Проверка статуса Docker daemon
if ! docker info &> /dev/null; then
    echo "Docker daemon не запущен. Пожалуйста, запустите Docker Desktop."
    exit 1
fi

# Проверка авторизации в Docker Hub
if [ ! -f "$HOME/.docker/config.json" ] || ! grep -q "auths" "$HOME/.docker/config.json"; then
    echo "Вы не авторизованы в Docker Hub. Выполните 'docker login'"
    exit 1
fi

# Остановка и удаление старых контейнеров
echo "Остановка старых контейнеров..."
docker-compose down

# Удаление старого образа
echo "Удаление старого образа..."
docker rmi veremeioleg/kafkareader:latest 2>/dev/null || true

# Проверка Java версии
echo "Проверка версии Java..."
if ! java -version 2>&1 | grep -q "version \"21"; then
    echo "Требуется Java 21. Пожалуйста, установите Java 21."
    exit 1
fi

# Сборка проекта
echo "Сборка проекта..."
./gradlew clean build

# Проверка наличия builder'а для multi-arch сборки
if ! docker buildx ls | grep -q "kafkareader-builder"; then
    echo "Создание builder'а для multi-arch сборки..."
    docker buildx create --name kafkareader-builder --use
fi

# Сборка и публикация образа
echo "Сборка и публикация Docker образа..."
if ! docker buildx build --platform linux/amd64,linux/arm64 -t veremeioleg/kafkareader:latest --push .; then
    echo "Ошибка при публикации образа. Убедитесь, что:"
    echo "1. Репозиторий veremeioleg/kafkareader существует в Docker Hub"
    echo "2. У вас есть права на публикацию в этот репозиторий"
    echo "3. Вы авторизованы в Docker Hub (docker login)"
    exit 1
fi

# Запуск приложения
echo "Запуск приложения..."
docker-compose up -d

echo "Готово! Приложение запущено и доступно по адресу http://localhost:5252" 