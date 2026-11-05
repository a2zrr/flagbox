package dev.a2zrr.flagbox.flag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.a2zrr.flagbox.environment.Environment;
import dev.a2zrr.flagbox.project.Project;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FlagConfigTest {

    private final Project project = new Project("checkout", "Checkout", null);
    private final Environment production = new Environment(project, "production", "Production");
    private final Flag flag = new Flag(project, "new-checkout", "New checkout", null);

    @Test
    void newConfigIsDisabledWithFullRolloutAndNoRules() {
        FlagConfig config = new FlagConfig(flag, production);

        assertThat(config.isEnabled()).isFalse();
        assertThat(config.getRolloutPercentage()).isEqualTo(100);
        assertThat(config.getRules()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 30, 99, 100})
    void acceptsRolloutWithinRange(int percentage) {
        FlagConfig config = new FlagConfig(flag, production);
        config.setRolloutPercentage(percentage);
        assertThat(config.getRolloutPercentage()).isEqualTo(percentage);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101, 1000})
    void rejectsRolloutOutsideRange(int percentage) {
        FlagConfig config = new FlagConfig(flag, production);
        assertThatThrownBy(() -> config.setRolloutPercentage(percentage))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 0 and 100");
    }

    @Test
    void rulesAreCopiedSoLaterChangesDoNotLeakIn() {
        FlagConfig config = new FlagConfig(flag, production);
        List<TargetingRule> source = new ArrayList<>();
        source.add(new UserListRule(List.of("user-1"), true));

        config.setRules(source);
        source.clear();

        assertThat(config.getRules()).hasSize(1);
    }

    @Test
    void rejectsMoreThanMaxRules() {
        FlagConfig config = new FlagConfig(flag, production);
        List<TargetingRule> tooMany = Collections.nCopies(FlagConfig.MAX_RULES + 1,
            new UserListRule(List.of("user-1"), true));

        assertThatThrownBy(() -> config.setRules(tooMany))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most 100 rules");
    }

    @Test
    void rejectsNullRuleEntries() {
        FlagConfig config = new FlagConfig(flag, production);
        List<TargetingRule> withNull = new ArrayList<>();
        withNull.add(null);

        assertThatThrownBy(() -> config.setRules(withNull))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
