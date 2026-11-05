package dev.a2zrr.flagbox.project;

import dev.a2zrr.flagbox.apikey.AccessGuard;
import dev.a2zrr.flagbox.common.web.Actors;
import dev.a2zrr.flagbox.common.web.PageResponse;
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
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Projects group flags. Each new project gets development and production environments. Creating, changing and deleting projects needs a global admin key.")
public class ProjectController {

    private static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private final ProjectService projects;
    private final AccessGuard access;

    public ProjectController(ProjectService projects, AccessGuard access) {
        this.projects = projects;
        this.access = access;
    }

    @PostMapping
    @Operation(summary = "Create a project", description = "Creates the project and its development and production environments. Global admin key only.")
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request,
                                                  Authentication authentication) {
        access.requireGlobal(authentication);
        Project project = projects.create(request.key(), request.name(), request.description(),
            Actors.nameOf(authentication));
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.getKey()))
            .body(ProjectResponse.from(project));
    }

    @GetMapping
    @Operation(summary = "List projects", description = "Paginated, sorted by key. Global admin key only.")
    public PageResponse<ProjectResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.requireGlobal(authentication);
        return PageResponse.of(projects.list(PageRequest.of(page, size, Sort.by("key"))), ProjectResponse::from);
    }

    @GetMapping("/{project}")
    @Operation(summary = "Get a project")
    public ProjectResponse get(@PathVariable("project") String key, Authentication authentication) {
        access.requireProjectAccess(authentication, key);
        return ProjectResponse.from(projects.get(key));
    }

    @PatchMapping("/{project}")
    @Operation(summary = "Update a project", description = "Omitted fields are unchanged. An empty description clears it. Global admin key only.")
    public ProjectResponse update(@PathVariable("project") String key,
                                  @Valid @RequestBody UpdateProjectRequest request,
                                  Authentication authentication) {
        access.requireGlobal(authentication);
        return ProjectResponse.from(projects.update(key, request.name(), request.description(),
            Actors.nameOf(authentication)));
    }

    @DeleteMapping("/{project}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a project", description = "Deletes the project with its environments, flags and keys. Audit history is kept. Global admin key only.")
    public void delete(@PathVariable("project") String key, Authentication authentication) {
        access.requireGlobal(authentication);
        projects.delete(key, Actors.nameOf(authentication));
    }

    public record CreateProjectRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = SLUG) String key,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description) {
    }

    public record UpdateProjectRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 1000) String description) {
    }

    public record ProjectResponse(Long id, String key, String name, String description, Instant createdAt) {

        static ProjectResponse from(Project project) {
            return new ProjectResponse(project.getId(), project.getKey(), project.getName(),
                project.getDescription(), project.getCreatedAt());
        }
    }
}
