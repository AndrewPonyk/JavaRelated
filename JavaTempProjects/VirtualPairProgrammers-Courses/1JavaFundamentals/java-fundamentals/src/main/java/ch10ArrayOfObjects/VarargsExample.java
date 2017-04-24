package ch10ArrayOfObjects;

/**
 * Created by andrii on 23.04.17.
 */
public class VarargsExample {

    public static void main(String[] args) {
        foo(1); // "int i"
        //foo(1,1); //Ambuguous, "int ... i" and "int i, int... j"
    }


    public static void foo(int i){
        System.out.println("int i");
    }

    public static void foo(int ... i){
        System.out.println("int ... i");
    }

    public static void foo(int i, int... j){
        System.out.println(j.length);
        System.out.println("int i, int... j");
    }
}
