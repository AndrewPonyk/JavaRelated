package ch19PracticalSessionLoanRegistry;


import ch19PracticalSessionLoanRegistry.models.Book;
import ch19PracticalSessionLoanRegistry.models.Customer;
import ch19PracticalSessionLoanRegistry.models.Loan;
import ch19PracticalSessionLoanRegistry.utilities.GenderType;
import org.junit.Test;

import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;

public class LoanTest {

	@Test
	public void testDueDate() {
		Book book = new Book(0,"","","","",0);
		Customer customer = new Customer("", "", "", "", "", "", 0, GenderType.MALE);
		Loan loan = new Loan(0, customer, book);
		
		GregorianCalendar gcExpected = new GregorianCalendar();
		gcExpected.add(GregorianCalendar.DAY_OF_MONTH, 14);

		assertEquals("Checking loan 2 weeks",gcExpected.getTime(), loan.getDueDate());

		// why use this? if there is equals
//		GregorianCalendar gcActual = new GregorianCalendar();
//		gcActual.setTime(loan.getDueDate());
//		assertEquals("Checking year",gcExpected.get(GregorianCalendar.YEAR), gcActual.get(GregorianCalendar.YEAR));
//		assertEquals("Checking month",gcExpected.get(GregorianCalendar.MONTH), gcActual.get(GregorianCalendar.MONTH));
//		assertEquals("Checking day",gcExpected.get(GregorianCalendar.DAY_OF_MONTH), gcActual.get(GregorianCalendar.DAY_OF_MONTH));
	}
	
}
