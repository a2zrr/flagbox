package dev.a2zrr.flagbox.evaluation;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.a2zrr.flagbox.apikey.ApiKeyAuthentication;
import dev.a2zrr.flagbox.apikey.ApiKeyPrincipal;
import dev.a2zrr.flagbox.apikey.ApiKeyType;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import dev.a2zrr.flagbox.project.ProjectRepository;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class EvaluationApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PROJECT = "/api/v1/projects/checkout";
    private static final String EVALUATE = "/api/v1/evaluate";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private EnvironmentRepository environments;

    @BeforeEach
    @AfterEach
    void resetTables() {
        jdbc.execute("TRUNCATE audit_logs, projects RESTART IDENTITY CASCADE");
    }

    @Test
    void unknownFlagReturnsNullValueAndFlagNotFound() throws Exception {
        setUpProject();

        evaluate(productionKey(), "{\"flagKey\":\"nope\",\"context\":{\"userId\":\"user-1\"}}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.value", nullValue()))
            .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
    }

    @Test
    void disabledFlagServesFalse() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.value").value(false))
            .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void offUserRuleBeforeOnUserRuleWinsOverTheWire() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        configure("production", "new-checkout", """
            {"enabled": true, "rolloutPercentage": 100, "rules": [
              {"type": "USER_LIST", "userIds": ["user-1"], "value": false},
              {"type": "USER_LIST", "userIds": ["user-1"], "value": true}
            ]}
            """);

        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.value").value(false))
            .andExpect(jsonPath("$.reason").value("USER_DENIED"));
    }

    @Test
    void attributeRuleServingFalseReportsRuleMatchWithFalse() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        configure("production", "new-checkout", """
            {"enabled": true, "rolloutPercentage": 100, "rules": [
              {"type": "ATTRIBUTE", "attribute": "country", "operator": "IN", "values": ["DE"], "value": false}
            ]}
            """);

        evaluate(productionKey(), "{\"flagKey\":\"new-checkout\",\"context\":{\"userId\":\"user-1\","
                + "\"attributes\":{\"country\":\"DE\"}}}")
            .andExpect(jsonPath("$.value").value(false))
            .andExpect(jsonPath("$.reason").value("RULE_MATCH"));
    }

    @Test
    void rolloutZeroAndHundredAreDeterministic() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        configure("production", "new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 0}");

        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_OUT"));

        configure("production", "new-checkout", "{\"rolloutPercentage\": 100}");

        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.value").value(true))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));
    }

    @Test
    void configChangeIsVisibleOnTheNextEvaluation() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        configure("production", "new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 0}");
        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_OUT"));

        // The snapshot is cached; this change must evict it.
        configure("production", "new-checkout", "{\"enabled\": false}");

        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
    }

    @Test
    void archivedFlagIsNotFoundToClients() throws Exception {
        setUpProject();
        createFlag("old-banner");
        configure("production", "old-banner", "{\"enabled\": true, \"rolloutPercentage\": 100}");
        evaluate(productionKey(), evaluateBody("old-banner", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));

        mvc.perform(patch(PROJECT + "/flags/old-banner").contentType(JSON).content("{\"archived\": true}"))
            .andExpect(status().isOk());

        evaluate(productionKey(), evaluateBody("old-banner", "user-1"))
            .andExpect(jsonPath("$.reason").value("FLAG_NOT_FOUND"));
    }

    @Test
    void environmentsAreIsolated() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        configure("production", "new-checkout", "{\"enabled\": true, \"rolloutPercentage\": 100}");

        // Staging still has the flag disabled, and a staging key only sees staging.
        evaluate(stagingKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("FLAG_DISABLED"));
        evaluate(productionKey(), evaluateBody("new-checkout", "user-1"))
            .andExpect(jsonPath("$.reason").value("ROLLOUT_IN"));
    }

    @Test
    void evaluateAllReturnsActiveFlagsSortedByKey() throws Exception {
        setUpProject();
        createFlag("zeta");
        createFlag("alpha");
        createFlag("archived-one");
        mvc.perform(patch(PROJECT + "/flags/archived-one").contentType(JSON).content("{\"archived\": true}"))
            .andExpect(status().isOk());

        mvc.perform(post(EVALUATE + "/all").with(authentication(productionKey()))
                .contentType(JSON).content("{\"context\":{\"userId\":\"user-1\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.flags", hasSize(2)))
            .andExpect(jsonPath("$.flags[0].flagKey").value("alpha"))
            .andExpect(jsonPath("$.flags[1].flagKey").value("zeta"))
            .andExpect(jsonPath("$.flags[0].reason").value("FLAG_DISABLED"));
    }

    @Test
    void missingUserIdIsRejected() throws Exception {
        setUpProject();

        evaluate(productionKey(), "{\"flagKey\":\"new-checkout\",\"context\":{}}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("context.userId"));
    }

    @Test
    void managementUserWithoutAKeyCannotEvaluate() throws Exception {
        setUpProject();

        // @WithMockUser is a management login, not an API key: evaluation needs a key scope.
        mvc.perform(post(EVALUATE).contentType(JSON).content(evaluateBody("new-checkout", "user-1")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }

    private ResultActions evaluate(ApiKeyAuthentication key, String body) throws Exception {
        return mvc.perform(post(EVALUATE).with(authentication(key)).contentType(JSON).content(body));
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

    private void createFlag(String key) throws Exception {
        mvc.perform(post(PROJECT + "/flags").contentType(JSON)
                .content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }

    private ApiKeyAuthentication productionKey() {
        return keyFor("production");
    }

    private ApiKeyAuthentication stagingKey() throws Exception {
        mvc.perform(post(PROJECT + "/environments").contentType(JSON)
                .content("{\"key\":\"staging\",\"name\":\"Staging\"}"))
            .andExpect(status().isCreated());
        return keyFor("staging");
    }

    private ApiKeyAuthentication keyFor(String environmentKey) {
        Long projectId = projects.findByKey("checkout").orElseThrow().getId();
        Long environmentId = environments
            .findByProjectAndKey(projects.findByKey("checkout").orElseThrow(), environmentKey)
            .orElseThrow().getId();
        return new ApiKeyAuthentication(new ApiKeyPrincipal(1L, "test-client", ApiKeyType.CLIENT,
            projectId, environmentId));
    }
}
