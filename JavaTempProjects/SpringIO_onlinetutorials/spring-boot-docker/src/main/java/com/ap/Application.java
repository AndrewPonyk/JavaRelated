package com.ap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.websocket.server.PathParam;
import java.time.LocalDateTime;

@SpringBootApplication
@RestController
public class Application {

    public static String startTime = LocalDateTime.now().toString();

    @RequestMapping("/{param}")
    public String home(@PathVariable("param") String param){
        return startTime + "Hello docker world! + " + param;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
