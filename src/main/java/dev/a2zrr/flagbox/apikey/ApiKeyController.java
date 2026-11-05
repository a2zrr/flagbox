package dev.a2zrr.flagbox.apikey;

import dev.a2zrr.flagbox.apikey.ApiKeyService.CreatedApiKey;
import dev.a2zrr.flagbox.common.web.Actors;
import dev.a2zrr.flagbox.common.web.PageResponse;
import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentService;
import dev.a2zrr.flagbox.project.Project;
import dev.a2zrr.flagbox.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{project}/environments/{environment}/api-keys")
@Tag(name = "API keys", description = "Keys scoped to one project and environment. The raw key is shown only once, at creation.")
public class ApiKeyController {

    private final ProjectService projects;
    private final EnvironmentService environments;
    private final ApiKeyService apiKeys;

    public ApiKeyController(ProjectService projects, EnvironmentService environments, ApiKeyService apiKeys) {
        this.projects = projects;
        this.environments = environments;
        this.apiKeys = apiKeys;
    }

    @PostMapping
    @Operation(summary = "Create an API key", description = "Returns the raw key in the 'key' field. Store it now; it cannot be shown again.")
    public ResponseEntity<CreatedApiKeyResponse> create(@PathVariable("project") String projectKey,
                                                        @PathVariable("environment") String environmentKey,
                                                        @Valid @RequestBody CreateApiKeyRequest request,
                                                        Authentication authentication) {
        Project project = projects.get(projectKey);
        Environment environment = environments.get(project, environmentKey);
        CreatedApiKey created = apiKeys.create(project, environment, request.name(), request.type(),
            Actors.nameOf(authentication));
        URI location = URI.create("/api/v1/projects/" + projectKey + "/environments/" + environmentKey
            + "/api-keys/" + created.key().getId());
        return ResponseEntity.created(location).body(CreatedApiKeyResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "List API keys", description = "Paginated. Raw keys are never returned here.")
    public PageResponse<ApiKeyResponse> list(
            @PathVariable("project") String projectKey,
            @PathVariable("environment") String environmentKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Project project = projects.get(projectKey);
        Environment environment = environments.get(project, environmentKey);
        return PageResponse.of(
            apiKeys.list(project, environment, PageRequest.of(page, size, Sort.by("id"))),
            ApiKeyResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke an API key", description = "The key stops working immediately. Revoking twice is harmless.")
    public void revoke(@PathVariable("project") String projectKey,
                       @PathVariable("environment") String environmentKey,
                       @PathVariable("id") Long id,
                       Authentication authentication) {
        Project project = projects.get(projectKey);
        Environment environment = environments.get(project, environmentKey);
        apiKeys.revoke(project, environment, id, Actors.nameOf(authentication));
    }

    public record CreateApiKeyRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull ApiKeyType type) {
    }

    public record ApiKeyResponse(Long id, String name, ApiKeyType type, String keyPrefix,
                                 Instant createdAt, Instant revokedAt, boolean active) {

        static ApiKeyResponse from(ApiKey key) {
            return new ApiKeyResponse(key.getId(), key.getName(), key.getType(), key.getKeyPrefix(),
                key.getCreatedAt(), key.getRevokedAt(), key.isActive());
        }
    }

    public record CreatedApiKeyResponse(Long id, String name, ApiKeyType type, String keyPrefix,
                                        Instant createdAt, String key) {

        static CreatedApiKeyResponse from(CreatedApiKey created) {
            ApiKey key = created.key();
            return new CreatedApiKeyResponse(key.getId(), key.getName(), key.getType(), key.getKeyPrefix(),
                key.getCreatedAt(), created.rawKey());
        }
    }
}
