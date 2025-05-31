package kafkareader.service.impl;

import kafkareader.model.KafkaConfig;
import kafkareader.service.KafkaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Lazy
public class KafkaServiceImpl implements KafkaService {
    private static final Logger logger = LoggerFactory.getLogger(KafkaServiceImpl.class);

    @Autowired(required = false)
    @Lazy
    private KafkaTemplate<String, String> kafkaTemplate;

    private boolean isRunning = false;
    private long messagesSent = 0;
    private long bytesSent = 0;
    private long startTime = 0;
    private String currentTopic = null;
    private boolean isFileProcessed = false;

    @Override
    public void testConnection(KafkaConfig config) throws Exception {
        logger.info("Проверка подключения к Kafka...");
        
        if (kafkaTemplate == null) {
            throw new Exception("KafkaTemplate не инициализирован");
        }
        
        try {
            // Проверяем подключение, пытаясь получить список топиков
            kafkaTemplate.getDefaultTopic();
            logger.info("Подключение к Kafka успешно установлено");
        } catch (Exception e) {
            logger.error("Ошибка при проверке подключения к Kafka", e);
            throw new Exception("Ошибка при проверке подключения к Kafka: " + e.getMessage());
        }
    }

    @Override
    public void saveConfig(KafkaConfig config) throws Exception {
        logger.info("Сохранение конфигурации Kafka...");
        
        // Проверяем подключение перед сохранением
        testConnection(config);
        
        // Сохраняем текущий топик
        currentTopic = config.getTopic();
        
        logger.info("Конфигурация Kafka успешно сохранена");
    }

    @Override
    public void processFile(MultipartFile file, double targetSpeed, double targetDataSpeed) throws Exception {
        if (kafkaTemplate == null) {
            throw new Exception("KafkaTemplate не инициализирован");
        }
        
        if (currentTopic == null) {
            throw new Exception("Топик не выбран");
        }

        logger.info("Начало обработки файла: {}", file.getOriginalFilename());
        isRunning = true;
        isFileProcessed = false;
        startTime = System.currentTimeMillis();
        messagesSent = 0;
        bytesSent = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            long lastMessageTime = System.currentTimeMillis();
            long targetInterval = (long) (1000.0 / targetSpeed); // интервал между сообщениями в мс

            while (isRunning && (line = reader.readLine()) != null) {
                if (!isRunning) {
                    break;
                }

                // Отправка сообщения в Kafka
                kafkaTemplate.send(currentTopic, line);
                
                // Обновление метрик
                messagesSent++;
                bytesSent += line.getBytes().length;

                // Контроль скорости отправки
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - lastMessageTime;
                if (elapsedTime < targetInterval) {
                    TimeUnit.MILLISECONDS.sleep(targetInterval - elapsedTime);
                }
                lastMessageTime = System.currentTimeMillis();

                // Проверка скорости передачи данных
                long processingTime = System.currentTimeMillis() - startTime;
                if (processingTime > 0) {
                    double currentDataSpeed = (bytesSent * 1000.0) / processingTime / (1024 * 1024); // МБ/с
                    if (currentDataSpeed > targetDataSpeed) {
                        TimeUnit.MILLISECONDS.sleep(100); // Пауза для снижения скорости
                    }
                }
            }

            isFileProcessed = true;
            logger.info("Обработка файла завершена. Отправлено сообщений: {}, байт: {}", messagesSent, bytesSent);
        } catch (Exception e) {
            logger.error("Ошибка при обработке файла", e);
            throw new Exception("Ошибка при обработке файла: " + e.getMessage());
        } finally {
            isRunning = false;
        }
    }

    @Override
    public void stop() {
        logger.info("Остановка процесса...");
        isRunning = false;
        isFileProcessed = true;
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("isRunning", isRunning);
        metrics.put("messagesSent", messagesSent);
        metrics.put("bytesSent", bytesSent);
        metrics.put("isFileProcessed", isFileProcessed);
        
        if (startTime > 0) {
            long processingTime = System.currentTimeMillis() - startTime;
            metrics.put("totalProcessingTimeMs", processingTime);
            
            if (processingTime > 0) {
                double speed = (messagesSent * 1000.0) / processingTime;
                double dataSpeed = (bytesSent * 1000.0) / processingTime / (1024 * 1024); // МБ/с
                metrics.put("currentSpeed", speed);
                metrics.put("currentDataSpeed", dataSpeed);
            }
        }
        
        return metrics;
    }

    public boolean isFileProcessed() {
        return isFileProcessed;
    }
} 