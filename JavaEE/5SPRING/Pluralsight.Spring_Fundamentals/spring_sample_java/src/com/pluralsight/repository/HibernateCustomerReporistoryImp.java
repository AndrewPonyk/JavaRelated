package com.pluralsight.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;

import com.pluralsight.model.Customer;

public class HibernateCustomerReporistoryImp implements CustomerRepository {

	/* (non-Javadoc)
	 * @see com.pluralsight.repository.CustomerRepository#findAll()
	 */
	
	@Value("${dburl}")
	private String dburl;
	
	public List<Customer> findAll() {
		System.out.println("Finding customers using  db : " + dburl);
		List<Customer> customers = new ArrayList<Customer>();
		
		Customer customer = new Customer();
		customer.setFirstName("Bryan");
		customer.setLastName("Hensen");
		
		customers.add(customer);
		
		return customers;
	}
}
