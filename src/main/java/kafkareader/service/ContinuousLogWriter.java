package kafkareader.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@Lazy
public class ContinuousLogWriter {
    private volatile KafkaTemplate<String, String> kafkaTemplate;
    private String currentTopicName;
    private Map<String, Object> kafkaConfig = new HashMap<>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isStopping = new AtomicBoolean(false);
    private final AtomicLong messagesSent = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong startTime = new AtomicLong(0);
    private String fileName;
    private long totalProcessingTimeMs = 0;
    private long kafkaWriteTimeMs = 0;
    private long fileReadTimeMs = 0;
    private long totalLinesProcessed = 0;
    private long linesWrittenToKafka = 0;
    private long fileSizeBytes = 0;
    private int targetSpeed = 1000;
    private double targetDataSpeed = 1.0;
    private List<String> validLines = new ArrayList<>();
    
    // Паттерны для определения временных меток
    private static final Pattern TIME_PATTERN_1 = Pattern.compile("^(\\d{2}:\\d{2}:\\d{2},\\d{3})");
    private static final Pattern TIME_PATTERN_2 = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})");

    private static final int BATCH_SIZE = 100000; // Увеличенный размер батча
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 50;

    @Autowired(required = false)
    public void setKafkaTemplate(KafkaTemplate<String, String> kafkaTemplate) {
        if (kafkaTemplate != null) {
            // Сохраняем текущую конфигурацию
            kafkaConfig = new HashMap<>(kafkaTemplate.getProducerFactory().getConfigurationProperties());
            
            // Оптимизируем настройки Kafka для максимальной производительности
            kafkaConfig.put(ProducerConfig.ACKS_CONFIG, "0"); // Отключаем подтверждения для максимальной скорости
            kafkaConfig.put(ProducerConfig.LINGER_MS_CONFIG, "0"); // Минимальная задержка
            kafkaConfig.put(ProducerConfig.BATCH_SIZE_CONFIG, "2097152"); // 2MB
            kafkaConfig.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "134217728"); // 128MB
            kafkaConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // Используем сжатие
            kafkaConfig.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "10");
            kafkaConfig.put(ProducerConfig.RETRIES_CONFIG, "0"); // Отключаем повторные попытки
            kafkaConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "false"); // Отключаем идемпотентность
            kafkaConfig.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "1000"); // Уменьшаем время блокировки
            kafkaConfig.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "1000"); // Уменьшаем таймаут запросов
            
            this.kafkaTemplate = kafkaTemplate;
            log.info("KafkaTemplate оптимизирован для максимальной производительности");
            
            try {
                String topic = kafkaTemplate.getDefaultTopic();
                if (topic != null) {
                    this.currentTopicName = topic;
                    log.info("Установлен топик из KafkaTemplate: {}", topic);
                }
            } catch (Exception e) {
                log.error("Ошибка при тестировании KafkaTemplate: {}", e.getMessage());
            }
        } else {
            log.warn("KafkaTemplate не был установлен!");
        }
    }

    private void ensureKafkaTemplate() {
        if (kafkaTemplate == null && !kafkaConfig.isEmpty()) {
            log.info("Восстанавливаем KafkaTemplate из сохраненной конфигурации");
            ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(kafkaConfig);
            kafkaTemplate = new KafkaTemplate<>(factory);
        }
    }

    public void processFile(@Nonnull MultipartFile file, int targetSpeed, double targetDataSpeed) {
        log.info("Начало обработки файла: {}", file.getOriginalFilename());
        
        ensureKafkaTemplate();
        
        if (!isKafkaConfigured()) {
            log.error("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
            return;
        }

        if (isRunning.get()) {
            log.warn("Уже идет обработка файла");
            return;
        }

        this.targetSpeed = targetSpeed;
        this.targetDataSpeed = targetDataSpeed;
        log.info("Установлены параметры скорости: targetSpeed={}, targetDataSpeed={}", targetSpeed, targetDataSpeed);

        try {
            fileName = file.getOriginalFilename();
            fileSizeBytes = file.getSize();
            isRunning.set(true);
            isStopping.set(false);
            startTime.set(System.currentTimeMillis());
            messagesSent.set(0);
            bytesSent.set(0);
            totalLinesProcessed = 0;
            linesWrittenToKafka = 0;
            validLines.clear();

            // Читаем файл в память
            long readStartTime = System.currentTimeMillis();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && isRunning.get()) {
                    if (isValidLogLine(line)) {
                        validLines.add(line);
                        totalLinesProcessed++;
                    }
                }
            }
            fileReadTimeMs = System.currentTimeMillis() - readStartTime;
            log.info("Файл прочитан за {} мс, найдено {} валидных строк", fileReadTimeMs, validLines.size());

            if (!isRunning.get()) {
                log.info("Обработка файла прервана");
                return;
            }

            // Бесконечный цикл отправки данных
            while (isRunning.get() && !isStopping.get()) {
                // Отправляем данные в Kafka
                long writeStartTime = System.currentTimeMillis();
                List<String> batch = new ArrayList<>(BATCH_SIZE);
                int currentIndex = 0;
                int retryCount = 0;

                while (isRunning.get() && !isStopping.get() && currentIndex < validLines.size()) {
                    // Собираем батч
                    batch.clear();
                    int batchSize = Math.min(BATCH_SIZE, validLines.size() - currentIndex);
                    for (int i = 0; i < batchSize; i++) {
                        batch.add(validLines.get(currentIndex++));
                    }

                    if (!batch.isEmpty()) {
                        try {
                            // Отправляем батч
                            for (String line : batch) {
                                if (kafkaTemplate == null) {
                                    throw new IllegalStateException("KafkaTemplate не инициализирован");
                                }
                                if (currentTopicName == null || currentTopicName.isEmpty()) {
                                    throw new IllegalStateException("Топик не установлен");
                                }
                                
                                kafkaTemplate.send(currentTopicName, line);
                                messagesSent.incrementAndGet();
                                bytesSent.addAndGet(line.getBytes(StandardCharsets.UTF_8).length);
                                linesWrittenToKafka++;
                                
                                // Контроль скорости
                                if (targetSpeed > 0 || targetDataSpeed > 0) {
                                    long currentTime = System.currentTimeMillis();
                                    long elapsedTime = currentTime - startTime.get();
                                    if (elapsedTime > 0) {
                                        double currentSpeed = (double) messagesSent.get() / (elapsedTime / 1000.0);
                                        double currentDataSpeed = (double) bytesSent.get() / (elapsedTime / 1000.0) / (1024 * 1024);
                                        
                                        // Если превышаем целевую скорость, делаем паузу
                                        if ((targetSpeed > 0 && currentSpeed > targetSpeed) || 
                                            (targetDataSpeed > 0 && currentDataSpeed > targetDataSpeed)) {
                                            Thread.sleep(10);
                                        }
                                    }
                                }
                            }
                            
                            retryCount = 0; // Сбрасываем счетчик попыток при успешной отправке
                        } catch (Exception e) {
                            log.error("Ошибка при отправке батча: {}", e.getMessage());
                            retryCount++;
                            
                            if (retryCount >= MAX_RETRIES) {
                                log.error("Превышено максимальное количество попыток. Останавливаем обработку.");
                                isRunning.set(false);
                                break;
                            }
                            
                            Thread.sleep(RETRY_BACKOFF_MS * retryCount);
                        }
                    }
                }

                kafkaWriteTimeMs = System.currentTimeMillis() - writeStartTime;
                totalProcessingTimeMs = System.currentTimeMillis() - startTime.get();
                
                // Если достигли конца файла, начинаем сначала
                if (currentIndex >= validLines.size()) {
                    currentIndex = 0;
                }

                // Логируем статистику каждые 100,000 сообщений
                if (messagesSent.get() % 100000 == 0) {
                    log.info("Статистика обработки:");
                    log.info("Время чтения файла: {} мс", fileReadTimeMs);
                    log.info("Время записи в Kafka: {} мс", kafkaWriteTimeMs);
                    log.info("Общее время обработки: {} мс", totalProcessingTimeMs);
                    log.info("Всего обработано строк: {}", totalLinesProcessed);
                    log.info("Записано в Kafka: {}", linesWrittenToKafka);
                    log.info("Отправлено сообщений: {}", messagesSent.get());
                    log.info("Отправлено байт: {}", bytesSent.get());
                }
            }

            log.info("Обработка файла завершена. Итоговая статистика:");
            log.info("Время чтения файла: {} мс", fileReadTimeMs);
            log.info("Время записи в Kafka: {} мс", kafkaWriteTimeMs);
            log.info("Общее время обработки: {} мс", totalProcessingTimeMs);
            log.info("Всего обработано строк: {}", totalLinesProcessed);
            log.info("Записано в Kafka: {}", linesWrittenToKafka);
            log.info("Отправлено сообщений: {}", messagesSent.get());
            log.info("Отправлено байт: {}", bytesSent.get());

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
            isStopping.set(false);
        }
    }

    public void stop() {
        if (isRunning.get()) {
            log.info("Останавливаем обработку файла...");
            isStopping.set(true);
            isRunning.set(false);
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("isRunning", isRunning.get());
        metrics.put("isStopping", isStopping.get());
        metrics.put("messagesSent", messagesSent.get());
        metrics.put("bytesSent", bytesSent.get());
        metrics.put("totalProcessingTimeMs", totalProcessingTimeMs);
        metrics.put("kafkaWriteTimeMs", kafkaWriteTimeMs);
        metrics.put("fileReadTimeMs", fileReadTimeMs);
        metrics.put("totalLinesProcessed", totalLinesProcessed);
        metrics.put("linesWrittenToKafka", linesWrittenToKafka);
        metrics.put("fileSizeBytes", fileSizeBytes);
        metrics.put("fileName", fileName);
        
        // Рассчитываем текущую скорость
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime.get();
        if (elapsedTime > 0) {
            double currentSpeed = (double) messagesSent.get() / (elapsedTime / 1000.0);
            double currentDataSpeed = (double) bytesSent.get() / (elapsedTime / 1000.0) / (1024 * 1024); // MB/s
            metrics.put("currentSpeed", currentSpeed);
            metrics.put("currentDataSpeed", currentDataSpeed);
        }
        
        return metrics;
    }

    public boolean isKafkaConfigured() {
        return kafkaTemplate != null && currentTopicName != null && !currentTopicName.isEmpty();
    }

    public void updateTopic(String newTopic) {
        if (newTopic != null && !newTopic.isEmpty()) {
            this.currentTopicName = newTopic;
            log.info("Топик обновлен: {}", newTopic);
        }
    }

    private boolean isValidLogLine(String line) {
        return TIME_PATTERN_1.matcher(line).find() || TIME_PATTERN_2.matcher(line).find();
    }
} 