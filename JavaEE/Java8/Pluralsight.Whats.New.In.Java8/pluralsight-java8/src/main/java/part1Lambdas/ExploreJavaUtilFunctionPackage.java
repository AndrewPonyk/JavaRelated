package part1Lambdas;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ExploreJavaUtilFunctionPackage {

    public  static  void sayHello(){
        System.out.println("HELLO...");
    }

    public static void main(String[] arg){

        Consumer<String> myPrint = System.out::println;
        myPrint.accept("Hello world");
        //=========================================

        Runnable r = ExploreJavaUtilFunctionPackage::sayHello;
        // The SAME code without method reference
        Runnable rr = ()->{ExploreJavaUtilFunctionPackage.sayHello();};

        rr.run();

        //================================= Predicates
        List<String> words = Arrays.asList("Alpha", "Com", "Nu");
        words.removeIf( e -> e.length()>2 ) ;
    }
}
