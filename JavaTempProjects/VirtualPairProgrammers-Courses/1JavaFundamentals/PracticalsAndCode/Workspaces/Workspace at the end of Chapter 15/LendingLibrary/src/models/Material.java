package models;

public class Material {

	private int id;
	private String title;
	private String branch;
	
	public Material(int id, String title, String branch) {
		this.id = id;
		this.title = title;
		this.branch = branch;
	}

	public String getTitle() {
		return title;
	}
	
	public int getID() {
		return id;
	}
	
	public void relocate (String newBranch) {
		this.branch = newBranch;
	}
	
	public boolean lend(Customer customer) {
		return true;
	}
	
}
