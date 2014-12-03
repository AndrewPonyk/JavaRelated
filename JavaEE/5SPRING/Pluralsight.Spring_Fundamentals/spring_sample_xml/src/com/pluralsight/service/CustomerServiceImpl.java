package com.pluralsight.service;

import java.util.List;

import com.pluralsight.model.Customer;
import com.pluralsight.repository.CustomerRepository;

public class CustomerServiceImpl implements CustomerService {
	
	public CustomerServiceImpl(){}
	
	public CustomerServiceImpl( CustomerRepository customerRepository){
		this.customerRepository = customerRepository;
	}
	
	//pain point 1 : service should know that you are using hibernate
	//private CustomerRepository customerRepository = new HibernateCustomerReporistoryImp();
	private CustomerRepository customerRepository;
	
	/* (non-Javadoc)
	 * @see com.pluralsight.service.CustomerService#findAll()
	 */
	public List<Customer> findAll(){
		return customerRepository.findAll();
	}

	public void setCustomerRepository(CustomerRepository customerRepository) {
		this.customerRepository = customerRepository;
	}

}
