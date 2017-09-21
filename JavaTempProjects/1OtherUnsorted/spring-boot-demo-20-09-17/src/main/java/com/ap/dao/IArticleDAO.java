package com.ap.dao;

import com.ap.entity.Article;

import java.util.List;

/**
 * Created by andrii on 20.09.17.
 */
public interface IArticleDAO {
    List<Article> getAllArticles();
    Article getArticleById(int articleId);
    void addArticle(Article article);
    void updateArticle(Article article);
    void deleteArticle(int articleId);
    boolean articleExists(String title, String category);
}
