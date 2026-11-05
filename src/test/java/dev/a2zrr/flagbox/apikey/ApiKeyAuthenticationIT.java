package dev.a2zrr.flagbox.apikey;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.a2zrr.flagbox.support.AdminKeyTestConfig;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Authentication and authorization, one case per test. This class does not use the
 * default admin header, so every request states its own key (or none).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyAuthenticationIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String ADMIN = AdminKeyTestConfig.BOOTSTRAP_ADMIN_KEY;
    private static final String CHECKOUT = "/api/v1/projects/checkout";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void resetTables() {
        jdbc.execute("DELETE FROM audit_logs; DELETE FROM projects");
    }

    // 401: missing, malformed, unknown or revoked key

    @Test
    void missingKeyIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/projects"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").value("Missing X-API-Key header"));
    }

    @Test
    void malformedKeyIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/projects").header("X-API-Key", "not-a-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("The API key is invalid or has been revoked"));
    }

    @Test
    void unknownKeyIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/projects").header("X-API-Key", "fbx_client_" + "x".repeat(43)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedKeyIsUnauthorizedImmediately() throws Exception {
        setUpProject();
        String client = createKey("production", "CLIENT");
        long id = keyId("production", client);
        evaluate(client).andExpect(status().isOk());

        admin(delete(CHECKOUT + "/environments/production/api-keys/" + id)).andExpect(status().isNoContent());

        evaluate(client).andExpect(status().isUnauthorized());
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    // 403: valid key without permission

    @Test
    void clientKeyCannotCallManagementEndpoints() throws Exception {
        setUpProject();
        String client = createKey("production", "CLIENT");

        mvc.perform(get(CHECKOUT).header("X-API-Key", client))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get(CHECKOUT + "/environments/production/flags").header("X-API-Key", client))
            .andExpect(status().isForbidden());
    }

    @Test
    void clientKeyCanEvaluate() throws Exception {
        setUpProject();
        String client = createKey("production", "CLIENT");

        evaluate(client).andExpect(status().isOk());
    }

    @Test
    void environmentAdminCanChangeItsOwnEnvironmentOnly() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        createEnvironment("staging");
        String productionAdmin = createKey("production", "ADMIN");

        mvc.perform(patch(CHECKOUT + "/environments/production/flags/new-checkout")
                .header("X-API-Key", productionAdmin).contentType(JSON)
                .content("{\"enabled\": true}"))
            .andExpect(status().isOk());

        mvc.perform(patch(CHECKOUT + "/environments/staging/flags/new-checkout")
                .header("X-API-Key", productionAdmin).contentType(JSON)
                .content("{\"enabled\": true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void environmentAdminCannotCreateProjectsOrFlagDefinitions() throws Exception {
        setUpProject();
        String productionAdmin = createKey("production", "ADMIN");

        mvc.perform(post("/api/v1/projects").header("X-API-Key", productionAdmin).contentType(JSON)
                .content("{\"key\":\"other\",\"name\":\"Other\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(CHECKOUT + "/flags").header("X-API-Key", productionAdmin).contentType(JSON)
                .content("{\"key\":\"sneaky\",\"name\":\"Sneaky\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects").header("X-API-Key", productionAdmin))
            .andExpect(status().isForbidden());
    }

    @Test
    void keyOfAnotherProjectCannotReadThisProject() throws Exception {
        setUpProject();
        mvc.perform(post("/api/v1/projects").header("X-API-Key", ADMIN).contentType(JSON)
                .content("{\"key\":\"search\",\"name\":\"Search\"}"))
            .andExpect(status().isCreated());
        String searchAdmin = createKeyIn("search", "production", "ADMIN");

        mvc.perform(get(CHECKOUT).header("X-API-Key", searchAdmin))
            .andExpect(status().isForbidden());
        mvc.perform(get(CHECKOUT + "/environments/production/flags").header("X-API-Key", searchAdmin))
            .andExpect(status().isForbidden());
    }

    @Test
    void environmentAdminCanReadItsOwnProject() throws Exception {
        setUpProject();
        String productionAdmin = createKey("production", "ADMIN");

        mvc.perform(get(CHECKOUT).header("X-API-Key", productionAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("checkout"));
    }

    // Helpers

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("X-API-Key", ADMIN).contentType(JSON));
    }

    private ResultActions evaluate(String clientKey) throws Exception {
        return mvc.perform(post("/api/v1/evaluate").header("X-API-Key", clientKey).contentType(JSON)
            .content("{\"flagKey\":\"new-checkout\",\"context\":{\"userId\":\"user-1\"}}"));
    }

    private void setUpProject() throws Exception {
        admin(post("/api/v1/projects").content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
    }

    private void createEnvironment(String key) throws Exception {
        admin(post(CHECKOUT + "/environments").content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }

    private void createFlag(String key) throws Exception {
        admin(post(CHECKOUT + "/flags").content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }

    private String createKey(String environment, String type) throws Exception {
        return createKeyIn("checkout", environment, type);
    }

    private String createKeyIn(String project, String environment, String type) throws Exception {
        MvcResult result = admin(post("/api/v1/projects/" + project + "/environments/" + environment + "/api-keys")
                .content("{\"name\":\"" + type.toLowerCase() + "-" + environment + "\",\"type\":\"" + type + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.key");
    }

    private long keyId(String environment, String rawKey) {
        Long id = jdbc.queryForObject("SELECT k.id FROM api_keys k WHERE k.key_hash = ?", Long.class,
            ApiKeyHasher.sha256Hex(rawKey));
        return id;
    }
}
