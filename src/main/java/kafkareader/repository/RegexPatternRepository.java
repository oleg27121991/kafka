package kafkareader.repository;

import kafkareader.entity.RegexPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegexPatternRepository extends JpaRepository<RegexPattern, Long> {
}