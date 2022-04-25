package com.ap.interview1602preparingbootdatah2.web;

import com.ap.interview1602preparingbootdatah2.domain.Book;
import com.ap.interview1602preparingbootdatah2.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BooksController {
    @Autowired
    private BookService bookService;

    @RequestMapping(value = "/add/{id}/{name}/{author}/{price}")
    public Book addBook(@PathVariable Long id, @PathVariable String name,
                        @PathVariable String author, @PathVariable Long price){
        Book book = new Book();
        book.setId(id);
        book.setName(name);
        book.setAuthor(author);
        book.setPrice(price);
        bookService.saveBook(book);
        return book;
    }

    @RequestMapping(value = "/delete/{id}")
    public void deleteBook(@PathVariable Long id){
        bookService.delete(id);
    }

    @RequestMapping("/")
    public List<Book> getBooks(){
        return bookService.findAll();
    }

    /**
     * IT is LIKE search
     * but there is problem: we can not use % sing in url
     * so we need to escape it and use %25 (it is % encoded)
     * so example wildcard search
     * http://localhost:8080/search/name/%25kob%25
     * will be mapped into select * from book where name like '%kob%'
     * @param name
     * @return
     */
    @RequestMapping("/search/name/{name}")
    public List<Book> getBooksByName(@PathVariable String name){
        return bookService.findByName(name);
    }

    @RequestMapping("/search/name/{name}/author/{author}")
    public List<Book> getBooksByNameAndAuthor(@PathVariable String name,
                                              @PathVariable String author){
        return bookService.findByNameAndAuthor(name, author);
    }
}
