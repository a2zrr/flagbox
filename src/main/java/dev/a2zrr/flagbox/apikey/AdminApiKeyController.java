package dev.a2zrr.flagbox.apikey;

import dev.a2zrr.flagbox.apikey.ApiKeyController.ApiKeyResponse;
import dev.a2zrr.flagbox.apikey.ApiKeyController.CreatedApiKeyResponse;
import dev.a2zrr.flagbox.apikey.ApiKeyService.CreatedApiKey;
import dev.a2zrr.flagbox.common.web.Actors;
import dev.a2zrr.flagbox.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
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

/**
 * Global admin keys, the keys that can manage every project. Used for bootstrap
 * and rotation. Only a global admin key can call these endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/api-keys")
@Tag(name = "Global admin keys", description = "Create, list and revoke global admin keys. Rotation: create the new key, then revoke the old one. The last active global admin key cannot be revoked.")
public class AdminApiKeyController {

    private final ApiKeyService apiKeys;
    private final AccessGuard access;

    public AdminApiKeyController(ApiKeyService apiKeys, AccessGuard access) {
        this.apiKeys = apiKeys;
        this.access = access;
    }

    @PostMapping
    @Operation(summary = "Create a global admin key", description = "Returns the raw key in the 'key' field once. Store it now.")
    public ResponseEntity<CreatedApiKeyResponse> create(@Valid @RequestBody CreateGlobalKeyRequest request,
                                                        Authentication authentication) {
        access.requireGlobal(authentication);
        CreatedApiKey created = apiKeys.createGlobalAdminKey(request.name(), Actors.nameOf(authentication));
        return ResponseEntity.created(URI.create("/api/v1/admin/api-keys/" + created.key().getId()))
            .body(CreatedApiKeyResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "List global admin keys", description = "Includes revoked keys. Raw keys are never returned.")
    public PageResponse<ApiKeyResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        access.requireGlobal(authentication);
        return PageResponse.of(
            apiKeys.listGlobalAdminKeys(PageRequest.of(page, size, Sort.by("id"))),
            ApiKeyResponse::from);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a global admin key", description = "Returns 409 if this is the last active global admin key.")
    public void revoke(@PathVariable("id") Long id, Authentication authentication) {
        access.requireGlobal(authentication);
        apiKeys.revokeGlobalAdminKey(id, Actors.nameOf(authentication));
    }

    public record CreateGlobalKeyRequest(@NotBlank @Size(max = 120) String name) {
    }
}
