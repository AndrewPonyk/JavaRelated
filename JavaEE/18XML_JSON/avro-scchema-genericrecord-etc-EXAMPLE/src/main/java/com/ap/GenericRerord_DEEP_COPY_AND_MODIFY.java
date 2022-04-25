package com.ap;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.ap.Serialize_Deserialize_AVRO.deserialize;

public class GenericRerord_DEEP_COPY_AND_MODIFY {
    public static void main(String[] args) throws IOException {
        System.out.println("Task from equifax");

        List<GenericRecord> listOfGenericRecords = deserialize();

        String key = "children.name";
        List<String> keys = Arrays.asList("child1", "child2");

        GenericRecord genericRecord = listOfGenericRecords.get(0);
        System.out.println(splitGenericRecord(key, keys, genericRecord));

    }

    public static List<GenericRecord> splitGenericRecord(String key, List<String> keys, GenericRecord genericRecord) {
        return keys.stream().map(k -> {
            GenericData.Record clone = new GenericRecordBuilder((GenericData.Record) genericRecord).build();
            GenericData.Array children = (GenericData.Array) clone.get("children");

            children.stream()
                    .filter(e -> !((GenericData.Record) e).get("name").toString().equals(k.toString())).
                    forEach(e -> children.remove(children.indexOf(e)));
            return clone;
        }).collect(Collectors.toList());
    }
}
