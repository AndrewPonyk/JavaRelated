package ch22CollectionsInPractice.ui;


import ch22CollectionsInPractice.models.Book;

import java.util.HashMap;
import java.util.Map;

public class UI {

    public void printHeader() {
        System.out.println("BookID  Title                 Author");
    }

    public void printBook(Book book) {
        System.out.println(fixLengthString(book.getID(), 6) + "  " + fixLengthString(book.getTitle(), 20) +
                "  " + fixLengthString(book.getAuthor(), 20));
    }

    private String fixLengthString(String start, int length) {
        //TODO: fix string padding problem
        if (start.length() >= length) {
            return start.substring(0, length);
        } else {
            while (start.length() < length) {
                start += " ";
            }
            return start;
        }
    }

    public void printBookCatalog(Map<String, Book> bookCatalog) {
        for (Book book : bookCatalog.values()) {
            printBook(book);
        }
    }

}
