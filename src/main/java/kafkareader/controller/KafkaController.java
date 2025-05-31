package kafkareader.controller;

import kafkareader.model.KafkaConfigDTO;
import kafkareader.model.KafkaConnectionResult;
import kafkareader.service.KafkaConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
public class KafkaController {

    @Autowired
    private KafkaConfigService kafkaConfigService;

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
} 