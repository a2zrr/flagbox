package dev.a2zrr.flagbox.apikey;

/**
 * The caller identified by an API key. The key's scope (project and environment)
 * is the only scope the caller may use.
 */
public record ApiKeyPrincipal(Long keyId, String keyName, ApiKeyType type, Long projectId, Long environmentId) {
}
