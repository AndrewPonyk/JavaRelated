package com.pluralsight.repository;

import com.pluralsight.model.Customer;

import java.util.List;

public interface CustomerRepository {
    public List<Customer> findAll();
}
