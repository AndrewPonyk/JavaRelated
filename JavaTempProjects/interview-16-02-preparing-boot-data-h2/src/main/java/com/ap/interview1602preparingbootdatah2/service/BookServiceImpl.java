package com.ap.interview1602preparingbootdatah2.service;

import com.ap.interview1602preparingbootdatah2.domain.Book;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.List;

@Service
@Transactional
public class BookServiceImpl implements BookService{
    @Autowired
    private BookOwnRepository bookOwnRepository;

    @Override
    public Book saveBook(Book book) {
        return bookOwnRepository.save(book);
    }

    @Override
    public void delete(Long id) {
        bookOwnRepository.deleteById(id);
    }

    @Override
    public List<Book> findAll() {
        return bookOwnRepository.findAll();
    }

    @Override
    public List<Book> findByName(String name) {
        return bookOwnRepository.findByNameLike(name); //NEED WILDCARD %test%
    }

    @Override
    public List<Book> findByNameAndAuthor(String name, String author) {
        return bookOwnRepository.findByNameAndAuthor(name, author);
    }


}
