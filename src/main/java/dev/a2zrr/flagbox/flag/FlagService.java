package dev.a2zrr.flagbox.flag;

import static dev.a2zrr.flagbox.audit.AuditService.fields;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.a2zrr.flagbox.audit.AuditAction;
import dev.a2zrr.flagbox.audit.AuditEntityType;
import dev.a2zrr.flagbox.audit.AuditService;
import dev.a2zrr.flagbox.common.Slug;
import dev.a2zrr.flagbox.common.error.ConflictException;
import dev.a2zrr.flagbox.common.error.ResourceNotFoundException;
import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import dev.a2zrr.flagbox.project.Project;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FlagService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<TargetingRule>> RULES_TYPE = new TypeReference<>() {};

    private final FlagRepository flags;
    private final FlagConfigRepository configs;
    private final EnvironmentRepository environments;
    private final AuditService audit;

    public FlagService(FlagRepository flags, FlagConfigRepository configs,
                       EnvironmentRepository environments, AuditService audit) {
        this.flags = flags;
        this.configs = configs;
        this.environments = environments;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Flag> list(Project project, boolean includeArchived, Pageable pageable) {
        return includeArchived
            ? flags.findByProject(project, pageable)
            : flags.findByProjectAndArchived(project, false, pageable);
    }

    @Transactional(readOnly = true)
    public Flag get(Project project, String key) {
        return flags.findByProjectAndKey(project, key)
            .orElseThrow(() -> new ResourceNotFoundException("Flag '" + key + "' not found"));
    }

    /** Creates a flag with a disabled config (100% rollout, no rules) in every environment of the project. */
    @Transactional
    public Flag create(Project project, String key, String name, String description, String actor) {
        Slug.requireValid(key, "key");
        if (flags.existsByProjectAndKey(project, key)) {
            throw new ConflictException("A flag with key '" + key + "' already exists in this project");
        }
        Flag flag = flags.save(new Flag(project, key, name, description));
        for (Environment environment : environments.findAllByProject(project)) {
            configs.save(new FlagConfig(flag, environment));
        }
        audit.record(AuditEntityType.FLAG, project.getId(), null, key, AuditAction.CREATE, actor,
            null, flagSnapshot(flag));
        return flag;
    }

    @Transactional
    public Flag update(Project project, String key, String name, String description, Boolean archived,
                       String actor) {
        Flag flag = get(project, key);
        Map<String, Object> before = flagSnapshot(flag);
        flag.updateDetails(name, description);
        if (archived != null) {
            if (archived) {
                flag.archive();
            } else {
                flag.restore();
            }
        }
        Map<String, Object> after = flagSnapshot(flag);
        if (!before.equals(after)) {
            audit.record(AuditEntityType.FLAG, project.getId(), null, key, AuditAction.UPDATE, actor,
                before, after);
        }
        return flag;
    }

    @Transactional
    public void delete(Project project, String key, String actor) {
        Flag flag = get(project, key);
        audit.record(AuditEntityType.FLAG, project.getId(), null, key, AuditAction.DELETE, actor,
            flagSnapshot(flag), null);
        flags.delete(flag);
    }

    /** Gives an environment a disabled config for every flag in its project. Used when an environment is created. */
    @Transactional
    public void ensureConfigsFor(Environment environment) {
        for (Flag flag : flags.findAllByProject(environment.getProject())) {
            if (configs.findByFlagAndEnvironment(flag, environment).isEmpty()) {
                configs.save(new FlagConfig(flag, environment));
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<FlagConfig> listConfigs(Project project, String environmentKey, Pageable pageable) {
        return configs.findByEnvironment(environment(project, environmentKey), pageable);
    }

    @Transactional(readOnly = true)
    public FlagConfig getConfig(Project project, String environmentKey, String flagKey) {
        Environment environment = environment(project, environmentKey);
        Flag flag = get(project, flagKey);
        return configs.findByFlagAndEnvironment(flag, environment)
            .orElseThrow(() -> new ResourceNotFoundException("Flag '" + flagKey + "' has no config in '"
                + environmentKey + "'"));
    }

    /**
     * Applies a partial update. A null argument leaves that part of the config unchanged.
     * Records one audit entry when something actually changed.
     */
    @Transactional
    public FlagConfig updateConfig(Project project, String environmentKey, String flagKey, Boolean enabled,
                                   Integer rolloutPercentage, List<TargetingRule> rules, String actor) {
        FlagConfig config = getConfig(project, environmentKey, flagKey);
        Map<String, Object> before = configSnapshot(config);
        if (enabled != null) {
            config.setEnabled(enabled);
        }
        if (rolloutPercentage != null) {
            config.setRolloutPercentage(rolloutPercentage);
        }
        if (rules != null) {
            config.setRules(rules);
        }
        Map<String, Object> after = configSnapshot(config);
        if (!before.equals(after)) {
            audit.record(AuditEntityType.FLAG_CONFIG, project.getId(), config.getEnvironment().getId(),
                flagKey, AuditAction.UPDATE, actor, before, after);
        }
        return config;
    }

    private Environment environment(Project project, String key) {
        return environments.findByProjectAndKey(project, key)
            .orElseThrow(() -> new ResourceNotFoundException("Environment '" + key + "' not found"));
    }

    private static Map<String, Object> flagSnapshot(Flag flag) {
        return fields("name", flag.getName(), "description", flag.getDescription(),
            "archived", flag.isArchived());
    }

    private static Map<String, Object> configSnapshot(FlagConfig config) {
        return fields("enabled", config.isEnabled(),
            "rolloutPercentage", config.getRolloutPercentage(),
            "rules", rulesAsJson(config.getRules()));
    }

    /** Converts rules to plain JSON structures (with their "type" discriminator) for the audit log. */
    private static List<Object> rulesAsJson(List<TargetingRule> rules) {
        try {
            String json = JSON.writerFor(RULES_TYPE).writeValueAsString(rules);
            return JSON.readValue(json, new TypeReference<List<Object>>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize targeting rules", e);
        }
    }
}
