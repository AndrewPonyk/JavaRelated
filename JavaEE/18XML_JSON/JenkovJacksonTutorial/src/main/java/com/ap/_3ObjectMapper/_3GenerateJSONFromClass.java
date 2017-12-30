package com.ap._3ObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class _3GenerateJSONFromClass {
    public static void main(String[] args) throws IOException {
        _2MapperWithModelClass.Car car = new _2MapperWithModelClass.Car();
        car.setBrand("Volkswagen");
        car.setDoors(5);
        car.setOwners(new String[]{"Owner1", "Owner2"});
        car.setLocalDate(LocalDate.now());

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        System.out.println(mapper.writeValueAsString(car));

        String s = mapper.writeValueAsString(car);
        mapper.readValue(s, _2MapperWithModelClass.Car.class);
        mapper.readValue("{\"brand\":\"Volkswagen\",\"doors\":5,\"owners\":[\"Owner1\",\"Owner2\"],\"localDate\":\"2017-12-30T22:07:34.311\"}",
                _2MapperWithModelClass.Car.class);
    }
}
//