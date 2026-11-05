package dev.a2zrr.flagbox.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import org.junit.jupiter.api.Test;

class FlagEntityTest {

    private final Project project = new Project("checkout", "Checkout", null);

    @Test
    void constructorValidatesProjectKeyAndName() {
        assertThatThrownBy(() -> new Flag(null, "beta", "Beta", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag(project, "Beta Flag", "Beta", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag(project, "beta", "  ", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Flag(project, "beta", "x".repeat(121), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullArgumentsLeaveDetailsUnchangedAndBlankDescriptionClearsIt() {
        Flag flag = new Flag(project, "beta", "  Beta  ", "first");
        assertThat(flag.getName()).isEqualTo("Beta");

        flag.updateDetails(null, null);
        assertThat(flag.getName()).isEqualTo("Beta");
        assertThat(flag.getDescription()).isEqualTo("first");

        flag.updateDetails("Beta v2", "   ");
        assertThat(flag.getName()).isEqualTo("Beta v2");
        assertThat(flag.getDescription()).isNull();
    }

    @Test
    void updatingWithABlankNameIsRejected() {
        Flag flag = new Flag(project, "beta", "Beta", null);

        assertThatThrownBy(() -> flag.updateDetails(" ", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(flag.getName()).isEqualTo("Beta");
    }

    @Test
    void archiveAndRestoreToggleTheFlag() {
        Flag flag = new Flag(project, "beta", "Beta", null);

        flag.archive();
        assertThat(flag.isArchived()).isTrue();

        flag.restore();
        assertThat(flag.isArchived()).isFalse();
    }

    @Test
    void flagConfigDefaultsAreDisabledWithFullRolloutAndNoRules() {
        Environment production = new Environment(project, "production", "Production");
        FlagConfig config = new FlagConfig(new Flag(project, "beta", "Beta", null), production);

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getRolloutPercentage()).isEqualTo(100);
        assertThat(config.getRules()).isEmpty();
    }

    @Test
    void flagConfigRequiresItsFlagAndEnvironment() {
        Environment production = new Environment(project, "production", "Production");
        Flag flag = new Flag(project, "beta", "Beta", null);

        assertThatThrownBy(() -> new FlagConfig(null, production)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FlagConfig(flag, null)).isInstanceOf(NullPointerException.class);
    }
}
