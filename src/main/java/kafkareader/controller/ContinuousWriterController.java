package kafkareader.controller;

import kafkareader.service.ContinuousLogWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/api/writer")
@RequiredArgsConstructor
public class ContinuousWriterController {
    private final ContinuousLogWriter continuousLogWriter;

    @PostMapping("/start")
    public ResponseEntity<?> startWriting(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "targetSpeed", defaultValue = "1000") int targetSpeed,
            @RequestParam(value = "targetDataSpeed", defaultValue = "1.0") double targetDataSpeed,
            @RequestParam(value = "regexGroupName", required = false) String regexGroupName) {

        if (!continuousLogWriter.isKafkaConfigured()) {
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }

        continuousLogWriter.processFile(file, targetSpeed, targetDataSpeed, regexGroupName);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopWriting() {
        if (!continuousLogWriter.isKafkaConfigured()) {
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }

        continuousLogWriter.stop();
        Map<String, Object> metrics = continuousLogWriter.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> getMetrics() {
        if (!continuousLogWriter.isKafkaConfigured()) {
            return ResponseEntity.badRequest().body("Kafka не настроен. Пожалуйста, настройте подключение к Kafka через веб-интерфейс.");
        }

        Map<String, Object> metrics = continuousLogWriter.getMetrics();
        return ResponseEntity.ok(metrics);
    }
} 