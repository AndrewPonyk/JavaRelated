package com.ap.springboot2hello;

import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Service;

import java.util.Map;

@EnableKafka
@Service
public class TestKafkaListener {

    public TestKafkaListener(){
        System.out.println("create listener");
    }

    @KafkaListener(id = "test1", topics = {"some-topic"})
    public void onMessage(String message, @Headers MessageHeaders messageHeaders){
        System.out.println(message);
    }
}