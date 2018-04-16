package org.test.bookpub.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.test.bookpub.entity.Book;
import org.test.bookpub.repository.BookRepository;

@RestController
@RequestMapping("/books")
public class BookController {
    @Autowired
    BookRepository bookRepository;

    @RequestMapping("")
    public Iterable<Book> getAllBooks(){
        return bookRepository.findAll();
    }
}