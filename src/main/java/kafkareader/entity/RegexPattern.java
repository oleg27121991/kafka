package kafkareader.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name = "regex_patterns")
public class RegexPattern {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 1024)
    private String pattern;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regex_group_id")
    @JsonBackReference
    private RegexGroup regexGroup;
}