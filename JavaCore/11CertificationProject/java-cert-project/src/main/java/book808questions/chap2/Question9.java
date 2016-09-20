package book808questions.chap2;

/**
 * Created by 4G-PC on 17.09.2016.
 */
// How many times 'Hello world' will be printed ?
    // I give wrong answer))))
public class Question9 {
    public static void main(String[] args) {
        for (int i =0;i<10;){
            i = i++;
            System.out.println("Hello world" + i);
        }
    }
}


























// Correct answer : infinite loop