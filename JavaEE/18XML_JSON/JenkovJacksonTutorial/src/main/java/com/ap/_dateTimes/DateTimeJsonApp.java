package com.ap._dateTimes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.TimeZone;

public class DateTimeJsonApp {

    public static void main(String[] args) throws IOException {
        System.out.println("Use dates with jackson");

        ObjectMapper objectMapper = objectMapper();
        Model model = objectMapper.readValue(modelExample, Model.class);

        System.out.println(model);

        System.out.println(objectMapper.writeValueAsString(model));
        DateTime dateTime;

        LocalDateTime.now();
    }


    public static ObjectMapper objectMapper() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JodaModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.setDateFormat(formatter);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    public static String modelExample = "{\"dateTime\":\"2019-07-17T16:34:25Z\","
            + "\"localDateTime\":\"2019-07-17T19:34:25.1Z\""
            + "}";

    public static class Model {
        private DateTime dateTime;

        private LocalDateTime localDateTime;

        public DateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(DateTime dateTime) {
            this.dateTime = dateTime;
        }

        @Override
        public String toString() {
            return "Model{" +
                    "dateTime=" + dateTime +
                    '}';
        }

        public LocalDateTime getLocalDateTime() {
            return localDateTime;
        }

        public void setLocalDateTime(LocalDateTime localDateTime) {
            this.localDateTime = localDateTime;
        }
    }
}