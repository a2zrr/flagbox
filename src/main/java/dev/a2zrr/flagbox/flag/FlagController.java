package dev.a2zrr.flagbox.flag;

import dev.a2zrr.flagbox.apikey.AccessGuard;
import dev.a2zrr.flagbox.common.web.Actors;
import dev.a2zrr.flagbox.common.web.PageResponse;
import dev.a2zrr.flagbox.project.Project;
import dev.a2zrr.flagbox.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{project}")
@Tag(name = "Flags", description = "Flags and their per-environment configs: enabled state, rollout percentage and targeting rules. Creating, changing and deleting flag definitions needs a global admin key; a key scoped to an environment can change that environment's config.")
public class FlagController {

    private static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final ProjectService projects;
    private final FlagService flags;
    private final AccessGuard access;

    public FlagController(ProjectService projects, FlagService flags, AccessGuard access) {
        this.projects = projects;
        this.flags = flags;
        this.access = access;
    }

    @GetMapping("/flags")
    @Operation(summary = "List flags", description = "Paginated, sorted by key. Archived flags are hidden unless includeArchived=true.")
    public PageResponse<FlagResponse> list(
            @PathVariable("project") String projectKey,
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.requireProjectAccess(authentication, projectKey);
        Project project = projects.get(projectKey);
        return PageResponse.of(
            flags.list(project, includeArchived, PageRequest.of(page, size, Sort.by("key"))),
            FlagResponse::from);
    }

    @PostMapping("/flags")
    @Operation(summary = "Create a flag", description = "The flag starts disabled, with 100% rollout and no rules, in every environment. Global admin key only.")
    public ResponseEntity<FlagResponse> create(@PathVariable("project") String projectKey,
                                               @Valid @RequestBody CreateFlagRequest request,
                                               Authentication authentication) {
        access.requireGlobal(authentication);
        Project project = projects.get(projectKey);
        Flag flag = flags.create(project, request.key(), request.name(),
            request.description(), Actors.nameOf(authentication));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectKey + "/flags/" + flag.getKey()))
            .body(FlagResponse.from(flag));
    }

    @GetMapping("/flags/{flag}")
    @Operation(summary = "Get a flag")
    public FlagResponse get(@PathVariable("project") String projectKey, @PathVariable("flag") String key,
                            Authentication authentication) {
        access.requireProjectAccess(authentication, projectKey);
        return FlagResponse.from(flags.get(projects.get(projectKey), key));
    }

    @PatchMapping("/flags/{flag}")
    @Operation(summary = "Update a flag's name, description or archived state. Global admin key only.")
    public FlagResponse update(@PathVariable("project") String projectKey,
                               @PathVariable("flag") String key,
                               @Valid @RequestBody UpdateFlagRequest request,
                               Authentication authentication) {
        access.requireGlobal(authentication);
        Project project = projects.get(projectKey);
        return FlagResponse.from(flags.update(project, key, request.name(), request.description(),
            request.archived(), Actors.nameOf(authentication)));
    }

    @DeleteMapping("/flags/{flag}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a flag", description = "Deletes the flag and its configs. Audit history is kept. Archive instead to keep the flag. Global admin key only.")
    public void delete(@PathVariable("project") String projectKey,
                       @PathVariable("flag") String key,
                       Authentication authentication) {
        access.requireGlobal(authentication);
        flags.delete(projects.get(projectKey), key, Actors.nameOf(authentication));
    }

    @GetMapping("/environments/{environment}/flags")
    @Operation(summary = "List flag configs in an environment", description = "Paginated, sorted by flag key.")
    public PageResponse<FlagConfigResponse> listConfigs(
            @PathVariable("project") String projectKey,
            @PathVariable("environment") String environmentKey,
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.requireEnvironmentAccess(authentication, projectKey, environmentKey);
        Project project = projects.get(projectKey);
        return PageResponse.of(
            flags.listConfigs(project, environmentKey, PageRequest.of(page, size, Sort.by("flag.key"))),
            FlagConfigResponse::from);
    }

    @GetMapping("/environments/{environment}/flags/{flag}")
    @Operation(summary = "Get a flag config in an environment")
    public FlagConfigResponse getConfig(@PathVariable("project") String projectKey,
                                        @PathVariable("environment") String environmentKey,
                                        @PathVariable("flag") String flagKey,
                                        Authentication authentication) {
        access.requireEnvironmentAccess(authentication, projectKey, environmentKey);
        return FlagConfigResponse.from(flags.getConfig(projects.get(projectKey), environmentKey, flagKey));
    }

    @PatchMapping("/environments/{environment}/flags/{flag}")
    @Operation(summary = "Update a flag config in an environment",
        description = "Toggle the flag, set the rollout percentage or replace the rules. Omitted fields are unchanged. "
            + "Rules are evaluated in order and the first match wins.")
    public FlagConfigResponse updateConfig(@PathVariable("project") String projectKey,
                                           @PathVariable("environment") String environmentKey,
                                           @PathVariable("flag") String flagKey,
                                           @Valid @RequestBody UpdateFlagConfigRequest request,
                                           Authentication authentication) {
        access.requireEnvironmentAccess(authentication, projectKey, environmentKey);
        Project project = projects.get(projectKey);
        return FlagConfigResponse.from(flags.updateConfig(project, environmentKey, flagKey, request.enabled(),
            request.rolloutPercentage(), request.rules(), Actors.nameOf(authentication)));
    }

    public record CreateFlagRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = SLUG) String key,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description) {
    }

    public record UpdateFlagRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 1000) String description,
        Boolean archived) {
    }

    public record UpdateFlagConfigRequest(
        Boolean enabled,
        @Min(0) @Max(100) Integer rolloutPercentage,
        @Size(max = FlagConfig.MAX_RULES) List<TargetingRule> rules) {
    }

    public record FlagResponse(Long id, String key, String name, String description, FlagType type,
                               boolean archived, Instant createdAt, Instant updatedAt) {

        static FlagResponse from(Flag flag) {
            return new FlagResponse(flag.getId(), flag.getKey(), flag.getName(), flag.getDescription(),
                flag.getType(), flag.isArchived(), flag.getCreatedAt(), flag.getUpdatedAt());
        }
    }

    public record FlagConfigResponse(String flagKey, String environmentKey, boolean enabled,
                                     int rolloutPercentage, List<TargetingRule> rules, Instant updatedAt) {

        static FlagConfigResponse from(FlagConfig config) {
            return new FlagConfigResponse(config.getFlag().getKey(), config.getEnvironment().getKey(),
                config.isEnabled(), config.getRolloutPercentage(), config.getRules(), config.getUpdatedAt());
        }
    }
}
