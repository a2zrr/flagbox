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
 * How one flag behaves in one environment: {@code enabled}, an ordered list of
 * {@code rules}, and a {@code rolloutPercentage}.
 *
 * <p>The server never serves a fallback value. When a flag is not found, the
 * calling application uses its own default.
 *
 * <p>Evaluation order (implemented in the evaluation engine):
 * <ol>
 *   <li>flag not found: FLAG_NOT_FOUND, the client uses its own fallback</li>
 *   <li>flag disabled: {@code false}, FLAG_DISABLED</li>
 *   <li>rules in order, first match wins: user in an off-list gives {@code false}
 *       (USER_DENIED); user in an on-list gives {@code true} (USER_ALLOWED);
 *       an attribute rule that matches serves its value (RULE_MATCH)</li>
 *   <li>rollout: stable bucket below {@code rolloutPercentage} gives {@code true}
 *       (ROLLOUT_IN), otherwise {@code false} (ROLLOUT_OUT)</li>
 * </ol>
 */
@Entity
@Table(name = "flag_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlagConfig {

    public static final int MAX_RULES = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER: responses expose the flag and environment keys after the transaction ends.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "flag_id", nullable = false, updatable = false)
    private Flag flag;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage = 100;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<TargetingRule> rules = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Creates a disabled config with 100% rollout and no rules. */
    public FlagConfig(Flag flag, Environment environment) {
        this.flag = Objects.requireNonNull(flag, "flag is required");
        this.environment = Objects.requireNonNull(environment, "environment is required");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
        if (rules.size() > MAX_RULES) {
            throw new IllegalArgumentException("a flag config may contain at most " + MAX_RULES + " rules");
        }
        if (rules.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("rules must not contain null entries");
        }
        this.rules = List.copyOf(rules);
    }
}
