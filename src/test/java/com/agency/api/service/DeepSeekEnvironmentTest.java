package com.agency.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class DeepSeekEnvironmentTest {
    private final Environment environment;

    @Autowired
    DeepSeekEnvironmentTest(Environment environment) { this.environment = environment; }

    @Test
    void loadsDeepSeekKeyFromOptionalDotEnvFile() {
        assertFalse(environment.getProperty("DEEPSEEK_API_KEY", "").isBlank());
    }
}
