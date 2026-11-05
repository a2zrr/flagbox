package dev.a2zrr.flagbox.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.a2zrr.flagbox.support.AdminKeyTestConfig;
import dev.a2zrr.flagbox.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BootstrapAdminKeyIT extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiKeyRepository keys;

    @Autowired
    private BootstrapAdminKey bootstrap;

    @Test
    void bootstrapKeyIsRegisteredAsAGlobalAdminKeyAndWorks() throws Exception {
        assertThat(keys.existsByProjectIsNullAndType(ApiKeyType.ADMIN)).isTrue();

        mvc.perform(get("/api/v1/projects").header("X-API-Key", AdminKeyTestConfig.BOOTSTRAP_ADMIN_KEY))
            .andExpect(status().isOk());
    }

    @Test
    void registeringAgainDoesNotCreateASecondGlobalKey() {
        long before = globalKeyCount();

        bootstrap.ensureGlobalAdminKey();
        bootstrap.ensureGlobalAdminKey();

        assertThat(globalKeyCount()).isEqualTo(before).isEqualTo(1);
    }

    @Test
    void theRawKeyIsNeverStored() {
        assertThat(keys.findAll())
            .allSatisfy(key -> assertThat(key.getKeyHash()).isNotEqualTo(AdminKeyTestConfig.BOOTSTRAP_ADMIN_KEY));
    }

    private long globalKeyCount() {
        return keys.findAll().stream().filter(ApiKey::isGlobal).count();
    }
}
