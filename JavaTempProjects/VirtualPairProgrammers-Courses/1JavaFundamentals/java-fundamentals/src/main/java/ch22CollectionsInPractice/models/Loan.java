package ch22CollectionsInPractice.models;

import ch22CollectionsInPractice.utilities.LoanStatus;

import java.util.Date;
import java.util.GregorianCalendar;


public class Loan {

	private int ID;
	private Customer customer;
	private Book book;
	private Date startDate;
	private Date dueDate;
	private Date returnDate;
	private LoanStatus status;
	
	public Loan(int iD, Customer customer, Book book) {
		super();
		ID = iD;
		this.customer = customer;
		this.book = book;
		startDate = new Date();
		
		GregorianCalendar gCal = new GregorianCalendar();
		gCal.add(GregorianCalendar.DAY_OF_MONTH,14);
		dueDate = gCal.getTime();
		
		status = LoanStatus.CURRENT;
	}

	@Override
	public String toString() {
		return "Loan [ID=" + ID + ", customer=" + customer.getMailingName() + ", book=" + book.getTitle()
				+ "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ID;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Loan other = (Loan) obj;
		if (ID != other.ID)
			return false;
		return true;
	}

	public Customer getCustomer() {
		return customer;
	}

	public Book getBook() {
		return book;
	}

	public Date getDueDate() {
		return dueDate;
	}
	
	public LoanStatus getStatus() {
		return status;
	}

	public void endLoan() {
		returnDate = new Date();
		status = LoanStatus.HISTORIC;
	}
	
}
