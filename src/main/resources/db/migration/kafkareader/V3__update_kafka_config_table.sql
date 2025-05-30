-- Создаем таблицу, если она не существует
CREATE TABLE IF NOT EXISTS kafka_config (
    id BIGSERIAL PRIMARY KEY,
    bootstrap_servers VARCHAR(255) NOT NULL,
    topic VARCHAR(255),
    username VARCHAR(255),
    password VARCHAR(255),
    is_active boolean NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Переименовываем колонку is_active в active
ALTER TABLE kafka_config RENAME COLUMN is_active TO active;

-- Добавляем индекс для быстрого поиска активной конфигурации, если его еще нет
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE tablename = 'kafka_config'
        AND indexname = 'idx_kafka_config_active'
    ) THEN
        CREATE INDEX idx_kafka_config_active ON kafka_config(active) WHERE active = true;
    END IF;
END $$; 