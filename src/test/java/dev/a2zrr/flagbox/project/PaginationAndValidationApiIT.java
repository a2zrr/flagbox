package dev.a2zrr.flagbox.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Pagination limits and request validation, shared by every list endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminKeyTestConfig.class)
class PaginationAndValidationApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

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
    void pageSizeOutsideOneToOneHundredIsRejected() throws Exception {
        mvc.perform(get("/api/v1/projects").param("size", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/projects").param("size", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void negativePageIsRejected() throws Exception {
        mvc.perform(get("/api/v1/projects").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void nonNumericPageIsRejectedAsAMalformedRequest() throws Exception {
        mvc.perform(get("/api/v1/projects").param("page", "first"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void pageBeyondTheEndIsEmptyButStillReportsTheTotals() throws Exception {
        createProjects("alpha", "beta", "gamma");

        mvc.perform(get("/api/v1/projects").param("size", "2").param("page", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void lastPageHoldsTheRemainingItems() throws Exception {
        createProjects("alpha", "beta", "gamma");

        mvc.perform(get("/api/v1/projects").param("size", "2").param("page", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].key").value("gamma"));
    }

    @Test
    void emptyListReturnsZeroTotals() throws Exception {
        mvc.perform(get("/api/v1/projects"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void invalidProjectBodyReportsEveryBadField() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"-leading\",\"name\":\"" + "n".repeat(121) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItems("key", "name")));
    }

    @Test
    void unknownFieldsInABodyAreIgnored() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\",\"surprise\":true}"))
            .andExpect(status().isCreated());
    }

    private void createProjects(String... keys) throws Exception {
        for (String key : keys) {
            mvc.perform(post("/api/v1/projects").contentType(JSON)
                    .content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
                .andExpect(status().isCreated());
        }
    }
}
