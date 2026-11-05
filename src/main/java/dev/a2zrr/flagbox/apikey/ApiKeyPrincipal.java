package dev.a2zrr.flagbox.apikey;

/**
 * The caller identified by an API key and the scope it grants.
 *
 * <p>A global key ({@code projectId == null}) can manage every project. Any other
 * key is scoped to exactly one project and one environment.
 */
public record ApiKeyPrincipal(Long keyId, String keyName, ApiKeyType type,
                              Long projectId, String projectKey,
                              Long environmentId, String environmentKey) {

    public boolean isGlobal() {
        return projectId == null;
    }
}
