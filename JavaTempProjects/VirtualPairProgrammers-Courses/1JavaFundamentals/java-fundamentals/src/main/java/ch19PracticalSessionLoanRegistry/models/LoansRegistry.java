package ch19PracticalSessionLoanRegistry.models;


import ch19PracticalSessionLoanRegistry.utilities.LoanStatus;

public class LoansRegistry {

	private Loan[] registry;
	private int nextPosition;
	
	public LoansRegistry() {
		registry = new Loan[100];
		nextPosition = 0;
	}

	public void addLoan(Loan loan) throws LoanAlreadyExistsException {
		for (int i = 0; i < nextPosition; i++) {
			if (registry[i].equals(loan)) {
				throw new LoanAlreadyExistsException();
			}
		}
		registry[nextPosition] = loan;
		nextPosition++;
	}
	
	public Loan findLoan(int bookID) throws LoanNotFoundException {
		for (int i = 0; i < nextPosition; i++) {
			if (registry[i].getBook().getID() == bookID && registry[i].getStatus() == LoanStatus.CURRENT) {
				return registry[i];
			}
		}
		
		throw new LoanNotFoundException();
	}
	
	public boolean isBookOnLoan(int bookID) {
		
		try {
			Loan foundLoan = findLoan(bookID);
			return true;
		}
		catch (LoanNotFoundException e){
			return false;
		}
		
	}
	
}
