package kafkareader.controller;

import kafkareader.dto.KafkaConfigDTO;
import kafkareader.dto.KafkaConnectionResult;
import kafkareader.service.KafkaConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaConfigController {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfigController.class);

    private final KafkaConfigService kafkaConfigService;

    @GetMapping("/config")
    public ResponseEntity<KafkaConfigDTO> getConfig() {
        KafkaConfigDTO config = new KafkaConfigDTO();
        config.setBootstrapServers(kafkaConfigService.getBootstrapServers());
        config.setTopic(kafkaConfigService.getTopic());
        config.setUsername(kafkaConfigService.getUsername());
        config.setPassword(kafkaConfigService.getPassword());
        return ResponseEntity.ok(config);
    }

    @PostMapping("/config")
    public ResponseEntity<KafkaConfigDTO> updateConfig(@RequestBody KafkaConfigDTO config) {
        kafkaConfigService.updateConfig(config);
        return getConfig();
    }

    @PostMapping("/check-connection")
    public ResponseEntity<KafkaConnectionResult> checkConnection(@RequestBody KafkaConfigDTO config) {
        KafkaConnectionResult result = kafkaConfigService.checkConnection(
            config.getBootstrapServers(),
            config.getTopic(),
            config.getUsername(),
            config.getPassword()
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/topic/info")
    public ResponseEntity<Map<String, Object>> getTopicInfo() {
        try {
            String topic = kafkaConfigService.getTopic();
            if (topic == null || topic.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Топик не выбран");
                return ResponseEntity.ok(response);
            }

            Map<String, Object> topicInfo = kafkaConfigService.getTopicInfo(topic);
            return ResponseEntity.ok(topicInfo);
        } catch (Exception e) {
            log.error("Ошибка при получении информации о топике:", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Ошибка при получении информации о топике: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/topic/create")
    public ResponseEntity<Map<String, Object>> createTopic(
            @RequestParam String topicName,
            @RequestParam(defaultValue = "1") int partitions,
            @RequestParam(defaultValue = "1") short replicationFactor) {
        try {
            kafkaConfigService.createTopic(topicName, partitions, replicationFactor);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Топик успешно создан");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при создании топика:", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Ошибка при создании топика: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/topics")
    public ResponseEntity<Map<String, Object>> listTopics() {
        try {
            Set<String> topics = kafkaConfigService.listTopics();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("topics", topics);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Ошибка при получении списка топиков:", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Ошибка при получении списка топиков: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 