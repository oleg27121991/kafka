package kafkareader.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaConnectionResult {
    private String bootstrapServers;
    private String topic;
    private boolean success;
    private String message;

    public KafkaConnectionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
        this.bootstrapServers = null;
        this.topic = null;
    }
} 