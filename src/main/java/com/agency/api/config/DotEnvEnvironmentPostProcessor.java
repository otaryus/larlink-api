package com.agency.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) return;
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            for (String rawLine : Files.readAllLines(file)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("export ")) line = line.substring(7).trim();
                int separator = line.indexOf('=');
                if (separator < 1) continue;
                String key = line.substring(0, separator).trim();
                String value = unquote(line.substring(separator + 1).trim());
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]*") || environment.containsProperty(key)) continue;
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar o ficheiro .env.", e);
        }
        if (!values.isEmpty()) environment.getPropertySources().addLast(new MapPropertySource("larlinkDotEnv", values));
    }

    private String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
