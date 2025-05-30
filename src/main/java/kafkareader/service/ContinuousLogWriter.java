package kafkareader.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.clients.producer.ProducerConfig;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.producer.RecordMetadata;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@Lazy
public class ContinuousLogWriter {
    private volatile KafkaTemplate<String, String> kafkaTemplate;
    private ThreadPoolTaskExecutor taskExecutor;
    private String currentTopicName;
    private Map<String, Object> kafkaConfig = new HashMap<>();

    @Value("${kafka.writer.threads:1}")
    private int writerThreads;
    
    @Value("${kafka.writer.batch-size:10000}")
    private int batchSize;
    
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
    private final AtomicInteger currentLineIndex = new AtomicInteger(0);
    
    // Паттерны для определения временных меток
    private static final Pattern TIME_PATTERN_1 = Pattern.compile("^(\\d{2}:\\d{2}:\\d{2},\\d{3})");
    private static final Pattern TIME_PATTERN_2 = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})");

    private static final int maxBatchSize = 50000; // Максимальный размер батча
    private static final int minBatchSize = 10000; // Минимальный размер батча
    private static final long batchTimeoutMs = 20; // Таймаут для отправки батча
    private static final int maxRetries = 3; // Максимальное количество попыток
    private static final long retryBackoffMs = 50; // Задержка между попытками

    private ExecutorService asyncExecutor;

    private void initTaskExecutor() {
        if (taskExecutor != null) {
            taskExecutor.shutdown();
            try {
                if (!taskExecutor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                    taskExecutor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                taskExecutor.getThreadPoolExecutor().shutdownNow();
            }
        }
        
        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(writerThreads);
        taskExecutor.setMaxPoolSize(writerThreads);
        taskExecutor.setQueueCapacity(0); // Отключаем очередь, чтобы потоки создавались сразу
        taskExecutor.setThreadNamePrefix("KafkaWriter-");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        taskExecutor.setAwaitTerminationSeconds(60);
        taskExecutor.initialize();
        
        log.info("Инициализирован ThreadPoolTaskExecutor с {} потоками", writerThreads);
    }

    @PostConstruct
    public void init() {
        initTaskExecutor();
        asyncExecutor = Executors.newFixedThreadPool(writerThreads * 2);
        log.info("Инициализирован asyncExecutor с {} потоками", writerThreads * 2);
    }

    @Autowired(required = false)
    public void setKafkaTemplate(KafkaTemplate<String, String> kafkaTemplate) {
        if (kafkaTemplate != null) {
            // Сохраняем текущую конфигурацию
            kafkaConfig = new HashMap<>(kafkaTemplate.getProducerFactory().getConfigurationProperties());
            
            // Оптимизируем настройки Kafka для максимальной производительности
            kafkaConfig.put(ProducerConfig.ACKS_CONFIG, "0"); // Отключаем подтверждения для максимальной скорости
            kafkaConfig.put(ProducerConfig.LINGER_MS_CONFIG, "5"); // Минимальная задержка
            kafkaConfig.put(ProducerConfig.BATCH_SIZE_CONFIG, "1048576"); // 1MB
            kafkaConfig.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "67108864"); // 64MB
            kafkaConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // Используем сжатие
            kafkaConfig.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
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

    private void processFileInThread(int threadId) {
        try {
            List<String> batch = new ArrayList<>(batchSize);
            long lastSendTime = System.currentTimeMillis();
            int currentBatchSize = batchSize;
            int currentIndex = 0;
            int retryCount = 0;
            
            while (isRunning.get()) {
                // Собираем пакет строк
                batch.clear();
                for (int i = 0; i < currentBatchSize && isRunning.get(); i++) {
                    if (currentIndex >= validLines.size()) {
                        currentIndex = 0;
                    }
                    batch.add(validLines.get(currentIndex++));
                }
                
                if (!batch.isEmpty()) {
                    try {
                        List<CompletableFuture<RecordMetadata>> futures = new ArrayList<>();
                        for (String line : batch) {
                            futures.add(CompletableFuture.supplyAsync(() -> {
                                try {
                                    if (kafkaTemplate == null) {
                                        throw new IllegalStateException("KafkaTemplate не инициализирован");
                                    }
                                    if (currentTopicName == null || currentTopicName.isEmpty()) {
                                        throw new IllegalStateException("Топик не установлен");
                                    }
                                    RecordMetadata metadata = kafkaTemplate.send(currentTopicName, line).get().getRecordMetadata();
                                    messagesSent.incrementAndGet();
                                    bytesSent.addAndGet(line.getBytes(StandardCharsets.UTF_8).length);
                                    linesWrittenToKafka++;
                                    return metadata;
                                } catch (Exception e) {
                                    log.error("Ошибка при отправке сообщения в Kafka: {}", e.getMessage());
                                    throw new CompletionException(e);
                                }
                            }, asyncExecutor));
                        }
                        
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                        retryCount = 0; // Сбрасываем счетчик попыток при успешной отправке
                        
                        // Динамическая настройка размера батча
                        long currentTime = System.currentTimeMillis();
                        long timeDiff = currentTime - lastSendTime;
                        if (timeDiff < batchTimeoutMs) {
                            currentBatchSize = Math.min(currentBatchSize * 2, maxBatchSize);
                        } else if (timeDiff > batchTimeoutMs * 2) {
                            currentBatchSize = Math.max(currentBatchSize / 2, minBatchSize);
                        }
                        lastSendTime = currentTime;
                        
                    } catch (Exception e) {
                        log.error("Error sending batch in thread {}: {}", threadId, e.getMessage());
                        retryCount++;
                        
                        if (retryCount >= maxRetries) {
                            log.error("Превышено максимальное количество попыток в потоке {}. Останавливаем поток.", threadId);
                            isRunning.set(false);
                            break;
                        }
                        
                        // Уменьшаем размер батча при ошибке
                        currentBatchSize = Math.max(currentBatchSize / 2, minBatchSize);
                        Thread.sleep(retryBackoffMs * retryCount); // Увеличиваем задержку с каждой попыткой
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in writer thread {}: {}", threadId, e.getMessage(), e);
            isRunning.set(false);
            throw new RuntimeException("Error in writer thread " + threadId, e);
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
            log.info("Размер файла: {} байт", fileSizeBytes);
            
            // Читаем файл и фильтруем строки
            long startReadTime = System.currentTimeMillis();
            validLines.clear();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (TIME_PATTERN_1.matcher(line).find() || TIME_PATTERN_2.matcher(line).find()) {
                        validLines.add(line);
                    }
                }
            }
            fileReadTimeMs = System.currentTimeMillis() - startReadTime;
            log.info("Файл прочитан за {} мс, найдено {} валидных строк", fileReadTimeMs, validLines.size());
            
            // Сбрасываем счетчики
            messagesSent.set(0);
            bytesSent.set(0);
            totalProcessingTimeMs = 0;
            kafkaWriteTimeMs = 0;
            totalLinesProcessed = validLines.size();
            linesWrittenToKafka = 0;
            
            // Останавливаем предыдущие задачи
            stop();
            
            // Инициализируем новый пул потоков
            initTaskExecutor();
            
            // Пересоздаем asyncExecutor
            if (asyncExecutor != null) {
                asyncExecutor.shutdown();
                try {
                    if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        asyncExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    asyncExecutor.shutdownNow();
                }
            }
            asyncExecutor = Executors.newFixedThreadPool(writerThreads * 2);
            log.info("Пересоздан asyncExecutor с {} потоками", writerThreads * 2);
            
            // Запускаем новые задачи
            isRunning.set(true);
            startTime.set(System.currentTimeMillis());
            
            for (int i = 0; i < writerThreads; i++) {
                final int threadId = i;
                taskExecutor.execute(() -> processFileInThread(threadId));
            }
            
            log.info("Запущено {} потоков обработки", writerThreads);
            
        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            stop();
            throw new RuntimeException("Ошибка при обработке файла", e);
        }
    }

    public void stop() {
        log.info("Получена команда остановки");
        isStopping.set(true);
        isRunning.set(false);
        
        // Останавливаем taskExecutor
        if (taskExecutor != null) {
            log.info("Останавливаем taskExecutor...");
            taskExecutor.shutdown();
            try {
                if (!taskExecutor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Принудительное завершение taskExecutor...");
                    taskExecutor.getThreadPoolExecutor().shutdownNow();
                    if (!taskExecutor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("Не удалось остановить taskExecutor");
                    }
                }
            } catch (InterruptedException e) {
                log.error("Прерывание при остановке taskExecutor", e);
                taskExecutor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Останавливаем asyncExecutor
        if (asyncExecutor != null) {
            log.info("Останавливаем asyncExecutor...");
            asyncExecutor.shutdown();
            try {
                if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Принудительное завершение asyncExecutor...");
                    asyncExecutor.shutdownNow();
                    if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("Не удалось остановить asyncExecutor");
                    }
                }
            } catch (InterruptedException e) {
                log.error("Прерывание при остановке asyncExecutor", e);
                asyncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Не сбрасываем счетчики при остановке
        isStopping.set(false);
        log.info("Все потоки остановлены");
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new java.util.HashMap<>();
        metrics.put("isRunning", isRunning.get());
        metrics.put("isStopping", isStopping.get());
        metrics.put("fileName", fileName);
        metrics.put("totalLinesProcessed", totalLinesProcessed);
        metrics.put("linesWrittenToKafka", linesWrittenToKafka);
        metrics.put("messagesSent", messagesSent.get());
        metrics.put("bytesSent", bytesSent.get());
        
        // Обновляем общее время обработки только если процесс запущен
        if (isRunning.get()) {
            totalProcessingTimeMs = System.currentTimeMillis() - startTime.get();
        }
        metrics.put("totalProcessingTimeMs", totalProcessingTimeMs);
        
        metrics.put("kafkaWriteTimeMs", kafkaWriteTimeMs);
        metrics.put("fileReadTimeMs", fileReadTimeMs);
        metrics.put("fileSizeBytes", fileSizeBytes);
        metrics.put("activeThreads", taskExecutor != null ? taskExecutor.getActiveCount() : 0);
        
        // Обновляем скорости только если процесс запущен
        if (isRunning.get() && totalProcessingTimeMs > 0) {
            double currentSpeed = (double) messagesSent.get() / (totalProcessingTimeMs / 1000.0);
            double currentDataSpeed = (double) bytesSent.get() / (totalProcessingTimeMs / 1000.0) / (1024 * 1024);
            metrics.put("currentSpeed", currentSpeed);
            metrics.put("currentDataSpeed", currentDataSpeed);
            metrics.put("avgSpeed", currentSpeed);
            metrics.put("avgDataSpeed", currentDataSpeed);
        } else {
            // Если процесс остановлен, используем последние известные значения
            double lastSpeed = totalProcessingTimeMs > 0 ? 
                (double) messagesSent.get() / (totalProcessingTimeMs / 1000.0) : 0;
            double lastDataSpeed = totalProcessingTimeMs > 0 ? 
                (double) bytesSent.get() / (totalProcessingTimeMs / 1000.0) / (1024 * 1024) : 0;
            
            metrics.put("currentSpeed", 0.0);
            metrics.put("currentDataSpeed", 0.0);
            metrics.put("avgSpeed", lastSpeed);
            metrics.put("avgDataSpeed", lastDataSpeed);
        }
        
        return metrics;
    }

    public boolean isKafkaConfigured() {
        KafkaTemplate<String, String> template = this.kafkaTemplate;
        if (template == null) {
            log.warn("Kafka не настроен: KafkaTemplate отсутствует");
            return false;
        }

        try {
            // Проверяем, что bootstrap servers установлены
            Object bootstrapServers = template.getProducerFactory().getConfigurationProperties()
                .get("bootstrap.servers");
            if (bootstrapServers == null || bootstrapServers.toString().isEmpty()) {
                log.warn("KafkaTemplate не имеет установленных bootstrap servers");
                return false;
            }
            
            log.info("Kafka настроен: bootstrap.servers={}", bootstrapServers);
            return true;
        } catch (Exception e) {
            log.error("Ошибка при проверке конфигурации Kafka: {}", e.getMessage());
            return false;
        }
    }

    public void updateTopic(String newTopic) {
        if (newTopic != null && !newTopic.isEmpty()) {
            log.info("Обновление топика с {} на {}", this.currentTopicName, newTopic);
            this.currentTopicName = newTopic;
            log.info("Топик обновлен на: {}", newTopic);
        } else {
            log.warn("Попытка обновить топик на пустое значение");
        }
    }
} 