package dev.a2zrr.flagbox.apikey;

import dev.a2zrr.flagbox.environment.Environment;
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

/**
 * A key scoped to one project and one environment. Only the SHA-256 hash of the
 * raw key is stored; the raw key is shown once, when the key is created.
 */
@Entity
@Table(name = "api_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ApiKeyType type;

    @Column(name = "key_prefix", nullable = false, length = 32, updatable = false)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, updatable = false, length = 64)
    private String keyHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public ApiKey(Project project, Environment environment, ApiKeyType type, String name,
                  String keyPrefix, String keyHash) {
        if (project == null || environment == null || type == null) {
            throw new IllegalArgumentException("project, environment and type are required");
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name must be 1 to 120 characters");
        }
        if (keyHash == null || keyHash.length() != 64) {
            throw new IllegalArgumentException("keyHash must be a 64-character SHA-256 hex digest");
        }
        this.project = project;
        this.environment = environment;
        this.type = type;
        this.name = name.strip();
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    /** Revokes the key. Revoking an already revoked key changes nothing. */
    public void revoke(Instant at) {
        if (revokedAt == null) {
            revokedAt = at;
        }
    }
}
