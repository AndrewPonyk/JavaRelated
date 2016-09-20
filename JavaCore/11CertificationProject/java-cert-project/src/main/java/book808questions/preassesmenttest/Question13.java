package book808questions.preassesmenttest;

/**
 * Created by 4G-PC on 17.09.2016.
 */
public class Question13 {

    public static void main(String[] args) {
        int luck = 10;
        if ((luck > 10 ? luck++ : --luck) < 10) {
            System.out.print("Bear");
        }
        if (luck < 10) System.out.print("Shark");
    }
}

/*
* What is the output of the following program?
1: public class BearOrShark {
2: public static void main(String[] args) {
3: int luck = 10;
4: if((luck>10 ? luck++: --luck)<10) {
5: System.out.print("Bear");
6: } if(luck<10) System.out.print("Shark");
7: } }
A. Bear
B. Shark
C. BearShark
D. The code will not compile because of line 4.
E. The code will not compile because of line 6.
F. The code compiles without issue but does not produce any output
*/
