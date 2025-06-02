package kafkareader.service;

import kafkareader.model.ProcessingMetrics;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
@Lazy
public class SingleFileProcessor {
    private static final Logger log = LoggerFactory.getLogger(SingleFileProcessor.class);

    private KafkaProducer<String, String> kafkaProducer;
    private final Map<String, ProcessingMetrics> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Thread> processingThreads = new ConcurrentHashMap<>();

    private KafkaProducer<String, String> createKafkaProducer(String bootstrapServers, String username, String password) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "kafkareader-producer-" + System.currentTimeMillis());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 5);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 120000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 120000);

        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            props.put("security.protocol", "SASL_PLAINTEXT");
            props.put("sasl.mechanism", "PLAIN");
            props.put("sasl.jaas.config", String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password
            ));
        }

        return new KafkaProducer<>(props);
    }

    public void processFile(String processId, MultipartFile file, String logLevelPattern, int batchSize,
                          String bootstrapServers, String topic, String username, String password) {
        log.info("Создаем KafkaProducer для processId: {}", processId);
        kafkaProducer = createKafkaProducer(bootstrapServers, username, password);

        ProcessingMetrics metrics = new ProcessingMetrics();
        metrics.setStatus("processing");
        processingMetrics.put(processId, metrics);

        Thread processingThread = new Thread(() -> {
            try {
                processFileContent(processId, file, logLevelPattern, batchSize, topic);
                metrics.setStatus("completed");
            } catch (Exception e) {
                metrics.setStatus("failed");
                metrics.setError(e.getMessage());
            } finally {
                if (kafkaProducer != null) {
                    kafkaProducer.close();
                    kafkaProducer = null;
                }
            }
        });

        processingThreads.put(processId, processingThread);
        processingThread.start();
    }

    private void processFileContent(String processId, MultipartFile file, String logLevelPattern, int batchSize, String topic) throws Exception {
        ProcessingMetrics metrics = processingMetrics.get(processId);
        Pattern pattern = Pattern.compile(logLevelPattern);
        Map<String, List<String>> partitionBuffers = new HashMap<>();
        long startTime = System.currentTimeMillis();
        long lastUpdateTime = startTime;
        long totalLines = 0;
        long processedLines = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            String currentPartition = null;
            StringBuilder currentMessage = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                totalLines++;
                
                if (pattern.matcher(line).find()) {
                    if (currentPartition != null && currentMessage.length() > 0) {
                        String message = currentMessage.toString();
                        partitionBuffers.computeIfAbsent(currentPartition, k -> new ArrayList<>())
                                .add(message);
                        processedLines++;
                        
                        ProcessingMetrics.PartitionStats stats = metrics.getPartitions()
                                .computeIfAbsent(currentPartition, k -> new ProcessingMetrics.PartitionStats());
                        stats.setLines(stats.getLines() + 1);
                        stats.setBytes(stats.getBytes() + message.getBytes(StandardCharsets.UTF_8).length);
                    }
                    
                    currentPartition = line;
                    currentMessage = new StringBuilder(line);
                } else if (currentPartition != null) {
                    currentMessage.append("\n").append(line);
                }

                if (processedLines % batchSize == 0) {
                    sendBatchToKafka(partitionBuffers, topic);
                    partitionBuffers.clear();
                }

                long currentTime = System.currentTimeMillis();
                if (currentTime - lastUpdateTime >= 1000) {
                    updateMetrics(metrics, totalLines, processedLines, startTime);
                    lastUpdateTime = currentTime;
                }
            }

            if (!partitionBuffers.isEmpty()) {
                sendBatchToKafka(partitionBuffers, topic);
            }

            updateMetrics(metrics, totalLines, processedLines, startTime);
        }
    }

    private void sendBatchToKafka(Map<String, List<String>> partitionBuffers, String topic) {
        partitionBuffers.forEach((partition, messages) -> {
            messages.forEach(message -> {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    topic,
                    partition,
                    message
                );
                kafkaProducer.send(record);
            });
        });
    }

    private void updateMetrics(ProcessingMetrics metrics, long totalLines, long processedLines, long startTime) {
        long currentTime = System.currentTimeMillis();
        double elapsedSeconds = (currentTime - startTime) / 1000.0;
        
        metrics.setTotalLines(totalLines);
        metrics.setProcessedLines(processedLines);
        metrics.setSpeed(processedLines / elapsedSeconds);
        metrics.setProgress((double) processedLines / totalLines * 100);
    }

    public ProcessingMetrics getMetrics(String processId) {
        ProcessingMetrics metrics = processingMetrics.get(processId);
        if (metrics == null) {
            throw new IllegalArgumentException("Process not found: " + processId);
        }
        return metrics;
    }

    public void cleanup(String processId) {
        processingMetrics.remove(processId);
        Thread thread = processingThreads.remove(processId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }
} 