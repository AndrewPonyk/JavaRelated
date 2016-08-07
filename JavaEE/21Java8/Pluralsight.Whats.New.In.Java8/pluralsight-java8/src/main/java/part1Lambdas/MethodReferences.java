package part1Lambdas;
import java.util.Arrays;
import java.util.List;

public class MethodReferences {

    // https://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html
    // https://blog.idrsolutions.com/2015/02/java-8-method-references-explained-5-minutes/

    public static void main(String[] arg){
        System.out.println("Method references");

        List<String> customers = Arrays.asList("WDS", "Xerox", "Nokia");

        //next strings are equals
        customers.forEach( (String e) -> { System.out.printf(e + "3310\n"); } );
        customers.forEach( System.out::println);




    }
}
