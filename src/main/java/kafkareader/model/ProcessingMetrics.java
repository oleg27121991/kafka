package kafkareader.model;

import java.util.Map;
import java.util.HashMap;

public class ProcessingMetrics {
    private String status; // "processing", "completed", "failed"
    private long totalLines;
    private long processedLines;
    private double speed;
    private double progress;
    private Map<String, PartitionStats> partitions;
    private String error;

    public ProcessingMetrics() {
        this.partitions = new HashMap<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(long totalLines) {
        this.totalLines = totalLines;
    }

    public long getProcessedLines() {
        return processedLines;
    }

    public void setProcessedLines(long processedLines) {
        this.processedLines = processedLines;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public Map<String, PartitionStats> getPartitions() {
        return partitions;
    }

    public void setPartitions(Map<String, PartitionStats> partitions) {
        this.partitions = partitions;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public static class PartitionStats {
        private long lines;
        private long bytes;

        public PartitionStats() {
        }

        public PartitionStats(long lines, long bytes) {
            this.lines = lines;
            this.bytes = bytes;
        }

        public long getLines() {
            return lines;
        }

        public void setLines(long lines) {
            this.lines = lines;
        }

        public long getBytes() {
            return bytes;
        }

        public void setBytes(long bytes) {
            this.bytes = bytes;
        }
    }
} 