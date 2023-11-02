package com.example.hellospringdatamysql.repository;

import com.example.hellospringdatamysql.model.Category;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends CrudRepository<Category, Long> {
}
