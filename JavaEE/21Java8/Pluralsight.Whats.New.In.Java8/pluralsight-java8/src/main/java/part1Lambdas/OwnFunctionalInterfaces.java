package part1Lambdas;

import java.util.Objects;

public class OwnFunctionalInterfaces {
    public static void main(String[] arg) {

        int multipleBy2 = calculation(10, e -> e * 2);
        int square = calculation(10, OwnFunctionalInterfaces::square);

        System.out.println(multipleBy2);
        System.out.println(square);

        Multiple.say();
    }

    public static int square(Integer n) {
        return n * n;
    }

    public static int calculation(int n, Multiple operation) {
        Objects.requireNonNull(operation);

        return operation.mult(n);
    }
}

@FunctionalInterface
interface Multiple {
    Integer mult(Integer k);

    static String CONSTANTA = "123";

    static void say(){
        System.out.println("IN JAVA 8 INTERFACE CAN HAVE STATIC METHODS" + CONSTANTA);
    }
}