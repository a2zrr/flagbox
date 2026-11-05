package dev.a2zrr.flagbox.project;

import dev.a2zrr.flagbox.common.Slug;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** A project groups the flags of one application or service. */
@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "key", nullable = false, unique = true, updatable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Project(String key, String name, String description) {
        Slug.requireValid(key, "key");
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name must be 1 to 120 characters");
        }
        this.key = key;
        this.name = name.strip();
        this.description = description;
    }
}
