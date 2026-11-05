package dev.a2zrr.flagbox.environment;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKeyTestConfig.class)
class EnvironmentApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String ENVS = "/api/v1/projects/checkout/environments";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void resetTables() {
        jdbc.execute("DELETE FROM audit_logs; DELETE FROM projects");
    }

    @Test
    void getsAndRenamesAnEnvironmentButNotItsKey() throws Exception {
        setUpProject();

        mvc.perform(get(ENVS + "/production"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("production"))
            .andExpect(jsonPath("$.name").value("Production"));

        mvc.perform(patch(ENVS + "/production").contentType(JSON).content("{\"name\":\"Live\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Live"))
            .andExpect(jsonPath("$.key").value("production"));
    }

    @Test
    void duplicateEnvironmentKeyIsAConflict() throws Exception {
        setUpProject();

        mvc.perform(post(ENVS).contentType(JSON).content("{\"key\":\"production\",\"name\":\"Again\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void invalidEnvironmentKeyAndMissingNameAreFieldErrors() throws Exception {
        setUpProject();

        mvc.perform(post(ENVS).contentType(JSON).content("{\"key\":\"Staging Area\",\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItems("key", "name")));
    }

    @Test
    void renameWithAnEmptyNameIsRejected() throws Exception {
        setUpProject();

        mvc.perform(patch(ENVS + "/production").contentType(JSON).content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void deletingAnEnvironmentRemovesItsKeysAndLeavesTheOthersWorking() throws Exception {
        setUpProject();
        mvc.perform(post(ENVS).contentType(JSON).content("{\"key\":\"staging\",\"name\":\"Staging\"}"))
            .andExpect(status().isCreated());
        MvcResult keyResult = mvc.perform(post(ENVS + "/staging/api-keys").contentType(JSON)
                .content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String stagingKey = JsonPath.read(keyResult.getResponse().getContentAsString(), "$.key");

        mvc.perform(delete(ENVS + "/staging")).andExpect(status().isNoContent());

        mvc.perform(get(ENVS + "/staging")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/evaluate").header("X-API-Key", stagingKey).contentType(JSON)
                .content("{\"flagKey\":\"x\",\"context\":{\"userId\":\"u\"}}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(ENVS + "/production")).andExpect(status().isOk());
    }

    @Test
    void environmentListIsPaginatedAndSortedByKey() throws Exception {
        setUpProject();
        mvc.perform(post(ENVS).contentType(JSON).content("{\"key\":\"qa\",\"name\":\"QA\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get(ENVS).param("size", "1").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            // Sorted by key: development, production, qa. Page 1 of size 1 is production.
            .andExpect(jsonPath("$.items[0].key").value("production"))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(3));
    }

    private void setUpProject() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
    }
}
