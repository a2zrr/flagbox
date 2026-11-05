package dev.a2zrr.flagbox.evaluation;

import dev.a2zrr.flagbox.apikey.ApiKeyAuthentication;
import dev.a2zrr.flagbox.apikey.ApiKeyPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Evaluation endpoints. The project and environment come from the API key that
 * made the request, never from the body, so a key can only evaluate its own
 * environment.
 */
@RestController
@RequestMapping("/api/v1/evaluate")
@Tag(name = "Evaluation", description = "Evaluate flags for a user. Uses the project and environment of the API key.")
public class EvaluationController {

    private final EvaluationService evaluations;

    public EvaluationController(EvaluationService evaluations) {
        this.evaluations = evaluations;
    }

    @PostMapping
    @Operation(summary = "Evaluate one flag",
        description = "Returns the value and the reason. For an unknown or archived flag, value is null and reason is FLAG_NOT_FOUND.")
    public EvaluationResponse evaluate(@Valid @RequestBody EvaluateRequest request, Authentication authentication) {
        Evaluation evaluation = evaluations.evaluate(scopeOf(authentication), request.flagKey(),
            request.context().toDomain());
        return EvaluationResponse.from(evaluation);
    }

    @PostMapping("/all")
    @Operation(summary = "Evaluate every active flag for one user",
        description = "Returns all non-archived flags of the key's environment, sorted by key.")
    public EvaluateAllResponse evaluateAll(@Valid @RequestBody EvaluateContextRequest request,
                                           Authentication authentication) {
        List<EvaluationResponse> results = evaluations.evaluateAll(scopeOf(authentication),
                request.context().toDomain()).stream()
            .map(EvaluationResponse::from)
            .toList();
        return new EvaluateAllResponse(results);
    }

    private static ApiKeyPrincipal scopeOf(Authentication authentication) {
        if (authentication instanceof ApiKeyAuthentication apiKey) {
            ApiKeyPrincipal principal = apiKey.getPrincipal();
            if (!principal.isGlobal()) {
                return principal;
            }
            throw new AccessDeniedException("Evaluation needs a key scoped to an environment; global keys have none");
        }
        throw new AccessDeniedException("An API key is required to evaluate flags");
    }

    public record ContextRequest(
        @NotBlank(message = "is required. Send a stable user ID. For logged-out users, send a stable anonymous ID "
            + "(a cookie or device ID) so their rollout bucket does not change between requests.")
        @Size(max = 128) String userId,
        @Size(max = EvaluationContext.MAX_ATTRIBUTES) Map<@NotBlank @Size(max = 64) String, @Size(max = 256) String> attributes) {

        EvaluationContext toDomain() {
            return new EvaluationContext(userId, attributes);
        }
    }

    public record EvaluateRequest(
        @NotBlank @Size(max = 64) String flagKey,
        @NotNull @Valid ContextRequest context) {
    }

    public record EvaluateContextRequest(@NotNull @Valid ContextRequest context) {
    }

    public record EvaluationResponse(String flagKey, Boolean value, Reason reason) {

        static EvaluationResponse from(Evaluation evaluation) {
            return new EvaluationResponse(evaluation.flagKey(), evaluation.value(), evaluation.reason());
        }
    }

    public record EvaluateAllResponse(List<EvaluationResponse> flags) {
    }
}
