package ch10ArrayOfObjects;


public class Book {

    private int bookID;
    private String title;
    private String author;
    private String isbn;

    public Book(int bookID, String title, String author, String isbn)
    {
        this.bookID = bookID;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getIsbn() {
        return isbn;
    }

    public int getBookID() {
        return bookID;
    }
}
