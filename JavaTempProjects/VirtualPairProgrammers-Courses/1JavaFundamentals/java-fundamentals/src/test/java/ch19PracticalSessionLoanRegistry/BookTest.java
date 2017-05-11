package ch19PracticalSessionLoanRegistry;


import ch19PracticalSessionLoanRegistry.models.Book;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BookTest {

	@Test
	public void test2EqualBooks() {
		Book book1 = new Book(100,"","","","",1);
		Book book2 = new Book(100,"","","","",1);
		
		assertTrue(book1.equals(book2));
	}
	
	@Test
	public void test2NonEqualBooks() {
		Book book1 = new Book(100,"","","","",1);
		Book book2 = new Book(101,"","","","",1);
		
		assertFalse(book1.equals(book2));
	}

}
