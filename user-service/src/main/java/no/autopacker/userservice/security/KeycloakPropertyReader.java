package no.autopacker.userservice.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;

@Configuration
@PropertySource("classpath:bootstrap.yml")
public class KeycloakPropertyReader {

    private final Environment env;

    public KeycloakPropertyReader(Environment env) {
        this.env = env;
    }

    public String getProperty(String key) {
        return env.getProperty(key);
    }
}
