package declarationInitializationScoping;

import static java.lang.System.*;
public class ArrayInitializer{

     public static void main(String []args){
        System.out.println("Hello World");
        int [] data = {10,20,30,40,50,60,71,80,90,91};
        
        do1(data);
        out.println();
        
        // do1({1,2,3}); // -ERROR
        do1(new int[]{1,2,3}); // CORRECT
     }

     public static void do1(int [] array ){
       for(int item : array)
        out.print(item + " ");
        
        
        
     }
}

