package dev.a2zrr.flagbox.flag;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = "ADMIN")
class FlagApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String PROJECT = "/api/v1/projects/checkout";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    // Each request commits on its own, so reset the tables instead of rolling back.
    @BeforeEach
    @AfterEach
    void resetTables() {
        jdbc.execute("TRUNCATE audit_logs, projects RESTART IDENTITY CASCADE");
    }

    @Test
    void newFlagStartsDisabledInEveryEnvironment() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(get(PROJECT + "/environments/production/flags/new-checkout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.rolloutPercentage").value(100))
            .andExpect(jsonPath("$.rules.length()").value(0));
        mvc.perform(get(PROJECT + "/environments/development/flags/new-checkout"))
            .andExpect(status().isOk());
    }

    @Test
    void environmentCreatedLaterGetsConfigsForExistingFlags() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(post(PROJECT + "/environments").contentType(JSON)
                .content("{\"key\":\"staging\",\"name\":\"Staging\"}"))
            .andExpect(status().isCreated());

        mvc.perform(get(PROJECT + "/environments/staging/flags/new-checkout"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void patchTogglesRolloutAndSetsRulesInOrder() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("""
                    {
                      "enabled": true,
                      "rolloutPercentage": 30,
                      "rules": [
                        {"type": "USER_LIST", "userIds": ["beta-1"], "value": true},
                        {"type": "ATTRIBUTE", "attribute": "country", "operator": "IN", "values": ["FR", "DZ"], "value": true}
                      ]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.rolloutPercentage").value(30))
            .andExpect(jsonPath("$.rules[0].type").value("USER_LIST"))
            .andExpect(jsonPath("$.rules[0].userIds[0]").value("beta-1"))
            .andExpect(jsonPath("$.rules[1].type").value("ATTRIBUTE"))
            .andExpect(jsonPath("$.rules[1].operator").value("IN"));
    }

    @Test
    void partialPatchLeavesOtherFieldsUnchanged() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"rolloutPercentage\": 40}"))
            .andExpect(status().isOk());

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"enabled\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.rolloutPercentage").value(40));
    }

    @Test
    void rolloutOutsideRangeIsRejectedWithFieldError() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"rolloutPercentage\": 150}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("rolloutPercentage"));
    }

    @Test
    void invalidRuleIsRejectedWithTheDomainMessage() throws Exception {
        setUpProject();
        createFlag("new-checkout");

        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("""
                    {"rules": [{"type": "ATTRIBUTE", "attribute": "plan", "operator": "EQUALS",
                                "values": ["pro", "free"], "value": true}]}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail", containsString("EQUALS takes exactly one value")));
    }

    @Test
    void archivedFlagsAreHiddenUnlessRequested() throws Exception {
        setUpProject();
        createFlag("old-banner");
        mvc.perform(patch(PROJECT + "/flags/old-banner").contentType(JSON).content("{\"archived\": true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(true));

        mvc.perform(get(PROJECT + "/flags"))
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(PROJECT + "/flags").param("includeArchived", "true"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void historyListsChangesNewestFirstWithOldAndNewValues() throws Exception {
        setUpProject();
        createFlag("new-checkout");
        mvc.perform(patch(PROJECT + "/environments/production/flags/new-checkout").contentType(JSON)
                .content("{\"enabled\": true, \"rolloutPercentage\": 30}"))
            .andExpect(status().isOk());

        mvc.perform(get(PROJECT + "/flags/new-checkout/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].entityType").value("FLAG_CONFIG"))
            .andExpect(jsonPath("$.items[0].action").value("UPDATE"))
            .andExpect(jsonPath("$.items[0].actor").value("admin"))
            .andExpect(jsonPath("$.items[0].oldValue.enabled").value(false))
            .andExpect(jsonPath("$.items[0].newValue.enabled").value(true))
            .andExpect(jsonPath("$.items[0].newValue.rolloutPercentage").value(30))
            .andExpect(jsonPath("$.items[1].entityType").value("FLAG"))
            .andExpect(jsonPath("$.items[1].action").value("CREATE"));
    }

    @Test
    void unknownFlagReturnsNotFound() throws Exception {
        setUpProject();

        mvc.perform(get(PROJECT + "/environments/production/flags/nope"))
            .andExpect(status().isNotFound());
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
}
