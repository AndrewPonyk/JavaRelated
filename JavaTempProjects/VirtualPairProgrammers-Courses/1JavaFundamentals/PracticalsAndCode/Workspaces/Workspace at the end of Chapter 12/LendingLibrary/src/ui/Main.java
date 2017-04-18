package ui;
import utilities.GenderType;
import models.Book;
import models.BookCatalog;
import models.Customer;

public class Main {

	public static void main(String[] args) {
	
		BookCatalog bookCatalog = new BookCatalog();
		
		Book book1 = new Book(1,"An introduction to Java","Matt Greencroft","12345");
		Book book2 = new Book(2,"Better Java","Joe Le Blanc","23456");

		bookCatalog.addBook(book1);
		bookCatalog.addBook(book2);


		UI ui = new UI();
		ui.printHeader();
		ui.printBookCatalog(bookCatalog.getBookArray());
		
		Book foundBook = bookCatalog.findBook("Better java");
		if (foundBook !=null) {
			System.out.println(foundBook.getTitle());
		}
		
		Customer customer = new Customer("Mr", "Michael", "Smith", "1 The High Street","1234","a@b.com",1,GenderType.MALE);
		System.out.println(customer.getExpiryDate());
		
	}

}
