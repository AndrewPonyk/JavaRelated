package com.ap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.RepositoryDetectionStrategy;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurer;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurerAdapter;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    // By default, if you add Spring Data Rest dependency -> all your crudrepositories are exposed as rest endpoints
    // This bean set:
    //ANNOTATION
    //Only repositories annotated with @(Repository)RestResource are exposed,
    // unless their exported flag is set to false.
    @Bean
    public RepositoryRestConfigurer repositoryRestConfigurer() {

        return new RepositoryRestConfigurerAdapter() {
            @Override
            public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config) {
                config.setRepositoryDetectionStrategy(RepositoryDetectionStrategy
                        .RepositoryDetectionStrategies.ANNOTATED);
            }
        };
    }
}
