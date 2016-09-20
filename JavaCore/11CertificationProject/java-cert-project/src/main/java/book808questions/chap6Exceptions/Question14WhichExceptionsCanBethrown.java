package book808questions.chap6Exceptions;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class Question14WhichExceptionsCanBethrown {
}

/*
* Which of the following can be inserted on line 8 to make this code compile? (Choose all
that apply)
7: public void ohNo() throws IOException {
8: // INSERT CODE HERE
9: }
A. System.out.println("it's ok");
B. throw new Exception();
C. throw new IllegalArgumentException();
D. throw new java.io.IOException();
E. throw new RuntimeException();
* */

//Correct A,C,D,E

// Explanation : runtime exceptions can be thrown everythere, D is correct because it matches 'throws' signature,
// option B is not correct because it doesnt match 'throws' signature