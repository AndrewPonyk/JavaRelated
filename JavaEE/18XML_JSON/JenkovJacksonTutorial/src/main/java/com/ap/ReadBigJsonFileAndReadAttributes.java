package com.ap;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Iterator;

public class ReadBigJsonFileAndReadAttributes {
    private static int counter = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("Read big json file");

//        URL url = new URL("https://data.opendatasoft.com/explore/dataset/vehicules-commercialises@public/download/?format=json&timezone=Europe/Berlin");
        File file = new File("C:\\Users\\hp\\Downloads\\Telegram Desktop\\ChatExport_2021-05-29\\result.json");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            FieldsJsonIterator fieldsJsonIterator = new FieldsJsonIterator(reader);
            while (fieldsJsonIterator.hasNext()) {
                Fields fields = fieldsJsonIterator.next();
                filterAndPrint(fields);
            }
        }
    }

    private static void filterAndPrint(Fields fields) {
        String s = fields.text.toString();
        if(s.contains("ratio:")){

            try {
                String ratio = s.substring(s.indexOf("ratio:") + 6).trim().replaceAll("]","");

                if(Double.valueOf(ratio) > 1.69){
                    System.out.println(++counter + fields.toString());
                }
            }catch (Exception e){
             e.printStackTrace();
            }
        }
    }
}
class FieldsJsonIterator implements Iterator<Fields> {

    private final ObjectMapper mapper;
    private final JsonParser parser;

    public FieldsJsonIterator(Reader reader) throws IOException {
        mapper = new ObjectMapper();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        parser = mapper.getFactory().createParser(reader);
        skipStart();
    }

    private void skipStart() throws IOException {
        while (parser.currentToken() != JsonToken.START_OBJECT) {
            parser.nextToken();
        }
    }

    @Override
    public boolean hasNext() {
        try {
            while (parser.currentToken() == null) {
                parser.nextToken();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        return parser.currentToken() == JsonToken.START_OBJECT;
    }

    @Override
    public Fields next() {
        try {

            return mapper.readValue(parser, Fields.class);
        } catch (IOException e) {
            return new Fields();
//            throw new IllegalStateException(e);
        }
    }

    private static final class FieldsWrapper {
        public Fields fields;
    }
}


class Fields {
    @Override
    public String toString() {
        return "Fields{" +
                "date='" + date + '\'' +
                ",text=" + text +
                ", id=" + id +
                '}';
    }

    @JsonProperty("text")
    public Object text; // !!!!!!!! can be any ANYYYY string, jsonobject, array...... COOL

    @JsonProperty("id")
    public Integer id;

    @JsonProperty
    public String date;

}
