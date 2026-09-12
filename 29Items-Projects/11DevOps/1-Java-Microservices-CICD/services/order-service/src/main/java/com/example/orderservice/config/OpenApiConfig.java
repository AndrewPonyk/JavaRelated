package com.example.orderservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata; springdoc generates the spec at /v3/api-docs and the UI at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .description("E-commerce order management: creation, lifecycle transitions, paginated queries. "
                        + "Errors follow RFC 7807 (application/problem+json).")
                .version("v1")
                .contact(new Contact().name("Orders Team").email("orders-team@example.com")));
    }
}
