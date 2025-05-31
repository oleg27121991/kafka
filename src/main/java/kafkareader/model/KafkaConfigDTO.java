package kafkareader.model;

import lombok.Data;

@Data
public class KafkaConfigDTO {
    private String bootstrapServers;
    private String topic;
    private String username;
    private String password;
} 