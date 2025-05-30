package kafkareader.controller;

import kafkareader.model.KafkaConfig;
import kafkareader.service.KafkaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
public class KafkaController {
    private static final Logger logger = LoggerFactory.getLogger(KafkaController.class);

    @Autowired
    private KafkaService kafkaService;

    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection(@RequestBody KafkaConfig config) {
        try {
            kafkaService.testConnection(config);
            return ResponseEntity.ok(Map.of("message", "Подключение к Kafka успешно установлено"));
        } catch (Exception e) {
            logger.error("Ошибка при проверке подключения к Kafka", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/save-config")
    public ResponseEntity<?> saveConfig(@RequestBody KafkaConfig config) {
        try {
            kafkaService.saveConfig(config);
            return ResponseEntity.ok(Map.of("message", "Настройки Kafka успешно сохранены"));
        } catch (Exception e) {
            logger.error("Ошибка при сохранении настроек Kafka", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/process-file")
    public ResponseEntity<?> processFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetSpeed") double targetSpeed,
            @RequestParam("targetDataSpeed") double targetDataSpeed) {
        try {
            kafkaService.processFile(file, targetSpeed, targetDataSpeed);
            return ResponseEntity.ok(Map.of("message", "Файл успешно загружен и начата обработка"));
        } catch (Exception e) {
            logger.error("Ошибка при обработке файла", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        try {
            kafkaService.stop();
            return ResponseEntity.ok(Map.of("message", "Процесс остановлен"));
        } catch (Exception e) {
            logger.error("Ошибка при остановке процесса", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        try {
            Map<String, Object> metrics = kafkaService.getMetrics();
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Ошибка при получении метрик", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
} 