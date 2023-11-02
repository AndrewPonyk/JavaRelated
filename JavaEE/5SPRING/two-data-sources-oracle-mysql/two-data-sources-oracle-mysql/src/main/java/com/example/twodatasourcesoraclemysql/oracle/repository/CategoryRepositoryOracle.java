package com.example.twodatasourcesoraclemysql.oracle.repository;

import com.example.twodatasourcesoraclemysql.oracle.domain.CategoryEntityOracle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepositoryOracle extends JpaRepository<CategoryEntityOracle, Long> {
}
