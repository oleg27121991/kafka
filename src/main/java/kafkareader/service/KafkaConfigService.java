package kafkareader.service;

import kafkareader.entity.KafkaConfig;
import kafkareader.model.KafkaConfigDTO;
import kafkareader.model.KafkaConnectionResult;
import kafkareader.repository.KafkaConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG;
import static org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM;
import static org.apache.kafka.common.security.auth.SecurityProtocol.PLAINTEXT;
import static org.apache.kafka.common.security.auth.SecurityProtocol.SASL_PLAINTEXT;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Optional;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@Lazy
public class KafkaConfigService implements DisposableBean {
    private static final String SECURITY_PROTOCOL = "security.protocol";
    private static final String SASL_MECHANISM = "sasl.mechanism";
    private static final String SASL_JAAS_CONFIG = "sasl.jaas.config";

    private final ReentrantLock configLock = new ReentrantLock();
    private volatile AdminClient adminClient;
    private volatile KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaConfigRepository kafkaConfigRepository;
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private ContinuousLogWriter continuousLogWriter;

    private volatile boolean isCheckingConnection = false;
    private volatile long lastCheckTime = 0;
    private static final long CHECK_TIMEOUT = 5000; // 5 секунд таймаут на проверку

    private volatile AdminClient connectionCheckClient;
    private final Object connectionCheckLock = new Object();

    @Autowired
    public KafkaConfigService(KafkaConfigRepository kafkaConfigRepository) {
        this.kafkaConfigRepository = kafkaConfigRepository;
        log.info("KafkaConfigService инициализирован");
    }

    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("Инициализация KafkaConfigService...");
        Optional<KafkaConfig> activeConfig = kafkaConfigRepository.findByActiveTrue();
        
        if (activeConfig.isPresent()) {
            KafkaConfig config = activeConfig.get();
            log.info("Найдена активная конфигурация: bootstrapServers={}, topic={}, username={}, active={}",
                config.getBootstrapServers(), config.getTopic(), config.getUsername(), config.getActive());
            
            try {
                Map<String, Object> producerProps = new HashMap<>();
                producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
                producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "kafkareader-producer-" + System.currentTimeMillis());
                producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
                producerProps.put(ProducerConfig.RETRIES_CONFIG, 5);
                producerProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
                producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 60000);
                
                // Добавляем дополнительные параметры для улучшения подключения
                producerProps.put("client.dns.lookup", "use_all_dns_ips");
                producerProps.put("reconnect.backoff.ms", 1000);
                producerProps.put("reconnect.backoff.max.ms", 5000);
                producerProps.put("connections.max.idle.ms", 540000);
                producerProps.put("metadata.max.age.ms", 300000);
                producerProps.put("socket.connection.setup.timeout.ms", 10000);
                producerProps.put("socket.connection.setup.timeout.max.ms", 30000);
                
                if (config.getUsername() != null && !config.getUsername().isEmpty() && 
                    config.getPassword() != null && !config.getPassword().isEmpty()) {
                    log.info("Настраиваем SASL аутентификацию...");
                    producerProps.put("security.protocol", "SASL_PLAINTEXT");
                    producerProps.put("sasl.mechanism", "PLAIN");
                    producerProps.put("sasl.jaas.config", String.format(
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                        config.getUsername(), config.getPassword()
                    ));
                }
                
                log.info("Создаем ProducerFactory с настройками:");
                producerProps.forEach((key, value) -> {
                    if (key.toString().contains("password")) {
                        log.info("{} = ***", key);
                    } else {
                        log.info("{} = {}", key, value);
                    }
                });
                
                DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
                kafkaTemplate = new KafkaTemplate<>(producerFactory);
                if (config.getTopic() != null && !config.getTopic().isEmpty()) {
                    kafkaTemplate.setDefaultTopic(config.getTopic());
                    log.info("Установлен default topic: {}", config.getTopic());
                }
                log.info("KafkaTemplate успешно инициализирован");
                
                // Обновляем KafkaTemplate в ContinuousLogWriter
                if (continuousLogWriter != null) {
                    log.info("Обновляем KafkaTemplate в ContinuousLogWriter");
                    continuousLogWriter.setKafkaTemplate(kafkaTemplate);
                }
            } catch (Exception e) {
                log.error("Ошибка при инициализации KafkaTemplate: {}", e.getMessage(), e);
                kafkaTemplate = null;
            }
        } else {
            log.warn("Активная конфигурация не найдена");
            kafkaTemplate = null;
        }
    }

    private void closeAdminClient() {
        if (adminClient != null) {
            try {
                log.info("Закрытие AdminClient...");
                adminClient.close(Duration.ofSeconds(1));
                log.info("AdminClient успешно закрыт");
            } catch (Exception e) {
                log.warn("Ошибка при закрытии AdminClient: {}", e.getMessage());
            } finally {
                adminClient = null;
            }
        }
    }

    @Override
    public void destroy() {
        closeAdminClient();
        if (kafkaTemplate != null) {
            try {
                ((DisposableBean) kafkaTemplate).destroy();
                kafkaTemplate = null;
            } catch (Exception e) {
                log.warn("Ошибка при закрытии KafkaTemplate", e);
            }
        }
    }

    private void closeAllConnections() {
        log.info("Закрытие всех соединений с Kafka...");
        if (adminClient != null) {
            try {
                adminClient.close(Duration.ofSeconds(5));
                adminClient = null;
            } catch (Exception e) {
                log.error("Ошибка при закрытии AdminClient: {}", e.getMessage());
            }
        }
        if (kafkaTemplate != null) {
            try {
                kafkaTemplate.flush();
                kafkaTemplate = null;
        } catch (Exception e) {
                log.error("Ошибка при закрытии KafkaTemplate: {}", e.getMessage());
            }
        }
    }

    private void cleanupOldTopics(String bootstrapServers) {
        log.info("Очистка старых топиков для bootstrap.servers={}", bootstrapServers);
        try {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-cleanup-" + UUID.randomUUID().toString());
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
            props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 60000);
            
            try (AdminClient client = AdminClient.create(props)) {
                Set<String> topics = client.listTopics().names().get(30, TimeUnit.SECONDS);
                log.info("Найдено {} топиков для очистки", topics.size());
                
                // Удаляем все топики, кроме системных
                topics.stream()
                    .filter(topic -> !topic.startsWith("__"))
                    .forEach(topic -> {
                        try {
                            log.info("Удаление топика: {}", topic);
                            client.deleteTopics(Collections.singleton(topic))
                                .all()
                                .get(30, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.error("Ошибка при удалении топика {}: {}", topic, e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            log.error("Ошибка при очистке топиков: {}", e.getMessage());
        }
    }

    @Transactional
    public KafkaConnectionResult updateConfig(String bootstrapServers, String topic, String username, String password) {
        configLock.lock();
        try {
            log.info("Обновление конфигурации: bootstrapServers={}, topic={}, username={}", 
                bootstrapServers, topic, username);
            
            // Проверяем обязательные параметры
            if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
                log.error("Bootstrap servers не может быть пустым");
                return new KafkaConnectionResult(false, "Bootstrap servers не может быть пустым");
            }
            
            // Закрываем все существующие соединения
            log.info("Закрываем существующие соединения...");
            closeAllConnections();

            // Создаем новую конфигурацию
            KafkaConfig newConfig = new KafkaConfig();
            newConfig.setBootstrapServers(bootstrapServers);
            // Устанавливаем топик только если он явно указан
            if (topic != null && !topic.trim().isEmpty()) {
                newConfig.setTopic(topic);
            }
            newConfig.setUsername(username);
            newConfig.setPassword(password);
            newConfig.setActive(true);
            
            // Деактивируем все существующие конфигурации
            log.info("Деактивируем старые конфигурации...");
            kafkaConfigRepository.findAll().forEach(config -> {
                log.info("Деактивация конфигурации: id={}, bootstrapServers={}, active={}", 
                    config.getId(), config.getBootstrapServers(), config.getActive());
                config.setActive(false);
                kafkaConfigRepository.save(config);
            });
            
            // Сохраняем новую конфигурацию
            log.info("Сохраняем новую конфигурацию...");
            KafkaConfig savedConfig = kafkaConfigRepository.save(newConfig);
            
            log.info("Создана новая конфигурация: id={}, bootstrapServers={}, topic={}, active={}", 
                savedConfig.getId(), savedConfig.getBootstrapServers(), savedConfig.getTopic(), savedConfig.getActive());

            // Проверяем подключение к Kafka
            try {
                log.info("Создаем AdminClient...");
                Properties props = new Properties();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                props.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-config-check-" + System.currentTimeMillis());
                props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
                props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 60000);
                
                // Добавляем дополнительные параметры для улучшения подключения
                props.put("client.dns.lookup", "use_all_dns_ips");
                props.put("reconnect.backoff.ms", 1000);
                props.put("reconnect.backoff.max.ms", 5000);
                props.put("connections.max.idle.ms", 540000);
                props.put("metadata.max.age.ms", 300000);
                props.put("socket.connection.setup.timeout.ms", 10000);
                props.put("socket.connection.setup.timeout.max.ms", 30000);
                
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    log.info("Добавляем настройки безопасности...");
                    props.put(SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                    props.put(SASL_MECHANISM, "PLAIN");
                    props.put(SASL_JAAS_CONFIG, String.format(
                    "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                    username, password
                ));
            }

                adminClient = AdminClient.create(props);
                log.info("AdminClient успешно создан");

                // Проверяем существование топика только если он указан
                if (topic != null && !topic.trim().isEmpty()) {
                    log.info("Проверяем существование топика: {}", topic);
                    try {
                        TopicDescription topicDescription = adminClient.describeTopics(Collections.singletonList(topic))
                            .all()
                            .get(30, TimeUnit.SECONDS)
                            .get(topic);
                        
                        log.info("Топик {} существует: партиций={}, внутренний={}", 
                            topic, topicDescription.partitions().size(), topicDescription.isInternal());
                    } catch (ExecutionException e) {
                        if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                            log.info("Топик {} не существует, создаем...", topic);
                            createTopic(topic, 32, (short)1);
                        } else {
                            throw e;
                        }
                    }
                }
                
                // Создаем новый KafkaTemplate
                log.info("Создаем KafkaTemplate...");
                Map<String, Object> producerProps = new HashMap<>();
                producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                producerProps.put(ProducerConfig.CLIENT_ID_CONFIG, "kafkareader-producer-" + System.currentTimeMillis());
                producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
                producerProps.put(ProducerConfig.RETRIES_CONFIG, 5);
                producerProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
                producerProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 60000);
                
                // Добавляем дополнительные параметры для улучшения подключения
                producerProps.put("client.dns.lookup", "use_all_dns_ips");
                producerProps.put("reconnect.backoff.ms", 1000);
                producerProps.put("reconnect.backoff.max.ms", 5000);
                producerProps.put("connections.max.idle.ms", 540000);
                producerProps.put("metadata.max.age.ms", 300000);
                producerProps.put("socket.connection.setup.timeout.ms", 10000);
                producerProps.put("socket.connection.setup.timeout.max.ms", 30000);
                
                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    log.info("Добавляем настройки безопасности в KafkaTemplate...");
                    producerProps.put(SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                    producerProps.put(SASL_MECHANISM, "PLAIN");
                    producerProps.put(SASL_JAAS_CONFIG, String.format(
                        "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                        username, password
                    ));
                }
                
                DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
                kafkaTemplate = new KafkaTemplate<>(producerFactory);
                if (topic != null && !topic.trim().isEmpty()) {
                    log.info("Устанавливаем топик по умолчанию: {}", topic);
                    kafkaTemplate.setDefaultTopic(topic);
                }
                
                // Обновляем KafkaTemplate в ContinuousLogWriter
                if (continuousLogWriter != null) {
                    log.info("Обновляем KafkaTemplate в ContinuousLogWriter");
                    continuousLogWriter.setKafkaTemplate(kafkaTemplate);
                }
                
                // Обновляем топик в ContinuousLogWriter только если он указан
                if (continuousLogWriter != null && topic != null && !topic.trim().isEmpty()) {
                    log.info("Обновляем топик в ContinuousLogWriter: {}", topic);
                    continuousLogWriter.updateTopic(topic);
                }
                
                log.info("=== Конфигурация успешно обновлена ===");
                return new KafkaConnectionResult(true, "Подключение успешно установлено");
            } catch (Exception e) {
                log.error("Ошибка при проверке подключения: {}", e.getMessage(), e);
                return new KafkaConnectionResult(false, "Ошибка при проверке подключения: " + e.getMessage());
            }
        } finally {
            configLock.unlock();
        }
    }

    public void updateConfig(KafkaConfigDTO config) {
        updateConfig(config.getBootstrapServers(), config.getTopic(), config.getUsername(), config.getPassword());
    }

    public KafkaConnectionResult checkConnection(String bootstrapServers, String topic, String username, String password) {
        KafkaConnectionResult result = new KafkaConnectionResult();
        result.setSuccess(false);

        if (bootstrapServers == null || bootstrapServers.trim().isEmpty()) {
            result.setMessage("Bootstrap servers не указаны");
            return result;
        }

        // Проверяем, не выполняется ли уже проверка подключения
        if (isCheckingConnection) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastCheckTime < CHECK_TIMEOUT) {
                result.setMessage("Проверка подключения уже выполняется. Пожалуйста, подождите.");
                return result;
            }
        }

        isCheckingConnection = true;
        lastCheckTime = System.currentTimeMillis();

        synchronized (connectionCheckLock) {
            try {
                // Закрываем предыдущий клиент, если он существует
                if (connectionCheckClient != null) {
                    try {
                        connectionCheckClient.close(Duration.ofSeconds(1));
                    } catch (Exception e) {
                        log.warn("Ошибка при закрытии предыдущего AdminClient: {}", e.getMessage());
                    }
                    connectionCheckClient = null;
                }

                Map<String, Object> props = new HashMap<>();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
                props.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-connection-check");
                props.put(SECURITY_PROTOCOL, "PLAINTEXT");

                if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
                    props.put(SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                    props.put(SASL_MECHANISM, "PLAIN");
                    props.put(SASL_JAAS_CONFIG, 
                        String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";", 
                            username, password));
                }

                // Создаем новый клиент
                connectionCheckClient = AdminClient.create(props);
                
                // Проверяем подключение с таймаутом
                Set<String> topics = connectionCheckClient.listTopics().names().get(5, TimeUnit.SECONDS);
                result.setSuccess(true);
                result.setMessage("Подключение к Kafka успешно установлено. Доступные топики: " + topics);

                // Если указан топик, проверяем его существование
                if (topic != null && !topic.trim().isEmpty()) {
                    if (topics.contains(topic)) {
                        result.setMessage("Топик " + topic + " существует");
                    } else {
                        result.setMessage("Топик " + topic + " не существует");
                        // Создаем топик, если его нет
                        try {
                            NewTopic newTopic = new NewTopic(topic, 1, (short) 1);
                            CreateTopicsResult createResult = connectionCheckClient.createTopics(Collections.singleton(newTopic));
                            createResult.all().get(5, TimeUnit.SECONDS);
                            result.setMessage("Топик " + topic + " успешно создан");
                        } catch (Exception e) {
                            log.error("Ошибка при создании топика {}: {}", topic, e.getMessage());
                            result.setMessage("Ошибка при создании топика " + topic + ": " + e.getMessage());
                            result.setSuccess(false);
                            return result;
                        }
                    }
                }

                // Если подключение успешно, сохраняем конфигурацию
                if (result.isSuccess()) {
                    try {
                        // Деактивируем все существующие конфигурации
                        kafkaConfigRepository.findAll().forEach(config -> {
                            config.setActive(false);
                            kafkaConfigRepository.save(config);
                        });

                        // Создаем новую конфигурацию
                        KafkaConfig newConfig = new KafkaConfig();
                        newConfig.setBootstrapServers(bootstrapServers);
                        newConfig.setTopic(topic);
                        newConfig.setUsername(username);
                        newConfig.setPassword(password);
                        newConfig.setActive(true);

                        // Сохраняем новую конфигурацию
                        kafkaConfigRepository.save(newConfig);
                        log.info("Конфигурация успешно сохранена: bootstrapServers={}, topic={}", bootstrapServers, topic);
                    } catch (Exception e) {
                        log.error("Ошибка при сохранении конфигурации: {}", e.getMessage());
                        result.setMessage("Подключение успешно, но не удалось сохранить конфигурацию: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при проверке подключения: {}", e.getMessage());
                result.setSuccess(false);
                String errorMessage = e.getMessage();
                if (errorMessage == null || errorMessage.isEmpty()) {
                    errorMessage = "Проверьте правильность адреса и доступность сервера Kafka";
                } else if (errorMessage.contains("Can't assign requested address")) {
                    errorMessage = "Не удалось подключиться к указанному адресу. Проверьте правильность адреса и доступность сервера";
                } else if (errorMessage.contains("Connection refused")) {
                    errorMessage = "Соединение отклонено. Проверьте, запущен ли сервер Kafka и доступен ли указанный порт";
                }
                result.setMessage("Не удалось подключиться к Kafka: " + errorMessage);
            } finally {
                // Закрываем клиент после использования
                if (connectionCheckClient != null) {
                    try {
                        connectionCheckClient.close(Duration.ofSeconds(1));
                    } catch (Exception e) {
                        log.warn("Ошибка при закрытии AdminClient: {}", e.getMessage());
                    }
                    connectionCheckClient = null;
                }
                isCheckingConnection = false;
            }
        }

        return result;
    }

    public void createTopic(String topicName, int partitions, short replicationFactor) {
        configLock.lock();
        try {
            KafkaConfig config = kafkaConfigRepository.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("Активная конфигурация не найдена"));

            try {
                Map<String, Object> props = new HashMap<>();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
                props.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-topic-creator-" + UUID.randomUUID().toString());
                props.put(SECURITY_PROTOCOL, "PLAINTEXT");

                if (config.getUsername() != null && !config.getUsername().isEmpty() && 
                    config.getPassword() != null && !config.getPassword().isEmpty()) {
                    props.put(SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                    props.put(SASL_MECHANISM, "PLAIN");
                    props.put(SASL_JAAS_CONFIG, 
                        String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";", 
                            config.getUsername(), config.getPassword()));
                }

                try (AdminClient client = AdminClient.create(props)) {
                    NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
                    CreateTopicsResult result = client.createTopics(Collections.singleton(newTopic));
                    result.all().get(30, TimeUnit.SECONDS);
                    log.info("Топик {} успешно создан", topicName);
                }
            } catch (Exception e) {
                log.error("Ошибка при создании топика {}: {}", topicName, e.getMessage());
                throw new RuntimeException("Не удалось создать топик: " + e.getMessage());
            }
        } finally {
            configLock.unlock();
        }
    }

    public Set<String> listTopics() {
        configLock.lock();
        try {
            KafkaConfig config = kafkaConfigRepository.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("Активная конфигурация не найдена"));

            if (config.getBootstrapServers() == null || config.getBootstrapServers().isEmpty()) {
                throw new RuntimeException("Bootstrap servers не настроены");
            }

            Map<String, Object> props = new HashMap<>();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
            props.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-topic-lister-" + UUID.randomUUID().toString());
            props.put(SECURITY_PROTOCOL, "PLAINTEXT");
            props.put("request.timeout.ms", 30000);

            if (config.getUsername() != null && !config.getUsername().isEmpty() && 
                config.getPassword() != null && !config.getPassword().isEmpty()) {
                props.put(SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                props.put(SASL_MECHANISM, "PLAIN");
                props.put(SASL_JAAS_CONFIG, 
                    String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";", 
                        config.getUsername(), config.getPassword()));
            }

            log.info("Получение списка топиков с bootstrap.servers={}", config.getBootstrapServers());
            try (AdminClient client = AdminClient.create(props)) {
                Set<String> topics = client.listTopics().names().get(30, TimeUnit.SECONDS);
                log.info("Получено {} топиков", topics.size());
                return topics;
            }
        } catch (Exception e) {
            log.error("Ошибка при получении списка топиков: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось получить список топиков: " + e.getMessage());
            } finally {
            configLock.unlock();
        }
    }

    public String getBootstrapServers() {
        String servers = kafkaConfigRepository.findByActiveTrue()
            .map(KafkaConfig::getBootstrapServers)
            .orElse(null);
        log.info("Получены bootstrap servers: {}", servers);
        return servers;
    }

    public String getTopic() {
        String topic = kafkaConfigRepository.findByActiveTrue()
            .map(KafkaConfig::getTopic)
            .orElse(null);
        log.info("Получен topic: {}", topic);
        return topic;
    }

    public String getUsername() {
        String username = kafkaConfigRepository.findByActiveTrue()
            .map(KafkaConfig::getUsername)
            .orElse(null);
        log.debug("Получен username из БД: {}", username);
        return username;
    }

    public String getPassword() {
        String password = kafkaConfigRepository.findByActiveTrue()
            .map(KafkaConfig::getPassword)
            .orElse(null);
        log.debug("Получен password из БД: {}", password != null ? "***" : null);
        return password;
    }

    public Map<String, Object> getTopicInfo(String topicName) {
        configLock.lock();
        try {
            KafkaConfig config = kafkaConfigRepository.findByActiveTrue()
                .orElseThrow(() -> new RuntimeException("Активная конфигурация не найдена"));

            try {
                Map<String, Object> props = new HashMap<>();
                props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
                props.put(AdminClientConfig.CLIENT_ID_CONFIG, "kafkareader-topic-info-" + UUID.randomUUID().toString());
                props.put(SECURITY_PROTOCOL, "PLAINTEXT");

                if (config.getUsername() != null && !config.getUsername().isEmpty() && 
                    config.getPassword() != null && !config.getPassword().isEmpty()) {
                    props.put(SECURITY_PROTOCOL, "SASL_PLAINTEXT");
                    props.put(SASL_MECHANISM, "PLAIN");
                    props.put(SASL_JAAS_CONFIG, 
                        String.format("org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";", 
                            config.getUsername(), config.getPassword()));
                }

                try (AdminClient client = AdminClient.create(props)) {
                    Map<String, TopicDescription> descriptions = client.describeTopics(Collections.singleton(topicName))
                        .all()
                        .get(30, TimeUnit.SECONDS);

                    TopicDescription description = descriptions.get(topicName);
                    Map<String, Object> topicInfo = new HashMap<>();
                    topicInfo.put("name", description.name());
                    topicInfo.put("internal", description.isInternal());
                    topicInfo.put("partitions", description.partitions().size());
                    
                    // Добавляем информацию о партициях
                    List<Map<String, Object>> partitionsInfo = new ArrayList<>();
                    for (org.apache.kafka.common.TopicPartitionInfo partition : description.partitions()) {
                        Map<String, Object> partitionInfo = new HashMap<>();
                        partitionInfo.put("partition", partition.partition());
                        partitionInfo.put("leader", partition.leader() != null ? partition.leader().id() : -1);
                        partitionInfo.put("replicas", partition.replicas().stream()
                            .map(node -> node.id())
                            .collect(Collectors.toList()));
                        partitionInfo.put("isr", partition.isr().stream()
                            .map(node -> node.id())
                            .collect(Collectors.toList()));
                        partitionsInfo.add(partitionInfo);
                    }
                    topicInfo.put("partitionsInfo", partitionsInfo);

                    return topicInfo;
                }
            } catch (Exception e) {
                log.error("Ошибка при получении информации о топике {}: {}", topicName, e.getMessage());
                throw new RuntimeException("Не удалось получить информацию о топике: " + e.getMessage());
            }
        } finally {
            configLock.unlock();
        }
    }
} 