package reactiveExamples;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;

public class ReactiveClient {
    public static void main(String[] args) {
        System.out.println("Test reactive client");

        WebClient client = WebClient.create("https://jsonplaceholder.typicode.com");

        Flux<Post> posts = client.get()
                .uri("/posts")
                .retrieve()
                .bodyToFlux(Post.class)
                .log();

        Flux<String> titles = posts
                .filter(post -> {
                    return post.getUserId() > 0;
                })
                .map(Post::getTitle)
                .log();
        List<String> titleList = titles.collectList().block();

        titles.subscribe(System.out::println);
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
