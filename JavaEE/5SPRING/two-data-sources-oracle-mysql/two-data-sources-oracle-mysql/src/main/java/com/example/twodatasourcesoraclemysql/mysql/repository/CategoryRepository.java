package com.example.twodatasourcesoraclemysql.mysql.repository;

import com.example.twodatasourcesoraclemysql.mysql.domain.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, Long> {
}