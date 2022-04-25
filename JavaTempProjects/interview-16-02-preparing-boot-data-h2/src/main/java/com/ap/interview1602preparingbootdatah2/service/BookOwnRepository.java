package com.ap.interview1602preparingbootdatah2.service;

import com.ap.interview1602preparingbootdatah2.domain.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookOwnRepository extends JpaRepository<Book, Long> {
    List<Book> findByName(String name);
    List<Book> findByNameLike(String name); // there are lot more (like, contains, notcontains....)
    List<Book> findByPrice(Long price);
    List<Book> findByNameAndAuthor(String name, String author);
}
