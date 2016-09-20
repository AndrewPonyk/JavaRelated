package book808questions.chap6Exceptions;

import java.io.IOException;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class Question17AboutInheritedMethodsSignature {
    public static void main(String[] args) {

    }
}

class HasSoreThroatException extends Exception {}
class TiredException extends RuntimeException {}
interface Roar {
    void roar() throws HasSoreThroatException;
}
class Lion implements Roar {
    @Override
    public void roar() throws TiredException{
    }// INSERT CODE HERE
}

/*
* Which of the following can be inserted into Lion to make this code compile? (Choose all
that apply)
class HasSoreThroatException extends Exception {}
class TiredException extends RuntimeException {}
interface Roar {
void roar() throws HasSoreThroatException;
}
class Lion implements Roar {// INSERT CODE HERE
}
A. public void roar(){}
B. public void roar() throws Exception{}
C. public void roar() throws HasSoreThroatException{}
D. public void roar() throws IllegalArgumentException{}
E. public void roar() throws TiredException{}
* */

//correct are a, c, d, e
// Explanation : a - we cant override method withot throws, c - is correct because it matches overrided method,
// d and e are correct because java allows add 'throws' with runtime exceptions in overrided methods