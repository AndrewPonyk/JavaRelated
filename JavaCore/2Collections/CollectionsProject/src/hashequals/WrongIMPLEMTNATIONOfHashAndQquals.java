package hashequals;

import java.util.HashMap;
import java.util.Random;

/**
 * Created by andrii on 17.04.16.
 */
public class WrongIMPLEMTNATIONOfHashAndQquals {


    public static void main(String[] args) {
        System.out.println("Test equals and hash");
        A a = new A(); a.a1 = 1;
        A b = new A(); b.a1 = 2;

        HashMap<A, String> map = new HashMap<>();
        map.put(a, "a"); map.put(b, "b");

        A c = new A(); c.a1 = 1;
        System.out.println(a.equals(c)); //  a equals c
        System.out.println(map.get(c)); // null, because of wrong implementation of hashCode

        System.out.println("");
    }




    public static class A {
        public int a1;

        @Override
        public boolean equals(Object obj) {
            if(this == obj){
                return true;
            }
            if(obj instanceof A){
                if (this.a1 == ((A) obj).a1){
                    return true;
                }

            }
            return false;
        }

        @Override
        public int hashCode() {
            Random r = new Random();
            r.nextInt(15);
            int hash = (int) System.currentTimeMillis() * r.nextInt(15) ;
            //System.out.println(hash);
            return hash;
        }
    }
}
