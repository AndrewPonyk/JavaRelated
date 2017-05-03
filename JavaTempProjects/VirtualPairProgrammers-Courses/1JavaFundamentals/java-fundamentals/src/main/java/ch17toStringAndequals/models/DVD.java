package ch17toStringAndequals.models;

public class DVD extends Material {

	private String director;
	private String catalogNo;
	private int runningTime;
	private boolean licenced;
	
	public DVD(int id, String title, String branch, String director,
			String catalogNo, int runningTime) {
		super(id, title, branch);
		this.director = director;
		this.catalogNo = catalogNo;
		this.runningTime = runningTime;
		licenced = false;
	}
	
	public void licence() {
		licenced = true;
	}
	
	public boolean lend(Customer customer) {
		if(licenced) {
			return super.lend(customer);
		}
		else {
			return false;
		}
	}
	
	public int getLoanPeriod() {
		return 7;
	}
}
