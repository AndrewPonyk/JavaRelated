package ch4variables;

/**
 * Created by andrii on 17.04.17.
 */
public class Main {
    public static void main(String[] args) {

        long l = 9000000000L; // without L we got error 'value is out of range'

        double d = 1.23;
        float ff = 1.23F;
        System.out.println(d == ff); // false

        double d2 = 1.23;
        System.out.println(d==d2); // true

        double d3 = 2.46/2;
        System.out.println(d3 == d2);//true
        
        int i3 = 100;
        int i4 = 7;
        int i5 = i3%i4;
        System.out.println(i5);//2
    }
}
