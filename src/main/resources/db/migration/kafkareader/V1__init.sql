-- Создание схемы
CREATE SCHEMA IF NOT EXISTS kafkareader;

-- Установка схемы по умолчанию
SET search_path TO kafkareader;

-- Создание таблицы для хранения метрик обработки логов
CREATE TABLE IF NOT EXISTS log_processing_metrics (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    total_processing_time_ms BIGINT NOT NULL,
    kafka_write_time_ms BIGINT NOT NULL,
    file_read_time_ms BIGINT NOT NULL,
    total_lines_processed INTEGER NOT NULL,
    lines_written_to_kafka INTEGER NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
); 