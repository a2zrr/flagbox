package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.a2zrr.flagbox.support.AdminKeyTestConfig;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Global admin key rotation: create, list, revoke, and the last-key guard. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKeyTestConfig.class)
class AdminApiKeyIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String ADMIN_KEYS = "/api/v1/admin/api-keys";
    private static final String BOOTSTRAP = AdminKeyTestConfig.BOOTSTRAP_ADMIN_KEY;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // Each test starts with only the bootstrap key, and it active, so the counts and the last-key test are predictable.
    @BeforeEach
    @AfterEach
    void resetState() {
        jdbc.execute("DELETE FROM audit_logs; DELETE FROM projects");
        jdbc.update("DELETE FROM api_keys WHERE project_id IS NULL AND name <> 'bootstrap-admin'");
        jdbc.update("UPDATE api_keys SET revoked_at = NULL WHERE project_id IS NULL");
    }

    @Test
    void createsAGlobalKeyThatReturnsTheRawKeyOnceAndWorks() throws Exception {
        String rotated = createGlobalKey("rotated-2026");

        assertThat(rotated).startsWith("fbx_admin_");
        mvc.perform(get("/api/v1/projects").header("X-API-Key", rotated))
            .andExpect(status().isOk());
    }

    @Test
    void listIncludesGlobalKeysButNeverReturnsRawKeys() throws Exception {
        createGlobalKey("rotated-2026");

        mvc.perform(get(ADMIN_KEYS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].keyPrefix").value(org.hamcrest.Matchers.startsWith("fbx_admin_")))
            .andExpect(jsonPath("$.items[0].key").doesNotExist())
            .andExpect(jsonPath("$.items[1].key").doesNotExist());
    }

    @Test
    void revokingAGlobalKeyDisablesItWhileOthersStillWork() throws Exception {
        String rotated = createGlobalKey("rotated-2026");
        long id = keyIdByName("rotated-2026");

        mvc.perform(delete(ADMIN_KEYS + "/" + id).header("X-API-Key", BOOTSTRAP))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/projects").header("X-API-Key", rotated))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/projects").header("X-API-Key", BOOTSTRAP))
            .andExpect(status().isOk());
    }

    @Test
    void revokingTheLastActiveGlobalKeyIsRefusedWith409ProblemDetails() throws Exception {
        String only = createGlobalKey("only-one");
        // Leave exactly one active global key: the one this test uses to revoke itself.
        jdbc.update("UPDATE api_keys SET revoked_at = now() WHERE project_id IS NULL AND key_hash <> ?",
            ApiKeyHasher.sha256Hex(only));
        long id = keyIdByName("only-one");

        mvc.perform(delete(ADMIN_KEYS + "/" + id).header("X-API-Key", only))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail", Matchers.containsString("last active global admin key")));

        // The refused revocation changed nothing.
        mvc.perform(get("/api/v1/projects").header("X-API-Key", only)).andExpect(status().isOk());
    }

    @Test
    void revokingAnUnknownIdIsNotFound() throws Exception {
        mvc.perform(delete(ADMIN_KEYS + "/999999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void revokingAnEnvironmentKeyThroughTheGlobalEndpointIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON).content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
        MvcResult scoped = mvc.perform(post("/api/v1/projects/checkout/environments/production/api-keys")
                .contentType(JSON).content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long scopedId = ((Number) JsonPath.read(scoped.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(delete(ADMIN_KEYS + "/" + scopedId))
            .andExpect(status().isNotFound());
    }

    @Test
    void scopedAdminKeyGetsForbiddenOnEveryGlobalKeyEndpoint() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON).content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
        MvcResult scoped = mvc.perform(post("/api/v1/projects/checkout/environments/production/api-keys")
                .contentType(JSON).content("{\"name\":\"production-admin\",\"type\":\"ADMIN\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String scopedKey = JsonPath.read(scoped.getResponse().getContentAsString(), "$.key");

        mvc.perform(post(ADMIN_KEYS).header("X-API-Key", scopedKey).contentType(JSON)
                .content("{\"name\":\"sneaky\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
        mvc.perform(get(ADMIN_KEYS).header("X-API-Key", scopedKey))
            .andExpect(status().isForbidden());
        mvc.perform(delete(ADMIN_KEYS + "/1").header("X-API-Key", scopedKey))
            .andExpect(status().isForbidden());
    }

    @Test
    void clientKeyGetsForbiddenAndNoKeyGetsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON).content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
        MvcResult client = mvc.perform(post("/api/v1/projects/checkout/environments/production/api-keys")
                .contentType(JSON).content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String clientKey = JsonPath.read(client.getResponse().getContentAsString(), "$.key");

        mvc.perform(get(ADMIN_KEYS).header("X-API-Key", clientKey)).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_KEYS).header("X-API-Key", "")).andExpect(status().isUnauthorized());
    }

    @Test
    void everyGlobalKeyChangeIsAudited() throws Exception {
        long before = globalKeyAuditCount();

        String rotated = createGlobalKey("rotated-2026");
        mvc.perform(delete(ADMIN_KEYS + "/" + keyIdByName("rotated-2026")).header("X-API-Key", BOOTSTRAP))
            .andExpect(status().isNoContent());

        assertThat(rotated).isNotBlank();
        assertThat(globalKeyAuditCount() - before).isEqualTo(2);
        assertThat(jdbc.queryForList(
                "SELECT action FROM audit_logs WHERE entity_type = 'API_KEY' AND project_id IS NULL ORDER BY id",
                String.class))
            .containsExactly("CREATE", "UPDATE");
    }

    private String createGlobalKey(String name) throws Exception {
        MvcResult result = mvc.perform(post(ADMIN_KEYS).contentType(JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.key");
    }

    private long keyIdByName(String name) {
        Long id = jdbc.queryForObject("SELECT id FROM api_keys WHERE project_id IS NULL AND name = ?", Long.class, name);
        return id;
    }

    private long globalKeyAuditCount() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'API_KEY' AND project_id IS NULL", Long.class);
        return count;
    }
}
