package ch15Inheritance.ui;


import ch15Inheritance.models.Book;

public class UI {

	public void printHeader() {
		System.out.println("BookID  Title                 Author");
	}

	public void printBook(Book book) {
		System.out.println(fixLengthString(book.getID(), 6) + "  " + fixLengthString(book.getTitle(),20) +
				"  " + fixLengthString(book.getAuthor(),20));
	} 

	private String fixLengthString(String start, int length) {
		//TODO: fix string padding problem
		if (start.length() >= length) {
			return start.substring(0,length);
		}
		else {
			while (start.length() <length) {
				start += " ";
			}
			return start;
		}
	}

	private String fixLengthString(int start, int length) {
		String startString = String.valueOf(start);
		return fixLengthString(startString, length);
	}

	public void printBookCatalog(Book[] bookCatalog) {
		for (int counter = 0; counter <bookCatalog.length; counter++ )
		{
			if (bookCatalog[counter] != null) {
				printBook(bookCatalog[counter]);
			}
		}
	}

}
