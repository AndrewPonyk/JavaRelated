package com.crm.crm.model;

import java.net.URISyntaxException;
import java.util.Collection;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController // |su:9 Combines @Controller + @ResponseBody - returns JSON directly, not view names—c
@RequestMapping("/api") // |su:10 Base URL path prefix for all endpoints in this controller
@CrossOrigin(origins = "http://localhost:3000") // |su:11 Enables CORS - allows React frontend (port 3000) to call backend (port 8080)—c
class ContactsController {
    private ContactRepository contactRepository;

    public ContactsController(ContactRepository contactRepository) { // |su:12 Constructor injection - Spring auto-wires ContactRepository bean—c
        this.contactRepository = contactRepository;
    }

    @GetMapping("/contacts") // |su:13 Maps GET /api/contacts - retrieves all contacts --c
    Collection<Contact> contacts() {
        return (Collection<Contact>) contactRepository.findAll(); // |su:14 CrudRepository.findAll() returns all entities from database --c
    }

    @PostMapping("/contacts") // |su:15 Maps POST /api/contacts - creates new contact --c
    ResponseEntity<Contact> createContact(@Valid @RequestBody Contact contact) throws URISyntaxException { // |su:16 @Valid=validates input, @RequestBody=deserializes JSON to Contact object --c
        Contact result = contactRepository.save(contact); // |su:17 CrudRepository.save() persists entity and returns saved instance with generated ID
        return ResponseEntity.ok().body(result);
    }
}