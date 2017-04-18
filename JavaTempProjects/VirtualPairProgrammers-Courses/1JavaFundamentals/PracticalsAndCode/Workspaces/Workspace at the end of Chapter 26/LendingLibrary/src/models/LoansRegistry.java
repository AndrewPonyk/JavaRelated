package models;

import java.util.ArrayList;

import utilities.LoanStatus;

public class LoansRegistry {

	private ArrayList<Loan> registry;

	public LoansRegistry() {
		registry = new ArrayList<Loan>();
	}

	public void addLoan(Loan loan) throws LoanAlreadyExistsException {
		if (registry.contains(loan)) {
			throw new LoanAlreadyExistsException();
		}
		registry.add(loan);
	}

	public Loan findLoan(String bookID) throws LoanNotFoundException {
		for (Loan loan : registry) {
			if (loan.getBook().getID() == bookID && loan.getStatus() == LoanStatus.CURRENT) {
				return loan;
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
