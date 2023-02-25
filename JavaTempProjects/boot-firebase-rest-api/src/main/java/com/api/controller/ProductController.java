package com.api.controller;

import com.api.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/products")
    public List<Product> getAllProducts() throws ExecutionException, InterruptedException {
        return productService.getAll();
    }

    @GetMapping("/products/{id}")
    public Product getProduct(@PathVariable String id) throws ExecutionException, InterruptedException {
        return productService.getProductDetails(id);
    }

    @PostMapping("/products")
    public String saveProduct(@RequestBody Product product) throws ExecutionException, InterruptedException {
        return productService.saveProduct(product);
    }
}
