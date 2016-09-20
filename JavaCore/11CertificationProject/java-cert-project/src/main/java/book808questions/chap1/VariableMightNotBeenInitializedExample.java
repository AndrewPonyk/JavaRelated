package book808questions.chap1;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class VariableMightNotBeenInitializedExample {
    public static final void main(String[] args) {
       // int x;
       // System.out.println(x); // DOES NOT COMPILE
        // --------------------------------------------
//        int x;
//        for (int i = 0; i < 10; i++) {
//            if (10 > 100){
//                x =10;
//            }else{
//                x =100;
//            }
//        }
//
//        System.out.println(x); // DOES NOT COMPILE
        // ------------------------------------------------
        int x;
        if (10>100){
            x=1;
        }else{
            x=2;
        }
        System.out.println(x); // COMPILE OK
    }
}
