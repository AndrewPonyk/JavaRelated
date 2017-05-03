package ch17toStringAndequals.models;


public class BookCatalog {

	private Book[] bookArray = new Book[100];
	private int nextPosition = 0;

	public Book[] getBookArray() {
		return bookArray;
	}
	
	public void addBook(Book newBook) {
		bookArray[nextPosition] = newBook;
		nextPosition++;
	}
	
	public Book findBook(String title)
	{
		for (int counter = 0; counter < nextPosition; counter++) {
			if (bookArray[counter].getTitle().equalsIgnoreCase((title))) {
				return bookArray[counter];
			}
		}
		return null;
	}

}
