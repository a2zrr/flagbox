package dev.a2zrr.flagbox.evaluation;

import dev.a2zrr.flagbox.apikey.ApiKeyPrincipal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {

    private final FlagSnapshotCache snapshots;

    public EvaluationService(FlagSnapshotCache snapshots) {
        this.snapshots = snapshots;
    }

    /** Evaluates one flag in the key's environment. */
    public Evaluation evaluate(ApiKeyPrincipal scope, String flagKey, EvaluationContext context) {
        return EvaluationEngine.evaluate(flagKey, environmentFlags(scope).get(flagKey), context);
    }

    /** Evaluates every active flag in the key's environment, sorted by flag key. */
    public List<Evaluation> evaluateAll(ApiKeyPrincipal scope, EvaluationContext context) {
        Map<String, FlagDefinition> flags = environmentFlags(scope);
        return flags.keySet().stream()
            .sorted()
            .map(key -> EvaluationEngine.evaluate(key, flags.get(key), context))
            .toList();
    }

    private Map<String, FlagDefinition> environmentFlags(ApiKeyPrincipal scope) {
        return snapshots.get(scope.projectId()).getOrDefault(scope.environmentId(), Map.of());
    }
}
