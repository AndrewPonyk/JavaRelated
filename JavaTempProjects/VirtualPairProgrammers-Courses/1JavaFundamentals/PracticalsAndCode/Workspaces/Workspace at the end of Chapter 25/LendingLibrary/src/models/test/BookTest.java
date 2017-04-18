package models.test;

import static org.junit.Assert.*;

import models.Book;

import org.junit.Test;

public class BookTest {

	@Test
	public void test2EqualBooks() {
		Book book1 = new Book("100","","","","",1);
		Book book2 = new Book("100","","","","",1);
		
		assertTrue(book1.equals(book2));
	}
	
	@Test
	public void test2NonEqualBooks() {
		Book book1 = new Book("100","","","","",1);
		Book book2 = new Book("101","","","","",1);
		
		assertFalse(book1.equals(book2));
	}

}
