package dev.a2zrr.flagbox.environment;

import dev.a2zrr.flagbox.common.Slug;
import dev.a2zrr.flagbox.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** An environment (for example development or production) within a project. */
@Entity
@Table(name = "environments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, updatable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 120)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Environment(Project project, String key, String name) {
        if (project == null) {
            throw new IllegalArgumentException("project is required");
        }
        Slug.requireValid(key, "key");
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name must be 1 to 120 characters");
        }
        this.project = project;
        this.key = key;
        this.name = name.strip();
    }
}
