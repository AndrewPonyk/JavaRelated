package com.ap.controller;

import com.ap.entity.Article;
import com.ap.service.IArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("userV2")
public class ArticleControllerV2 {
    @Autowired
    private IArticleService articleService;

    @GetMapping("article/{id}")
    public Article getArticleById(@PathVariable("id") Integer id) {
        Article article = articleService.getArticleById(id);
        return article;
    }

    @GetMapping("articles")
    public List<Article> getAllArticles() {
        List<Article> list = articleService.getAllArticles();
        return list;
    }

    @PostMapping("article")
    public HttpStatus addArticle(@RequestBody Article article, UriComponentsBuilder builder) {
        boolean flag = articleService.addArticle(article);
        if (flag == false) {
            return HttpStatus.CONFLICT;
        }
        //HttpHeaders headers = new HttpHeaders();
        //headers.setLocation(builder.path("/article/{id}").buildAndExpand(article.getArticleId()).toUri());
        return HttpStatus.CREATED;
    }

    @PutMapping("article")
    public Article updateArticle(@RequestBody Article article) {
        articleService.updateArticle(article);
        return article;
    }

    @DeleteMapping("article/{id}")
    public HttpStatus deleteArticle(@PathVariable("id") Integer id) {
        articleService.deleteArticle(id);
        return HttpStatus.NO_CONTENT;
    }

}
