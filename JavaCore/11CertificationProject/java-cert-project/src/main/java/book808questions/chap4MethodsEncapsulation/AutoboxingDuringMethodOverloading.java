package book808questions.chap4MethodsEncapsulation;

import book808questions.chap4MethodsEncapsulation.anotherChap4Pack.Swan;

/**
 * Created by 4G-PC on 20.09.2016.
 */
public class AutoboxingDuringMethodOverloading {
    ////////////////////////////// Autoboxing during method overloading
    public int test(int n){
        return 0;
    }

    public int test(Integer n){
        return 0;
    }/////////


    //////////////////////////////// Here everything is ok
    public static void fly(String s) {
        System.out.print("string ");
    }
    public static void fly(Object o) {
        System.out.print("object ");
    }

    public static void main(String[] args) {
        fly("1"); // String
        fly(1);//Object
    }
    ///////////////////////////////


    public static void fly(short i){

    }


    public static void withBird(Bird b){
    }

    public static void withBird (Swan s){

    }
}
