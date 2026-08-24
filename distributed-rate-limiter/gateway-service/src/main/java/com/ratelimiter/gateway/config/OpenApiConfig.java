package com.ratelimiter.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Distributed Rate Limiter & API Gateway")
                        .description("""
                                **Production-grade API Gateway** with distributed rate limiting.

                                ## Features
                                - **Token Bucket** algorithm — burst-friendly, gradual refill
                                - **Sliding Window** algorithm — precise per-window request counting
                                - **Redis-backed** distributed state (atomic Lua scripts)
                                - **MySQL** for client/API/policy configuration
                                - **Admin REST API** for dynamic configuration — no restarts required
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("Rate Limiter Team").email("admin@ratelimiter.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(new Server().url("http://localhost:" + serverPort).description("Local Development")))
                .tags(List.of(
                        new Tag().name("Clients").description("Manage API clients and their API keys"),
                        new Tag().name("API Endpoints").description("Register and manage protected API endpoints"),
                        new Tag().name("Rate Limit Policies").description("Configure per-client per-API rate limit rules"),
                        new Tag().name("Statistics").description("View request analytics and rate limit statistics")));
    }
}
