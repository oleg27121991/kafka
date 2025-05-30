-- Добавление новых полей для метрик
ALTER TABLE kafkareader.log_processing_metrics
    ADD COLUMN IF NOT EXISTS avg_messages_per_second DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS avg_data_speed_mbps DECIMAL(10,4),
    ADD COLUMN IF NOT EXISTS current_messages_per_second DECIMAL(10,2),
    ADD COLUMN IF NOT EXISTS current_bytes_per_second BIGINT,
    ADD COLUMN IF NOT EXISTS total_messages_sent BIGINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS start_time TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS end_time TIMESTAMP WITH TIME ZONE;

-- Добавление индекса для быстрого поиска по времени
CREATE INDEX IF NOT EXISTS idx_log_processing_metrics_processed_at 
ON kafkareader.log_processing_metrics(processed_at);

-- Добавление комментариев к полям
COMMENT ON COLUMN kafkareader.log_processing_metrics.avg_messages_per_second IS 'Средняя скорость обработки сообщений в секунду';
COMMENT ON COLUMN kafkareader.log_processing_metrics.avg_data_speed_mbps IS 'Средняя скорость передачи данных в МБ/сек';
COMMENT ON COLUMN kafkareader.log_processing_metrics.current_messages_per_second IS 'Текущая скорость обработки сообщений в секунду';
COMMENT ON COLUMN kafkareader.log_processing_metrics.current_bytes_per_second IS 'Текущая скорость передачи данных в байтах/сек';
COMMENT ON COLUMN kafkareader.log_processing_metrics.total_messages_sent IS 'Общее количество отправленных сообщений';
COMMENT ON COLUMN kafkareader.log_processing_metrics.start_time IS 'Время начала обработки';
COMMENT ON COLUMN kafkareader.log_processing_metrics.end_time IS 'Время окончания обработки'; 