package kafkareader.repository;

import kafkareader.entity.RegexGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RegexGroupRepository extends JpaRepository<RegexGroup, Long> {
    Optional<RegexGroup> findByName(String name);
}