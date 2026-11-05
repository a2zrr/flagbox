package dev.a2zrr.flagbox.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code X-API-Key} header. A valid key sets the authentication for the
 * request. A key that is present but invalid is rejected here with 401. A request
 * with no key header passes through and is rejected by the authorization rules.
 *
 * <p>Not a Spring bean: it is added to the security filter chain explicitly so it
 * is not also registered as a servlet filter.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyAuthenticator authenticator;
    private final AuthenticationEntryPoint entryPoint;

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticator authenticator, AuthenticationEntryPoint entryPoint) {
        this.authenticator = authenticator;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null) {
            chain.doFilter(request, response);
            return;
        }

        Optional<ApiKeyPrincipal> principal = authenticator.authenticate(rawKey.strip());
        if (principal.isEmpty()) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("invalid API key"));
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(principal.get()));
        chain.doFilter(request, response);
    }
}
