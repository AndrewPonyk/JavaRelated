package book808questions.chap4MethodsEncapsulation;

/**
 * Created by 4G-PC on 19.09.2016.
 */
public class Bird {
    protected String text = "floating"; // protected access
    protected void floatInWater() { // protected access
        System.out.println(text);
    }
}
