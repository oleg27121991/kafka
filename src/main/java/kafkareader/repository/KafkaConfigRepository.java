package kafkareader.repository;

import kafkareader.entity.KafkaConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KafkaConfigRepository extends JpaRepository<KafkaConfig, Long> {
    Optional<KafkaConfig> findByActiveTrue();
} 