package ui;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import utilities.GenderType;
import models.Book;
import models.BookCatalog;
import models.Customer;

public class Main {

	public static void main(String[] args) {
	
		double d = 1;
		
		NumberFormat nf = NumberFormat.getCurrencyInstance();
		nf.setMinimumFractionDigits(5);
		nf.setMaximumFractionDigits(5); 
		
		BigDecimal price = new BigDecimal(0.1);
		BigDecimal addValue = new BigDecimal(0.1);
		for (int i = 0; i<10; i++) {
			price = price.add(addValue);
			System.out.println(price);
		}
		 
		
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
		System.out.println(customer.getMailingName());
		
	}

}
