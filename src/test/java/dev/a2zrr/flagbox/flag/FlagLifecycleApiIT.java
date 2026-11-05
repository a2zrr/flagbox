package dev.a2zrr.flagbox.flag;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.a2zrr.flagbox.support.AdminKeyTestConfig;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import java.util.Collections;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Flag lifecycle: validation, deletion, archiving, history, and the cache
 * eviction each change must trigger. Evaluation goes through a real CLIENT key.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKeyTestConfig.class)
class FlagLifecycleApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PROJECT = "/api/v1/projects/checkout";
    private static final String BODY = "{\"flagKey\":\"new-checkout\",\"context\":{\"userId\":\"user-1\"}}";

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
    void duplicateFlagKeyIsAConflict() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(post(PROJECT + "/flags").contentType(JSON)
                .content("{\"key\":\"new-checkout\",\"name\":\"Again\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("A flag with key 'new-checkout' already exists in this project"));
    }

    @Test
    void invalidFlagKeyAndBlankNameAreFieldErrors() throws Exception {
        setUpProject();

        mvc.perform(post(PROJECT + "/flags").contentType(JSON).content("{\"key\":\"New Flag\",\"name\":\" \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field", hasItems("key", "name")));
    }

    @Test
    void blankDescriptionClearsIt() throws Exception {
        setUpProject();
        mvc.perform(post(PROJECT + "/flags").contentType(JSON)
                .content("{\"key\":\"new-checkout\",\"name\":\"New\",\"description\":\"Redesign\"}"))
            .andExpect(status().isCreated());

        mvc.perform(patch(PROJECT + "/flags/new-checkout").contentType(JSON).content("{\"description\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description", nullValue()))
            .andExpect(jsonPath("$.name").value("New"));
    }

    @Test
    void deletingAFlagReturnsItToFlagNotFoundForClientsAndKeepsItsHistory() throws Exception {
        String client = setUpProjectAndClientKey();
        createFlag("new-checkout");
        configure("new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 100}");
        evaluate(client).andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));

        mvc.perform(delete(PROJECT + "/flags/new-checkout")).andExpect(status().isNoContent());

        // The cached snapshot must be gone, not just the flag row.
        evaluate(client).andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
        mvc.perform(get(PROJECT + "/flags/new-checkout")).andExpect(status().isNotFound());
        mvc.perform(get(PROJECT + "/flags/new-checkout/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].action", hasItems("CREATE", "UPDATE", "DELETE")));
    }

    @Test
    void archivingAndRestoringEvictsTheCachedSnapshotBothWays() throws Exception {
        String client = setUpProjectAndClientKey();
        createFlag("new-checkout");
        configure("new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 100}");
        evaluate(client).andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));

        mvc.perform(patch(PROJECT + "/flags/new-checkout").contentType(JSON).content("{\"archived\": true}"))
            .andExpect(status().isOk());
        evaluate(client).andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));

        mvc.perform(patch(PROJECT + "/flags/new-checkout").contentType(JSON).content("{\"archived\": false}"))
            .andExpect(status().isOk());
        evaluate(client).andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));
    }

    @Test
    void creatingAnEnvironmentAfterTheFlagGivesItAConfigThatClientsSee() throws Exception {
        String production = setUpProjectAndClientKey();
        createFlag("new-checkout");
        mvc.perform(post(PROJECT + "/environments").contentType(JSON)
                .content("{\"key\":\"staging\",\"name\":\"Staging\"}"))
            .andExpect(status().isCreated());
        String staging = clientKey("staging");

        evaluate(production).andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
        evaluate(staging).andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void revokingAKeyTwiceIsHarmless() throws Exception {
        setUpProject();
        MvcResult created = mvc.perform(post(PROJECT + "/environments/production/api-keys").contentType(JSON)
                .content("{\"name\":\"web\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(delete(PROJECT + "/environments/production/api-keys/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete(PROJECT + "/environments/production/api-keys/" + id)).andExpect(status().isNoContent());
        mvc.perform(get(PROJECT + "/environments/production/api-keys"))
            .andExpect(jsonPath("$.items[0].active").value(false));
    }

    @Test
    void tooManyRulesIsRejectedWithAMessage() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        String rule = "{\"type\":\"USER_LIST\",\"userIds\":[\"u\"],\"value\":true}";
        String rules = String.join(",", Collections.nCopies(FlagConfig.MAX_RULES + 1, rule));

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"rules\": [" + rules + "]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("rules"));
    }

    @Test
    void unknownRuleTypeIsAMalformedRequest() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"rules\": [{\"type\":\"GEO_FENCE\",\"value\":true}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void unknownEnvironmentOrFlagIsNotFoundForConfigOperations() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(get(PROJECT + "/environments/nowhere/flags")).andExpect(status().isNotFound());
        mvc.perform(get(PROJECT + "/environments/production/flags/nope")).andExpect(status().isNotFound());
        mvc.perform(patch(PROJECT + "/environments/production/flags/nope").contentType(JSON)
                .content("{\"enabled\": true}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void configListIsSortedByFlagKeyAndPaginated() throws Exception {
        setUpProject();
        createFlag("zeta");
        createFlag("alpha");

        mvc.perform(get(PROJECT + "/environments/production/flags").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].flagKey").value("alpha"))
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get(PROJECT + "/environments/production/flags").param("size", "1").param("page", "1"))
            .andExpect(jsonPath("$.items[0].flagKey").value("zeta"));
    }

    @Test
    void historyRecordsTheRolloutChangeWithItsOldAndNewValues() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        configure("new-checkout", "{\"rolloutPercentage\": 30}");

        mvc.perform(get(PROJECT + "/flags/new-checkout/history"))
            .andExpect(jsonPath("$.items[0].oldValue.rolloutPercentage").value(100))
            .andExpect(jsonPath("$.items[0].newValue.rolloutPercentage").value(30))
            .andExpect(jsonPath("$.items[0].newValue.rules").isArray());
    }

    @Test
    void unknownFlagHistoryIsEmptyAndUnknownProjectIsNotFound() throws Exception {
        setUpProject();
        mvc.perform(get(PROJECT + "/flags/never-existed/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/projects/ghost/flags/x/history")).andExpect(status().isNotFound());
    }

    @Test
    void errorBodyNamesTheCauseWithoutLeakingInternals() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"rolloutPercentage\": -5}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("One or more fields are invalid")))
            .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    private ResultActions evaluate(String clientKey) throws Exception {
        return mvc.perform(post("/api/v1/evaluate").header("X-API-Key", clientKey).contentType(JSON).content(BODY));
    }

    private void configure(String flagKey, String json) throws Exception {
        mvc.perform(patch(PROJECT + "/environments/production/flags/" + flagKey).contentType(JSON).content(json))
            .andExpect(status().isOk());
    }

    private void setUpProject() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
    }

    private String setUpProjectAndClientKey() throws Exception {
        setUpProject();
        return clientKey("production");
    }

    private String clientKey(String environment) throws Exception {
        MvcResult created = mvc.perform(post(PROJECT + "/environments/" + environment + "/api-keys")
                .contentType(JSON).content("{\"name\":\"web-" + environment + "\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(created.getResponse().getContentAsString(), "$.key");
    }

    private void createFlag(String key) throws Exception {
        mvc.perform(post(PROJECT + "/flags").contentType(JSON)
                .content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }
}
