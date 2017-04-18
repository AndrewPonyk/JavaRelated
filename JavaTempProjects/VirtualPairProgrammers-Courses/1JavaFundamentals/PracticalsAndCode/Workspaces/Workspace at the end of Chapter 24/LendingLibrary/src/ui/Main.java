package ui;
import models.Book;
import models.MaterialCatalogInterface;
import models.MaterialCatalogMemoryVersion;
import models.Customer;
import models.DVD;
import models.Loan;
import models.LoanAlreadyExistsException;
import models.LoansRegistry;
import utilities.GenderType;

public class Main {

	public static void main(String[] args) {

		MaterialCatalogInterface materialCatalog = new MaterialCatalogMemoryVersion();

		Book book1 = new Book("1001","An introduction to Java","Matt Greencroft","12345","Anytown Branch", 400);
		Book book2 = new Book("223X","Better Java","Joe Le Blanc","23456","Anytown Branch",150);
		Book book3 = new Book("9120","Learning French","Anton Le Noir","87654","Anytown Branch",100);
		Book book4 = new Book("444X","Learning More French","Anton Le Noir","87654","Anytown Branch",100);
		Book book5 = new Book("3345","Enough French Already","Anton Le Noir","87654","Anytown Branch",100);
		DVD dvd1 = new DVD("3","An Epic Film About Java","Anytown Branch","Stephen Spielberg","99887",120);
		DVD dvd2 = new DVD("4","An Epic Film About Java","Anytown Branch","Stephen Spielberg","99887",120);

		//System.out.println(dvd1.getTitle());
		//book1.relocate("MyCity branch");

		materialCatalog.addMaterial(book1);
		materialCatalog.addMaterial(book2);
		materialCatalog.addMaterial(book3);
		materialCatalog.addMaterial(book4);
		materialCatalog.addMaterial(book5);
		materialCatalog.addMaterial(dvd1);
		materialCatalog.addMaterial(dvd2);

		UI ui = new UI();
		ui.printHeader();
		ui.printMaterialCatalog(materialCatalog.getMaterialMap());

//		try {
//			Book foundBook = bookCatalog.findBook("Better");
//			System.out.println("We found " + foundBook.getTitle());
//		}
//		catch (BookNotFoundException e) {
//			System.out.println("The book wasn't found");
//		}
//
//		int myTest = 1;
//
//		try {
//			if (myTest != 2) {
//				throw new RuntimeException("Something went wrong");
//			}
//		}
//		catch (RuntimeException e) {
//			// do nothing here so that we can continue;
//		}
		
		Customer customer = new Customer("Mr", "Michael", "Smith", "1 The High Street","1234","a@b.com",1,GenderType.MALE);
		System.out.println(customer.getExpiryDate());
		System.out.println(customer.getMailingName());

		System.out.println(customer);
		

		
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
