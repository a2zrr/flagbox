package dev.a2zrr.flagbox.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.environment.Environment;
import org.junit.jupiter.api.Test;

class ProjectEntityTest {

    @Test
    void constructorValidatesKeyAndName() {
        assertThatThrownBy(() -> new Project("Checkout", "Checkout", null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Project("checkout", null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateDetailsTreatsNullAsUnchangedAndBlankDescriptionAsCleared() {
        Project project = new Project("checkout", " Checkout ", "Web checkout");
        assertThat(project.getName()).isEqualTo("Checkout");

        project.updateDetails(null, null);
        assertThat(project.getName()).isEqualTo("Checkout");
        assertThat(project.getDescription()).isEqualTo("Web checkout");

        project.updateDetails(null, "");
        assertThat(project.getDescription()).isNull();
    }

    @Test
    void updateDetailsRejectsAnOverlongName() {
        Project project = new Project("checkout", "Checkout", null);

        assertThatThrownBy(() -> project.updateDetails("x".repeat(121), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void environmentRenameValidatesAndStripsTheName() {
        Project project = new Project("checkout", "Checkout", null);
        Environment environment = new Environment(project, "staging", "Staging");

        environment.rename("  Pre-prod  ");
        assertThat(environment.getName()).isEqualTo("Pre-prod");
        assertThatThrownBy(() -> environment.rename(""))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Environment(null, "staging", "Staging"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
