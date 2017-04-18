package models.test;

import static org.junit.Assert.*;

import models.Customer;
import models.CustomerNotFoundException;
import models.CustomerRecords;

import org.junit.Test;

import utilities.GenderType;

public class CustomerRecordsTest {

	CustomerRecords records;
	
	@Test
	public void testAddCustomer() {
		Customer newCustomer = new Customer("Mr","Matt","Greencroft","1 High Street","12345","matt@matt.com",1,GenderType.MALE);
		
		int initialSize = records.getNumberOfCustomers();
		records.add(newCustomer);
		
		assertTrue(initialSize == records.getNumberOfCustomers() - 1); 
	}
	
	@Test
	public void testFindByName() {
		try
		{
		Customer foundCustomer = records.findByName("Mrs S Smith");
		}
		catch (CustomerNotFoundException e) {
			fail("Customer not found");
		}
	}
	
	@Test
	public void testNoDuplicates() {
		Customer newCustomer = new Customer("Mrs", "Sandra", "Smith","2 High Street", "12346", "sandra@matt.com", 3, GenderType.FEMALE);
		records.add(newCustomer);
		records.add(newCustomer);
		records.add(newCustomer);
		records.add(newCustomer);
		records.add(newCustomer);

		assertEquals(1,records.getNumberOfCustomers() ); 
	}

	public CustomerRecordsTest() {
		records = new CustomerRecords();
		Customer newCustomer = new Customer("Mrs", "Sandra", "Smith","2 High Street", "12346", "sandra@matt.com", 3, GenderType.FEMALE);
		records.add(newCustomer);
	}

} 
