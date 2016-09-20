package book808questions.chap1;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class CastingNumbersExample {
    public static void main(String[] args) {
        long l = 100;
        float f = 10 + l;
        long ll = 2147483647;
        //long lll = 2147483648; // ! doesnt compile, because literal by default is 'int', we should use long lll = 2147483648L


        int octal = 0123; // 83
        int hex = 0x11;// 17
        int binary = 0b111; // 7
        System.out.println(octal + " ," + hex + " " + binary);

        int readibility = 1000_000; // In Java SE 7 and later, any number of underscore characters (_) can appear anywhere between digits in a numerical literal. This feature enables you, for example, to separate groups of digits in numeric literals, which can improve the readability of your code.


    }
}
