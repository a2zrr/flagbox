package dev.a2zrr.flagbox.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class ProjectApiIT extends PostgresIntegrationTest {

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

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
    void createsProjectWithDefaultEnvironments() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Checkout\",\"description\":\"Web checkout\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/v1/projects/checkout"))
            .andExpect(jsonPath("$.key").value("checkout"))
            .andExpect(jsonPath("$.createdAt").exists());

        mvc.perform(get("/api/v1/projects/checkout/environments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].key").value("development"))
            .andExpect(jsonPath("$.items[1].key").value("production"));
    }

    @Test
    void duplicateKeyReturnsProblemDetails409() throws Exception {
        createProject("checkout");

        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"checkout\",\"name\":\"Again\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Conflict"))
            .andExpect(jsonPath("$.detail").value("A project with key 'checkout' already exists"));
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"Checkout App\",\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Validation failed"))
            .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItems("key", "name")));
    }

    @Test
    void malformedJsonReturnsProblemDetails400WithoutStackTrace() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON).content("{not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Malformed request"))
            .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void listIsPaginatedAndRejectsOversizedPages() throws Exception {
        createProject("alpha");
        createProject("beta");
        createProject("gamma");

        mvc.perform(get("/api/v1/projects").param("page", "0").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.items[0].key").value("alpha"));

        mvc.perform(get("/api/v1/projects").param("size", "500"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updatesDescriptionAndDeletesProject() throws Exception {
        createProject("checkout");

        mvc.perform(patch("/api/v1/projects/checkout").contentType(JSON)
                .content("{\"description\":\"Payments\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("checkout"))
            .andExpect(jsonPath("$.description").value("Payments"));

        mvc.perform(delete("/api/v1/projects/checkout"))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/projects/checkout"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unknownProjectReturnsNotFound() throws Exception {
        mvc.perform(get("/api/v1/projects/missing/flags"))
            .andExpect(status().isNotFound());
    }

    private void createProject(String key) throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(JSON)
                .content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
            .andExpect(status().isCreated());
    }
}
