package spring.java.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.VfsUtils;
import org.springframework.stereotype.Component;
import spring.java.service.BlogPostService;
import sun.reflect.generics.visitor.Visitor;

import java.io.File;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Created by andrii on 30.10.16.
 */
public class MainApplication {
    public static void main(String[] args) throws ClassNotFoundException {
        ApplicationContext applicationContext = new AnnotationConfigApplicationContext(JavaConfig.class);

        BlogPostService blogPostService = applicationContext.getBean("blogPostService2", BlogPostService.class);
        blogPostService.saveBlogPost();

        ((ConfigurableApplicationContext)applicationContext).close();

    }

}
