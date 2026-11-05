package dev.a2zrr.flagbox.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.environment.EnvironmentRepository;
import dev.a2zrr.flagbox.project.Project;
import dev.a2zrr.flagbox.project.ProjectService;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(ProjectService.class)
class FlagPersistenceIT extends PostgresIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private EnvironmentRepository environments;

    @Autowired
    private FlagRepository flags;

    @Autowired
    private FlagConfigRepository configs;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsTargetingRulesAsOrderedJsonAndReadsThemBack() {
        Project project = projectService.create("checkout", "Checkout", null);
        Environment production = environments.findByProjectAndKey(project, "production").orElseThrow();
        Flag flag = flags.saveAndFlush(new Flag(project, "new-checkout", "New checkout", "Redesigned flow"));

        FlagConfig config = new FlagConfig(flag, production);
        config.setEnabled(true);
        config.setRolloutPercentage(30);
        config.setDefaultValue(false);
        config.setRules(List.of(
            new UserListRule(List.of("beta-user-1"), true),
            new AttributeRule("country", AttributeOperator.IN, List.of("FR", "DZ"), true),
            new AttributeRule("plan", AttributeOperator.EQUALS, List.of("free"), false)));
        configs.saveAndFlush(config);

        entityManager.clear();

        FlagConfig loaded = configs.findByFlagAndEnvironment(flag, production).orElseThrow();
        assertThat(loaded.isEnabled()).isTrue();
        assertThat(loaded.getRolloutPercentage()).isEqualTo(30);
        assertThat(loaded.getRules()).containsExactly(
            new UserListRule(List.of("beta-user-1"), true),
            new AttributeRule("country", AttributeOperator.IN, List.of("FR", "DZ"), true),
            new AttributeRule("plan", AttributeOperator.EQUALS, List.of("free"), false));
    }

    @Test
    void flagKeyIsUniquePerProjectButCanRepeatAcrossProjects() {
        Project checkout = projectService.create("checkout", "Checkout", null);
        Project search = projectService.create("search", "Search", null);

        flags.saveAndFlush(new Flag(checkout, "beta", "Beta", null));
        flags.saveAndFlush(new Flag(search, "beta", "Beta", null));

        assertThat(flags.existsByProjectAndKey(search, "beta")).isTrue();
        assertThatThrownBy(() -> flags.saveAndFlush(new Flag(checkout, "beta", "Beta again", null)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsRolloutOutsideZeroToHundred() {
        Project project = projectService.create("checkout", "Checkout", null);
        Environment production = environments.findByProjectAndKey(project, "production").orElseThrow();
        Flag flag = flags.saveAndFlush(new Flag(project, "new-checkout", "New checkout", null));
        FlagConfig config = configs.saveAndFlush(new FlagConfig(flag, production));

        assertThatThrownBy(() -> jdbc.update(
            "UPDATE flag_configs SET rollout_percentage = 150 WHERE id = ?", config.getId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
