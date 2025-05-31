package kafkareader.model;

import lombok.Data;

@Data
public class KafkaConnectionResult {
    private boolean success;
    private String message;
    private String bootstrapServers;
    private String topic;

    public KafkaConnectionResult() {
    }

    public KafkaConnectionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
} 