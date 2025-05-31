package kafkareader.controller;

import kafkareader.model.KafkaConfigDTO;
import kafkareader.model.KafkaConnectionResult;
import kafkareader.service.KafkaConfigService;
import kafkareader.service.ContinuousLogWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
public class KafkaConfigController {

    @Autowired
    private KafkaConfigService kafkaConfigService;

    @Autowired
    private ContinuousLogWriter continuousLogWriter;

    @PostMapping("/check-connection")
    public ResponseEntity<?> checkConnection(@RequestBody KafkaConfigDTO config) {
        try {
            KafkaConnectionResult result = kafkaConfigService.checkConnection(
                config.getBootstrapServers(),
                config.getTopic(),
                config.getUsername(),
                config.getPassword()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/config")
    public ResponseEntity<?> updateConfig(@RequestBody KafkaConfigDTO config) {
        try {
            kafkaConfigService.updateConfig(config);
            return ResponseEntity.ok(Map.of("success", true, "message", "Конфигурация успешно сохранена"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        try {
            KafkaConfigDTO config = new KafkaConfigDTO();
            config.setBootstrapServers(kafkaConfigService.getBootstrapServers());
            config.setTopic(kafkaConfigService.getTopic());
            config.setUsername(kafkaConfigService.getUsername());
            config.setPassword(kafkaConfigService.getPassword());
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/topics")
    public ResponseEntity<?> listTopics() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "topics", kafkaConfigService.listTopics()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage(), "topics", new String[0]));
        }
    }

    @PostMapping("/writer/start")
    public ResponseEntity<?> startWriting(@RequestParam("file") MultipartFile file,
                                        @RequestParam(value = "targetSpeed", defaultValue = "1000") int targetSpeed,
                                        @RequestParam(value = "targetDataSpeed", defaultValue = "0") double targetDataSpeed) {
        try {
            continuousLogWriter.processFile(file, targetSpeed, targetDataSpeed);
            return ResponseEntity.ok(Map.of("success", true, "message", "Запись начата"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/writer/stop")
    public ResponseEntity<?> stopWriting() {
        try {
            continuousLogWriter.stop();
            return ResponseEntity.ok(Map.of("success", true, "message", "Запись остановлена"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/writer/metrics")
    public ResponseEntity<?> getMetrics() {
        try {
            return ResponseEntity.ok(continuousLogWriter.getMetrics());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
} 