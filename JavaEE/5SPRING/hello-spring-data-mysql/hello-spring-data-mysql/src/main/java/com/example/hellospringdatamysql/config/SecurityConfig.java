package com.example.hellospringdatamysql.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity // WebSecurityConfigurerAdapter - REMOVED in spring boot 3 !!!!!!!!!!!!! //we should use security chain instead
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private DataSource dataSource;

    // !!!!!!!!!!!!!!!!!!!!!!!!!! SHORT variant  - without entity-service-userdetails!!!!!!!!!!!
    @Autowired
    public void configAuthentication(AuthenticationManagerBuilder auth) throws Exception {
        auth.jdbcAuthentication().passwordEncoder(new BCryptPasswordEncoder())
                .dataSource(dataSource)
                .usersByUsernameQuery("select username, password, enabled from users where username=?")
                .authoritiesByUsernameQuery("select username, role from users where username=?");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
                .antMatchers("/category/").permitAll() // open this single url http://localhost:8080/category/ without authorization
                .anyRequest().authenticated()
                .and()
                .formLogin().permitAll()
                .and()
                .logout().permitAll();
    }
    /*
    CREATE TABLE `users` (
                         `user_id` int(11) NOT NULL AUTO_INCREMENT,
                         `username` varchar(45) NOT NULL,
                         `password` varchar(64) NOT NULL,
                         `role` varchar(45) NOT NULL,
                         `enabled` tinyint(4) DEFAULT NULL,
                         PRIMARY KEY (`user_id`));
INSERT INTO `users` (`username`,`password`,`role`,`enabled`)
VALUES ('namhm',
        '$2a$10$XptfskLsT1l/bRTLRiiCgejHqOpgXFreUnNUa35gJdCr2v2QbVFzu',
        'ROLE_USER', 1); -- pass = codejava

INSERT INTO `users` (`username`,`password`,`role`,`enabled`)
VALUES ('admin',
        '$2a$10$zxvEq8XzYEYtNjbkRsJEbukHeRx3XS6MDXHMu8cNuNsRfZJWwswDy',
        'ROLE_ADMIN', 1); --pass = nimda

     */



    //IF we do not specify password here or db table with users - PASSWORD IS GENERATED ON APP STARTUP !!!!!!!!!! - and print to console
   /* @Override
    protected void configure(HttpSecurity http) throws Exception {
        //allow several urls
        http.authorizeRequests()
                .antMatchers("/login", "/home").permitAll()
                .antMatchers("/category/").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin().permitAll()
                .and()
                .logout().permitAll();;

    }*/


}
