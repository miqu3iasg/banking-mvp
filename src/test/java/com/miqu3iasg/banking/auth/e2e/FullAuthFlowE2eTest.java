package com.miqu3iasg.banking.auth.e2e;

import com.miqu3iasg.banking.auth.config.TestContainersConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end authentication flow - placeholder for future implementation.
 * This test is disabled until the WebTestClient configuration is properly set up.
 */
@Disabled("Pending WebTestClient configuration fix")
@SpringBootTest(classes = TestContainersConfig.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FullAuthFlowE2eTest {

    @Test
    void placeholderTest() {
        // Placeholder - full E2E tests to be implemented in AuthE2eTest
    }
}
