package com.crm.crm.model;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource // |su:8 Exposes repository as REST endpoints automatically at /api/contacts
public interface ContactRepository extends CrudRepository<Contact, Long> { // |su:7 CrudRepository<Entity, IdType> provides findAll, save, delete, findById methods—c

}
