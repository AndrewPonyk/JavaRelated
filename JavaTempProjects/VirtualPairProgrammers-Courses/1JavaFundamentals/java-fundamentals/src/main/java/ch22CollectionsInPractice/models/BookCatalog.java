package ch22CollectionsInPractice.models;


import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class BookCatalog {

	private Map<String, Book> bookMap;

	public Map<String, Book> getBookMap() {
		return bookMap;
	}

	public BookCatalog() {
		bookMap = new LinkedHashMap<>();
	}

	public int getNumberOfBooks() {
		return bookMap.size();
	}


	public void addBook(Book newBook) {
		bookMap.put(newBook.getID(), newBook);
	}
	
	public Book findBook(String title) throws BookNotFoundException {
		for (Book book : bookMap.values()) {
			if (book.getTitle().equalsIgnoreCase(title.trim())) {
				return book;
			}
		}

		throw new BookNotFoundException();
	}


}
