package book808questions.chap6Exceptions;

import java.io.IOException;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class ThrowableCanBeCachedExample {
    public static void main(String[] args) {
        int a = 10;
        try {
             a = 10/0;
        }catch (Throwable e) {
            System.out.println("Error :" + e.getMessage());
            System.out.println(e.getClass()); // class java.lang.ArithmeticException
        }
    }

    public void test() throws IOException{
        throw new IOException();
    }

}
