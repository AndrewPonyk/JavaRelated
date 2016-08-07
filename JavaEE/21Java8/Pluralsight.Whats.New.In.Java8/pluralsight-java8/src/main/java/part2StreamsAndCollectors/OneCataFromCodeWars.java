package part2StreamsAndCollectors;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OneCataFromCodeWars {
    public static void main(String[] arg) {
        System.out.println("Cesar cheaper");
        String u = "I should have known that you would have a perfect answer for me!!!";

        List<String> correct = Arrays.asList("J vltasl rlhr ", "zdfog odxr ypw", " atasl rlhr p ", "gwkzzyq zntyhv", " lvz wp!!!");
        List<String> v = movingShift(u, 1);

        assertEquals(correct, v);
    }

    public static List<String> movingShift(String s, int shift) {
        List<String> result = new ArrayList<>(5);
        result.addAll(Arrays.asList("", "", "", "", ""));

        int partLen = s.length() / 4;
        int fithLen = s.length() - 4 * partLen;

        while (partLen > fithLen + 4) {
            partLen--;
            fithLen += 4;
        }

        for (int i = 0; i < s.length(); i++, shift++) {
            result.set(i / partLen, result.get(i / partLen) + shiftLetter(s.charAt(i), shift));
        }

        return result;
    }

    public static char shiftLetter(char c, int shift) {
        shift = shift % 26;

        if (c >= 97 && c <= 122) {
            c += shift;
            if (c > 122) {
                c = (char) (c % 122 + 96);
            }

            if (c < 97) {
                c = (char) (122 - (97 - c % 97) + 1);
            }

        } else if (c >= 65 && c <= 90) {
            c += shift;
            if (c > 90) {
                c = (char) (c % 90 + 64);
            }

            if (c < 65) {
                c = (char) (90 - (65 - c % 65) + 1);
            }
        }

        return c;
    }

    public static String demovingShift(List<String> s, int shift) {
        String result = "";

        String joined = s.stream().reduce((a,b)->a+b).get();

        for (int i = 0; i < joined.length(); i++, shift++) {
            result += shiftLetter(joined.charAt(i), -1 * shift);
        }

        return result;
    }

    @Test
    public void testMe() {
        String u = "I should have known that you would have a perfect answer for me!!!";
        List<String> v = Arrays.asList("J vltasl rlhr ", "zdfog odxr ypw", " atasl rlhr p ", "gwkzzyq zntyhv", " lvz wp!!!");
        List<String> fromMethod = OneCataFromCodeWars.movingShift(u, 1);
        assertEquals(5, fromMethod.size());
        assertEquals(v, fromMethod);

        assertEquals(u, OneCataFromCodeWars.demovingShift(v, 1));

    }

    @Test
    public void testMeBig() {
        String u = "I should have known that you would have a perfect answer for me!!!";
        List<String> fromMethod = OneCataFromCodeWars.movingShift(u, 100);
        System.out.println("from:" + fromMethod.get(0));
        assertEquals(u, OneCataFromCodeWars.demovingShift(fromMethod, 100));
    }

    @Test
    public void testMeAnotherString(){
        String u = "I should have known!!!!";
        List<String> fromMethod = OneCataFromCodeWars.movingShift(u, 2);
        assertEquals(u, OneCataFromCodeWars.demovingShift(fromMethod, 2));
    }
}
