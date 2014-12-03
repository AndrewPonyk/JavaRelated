package com.pluralsight.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pluralsight.model.Customer;
import com.pluralsight.repository.CustomerRepository;

@Service("customerService")
public class CustomerServiceImpl implements CustomerService {

	//pain point 1 : service should know that you are using hibernate
	//private CustomerRepository customerRepository = new HibernateCustomerReporistoryImp();
	@Autowired
	private CustomerRepository customerRepository;

	public CustomerServiceImpl(){}
	
	public CustomerServiceImpl( CustomerRepository customerRepository){
		this.customerRepository = customerRepository;
	}
	
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