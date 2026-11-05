package dev.a2zrr.flagbox.environment;

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
@RequestMapping("/api/v1/projects/{project}/environments")
@Tag(name = "Environments", description = "Environments such as development or production. Each has its own flag configs and API keys.")
public class EnvironmentController {

    private static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final ProjectService projects;
    private final EnvironmentService environments;

    public EnvironmentController(ProjectService projects, EnvironmentService environments) {
        this.projects = projects;
        this.environments = environments;
    }

    @PostMapping
    @Operation(summary = "Create an environment", description = "Creates a disabled config for every existing flag.")
    public ResponseEntity<EnvironmentResponse> create(@PathVariable("project") String projectKey,
                                                      @Valid @RequestBody CreateEnvironmentRequest request,
                                                      Authentication authentication) {
        Project project = projects.get(projectKey);
        Environment environment = environments.create(project, request.key(), request.name(),
            Actors.nameOf(authentication));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + projectKey
                + "/environments/" + environment.getKey()))
            .body(EnvironmentResponse.from(environment));
    }

    @GetMapping
    @Operation(summary = "List environments", description = "Paginated, sorted by key.")
    public PageResponse<EnvironmentResponse> list(
            @PathVariable("project") String projectKey,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Project project = projects.get(projectKey);
        return PageResponse.of(
            environments.list(project, PageRequest.of(page, size, Sort.by("key"))),
            EnvironmentResponse::from);
    }

    @GetMapping("/{environment}")
    @Operation(summary = "Get an environment")
    public EnvironmentResponse get(@PathVariable("project") String projectKey,
                                   @PathVariable("environment") String key) {
        return EnvironmentResponse.from(environments.get(projects.get(projectKey), key));
    }

    @PatchMapping("/{environment}")
    @Operation(summary = "Rename an environment", description = "The key cannot change.")
    public EnvironmentResponse update(@PathVariable("project") String projectKey,
                                      @PathVariable("environment") String key,
                                      @Valid @RequestBody UpdateEnvironmentRequest request,
                                      Authentication authentication) {
        Project project = projects.get(projectKey);
        return EnvironmentResponse.from(environments.update(project, key, request.name(),
            Actors.nameOf(authentication)));
    }

    @DeleteMapping("/{environment}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an environment", description = "Also deletes its flag configs and API keys.")
    public void delete(@PathVariable("project") String projectKey,
                       @PathVariable("environment") String key,
                       Authentication authentication) {
        environments.delete(projects.get(projectKey), key, Actors.nameOf(authentication));
    }

    public record CreateEnvironmentRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = SLUG) String key,
        @NotBlank @Size(max = 120) String name) {
    }

    public record UpdateEnvironmentRequest(@Size(min = 1, max = 120) String name) {
    }

    public record EnvironmentResponse(Long id, String key, String name, Instant createdAt) {

        static EnvironmentResponse from(Environment environment) {
            return new EnvironmentResponse(environment.getId(), environment.getKey(), environment.getName(),
                environment.getCreatedAt());
        }
    }
}
