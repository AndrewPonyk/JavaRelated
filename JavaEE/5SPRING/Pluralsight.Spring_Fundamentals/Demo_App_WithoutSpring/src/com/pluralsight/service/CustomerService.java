package com.pluralsight.service;

import com.pluralsight.model.Customer;

import java.util.List;

/**
 * Created by andrii on 25.09.16.
 */
public interface CustomerService {
    List<Customer> findAll();
}
