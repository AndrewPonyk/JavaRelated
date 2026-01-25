package com.crm.crm.model;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import lombok.Data;

@Data // |su:3 Lombok annotation - auto-generates getters, setters, toString, equals, hashCode
@Entity // |su:4 Marks class as JPA entity - will be mapped to database table 'contact'—c

public class Contact {
    public @Id @GeneratedValue Long id; // |su:5 @Id=primary key, @GeneratedValue=auto-increment by database—c
    public String firstName;
    public String lastName;
    public String email;

    public Contact() {} // |su:6 No-arg constructor required by JPA for entity instantiation

    public Contact(String firstName, String lastName, String email) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
    }
}
