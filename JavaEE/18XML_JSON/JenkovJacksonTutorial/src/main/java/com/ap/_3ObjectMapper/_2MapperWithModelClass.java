package com.ap._3ObjectMapper;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;

public class _2MapperWithModelClass {

    public static String json = "{\"brand\":\"Audi\",\"doors\":5,\"owners\":[\"Rob\",\"John\"]}";

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Car parsedValue = mapper.readValue(json, Car.class);

        System.out.println(parsedValue);
    }

    public static class Car {
        private String brand;
        private Integer doors;
        private String[] owners;

        @JsonSerialize(using = ToStringSerializer.class)
        @JsonDeserialize(using = LocalDateDeserializer.class)
        private LocalDate localDate;

        public String getBrand() {
            return brand;
        }

        public void setBrand(String brand) {
            this.brand = brand;
        }

        public Integer getDoors() {
            return doors;
        }

        public void setDoors(Integer doors) {
            this.doors = doors;
        }

        public String[] getOwners() {
            return owners;
        }

        public void setOwners(String[] owners) {
            this.owners = owners;
        }

        @Override
        public String toString() {
            return "Car{" +
                    "brand='" + brand + '\'' +
                    ", doors=" + doors +
                    ", owners=" + Arrays.toString(owners) +
                    '}';
        }


        public LocalDate getLocalDate() {
            return localDate;
        }

        public void setLocalDate(LocalDate localDate) {
            this.localDate = localDate;
        }
    }
}
