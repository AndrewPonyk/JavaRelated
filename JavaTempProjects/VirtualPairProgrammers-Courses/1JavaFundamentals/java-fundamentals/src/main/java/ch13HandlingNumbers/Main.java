package ch13HandlingNumbers;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Created by andrii on 27.04.17.
 */
public class Main {
    public static void main(String[] args) {
        double d = 1;

        NumberFormat nf = NumberFormat.getCurrencyInstance();
        nf.setMinimumFractionDigits(5);
        nf.setMaximumFractionDigits(5);
        System.out.println(nf.format(d));

        double dd = 0.5;
        nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(0);
        System.out.println(nf.format(dd)); // works like Oracle varchar 0.5 -> '.5'

        // Language - ISO 639, Country - ISO 3166
        //https://docs.oracle.com/cd/E13214_01/wli/docs92/xref/xqisocodes.html
        Locale ukraine = new Locale("uk", "UA");
        nf = NumberFormat.getCurrencyInstance(ukraine);
        System.out.println(nf.format(dd)); // 0.5 грн


        // DOUBLE magic))
        double one = 1d;
        for (int i = 0; i < 10; i++) {
            one += 0.1;
            System.out.println(one); // 1.1 1.2000000000000002 1.3000000000000003 ....
        }
    }
}
