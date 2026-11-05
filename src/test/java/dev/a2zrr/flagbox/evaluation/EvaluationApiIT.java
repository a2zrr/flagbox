package dev.a2zrr.flagbox.evaluation;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKeyTestConfig.class)
class EvaluationApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PROJECT = "/api/v1/projects/checkout";
    private static final String EVALUATE = "/api/v1/evaluate";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // Each request commits on its own, so reset the tables instead of rolling back.
    @BeforeEach
    @AfterEach
    void resetTables() {
        jdbc.execute("DELETE FROM audit_logs; DELETE FROM projects");
    }

    @Test
    void unknownFlagReturnsNullValueAndFlagNotFound() throws Exception {
        String key = setUpProjectAndClientKey("production");

        evaluate(key, "{\"flagKey\":\"nope\",\"context\":{\"userId\":\"user-1\"}}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value", nullValue()))
            .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
    }

    @Test
    void disabledFlagServesFalse() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("new-checkout");

        evaluate(key, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.value").value(false))
            .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void offUserRuleBeforeOnUserRuleWinsOverTheWire() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("new-checkout");
        configure("production", "new-checkout", """
            {"enabled": true, "rolloutPercentage": 100, "rules": [
              {"type": "USER_LIST", "userIds": ["user-1"], "value": false},
              {"type": "USER_LIST", "userIds": ["user-1"], "value": true}
            ]}
            """);

        evaluate(key, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.value").value(false))
            .andExpect(jsonPath("$.reason").value("USER_DENIED"));
    }

    @Test
    void attributeRuleServingFalseReportsRuleMatchWithFalse() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("new-checkout");
        configure("production", "new-checkout", """
            {"enabled": true, "rolloutPercentage": 100, "rules": [
              {"type": "ATTRIBUTE", "attribute": "country", "operator": "IN", "values": ["DE"], "value": false}
            ]}
            """);

        evaluate(key, "{\"flagKey\":\"new-checkout\",\"context\":{\"userId\":\"user-1\","
                + "\"attributes\":{\"country\":\"DE\"}}}")
            .andExpect(jsonPath("$.value").value(false))
            .andExpect(jsonPath("$.reason").value("RULE_MATCH"));
    }

    @Test
    void rolloutZeroAndHundredAreDeterministic() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("new-checkout");
        configure("production", "new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 0}");

        evaluate(key, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_OUT"));

        configure("production", "new-checkout", "{\"rolloutPercentage\": 100}");

        evaluate(key, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.value").value(true))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));
    }

    @Test
    void configChangeIsVisibleOnTheNextEvaluation() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("new-checkout");
        configure("production", "new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 0}");
        evaluate(key, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_OUT"));

        // The snapshot is cached; this change must evict it.
        configure("production", "new-checkout", "{\"enabled\": false}");

        evaluate(key, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void archivedFlagIsNotFoundToClients() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("old-banner");
        configure("production", "old-banner", "{\"enabled\": true, \"rolloutPercentage\": 100}");
        evaluate(key, evaluateBody("old-banner", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));

        mvc.perform(patch(PROJECT + "/flags/old-banner").contentType(JSON).content("{\"archived\": true}"))
            .andExpect(status().isOk());

        evaluate(key, evaluateBody("old-banner", "user-1"))
            .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
    }

    @Test
    void environmentsAreIsolated() throws Exception {
        String productionKey = setUpProjectAndClientKey("production");
        createEnvironment("staging");
        String stagingKey = clientKey("staging");
        createFlag("new-checkout");
        configure("production", "new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 100}");

        // Staging still has the flag disabled, and a staging key only sees staging.
        evaluate(stagingKey, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
        evaluate(productionKey, evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));
    }

    @Test
    void evaluateAllReturnsActiveFlagsSortedByKey() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("zeta");
        createFlag("alpha");
        createFlag("archived-one");
        mvc.perform(patch(PROJECT + "/flags/archived-one").contentType(JSON).content("{\"archived\": true}"))
            .andExpect(status().isOk());

        evaluateAll(key, "{\"context\":{\"userId\":\"user-1\"}}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flags", hasSize(2)))
            .andExpect(jsonPath("$.flags[0].flagKey").value("alpha"))
            .andExpect(jsonPath("$.flags[1].flagKey").value("zeta"))
            .andExpect(jsonPath("$.flags[0].reason").value("FLAG_DISABLED"));
    }

    @Test
    void missingUserIdIsRejectedWithAFieldError() throws Exception {
        String key = setUpProjectAndClientKey("production");

        evaluate(key, "{\"flagKey\":\"new-checkout\",\"context\":{}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("context.userId"));
    }

    @Test
    void globalAdminKeyCannotEvaluateBecauseItHasNoEnvironment() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(post(EVALUATE).header("X-API-Key", AdminKeyTestConfig.BOOTSTRAP_ADMIN_KEY)
                .contentType(JSON).content(evaluateBody("new-checkout", "user-1")))
            .andExpect(status().isForbidden());
    }

    @Test
    void evaluationsAreCountedPerFlagInMetrics() throws Exception {
        String key = setUpProjectAndClientKey("production");
        createFlag("counted");
        evaluate(key, evaluateBody("counted", "user-1")).andExpect(status().isOk());

        mvc.perform(get("/actuator/metrics/flagbox.evaluations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.measurements[0].value", greaterThanOrEqualTo(1.0)));
    }

    private ResultActions evaluate(String clientKey, String body) throws Exception {
        return mvc.perform(post(EVALUATE).header("X-API-Key", clientKey).contentType(JSON).content(body));
    }

    private ResultActions evaluateAll(String clientKey, String body) throws Exception {
        return mvc.perform(post(EVALUATE + "/all").header("X-API-Key", clientKey).contentType(JSON).content(body));
    }

    private static String evaluateBody(String flagKey, String userId) {
        return "{\"flagKey\":\"" + flagKey + "\",\"context\":{\"userId\":\"" + userId + "\"}}";
    }

    private void configure(String environment, String flagKey, String json) throws Exception {
        mvc.perform(patch(PROJECT + "/environments/" + environment + "/flags/" + flagKey)
                .contentType(JSON).content(json))
            .andExpect(status().isOk());
    }

    private void setUpProject() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
    }

    private String setUpProjectAndClientKey(String environment) throws Exception {
        setUpProject();
        return clientKey(environment);
    }

    private void createEnvironment(String key) throws Exception {
        mvc.perform(post(PROJECT + "/environments").contentType(JSON)
                .content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }

    private String clientKey(String environment) throws Exception {
        MvcResult result = mvc.perform(post(PROJECT + "/environments/" + environment + "/api-keys")
                .contentType(JSON).content("{\"name\":\"web-" + environment + "\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.key");
    }

    private void createFlag(String key) throws Exception {
        mvc.perform(post(PROJECT + "/flags").contentType(JSON)
                .content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }
}
