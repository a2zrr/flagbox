package dev.a2zrr.flagbox.apikey;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The Spring Security authentication for a request that presented a valid API key. */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final ApiKeyPrincipal principal;

    public ApiKeyAuthentication(ApiKeyPrincipal principal) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.type().name())));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public ApiKeyPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.keyName();
    }
}
