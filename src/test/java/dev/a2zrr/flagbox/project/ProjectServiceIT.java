package dev.a2zrr.flagbox.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(ProjectService.class)
class ProjectServiceIT extends PostgresIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EnvironmentRepository environments;

    @Test
    void createsProjectWithDefaultDevelopmentAndProductionEnvironments() {
        Project project = projectService.create("checkout-app", "Checkout App", "Web checkout");

        assertThat(project.getId()).isNotNull();
        assertThat(project.getCreatedAt()).isNotNull();
        assertThat(environments.findByProject(project))
            .extracting(Environment::getKey)
            .containsExactlyInAnyOrder("development", "production");
    }

    @Test
    void rejectsDuplicateProjectKey() {
        projectService.create("checkout-app", "Checkout App", null);

        assertThatThrownBy(() -> projectService.create("checkout-app", "Another name", null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void rejectsInvalidProjectKey() {
        assertThatThrownBy(() -> projectService.create("Checkout App", "Checkout App", null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
