package dev.a2zrr.flagbox.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.a2zrr.flagbox.apikey.ApiKeyAuthenticationFilter;
import dev.a2zrr.flagbox.apikey.ApiKeyAuthenticator;
import dev.a2zrr.flagbox.apikey.EvaluationRateLimitFilter;
import dev.a2zrr.flagbox.common.security.ProblemJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import io.github.bucket4j.TimeMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authentication is by {@code X-API-Key}. Roles come from the key type:
 * evaluation is open to ADMIN and CLIENT keys, management needs ADMIN.
 * Scope (which project and environment) is checked by {@code AccessGuard}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** The rate limiter's time source. Tests replace it with a frozen clock. */
    @Bean
    TimeMeter rateLimitClock() {
        return TimeMeter.SYSTEM_NANOTIME;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticator authenticator,
                                            ObjectMapper mapper, TimeMeter rateLimitClock,
                                            @Value("${flagbox.rate-limit.evaluations-per-second:100}")
                                            int evaluationsPerSecond) throws Exception {
        AuthenticationEntryPoint unauthenticated = (request, response, ex) ->
            writeUnauthorized(request, response, mapper);
        AccessDeniedHandler forbidden = (request, response, ex) ->
            ProblemJson.write(response, mapper, HttpStatus.FORBIDDEN, "Forbidden",
                "This API key is not allowed to perform this action");

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(unauthenticated)
                .accessDeniedHandler(forbidden))
            .addFilterBefore(new ApiKeyAuthenticationFilter(authenticator, unauthenticated),
                UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new EvaluationRateLimitFilter(mapper, rateLimitClock, evaluationsPerSecond),
                ApiKeyAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**",
                    "/swagger-ui.html", "/error").permitAll()
                .requestMatchers("/api/v1/evaluate", "/api/v1/evaluate/**").hasAnyRole("ADMIN", "CLIENT")
                .requestMatchers("/api/v1/**", "/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated());
        return http.build();
    }

    private static void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                          ObjectMapper mapper) throws IOException {
        String detail = request.getHeader(ApiKeyAuthenticationFilter.HEADER) == null
            ? "Missing X-API-Key header"
            : "The API key is invalid or has been revoked";
        ProblemJson.write(response, mapper, HttpStatus.UNAUTHORIZED, "Unauthorized", detail);
    }
}
