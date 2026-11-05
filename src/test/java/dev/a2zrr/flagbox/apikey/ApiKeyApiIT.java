package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import dev.a2zrr.flagbox.support.AdminKeyTestConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKeyTestConfig.class)
class ApiKeyApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String KEYS = "/api/v1/projects/checkout/environments/production/api-keys";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private JdbcTemplate jdbc;

    // Each request commits on its own, so reset the tables instead of rolling back.
    @BeforeEach
    @AfterEach
    void resetTables() {
        jdbc.execute("DELETE FROM audit_logs; DELETE FROM projects");
    }

    @Test
    void createReturnsRawKeyOnceAndStoresOnlyTheHash() throws Exception {
        setUpProject();

        MvcResult result = mvc.perform(post(KEYS).contentType(JSON)
                .content("{\"name\":\"CI admin\",\"type\":\"ADMIN\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(org.hamcrest.Matchers.startsWith("fbx_admin_")))
            .andReturn();

        String raw = JsonPath.read(result.getResponse().getContentAsString(), "$.key");
        assertThat(apiKeys.findByKeyHash(ApiKeyHasher.sha256Hex(raw))).isPresent();
        assertThat(apiKeys.findAll()).noneMatch(key -> key.getKeyHash().equals(raw));
    }

    @Test
    void listNeverReturnsTheRawKey() throws Exception {
        setUpProject();
        mvc.perform(post(KEYS).contentType(JSON).content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get(KEYS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].key").doesNotExist())
            .andExpect(jsonPath("$.items[0].keyPrefix").value(org.hamcrest.Matchers.startsWith("fbx_client_")))
            .andExpect(jsonPath("$.items[0].active").value(true));
    }

    @Test
    void revokeDisablesTheKey() throws Exception {
        setUpProject();
        MvcResult created = mvc.perform(post(KEYS).contentType(JSON)
                .content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(delete(KEYS + "/" + id)).andExpect(status().isNoContent());

        mvc.perform(get(KEYS))
            .andExpect(jsonPath("$.items[0].active").value(false))
            .andExpect(jsonPath("$.items[0].revokedAt").exists());
    }

    @Test
    void unknownKeyTypeIsRejected() throws Exception {
        setUpProject();

        mvc.perform(post(KEYS).contentType(JSON).content("{\"name\":\"x\",\"type\":\"SUPERUSER\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void keysOfAnotherEnvironmentCannotBeRevokedHere() throws Exception {
        setUpProject();
        mvc.perform(post("/api/v1/projects/checkout/environments").contentType(JSON)
                .content("{\"key\":\"staging\",\"name\":\"Staging\"}"))
            .andExpect(status().isCreated());
        MvcResult created = mvc.perform(post("/api/v1/projects/checkout/environments/staging/api-keys")
                .contentType(JSON).content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        Integer id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(delete(KEYS + "/" + id)).andExpect(status().isNotFound());
    }

    private void setUpProject() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
    }
}
