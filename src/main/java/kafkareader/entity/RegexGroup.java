package kafkareader.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "regex_groups")
public class RegexGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(
            mappedBy = "regexGroup",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.EAGER
    )
    @JsonManagedReference
    private List<RegexPattern> patterns;

    public void addPattern(RegexPattern pattern) {
        patterns.add(pattern);
        pattern.setRegexGroup(this);
    }

    public void removePattern(RegexPattern pattern) {
        patterns.remove(pattern);
        pattern.setRegexGroup(null);
    }
}