package dev.a2zrr.flagbox.support;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Sends the test bootstrap admin key with every MockMvc request, so tests exercise
 * the real authentication path. Import this in test classes that call the API.
 */
@TestConfiguration
public class AdminKeyTestConfig {

    /** Must match {@code src/test/resources/application-test.yml}. */
    public static final String BOOTSTRAP_ADMIN_KEY = "fbx_admin_test-bootstrap-0123456789abcdefghijk";

    @Bean
    MockMvcBuilderCustomizer adminKeyHeader() {
        return builder -> builder.defaultRequest(
            MockMvcRequestBuilders.get("/").header("X-API-Key", BOOTSTRAP_ADMIN_KEY));
    }
}
