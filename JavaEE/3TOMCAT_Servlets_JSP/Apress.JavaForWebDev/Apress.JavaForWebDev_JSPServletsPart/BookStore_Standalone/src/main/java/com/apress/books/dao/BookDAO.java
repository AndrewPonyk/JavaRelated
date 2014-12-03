package com.apress.books.dao;

import java.util.List;

import com.apress.books.model.Book;
import com.apress.books.model.Category;

public interface BookDAO {
	public List<Book> findAllBooks();

	public List<Book> searchBooksByKeyword(String keyWord);

	public List<Category> findAllCategories();

	public void insert(Book book);

	public void update(Book book);

	public void delete(Long bookId);
}
