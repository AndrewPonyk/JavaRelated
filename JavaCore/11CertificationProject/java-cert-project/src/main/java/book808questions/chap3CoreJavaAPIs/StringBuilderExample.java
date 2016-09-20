package book808questions.chap3CoreJavaAPIs;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class StringBuilderExample {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("12344");
        System.out.println(sb.substring(2,2)); // empty line

        System.out.println(sb.substring(1,2));

        System.out.println(sb);

        System.out.println(sb.substring(2,1)); // Exception StringIndexOutOfBoundsException

    }

}
