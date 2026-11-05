package dev.a2zrr.flagbox.evaluation;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.a2zrr.flagbox.flag.FlagConfig;
import dev.a2zrr.flagbox.flag.FlagConfigRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caches, per project, the active flag definitions of every environment:
 * {@code environmentId -> flagKey -> definition}. A project is loaded in one
 * query, so evaluating a flag or all flags never touches the database once warm.
 *
 * <p>Every change to a project's flags, configs or environments evicts that
 * project's entry after the transaction commits. The TTL is a safety net only.
 */
@Component
public class FlagSnapshotCache {

    private final LoadingCache<Long, Map<Long, Map<String, FlagDefinition>>> cache;
    private final FlagConfigRepository configs;
    private final TransactionTemplate readOnlyTx;

    public FlagSnapshotCache(FlagConfigRepository configs, PlatformTransactionManager transactionManager) {
        this.configs = configs;
        this.readOnlyTx = new TransactionTemplate(transactionManager);
        this.readOnlyTx.setReadOnly(true);
        this.cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build(this::load);
    }

    public Map<Long, Map<String, FlagDefinition>> get(Long projectId) {
        return cache.get(projectId);
    }

    public void evict(Long projectId) {
        cache.invalidate(projectId);
    }

    /**
     * Evicts once the surrounding transaction has committed, so a read that
     * happens in between cannot re-cache the old state. Without an active
     * transaction it evicts immediately.
     */
    public void evictAfterCommit(Long projectId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(projectId);
                }
            });
        } else {
            evict(projectId);
        }
    }

    private Map<Long, Map<String, FlagDefinition>> load(Long projectId) {
        return readOnlyTx.execute(status -> {
            Map<Long, Map<String, FlagDefinition>> byEnvironment = new HashMap<>();
            for (FlagConfig config : configs.findActiveByProjectId(projectId)) {
                FlagDefinition definition = new FlagDefinition(
                    config.getFlag().getKey(),
                    config.isEnabled(),
                    config.getRolloutPercentage(),
                    config.getRules());
                byEnvironment
                    .computeIfAbsent(config.getEnvironment().getId(), id -> new HashMap<>())
                    .put(definition.key(), definition);
            }
            return byEnvironment;
        });
    }
}
