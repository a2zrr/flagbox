package dev.a2zrr.flagbox.apikey;

/** ADMIN keys manage flags; CLIENT keys can only evaluate them. */
public enum ApiKeyType {
    ADMIN("fbx_admin_"),
    CLIENT("fbx_client_");

    private final String prefix;

    ApiKeyType(String prefix) {
        this.prefix = prefix;
    }

    /** The visible prefix of every raw key of this type, for example {@code fbx_admin_}. */
    public String prefix() {
        return prefix;
    }
}
