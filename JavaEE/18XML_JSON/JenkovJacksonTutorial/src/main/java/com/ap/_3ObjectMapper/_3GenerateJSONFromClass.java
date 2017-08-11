package com.ap._3ObjectMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class _3GenerateJSONFromClass {
    public static void main(String[] args) throws JsonProcessingException {
        _2MapperWithModelClass.Car car = new _2MapperWithModelClass.Car();
        car.setBrand("Volkswagen");
        car.setDoors(5);
        car.setOwners(new String[]{"Owner1", "Owner2"});

        ObjectMapper mapper = new ObjectMapper();
        System.out.println(mapper.writeValueAsString(car));
    }
}
