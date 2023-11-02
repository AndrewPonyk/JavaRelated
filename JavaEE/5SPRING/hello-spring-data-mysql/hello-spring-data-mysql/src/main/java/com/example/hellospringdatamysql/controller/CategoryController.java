package com.example.hellospringdatamysql.controller;

import com.example.hellospringdatamysql.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/")
    public Map<String, String> getCategories(){
        Map<String, String> result = new HashMap<>();
        result.put("a", "100");

        categoryRepository.findAll().forEach(category -> {
            result.put(category.getId().toString(), category.toString());
        });

        return result;
    }

    @GetMapping("/admin")
    public Map<String, String> getCategoriesAdmin(){
        Map<String, String> result = new HashMap<>();
        result.put("a", "100");

        categoryRepository.findAll().forEach(category -> {
            result.put(category.getId().toString(), category.toString());
        });

        return result;
    }

    @GetMapping("/open")
    public Map<String, String> getCategoriesOpen(){
        Map<String, String> result = new HashMap<>();
        result.put("a", "100");

        categoryRepository.findAll().forEach(category -> {
            result.put(category.getId().toString(), category.toString());
        });

        return result;
    }


}
