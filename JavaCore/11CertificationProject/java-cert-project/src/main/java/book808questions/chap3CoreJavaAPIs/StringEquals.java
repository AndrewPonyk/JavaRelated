package book808questions.chap3CoreJavaAPIs;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class StringEquals {
    public static void main(String[] args) {
        String s = "he";
        String ss = "he";
        String sss = "h";
        sss += "e";

        System.out.println(s == ss);
        System.out.println(s == sss);// false
    }
}
