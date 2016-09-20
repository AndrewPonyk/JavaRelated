package book808questions.chap4MethodsEncapsulation.anotherChap4Pack;

import book808questions.chap4MethodsEncapsulation.Bird;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class Swan extends Bird {
    public void swim() {
         floatInWater(); // protected access to superclass
         System.out.println(text); // protected access to superclass
         }

    public void helpOtherBirdSwim() {
        Bird other = new Bird();
        //other.floatInWater();  // !!! WE HAVENT ACCESS TO PROTECTED OF ANOTHER CLASS
    }
}
