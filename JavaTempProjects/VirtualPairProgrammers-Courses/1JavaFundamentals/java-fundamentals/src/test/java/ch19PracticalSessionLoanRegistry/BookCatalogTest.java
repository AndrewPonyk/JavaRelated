package ch19PracticalSessionLoanRegistry;

import static org.junit.Assert.*;


import ch19PracticalSessionLoanRegistry.models.Book;
import ch19PracticalSessionLoanRegistry.models.BookCatalog;
import ch19PracticalSessionLoanRegistry.models.BookNotFoundException;
import org.junit.Test;

public class BookCatalogTest {

	private BookCatalog bc;
	private Book book1;

	public BookCatalogTest() {
		bc = new BookCatalog();
		Book book1 = new Book(1,"Learning Java","","","",0);
		bc.addBook(book1);
		System.out.println("Constructor being run"); // constructor is calling for each test
	}

	@Test
	public void testAddABook() {

		int initialNumber = bc.getNumberOfBooks();

		Book book = new Book(1,"","","","",0);
		bc.addBook(book);

		assertTrue(initialNumber == bc.getNumberOfBooks() -1);

	}

	@Test
	public void testFindBook() {

		try {
			Book foundBook = bc.findBook("Learning Java");
		}
		catch (BookNotFoundException e)
		{
			fail("Book not found");
		}
	}

	@Test
	public void testFindBookIgnoringCase() {

		try {
			Book foundBook = bc.findBook("learning Java");
		}
		catch (BookNotFoundException e) 
		{
			fail("Book not found");
		}
	}
	
	@Test
	public void testFindBookWithExtraSpaces() {

		try {
			Book foundBook = bc.findBook(" learning Java ");
		}
		catch (BookNotFoundException e) 
		{
			fail("Book not found");
		}
	}

	@Test(expected = BookNotFoundException.class)
	public void testFindBookThatDoesntExist() throws BookNotFoundException {

		Book foundBook = bc.findBook("Learning More Java");

	}

}
