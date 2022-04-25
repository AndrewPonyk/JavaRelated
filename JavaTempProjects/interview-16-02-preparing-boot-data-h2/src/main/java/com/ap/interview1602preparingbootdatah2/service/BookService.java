package com.ap.interview1602preparingbootdatah2.service;


import com.ap.interview1602preparingbootdatah2.domain.Book;

import java.util.List;

public interface BookService {
    Book saveBook(Book book);

    void delete(Long id);

    List<Book> findAll();

    List<Book> findByName(String name);

    List<Book> findByNameAndAuthor(String name, String author);
}
