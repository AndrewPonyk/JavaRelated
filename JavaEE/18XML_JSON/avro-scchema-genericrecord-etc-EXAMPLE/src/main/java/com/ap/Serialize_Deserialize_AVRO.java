package com.ap;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DatumWriter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


// https://www.tutorialspoint.com/avro/serialization_using_parsers.htm

public class Serialize_Deserialize_AVRO {
    public static void main(String[] args) throws IOException {
        serialize();
        deserialize();


    }

    public static String resources = "D:\\mygit\\JavaRelated\\JavaEE\\18XML_JSON\\avro-scchema-genericrecord-etc-EXAMPLE\\src\\main\\resources\\";
    public static void serialize() throws IOException {

        //Instantiating the Schema.Parser class.
        Schema schema = new Schema.Parser().parse(new File(resources + "emp.avsc"));
        Schema childSchema = schema.getField("children").schema().getElementType();

        //Instantiating the GenericRecord class.
        GenericRecord e1 = new GenericData.Record(schema);

        //Insert data according to schema
        e1.put("name", "ramu");
        e1.put("id", 001);
        e1.put("salary",30000);
        e1.put("age", 25);
        e1.put("address", "chenni");
        List<GenericRecord> childList1 = new ArrayList();
        GenericRecord child1 = new GenericData.Record(childSchema);
        child1.put("name", "child1");
        GenericRecord child2 = new GenericData.Record(childSchema);
        child2.put("name", "child2");
        childList1.add(child1);
        childList1.add(child2);
        e1.put("children", childList1);

        GenericRecord e2 = new GenericData.Record(schema);

        e2.put("name", "rahman");
        e2.put("id", 002);
        e2.put("salary", 35000);
        e2.put("age", 30);
        e2.put("address", "Delhi");
        List<GenericRecord> childList2 = new ArrayList();
        GenericRecord child3 = new GenericData.Record(childSchema);
        child3.put("name", "child3");
        childList2.add(child3);
        e2.put("children", childList2);


        DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<GenericRecord>(schema);

        DataFileWriter<GenericRecord> dataFileWriter = new DataFileWriter<GenericRecord>(datumWriter);
        dataFileWriter.create(schema, new File(resources + "mydata.txt"));

        dataFileWriter.append(e1);
        dataFileWriter.append(e2);
        dataFileWriter.close();

        System.out.println("data successfully serialized");
    }

    public static List<GenericRecord> deserialize() throws IOException {
        //Instantiating the Schema.Parser class.
        List<GenericRecord> employees = new ArrayList<>();
        Schema schema = new Schema.Parser().parse(new File(resources + "emp.avsc"));
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>(schema);
        DataFileReader<GenericRecord> dataFileReader = new DataFileReader<GenericRecord>(new File(resources + "mydata.txt"), datumReader);
        GenericRecord emp = null;

        while (dataFileReader.hasNext()) {
            emp = dataFileReader.next();
            employees.add(emp);
            System.out.println(emp);
        }
        System.out.println("hello");
        return employees;
    }
}
