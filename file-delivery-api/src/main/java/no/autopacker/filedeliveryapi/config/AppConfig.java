package no.autopacker.filedeliveryapi.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Contains configuration that is dynamically set dependent on the active profile
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "api")
public class AppConfig {

    @Value("${api.root-url}")
    private String apiRootUrl;

    @Value("${fdapi.mongo.url}")
    private String fdapiMongoUrl;
}