package com.example.twodatasourcesoraclemysql.controller;

import com.example.twodatasourcesoraclemysql.mysql.domain.CategoryEntity;
import com.example.twodatasourcesoraclemysql.mysql.repository.CategoryRepository;
import com.example.twodatasourcesoraclemysql.oracle.domain.CategoryEntityOracle;
import com.example.twodatasourcesoraclemysql.oracle.repository.CategoryRepositoryOracle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class TwoDataSourcesController {
    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryRepositoryOracle categoryRepositoryOracle;

    @GetMapping("/data")
    public String getData(){
        List<CategoryEntity> categories
                = categoryRepository.findAll();
        String categoriesString = categories.stream().map(Object::toString).collect(Collectors.joining());

        List<CategoryEntityOracle> oracleCategories = categoryRepositoryOracle.findAll();
        String oracleCategoriesString = oracleCategories.stream().map(Object::toString).collect(Collectors.joining());

        return categoriesString + "|" + oracleCategoriesString;
    }
}
