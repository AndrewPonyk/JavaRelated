package com.example.hellowebflux.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/posts")
public class PostController {
    private final WebClient webClient;

    public PostController() {
        this.webClient = WebClient.create("https://jsonplaceholder.typicode.com");;
    }

    @GetMapping("/posts")
    public Flux<String> getPostTitles() {
        return webClient.get()
                .uri("https://jsonplaceholder.typicode.com/posts")
                .retrieve()
                .bodyToFlux(Post.class)
                .filter(post -> {
                    return post.getUserId() > 0;
                })
                .map(Post::getTitle)
                .map(String::toUpperCase);
    }

    static class Post{
        private String title;
        private Integer userId;

        public Post(){

        }
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Integer getUserId() {
            return userId;
        }

        public void setUserId(Integer userId) {
            this.userId = userId;
        }

        @Override
        public String toString() {
            return "Post{" +
                    "title='" + title + '\'' +
                    ", userId=" + userId +
                    '}';
        }
    }
}
