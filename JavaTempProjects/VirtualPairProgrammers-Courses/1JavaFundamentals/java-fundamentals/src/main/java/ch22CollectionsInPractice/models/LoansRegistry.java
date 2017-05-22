package ch22CollectionsInPractice.models;


import ch22CollectionsInPractice.utilities.LoanStatus;

import java.util.ArrayList;
import java.util.List;

public class LoansRegistry {

	private List<Loan> registry;
	private int nextPosition;

	public LoansRegistry() {
		registry = new ArrayList<>();
		nextPosition = 0;
	}

	public void addLoan(Loan loan) throws LoanAlreadyExistsException {
		if(registry.contains(loan)){
			throw new LoanAlreadyExistsException();
		}
		registry.add(loan);
	}

	public Loan findLoan(String bookID) throws LoanNotFoundException {
		for ( Loan loan : registry) {
			if (loan.getBook().getID().equals(bookID) && loan.getStatus() == LoanStatus.CURRENT ) {

			}
		}
		throw new LoanNotFoundException();
	}
	
	public boolean isBookOnLoan(String bookID) {
		
		try {
			Loan foundLoan = findLoan(bookID);
			return true;
		}
		catch (LoanNotFoundException e){
			return false;
		}
		
	}
	
}
