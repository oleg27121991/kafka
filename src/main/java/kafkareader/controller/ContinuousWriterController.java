package kafkareader.controller;

import kafkareader.service.ContinuousLogWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/writer")
@RequiredArgsConstructor
public class ContinuousWriterController {
    private static final Logger log = LoggerFactory.getLogger(ContinuousWriterController.class);
    private final ContinuousLogWriter continuousLogWriter;

    @PostMapping("/start")
    public ResponseEntity<?> startWriting(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSpeed", defaultValue = "1000") int targetSpeed,
            @RequestParam(value = "targetDataSpeed", defaultValue = "1.0") double targetDataSpeed) {
        log.info("[API] /start called. file={}, targetSpeed={}, targetDataSpeed={}", file.getOriginalFilename(), targetSpeed, targetDataSpeed);
        if (!continuousLogWriter.isKafkaConfigured()) {
            log.warn("Kafka не настроен!");
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }
        continuousLogWriter.processFile(file, targetSpeed, targetDataSpeed);
        log.info("[API] /start: processFile вызван");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopWriting() {
        log.info("[API] /stop called");
        if (!continuousLogWriter.isKafkaConfigured()) {
            log.warn("Kafka не настроен!");
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }
        continuousLogWriter.stop();
        Map<String, Object> metrics = continuousLogWriter.getMetrics();
        log.info("[API] /stop: процесс остановлен, метрики={}", metrics);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        log.info("[API] /metrics called");
        if (!continuousLogWriter.isKafkaConfigured()) {
            log.warn("Kafka не настроен!");
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }
        Map<String, Object> metrics = continuousLogWriter.getMetrics();
        log.info("[API] /metrics: метрики={}", metrics);
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/start-multi")
    public ResponseEntity<?> startWritingMulti(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "targetSpeed", defaultValue = "1000") int targetSpeed,
            @RequestParam(value = "targetDataSpeed", defaultValue = "1.0") double targetDataSpeed,
            @RequestParam(value = "partitionRegex", required = false) String partitionRegex) {
        log.info("[API] /start-multi called. files={}, targetSpeed={}, targetDataSpeed={}, partitionRegex={}",
                files != null ? files.length : 0, targetSpeed, targetDataSpeed, partitionRegex);
        if (!continuousLogWriter.isKafkaConfigured()) {
            log.warn("Kafka не настроен!");
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }
        continuousLogWriter.processFiles(List.of(files), targetSpeed, targetDataSpeed, partitionRegex);
        log.info("[API] /start-multi: processFiles вызван");
        return ResponseEntity.ok().build();
    }
} 