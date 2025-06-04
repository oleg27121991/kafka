#!/bin/bash

# Очистка старых образов
docker-compose down
docker rmi veremeioleg/kafkareader:1.1 2>/dev/null || true

# Создание multi-arch builder'а
docker buildx rm mybuilder 2>/dev/null || true
docker buildx create --name mybuilder --driver docker-container --use

# Сборка и пуш multi-arch образа
export DOCKER_CLI_EXPERIMENTAL=enabled
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t veremeioleg/kafkareader:1.1 \
  --push \
  .