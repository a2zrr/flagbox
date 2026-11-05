package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.environment.Environment;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * How one flag behaves in one environment.
 *
 * <ul>
 *   <li>{@code enabled}: when false, the flag serves {@code false} to everyone.</li>
 *   <li>{@code rules}: checked in order; the first matching rule decides the value.</li>
 *   <li>{@code rolloutPercentage}: users whose stable bucket is below this value get {@code true}.</li>
 *   <li>{@code defaultValue}: served to enabled users that no rule or rollout bucket selects.</li>
 * </ul>
 */
@Entity
@Table(name = "flag_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlagConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flag_id", nullable = false, updatable = false)
    private Flag flag;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage = 100;

    @Column(name = "default_value", nullable = false)
    private boolean defaultValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<TargetingRule> rules = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Creates a disabled config with 100% rollout, no rules and a default value of false. */
    public FlagConfig(Flag flag, Environment environment) {
        this.flag = Objects.requireNonNull(flag, "flag is required");
        this.environment = Objects.requireNonNull(environment, "environment is required");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setDefaultValue(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        this.rolloutPercentage = rolloutPercentage;
    }

    /** Replaces the rule list. Order matters: the first matching rule wins. */
    public void setRules(List<TargetingRule> rules) {
        Objects.requireNonNull(rules, "rules is required");
        this.rules = List.copyOf(rules);
    }
}
