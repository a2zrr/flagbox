package dev.a2zrr.flagbox.evaluation;

import dev.a2zrr.flagbox.apikey.ApiKeyPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {

    static final String METRIC = "flagbox.evaluations";

    private final FlagSnapshotCache snapshots;
    private final MeterRegistry meters;

    public EvaluationService(FlagSnapshotCache snapshots, MeterRegistry meters) {
        this.snapshots = snapshots;
        this.meters = meters;
    }

    /** Evaluates one flag in the key's environment. */
    public Evaluation evaluate(ApiKeyPrincipal scope, String flagKey, EvaluationContext context) {
        Evaluation evaluation = EvaluationEngine.evaluate(flagKey, environmentFlags(scope).get(flagKey), context);
        count(evaluation);
        return evaluation;
    }

    /** Evaluates every active flag in the key's environment, sorted by flag key. */
    public List<Evaluation> evaluateAll(ApiKeyPrincipal scope, EvaluationContext context) {
        Map<String, FlagDefinition> flags = environmentFlags(scope);
        return flags.keySet().stream()
            .sorted()
            .map(key -> {
                Evaluation evaluation = EvaluationEngine.evaluate(key, flags.get(key), context);
                count(evaluation);
                return evaluation;
            })
            .toList();
    }

    private Map<String, FlagDefinition> environmentFlags(ApiKeyPrincipal scope) {
        return snapshots.get(scope.projectId()).getOrDefault(scope.environmentId(), Map.of());
    }

    /**
     * Counts evaluations per flag and reason. Unknown flag keys share one tag value, so
     * client input cannot create unbounded metric series.
     */
    private void count(Evaluation evaluation) {
        String flag = evaluation.reason() == Reason.FLAG_NOT_FOUND ? "unknown" : evaluation.flagKey();
        meters.counter(METRIC, "flag", flag, "reason", evaluation.reason().name()).increment();
    }
}
