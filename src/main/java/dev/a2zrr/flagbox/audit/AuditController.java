package dev.a2zrr.flagbox.audit;

import dev.a2zrr.flagbox.apikey.AccessGuard;
import dev.a2zrr.flagbox.common.web.PageResponse;
import dev.a2zrr.flagbox.project.Project;
import dev.a2zrr.flagbox.project.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{project}/flags/{flag}/history")
@Tag(name = "Audit", description = "Who changed what and when, with old and new values.")
public class AuditController {

    private final ProjectService projects;
    private final AuditService audit;
    private final AccessGuard access;

    public AuditController(ProjectService projects, AuditService audit, AccessGuard access) {
        this.projects = projects;
        this.audit = audit;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "Get a flag's change history",
        description = "Changes to the flag and to its configs in every environment, newest first.")
    public PageResponse<AuditEntryResponse> history(
            @PathVariable("project") String projectKey,
            @PathVariable("flag") String flagKey,
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.requireProjectAccess(authentication, projectKey);
        Project project = projects.get(projectKey);
        return PageResponse.of(
            audit.flagHistory(project.getId(), flagKey,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")))),
            AuditEntryResponse::from);
    }

    public record AuditEntryResponse(Long id, AuditEntityType entityType, AuditAction action,
                                     Long environmentId, String actor, Map<String, Object> oldValue,
                                     Map<String, Object> newValue, Instant occurredAt) {

        static AuditEntryResponse from(AuditLog entry) {
            return new AuditEntryResponse(entry.getId(), entry.getEntityType(), entry.getAction(),
                entry.getEnvironmentId(), entry.getActor(), entry.getOldValue(), entry.getNewValue(),
                entry.getOccurredAt());
        }
    }
}
