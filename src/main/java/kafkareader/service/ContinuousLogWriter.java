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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CountDownLatch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

@Slf4j
@Service
@Lazy
public class ContinuousLogWriter {
    private final RegexGroupService regexGroupService;
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
    private List<String> allLines = new ArrayList<>();

    private List<Pattern> timePatterns = new ArrayList<>();

    private static final int BATCH_SIZE = 5000; // Уменьшаем размер батча для лучшего контроля скорости
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_BACKOFF_MS = 100;
    private static final int THREAD_POOL_SIZE = 8; // Параллельная отправка
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

    @Autowired
    public ContinuousLogWriter(RegexGroupService regexGroupService) {
        this.regexGroupService = regexGroupService;
    }

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

    public void processFile(@Nonnull MultipartFile file, int targetSpeed, double targetDataSpeed, String regexGroupName) {
        log.info("Начало обработки файла: {}, группа regex: {}", file.getOriginalFilename(), regexGroupName);

        loadRegexPatterns(regexGroupName);

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
            allLines.clear();
            totalProcessingTimeMs = 0;

            // Читаем файл в память
            long readStartTime = System.currentTimeMillis();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && isRunning.get()) {
                    allLines.add(line);
                    totalLinesProcessed++;
                }
            }
            fileReadTimeMs = System.currentTimeMillis() - readStartTime;
            log.info("Файл прочитан за {} мс, найдено {} строк", fileReadTimeMs, allLines.size());

            if (!isRunning.get()) {
                log.info("Обработка файла прервана");
                return;
            }

            // Основной цикл отправки
            while (isRunning.get() && !isStopping.get()) {
                long writeStartTime = System.currentTimeMillis();
                sendDataToKafka();
                kafkaWriteTimeMs = System.currentTimeMillis() - writeStartTime;
                totalProcessingTimeMs = System.currentTimeMillis() - startTime.get();

                // Логирование статистики
                if (messagesSent.get() % 100000 == 0) {
                    logStatistics();
                }
            }

            log.info("Обработка файла завершена. Итоговая статистика:");
            logStatistics();

        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
        } finally {
            isRunning.set(false);
            isStopping.set(false);
            totalProcessingTimeMs = System.currentTimeMillis() - startTime.get();
        }
    }

    private void sendDataToKafka() throws InterruptedException {
        // Группируем строки по сообщениям
        List<String> messageGroups = new ArrayList<>();
        StringBuilder currentMessage = new StringBuilder();

        if (allLines.isEmpty()) {
            isStopping.set(true);
            return;
        }

        for (String line : allLines) {
            boolean isNewMessageStart = !timePatterns.isEmpty() && timePatterns.stream().anyMatch(p -> p.matcher(line).find());

            if (isNewMessageStart && currentMessage.length() > 0) {
                messageGroups.add(currentMessage.toString());
                currentMessage = new StringBuilder();
            }
            currentMessage.append(line).append("\n");
        }

        // Добавляем последнее сообщение
        if (currentMessage.length() > 0) {
            messageGroups.add(currentMessage.toString());
        }

        log.info("Сгруппировано {} сообщений", messageGroups.size());

        // Разбиваем сообщения на батчи для контроля скорости
        int batchSize = Math.min(BATCH_SIZE, messageGroups.size());
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < messageGroups.size(); i += batchSize) {
            batches.add(messageGroups.subList(i, Math.min(i + batchSize, messageGroups.size())));
        }

        // Отправляем батчи с контролем скорости
        for (List<String> batch : batches) {
            if (!isRunning.get() || isStopping.get()) {
                break;
            }

            long batchBytes = 0;
            for (String message : batch) {
                if (message != null && !message.trim().isEmpty()) {
                    batchBytes += message.getBytes(StandardCharsets.UTF_8).length;
                }
            }

            // Отправляем сообщения из батча
            for (String message : batch) {
                if (message != null && !message.trim().isEmpty()) {
                    try {
                        // Контроль скорости перед отправкой каждого сообщения
                        controlSpeed(message.getBytes(StandardCharsets.UTF_8).length, 1);

                        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                            currentTopicName, 
                            UUID.randomUUID().toString(), 
                            message
                        );
                        future.whenComplete((result, ex) -> {
                            if (ex == null) {
                                messagesSent.incrementAndGet();
                                bytesSent.addAndGet(message.getBytes(StandardCharsets.UTF_8).length);
                                linesWrittenToKafka++;
                            } else {
                                log.error("Ошибка при отправке сообщения: {}", ex.getMessage());
                            }
                        });
                    } catch (Exception e) {
                        log.error("Ошибка при отправке сообщения: {}", e.getMessage());
                    }
                }
            }
        }

        // Если все сообщения обработаны, останавливаем процесс
        if (messageGroups.size() == 0) {
            isStopping.set(true);
        }
    }

    private void controlSpeed(long messageBytes, int messageCount) throws InterruptedException {
        if (targetSpeed <= 0 && targetDataSpeed <= 0) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime.get();
        
        if (elapsedTime > 0) {
            double currentSpeed = (double) messagesSent.get() / (elapsedTime / 1000.0);
            double currentDataSpeed = (double) bytesSent.get() / (elapsedTime / 1000.0) / (1024 * 1024);
            
            // Если превышаем целевую скорость, делаем паузу
            if ((targetSpeed > 0 && currentSpeed > targetSpeed) || 
                (targetDataSpeed > 0 && currentDataSpeed > targetDataSpeed)) {
                Thread.sleep(10); // Пауза 10мс
            }
        }
    }

    private void logStatistics() {
        long elapsedTime = System.currentTimeMillis() - startTime.get();
        double currentSpeed = elapsedTime > 0 ? (double) messagesSent.get() / (elapsedTime / 1000.0) : 0;
        double currentDataSpeed = elapsedTime > 0 ? (double) bytesSent.get() / (elapsedTime / 1000.0) / (1024 * 1024) : 0;
        
        log.info("Статистика обработки:");
        log.info("Текущая скорость: {:.2f} сообщений/сек", currentSpeed);
        log.info("Текущая скорость данных: {:.2f} МБ/сек", currentDataSpeed);
        log.info("Время чтения файла: {} мс", fileReadTimeMs);
        log.info("Время записи в Kafka: {} мс", kafkaWriteTimeMs);
        log.info("Общее время обработки: {} мс", totalProcessingTimeMs);
        log.info("Всего обработано строк: {}", totalLinesProcessed);
        log.info("Записано в Kafka: {}", linesWrittenToKafka);
        log.info("Отправлено сообщений: {}", messagesSent.get());
        log.info("Отправлено байт: {}", bytesSent.get());
    }

    public void stop() {
        if (isRunning.get()) {
            log.info("Останавливаем обработку файла...");
            isStopping.set(true);
            isRunning.set(false);
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("isRunning", isRunning.get());
        metrics.put("isStopping", isStopping.get());
        metrics.put("messagesSent", messagesSent.get());
        metrics.put("bytesSent", bytesSent.get());
        metrics.put("totalProcessingTimeMs", isRunning.get() ? System.currentTimeMillis() - startTime.get() : totalProcessingTimeMs);
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


    private void processFile(Path filePath) {
        try {
            log.info("Обработка файла: {}", filePath);
            List<String> lines = Files.readAllLines(filePath);
            log.info("Прочитано {} строк из файла", lines.size());

            // Создаем пул потоков для параллельной обработки
            int numThreads = Runtime.getRuntime().availableProcessors() * 2;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            log.info("Создан пул из {} потоков", numThreads);

            // Группируем строки по сообщениям
            List<String> messageGroups = new ArrayList<>();
            StringBuilder currentMessage = new StringBuilder();

            for (String line : lines) {
                boolean isNewMessageStart = !timePatterns.isEmpty() && timePatterns.stream().anyMatch(p -> p.matcher(line).find());

                if (isNewMessageStart && currentMessage.length() > 0) {
                    messageGroups.add(currentMessage.toString());
                    currentMessage = new StringBuilder();
                }
                currentMessage.append(line).append("\n");
            }

            // Добавляем последнее сообщение
            if (currentMessage.length() > 0) {
                messageGroups.add(currentMessage.toString());
            }

            log.info("Сгруппировано {} сообщений", messageGroups.size());

            // Разбиваем сообщения на батчи
            int batchSize = 1000;
            List<List<String>> batches = new ArrayList<>();
            for (int i = 0; i < messageGroups.size(); i += batchSize) {
                batches.add(messageGroups.subList(i, Math.min(i + batchSize, messageGroups.size())));
            }
            log.info("Создано {} батчей по {} сообщений", batches.size(), batchSize);

            // Создаем CountDownLatch для отслеживания завершения всех задач
            CountDownLatch latch = new CountDownLatch(batches.size());
            AtomicInteger processedMessages = new AtomicInteger(0);
            AtomicLong totalBytes = new AtomicLong(0);
            long startTime = System.currentTimeMillis();

            // Отправляем батчи параллельно
            for (List<String> batch : batches) {
                executor.submit(() -> {
                    try {
                        for (String message : batch) {
                            if (message != null && !message.trim().isEmpty()) {
                                kafkaTemplate.send(currentTopicName, UUID.randomUUID().toString(), message);
                                processedMessages.incrementAndGet();
                                totalBytes.addAndGet(message.getBytes().length);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Ждем завершения всех задач с таймаутом
            if (!latch.await(5, TimeUnit.MINUTES)) {
                log.warn("Таймаут при ожидании завершения обработки файла");
            }

            long endTime = System.currentTimeMillis();
            double totalSeconds = (endTime - startTime) / 1000.0;
            double mbPerSecond = (totalBytes.get() / (1024.0 * 1024.0)) / totalSeconds;

            log.info("=== Результаты обработки файла ===");
            log.info("Обработано сообщений: {}", processedMessages.get());
            log.info("Общий объем данных: {} MB", String.format("%.2f", totalBytes.get() / (1024.0 * 1024.0)));
            log.info("Общее время: {} секунд", String.format("%.2f", totalSeconds));
            log.info("Средняя скорость: {} MB/сек", String.format("%.2f", mbPerSecond));

            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                log.warn("Таймаут при закрытии пула потоков");
                executor.shutdownNow();
            }

        } catch (Exception e) {
            log.error("Ошибка при обработке файла {}: {}", filePath, e.getMessage(), e);
        }
    }
    private void loadRegexPatterns(String groupName) {
        timePatterns.clear();

        if (groupName != null && !groupName.isEmpty()) {
            regexGroupService.getGroupByName(groupName).ifPresent(group -> {
                log.info("Загрузка группы регулярных выражений: {}", groupName);
                group.getPatterns().forEach(p -> {
                    if ("TIME".equalsIgnoreCase(p.getName())) {
                        timePatterns.add(Pattern.compile(p.getPattern()));
                    }
                });
            });
        }

        if (timePatterns.isEmpty()) {
            log.warn("Группа регулярных выражений не найдена или пуста. Используются паттерны по умолчанию.");
            // Паттерны по умолчанию
            timePatterns.add(Pattern.compile("^(\\d{2}:\\d{2}:\\d{2},\\d{3})"));
            timePatterns.add(Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})"));
            timePatterns.add(Pattern.compile("^[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2}"));
        }
    }
}