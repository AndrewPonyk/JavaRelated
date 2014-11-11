package com.pluralsight.service;

import java.util.List;

import com.pluralsight.model.Customer;
import com.pluralsight.repository.CustomerRepository;
import com.pluralsight.repository.HibernateCustomerReporistoryImp;

public class CustomerServiceImpl implements CustomerService {
	
	//pain point 1 : service should know that you are using hibernate
	private CustomerRepository customerRepository = new HibernateCustomerReporistoryImp();
	
	/* (non-Javadoc)
	 * @see com.pluralsight.service.CustomerService#findAll()
	 */
	public List<Customer> findAll(){
		return customerRepository.findAll();
	}
	
}
