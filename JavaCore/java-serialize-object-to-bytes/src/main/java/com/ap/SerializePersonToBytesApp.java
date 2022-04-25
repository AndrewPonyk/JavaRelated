package com.ap;

import org.apache.commons.lang3.SerializationUtils;

public class SerializePersonToBytesApp {
    public static void main(String[] args) {
        System.out.println("Custom serizaliztion to bytes");
        Person person = new Person(11, "And");
        byte[] data = SerializationUtils.serialize(person);

        System.out.println();

        Person person1 = SerializationUtils.deserialize(data);
        System.out.println(person1);
    }
}
