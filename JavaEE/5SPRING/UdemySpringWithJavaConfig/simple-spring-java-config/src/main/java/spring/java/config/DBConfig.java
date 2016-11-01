package spring.java.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import spring.java.service.BlogPostService;

@Configuration
public class DBConfig {

    @Bean
    public BlogPostService blogPostService2(){
        return new BlogPostService() {

            public void saveBlogPost() {
                System.err.println("Saving blog post");
            }
        };
    }
}
