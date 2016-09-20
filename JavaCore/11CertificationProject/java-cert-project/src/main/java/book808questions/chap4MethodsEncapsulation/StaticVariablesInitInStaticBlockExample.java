package book808questions.chap4MethodsEncapsulation;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class StaticVariablesInitInStaticBlockExample {
     private static int one;
     private static final int two;
     private static final int three = 3;
     //private static final int four; // DOES NOT COMPILE

    static {
        two =2;
    }
    public static void main(String[] args) {

    }
}
