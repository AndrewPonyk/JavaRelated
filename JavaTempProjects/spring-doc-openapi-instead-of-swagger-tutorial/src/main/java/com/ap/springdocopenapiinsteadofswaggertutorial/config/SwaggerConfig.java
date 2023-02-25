package com.ap.springdocopenapiinsteadofswaggertutorial.config;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.validation.constraints.NotNull;

//https://springdoc.org/index.html#Introduction
//https://stackoverflow.com/questions/59291371/migrating-from-springfox-swagger-2-to-springdoc-open-api

@Configuration
//http://localhost:8080/swagger-ui/index.html THIS is entry point
public class SwaggerConfig {
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("springshop-public")
                .pathsToMatch("/api/**")
                .build();
    }
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("springshop-admin")
                .pathsToMatch("/admin/**")
                .addOpenApiMethodFilter(method -> method.isAnnotationPresent(NotNull.class))
                .build();
    }

    // swagger-ui custom path
    // springdoc.swagger-ui.path=/swagger-ui.html

    //!!!!!!!
    //If you have only one Docket — remove it and instead add properties to your application.properties:
    //springdoc.packagesToScan=package1, package2
    //springdoc.pathsToMatch=/v1, /api/balance/**
}