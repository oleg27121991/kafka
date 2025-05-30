package kafkareader.service;

import kafkareader.model.KafkaConfig;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

public interface KafkaService {
    void testConnection(KafkaConfig config) throws Exception;
    void saveConfig(KafkaConfig config) throws Exception;
    void processFile(MultipartFile file, double targetSpeed, double targetDataSpeed) throws Exception;
    void stop();
    Map<String, Object> getMetrics();
} 