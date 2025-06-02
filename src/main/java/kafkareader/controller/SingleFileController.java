package kafkareader.controller;

import kafkareader.model.ProcessingMetrics;
import kafkareader.service.SingleFileProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class SingleFileController {
    private static final Logger log = LoggerFactory.getLogger(SingleFileController.class);

    private final SingleFileProcessor singleFileProcessor;

    @Autowired
    public SingleFileController(SingleFileProcessor singleFileProcessor) {
        this.singleFileProcessor = singleFileProcessor;
        log.info("SingleFileController инициализирован");
    }

    @PostMapping(value = "/process", consumes = "multipart/form-data")
    public ResponseEntity<?> processFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bootstrapServers") String bootstrapServers,
            @RequestParam("topic") String topic,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "logLevelPattern", defaultValue = "\\b(INFO|ERROR|WARN|DEBUG)\\b") String logLevelPattern,
            @RequestParam(value = "batchSize", defaultValue = "1000") int batchSize) {
        
        log.info("Получен запрос на обработку файла: {}, размер: {} байт, тип: {}", 
            file.getOriginalFilename(), 
            file.getSize(),
            file.getContentType());
        
        log.info("Настройки Kafka: bootstrapServers={}, topic={}, username={}", 
            bootstrapServers, topic, username != null ? "***" : "не указан");
        
        try {
            String processId = UUID.randomUUID().toString();
            log.info("Начинаем обработку файла с processId: {}", processId);
            
            singleFileProcessor.processFile(processId, file, logLevelPattern, batchSize, bootstrapServers, topic, username, password);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("processId", processId);
            response.put("message", "File processing started");
            
            log.info("Обработка файла успешно запущена, processId: {}", processId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при обработке файла: {}", e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/metrics/{processId}")
    public ResponseEntity<?> getMetrics(@PathVariable String processId) {
        try {
            ProcessingMetrics metrics = singleFileProcessor.getMetrics(processId);
            return ResponseEntity.ok(metrics);
        } catch (IllegalArgumentException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @DeleteMapping("/cleanup/{processId}")
    public ResponseEntity<?> cleanup(@PathVariable String processId) {
        try {
            singleFileProcessor.cleanup(processId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Processing cleanup completed");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
} 