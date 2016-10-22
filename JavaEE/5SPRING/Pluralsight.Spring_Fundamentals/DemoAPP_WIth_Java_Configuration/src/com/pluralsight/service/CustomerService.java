package com.pluralsight.service;

import com.pluralsight.model.Customer;

import java.util.List;

public interface CustomerService {
    List<Customer> findAll();
}
