package models;

public class Book extends Material {

	private String author;
	private String isbn;
	private int noOfPages;
	
	public Book(int id, String title, String author, String isbn, String branch, int noOfpages) 
	{
		super(id,title,branch);
		this.author = author;
		this.isbn = isbn;
		this.noOfPages= noOfpages;
	}
	
	public String getAuthor() {
		return author;
	}

	public String getIsbn() {
		return isbn;
	}

	public void sendForRepair() {
		System.out.println("Book has been sent for repair");
	}
	
}
