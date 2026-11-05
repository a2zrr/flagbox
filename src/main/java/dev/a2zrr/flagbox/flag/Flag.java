package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.common.Slug;
import dev.a2zrr.flagbox.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A feature flag. The flag itself holds identity and metadata; how it behaves in
 * each environment lives in {@link FlagConfig}.
 */
@Entity
@Table(name = "flags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flag {

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

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlagType type;

    @Column(nullable = false)
    private boolean archived;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Flag(Project project, String key, String name, String description) {
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
        this.description = description;
        this.type = FlagType.BOOLEAN;
        this.archived = false;
    }

    public void updateDetails(String name, String description) {
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name must be 1 to 120 characters");
        }
        this.name = name.strip();
        this.description = description;
    }

    public void archive() {
        this.archived = true;
    }

    public void restore() {
        this.archived = false;
    }
}
