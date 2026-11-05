package dev.a2zrr.flagbox.apikey;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.a2zrr.flagbox.support.AdminKeyTestConfig;
import dev.a2zrr.flagbox.support.FrozenRateLimitClockConfig;
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

/**
 * Over HTTP, with the test profile's limit of 3 per second. The clock is frozen,
 * so the fourth request is rejected without any waiting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AdminKeyTestConfig.class, FrozenRateLimitClockConfig.class})
class EvaluationRateLimitHttpIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;
    private static final String EVALUATE_BODY = "{\"flagKey\":\"new-checkout\",\"context\":{\"userId\":\"user-1\"}}";

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
    void fourthEvaluationWithinASecondIsRejectedWithRetryAfterAndProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\"}"))
            .andExpect(status().isCreated());
        MvcResult created = mvc.perform(post("/api/v1/projects/checkout/environments/production/api-keys")
                .contentType(JSON).content("{\"name\":\"busy-client\",\"type\":\"CLIENT\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String clientKey = JsonPath.read(created.getResponse().getContentAsString(), "$.key");

        for (int i = 1; i <= 3; i++) {
            mvc.perform(post("/api/v1/evaluate").header("X-API-Key", clientKey).contentType(JSON)
                    .content(EVALUATE_BODY))
                .andExpect(status().isOk());
        }

        mvc.perform(post("/api/v1/evaluate").header("X-API-Key", clientKey).contentType(JSON)
                .content(EVALUATE_BODY))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.title").value("Too many requests"))
            .andExpect(jsonPath("$.detail").value("This API key is limited to 3 evaluation requests per second"));
    }
}
