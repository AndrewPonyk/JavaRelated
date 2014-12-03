package com.pluralsight.repository;

import java.util.ArrayList;
import java.util.List;

import com.pluralsight.model.Customer;

public class HibernateCustomerReporistoryImp implements CustomerRepository {

	/* (non-Javadoc)
	 * @see com.pluralsight.repository.CustomerRepository#findAll()
	 */
	
	private String dbUrl;

	public List<Customer> findAll() {
		System.out.println("Finding customers in db : " +dbUrl);
		List<Customer> customers = new ArrayList<Customer>();
		
		Customer customer = new Customer();
		customer.setFirstName("Bryan");
		customer.setLastName("Hensen");
		
		customers.add(customer);
		
		return customers;
	}
	
	
	public void setDbUrl(String dbUrl) {
		this.dbUrl = dbUrl;
	}

}
