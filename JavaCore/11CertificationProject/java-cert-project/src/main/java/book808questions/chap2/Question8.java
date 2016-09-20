package book808questions.chap2;

/**
 * Created by 4G-PC on 17.09.2016.
 */
public class Question8 {
    public static void main(String[] args) {
        boolean x = true, z = true;
        int y = 20;
        x = (y != 10) ^ (z = false);
        System.out.println(x + ", " + y + ", " + z);

        System.out.println(true ^ true);
    }
}
