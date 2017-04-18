package ui;
import models.Book;
import models.BookCatalog;
import models.BookNotFoundException;
import models.Customer;
import models.DVD;
import models.Loan;
import models.LoanAlreadyExistsException;
import models.LoansRegistry;
import utilities.GenderType;

public class Main {

	public static void main(String[] args) {

		BookCatalog bookCatalog = new BookCatalog();

		Book book1 = new Book(1,"An introduction to Java","Matt Greencroft","12345","Anytown Branch", 400);
		Book book2 = new Book(2,"Better Java","Joe Le Blanc","23456","Anytown Branch",150);
		DVD dvd1 = new DVD(3,"An Epic Film About Java","Anytown Branch","Stephen Spielberg","99887",120);
		DVD dvd2 = new DVD(3,"An Epic Film About Java","Anytown Branch","Stephen Spielberg","99887",120);

		System.out.println(dvd1.getTitle());
		book1.relocate("MyCity branch");


		bookCatalog.addBook(book1);
		bookCatalog.addBook(book2);


		UI ui = new UI();
		ui.printHeader();
		ui.printBookCatalog(bookCatalog.getBookArray());

		try {
			Book foundBook = bookCatalog.findBook("Better");
			System.out.println("We found " + foundBook.getTitle());
		}
		catch (BookNotFoundException e) {
			System.out.println("The book wasn't found");
		}

		int myTest = 1;

		try {
			if (myTest != 2) {
				throw new RuntimeException("Something went wrong");
			}
		}
		catch (RuntimeException e) {
			// do nothing here so that we can continue;
		}
		
		Customer customer = new Customer("Mr", "Michael", "Smith", "1 The High Street","1234","a@b.com",1,GenderType.MALE);
		System.out.println(customer.getExpiryDate());
		System.out.println(customer.getMailingName());

		System.out.println(customer);
		System.out.println(dvd1);

		System.out.println(dvd1.equals(dvd2));
		System.out.println(customer.equals(customer));
		
		Loan firstLoan = new Loan(1,customer,book1);
		System.out.println(firstLoan.getDueDate());
		System.out.println(firstLoan);
		
		LoansRegistry registry = new LoansRegistry();
		try {	
			registry.addLoan(firstLoan);
			System.out.println("addLoan worked");
			}
		catch (LoanAlreadyExistsException e) {
			System.out.println("addLoan failed");
		}
		
		try {	
			registry.addLoan(firstLoan);
			System.out.println("addLoan worked");
			}
		catch (LoanAlreadyExistsException e) {
			System.out.println("addLoan failed");
		}
		
		System.out.println(registry.isBookOnLoan(book1.getID()));
		firstLoan.endLoan();
		System.out.println(registry.isBookOnLoan(book1.getID()));
	}

}
