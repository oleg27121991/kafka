package kafkareader.config;

import kafkareader.service.KafkaConfigService;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.core.KafkaAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.HashMap;
import java.util.Map;

@Configuration
@Lazy
public class KafkaConfig {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConfig.class);

    @Autowired(required = false)
    private KafkaConfigService kafkaConfigService;

    @Bean
    @Lazy
    public KafkaAdmin kafkaAdmin() {
        if (kafkaConfigService == null) {
            logger.warn("KafkaConfigService не инициализирован");
            return null;
        }
        
        String bootstrapServers = kafkaConfigService.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            logger.warn("Bootstrap servers not configured yet");
            return null;
        }
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configs.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-admin");
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 60000);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 60000);
        configs.put(AdminClientConfig.RETRIES_CONFIG, 5);
        configs.put(AdminClientConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        configs.put(AdminClientConfig.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        
        configs.put("client.dns.lookup", "use_all_dns_ips");
        configs.put("reconnect.backoff.ms", 1000);
        configs.put("reconnect.backoff.max.ms", 5000);
        configs.put("connections.max.idle.ms", 540000);
        configs.put("metadata.max.age.ms", 300000);
        configs.put("socket.connection.setup.timeout.ms", 10000);
        configs.put("socket.connection.setup.timeout.max.ms", 30000);
        
        String username = kafkaConfigService.getUsername();
        String password = kafkaConfigService.getPassword();
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            configs.put("security.protocol", "SASL_PLAINTEXT");
            configs.put("sasl.mechanism", "PLAIN");
            configs.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password
            ));
        }
        
        logger.info("Создание KafkaAdmin с настройками:");
        configs.forEach((key, value) -> {
            if (key.toString().contains("password")) {
                logger.info("{} = ***", key);
            } else {
                logger.info("{} = {}", key, value);
            }
        });
        
        logger.info("Creating KafkaAdmin with bootstrap servers: {}", bootstrapServers);
        return new KafkaAdmin(configs);
    }

    @Bean
    public Map<String, Object> producerConfigs() {
        logger.info("Создание producer configs...");
        String bootstrapServers = kafkaConfigService.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            logger.warn("Bootstrap servers не настроены");
            return new HashMap<>();
        }
        logger.info("Bootstrap servers: {}", bootstrapServers);

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "kafkareader-producer-" + System.currentTimeMillis());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 60000);
        
        // Добавляем дополнительные параметры для улучшения подключения
        props.put("client.dns.lookup", "use_all_dns_ips");
        props.put("reconnect.backoff.ms", 1000);
        props.put("reconnect.backoff.max.ms", 5000);
        props.put("connections.max.idle.ms", 540000);
        props.put("metadata.max.age.ms", 300000);
        props.put("socket.connection.setup.timeout.ms", 10000);
        props.put("socket.connection.setup.timeout.max.ms", 30000);
        
        String username = kafkaConfigService.getUsername();
        String password = kafkaConfigService.getPassword();
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            props.put("security.protocol", "SASL_PLAINTEXT");
            props.put("sasl.mechanism", "PLAIN");
            props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password
            ));
        }
        
        logger.info("Producer config создан с настройками:");
        props.forEach((key, value) -> {
            if (key.toString().contains("password")) {
                logger.info("{} = ***", key);
            } else {
                logger.info("{} = {}", key, value);
            }
        });
        
        return props;
    }

    @Bean
    @Lazy
    @ConditionalOnProperty(name = "kafka.bootstrap-servers")
    public ProducerFactory<String, String> producerFactory() {
        logger.info("Создание ProducerFactory...");
        String bootstrapServers = kafkaConfigService.getBootstrapServers();
        
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            logger.warn("Bootstrap servers не настроены, ProducerFactory не будет создан");
            return null;
        }
        
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "kafkareader-producer-" + System.currentTimeMillis());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 5);
        producerProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 120000);
        producerProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 120000);
        
        // Добавляем дополнительные параметры для улучшения подключения
        producerProps.put("client.dns.lookup", "use_all_dns_ips");
        producerProps.put("reconnect.backoff.ms", 1000);
        producerProps.put("reconnect.backoff.max.ms", 5000);
        producerProps.put("connections.max.idle.ms", 540000);
        producerProps.put("metadata.max.age.ms", 300000);
        producerProps.put("socket.connection.setup.timeout.ms", 30000);
        producerProps.put("socket.connection.setup.timeout.max.ms", 60000);
        producerProps.put("socket.timeout.ms", 60000);
        
        String username = kafkaConfigService.getUsername();
        String password = kafkaConfigService.getPassword();
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            logger.info("Настраиваем SASL аутентификацию...");
            producerProps.put("security.protocol", "SASL_PLAINTEXT");
            producerProps.put("sasl.mechanism", "PLAIN");
            producerProps.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password
            ));
        }
        
        logger.info("Создаем ProducerFactory с настройками:");
        producerProps.forEach((key, value) -> {
            if (key.toString().contains("password")) {
                logger.info("{} = ***", key);
            } else {
                logger.info("{} = {}", key, value);
            }
        });
        
        try {
            DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(producerProps);
            logger.info("ProducerFactory успешно создан");
            return factory;
        } catch (Exception e) {
            logger.error("Ошибка при создании ProducerFactory: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean
    @Lazy
    @ConditionalOnProperty(name = "kafka.bootstrap-servers")
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        logger.info("Создание KafkaTemplate...");
        if (producerFactory == null) {
            logger.warn("ProducerFactory не создан, KafkaTemplate не будет создан");
            return null;
        }
        
        String topic = kafkaConfigService.getTopic();
        logger.info("Используем topic: {}", topic);
        
        try {
            KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
            if (topic != null && !topic.isEmpty()) {
                template.setDefaultTopic(topic);
                logger.info("Установлен default topic: {}", topic);
            }
            logger.info("KafkaTemplate успешно создан");
            return template;
        } catch (Exception e) {
            logger.error("Ошибка при создании KafkaTemplate: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean
    @Lazy
    public KafkaProducer<String, String> kafkaProducer(ProducerFactory<String, String> producerFactory) {
        logger.info("Создание KafkaProducer...");
        if (producerFactory == null) {
            logger.warn("ProducerFactory не создан, KafkaProducer не будет создан");
            return null;
        }

        try {
            KafkaProducer<String, String> producer = new KafkaProducer<>(producerFactory.getConfigurationProperties());
            logger.info("KafkaProducer успешно создан");
            return producer;
        } catch (Exception e) {
            logger.error("Ошибка при создании KafkaProducer: {}", e.getMessage(), e);
            return null;
        }
    }

    @Bean
    public Map<String, Object> consumerConfigs() {
        String bootstrapServers = kafkaConfigService.getBootstrapServers();
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            logger.warn("Bootstrap servers not configured yet");
            return new HashMap<>();
        }

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkareader-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "kafkareader-consumer");
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        
        String username = kafkaConfigService.getUsername();
        String password = kafkaConfigService.getPassword();
        
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            props.put("security.protocol", "SASL_PLAINTEXT");
            props.put("sasl.mechanism", "PLAIN");
            props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password
            ));
        }
        
        logger.info("Creating consumer config with bootstrap servers: {}", bootstrapServers);
        return props;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> configs = consumerConfigs();
        if (configs.isEmpty()) {
            logger.warn("Consumer config is empty, returning null factory");
            return null;
        }
        logger.info("Creating consumer factory with config: {}", configs);
        return new DefaultKafkaConsumerFactory<>(configs);
    }

    @Bean
    @Lazy
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConsumerFactory<String, String> factory = consumerFactory();
        if (factory == null) {
            logger.warn("Consumer factory is null, returning null container factory");
            return null;
        }
        ConcurrentKafkaListenerContainerFactory<String, String> containerFactory = new ConcurrentKafkaListenerContainerFactory<>();
        containerFactory.setConsumerFactory(factory);
        containerFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        logger.info("Created KafkaListenerContainerFactory with manual ack mode");
        return containerFactory;
    }
} 