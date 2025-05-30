package kafkareader.dto;

import lombok.Data;

@Data
public class KafkaConnectionCheck {
    private String bootstrapServers;
    private String topic;
    private boolean success;
    private String message;
} 