package reactiveExamples;

import reactor.core.publisher.Flux;

public class ReactiveHelloWorldApp {
    public static void main(String[] args) {
        Flux<String> flux = Flux.just("apple", "banana", "orange")
                .map(String::toUpperCase)
                .filter(fruit -> fruit.startsWith("A"))
                .log();

        flux.subscribe(System.out::println);
    }
}
