package no.autopacker.gatewayapi.config;import org.springframework.cloud.gateway.route.RouteLocator;import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;import org.springframework.context.annotation.Bean;import org.springframework.context.annotation.Configuration;@Configurationpublic class SpringGatewayConfig {    @Bean    public RouteLocator router(RouteLocatorBuilder builder) {        return builder.routes()            // File Delivery API            .route(r -> r.path("/projects/**")                    .uri("http://localhost:8090/projects")                    .id("file-delivery-api"))            // General API            /*            .route(r -> r.path("/general/**")                    .uri("http://localhost:8081/api/general")                    .id("general-api"))*/            .route(r -> r.path("/organization/**")                    .uri("http://localhost:8081/api/organization")                    .id("general-api-organization"))            .route(r -> r.path("/languages/**")                    .uri("http://localhost:8081/api/languages")                    .id("general-api-languages"))            // Server API            .route(r -> r.path("/server/**")                    .uri("http://localhost:8082/api/server")                    .id("server-manager-api"))            // User Service API            .route(r -> r.path("/auth/**")                    .uri("http://localhost:8080/api/auth")                    .id("userservice-api"))            .build();    }}