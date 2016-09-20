package book808questions.chap2;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class IncrementDecrement_AndEqualsExample {
    public static void main(String[] args) {
        int x = 3;
        int y = ++x * 5 / x-- + --x; // x-- will be calculated before --x, so its is 4*5/4+2
        System.out.println("x is " + x); // 2
        System.out.println("y is " + y); // 7

        System.out.println(y);


        int a=5,i;
        i=++a + ++a + a++;
        System.out.println(i); // simple)
        a=5;
        i=++a + a++ + a++;
        System.out.println(i);// 19 ok, got it)

        System.out.println(null == null); // true!!!

        if(10>2) System.out.println("10>2");else System.out.println("...");
    }
}
