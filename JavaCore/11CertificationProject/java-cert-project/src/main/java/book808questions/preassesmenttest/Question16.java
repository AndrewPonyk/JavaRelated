package book808questions.preassesmenttest;

public class Question16 {
     public interface Animal { public default String getName() { return null; } }
     interface Mammal { default public String getName(){return null;}; }
//     abstract class Otter implements Mammal, Animal {
//     }
}

/*
    What individual changes, if any, would allow the following code to compile? (Choose all
        that apply)
        1: public interface Animal { public default String getName() { return null; } }
2: interface Mammal { public default String getName() { return null; } }
3: abstract class Otter implements Mammal, Animal {}
*/

/*    A. The code compiles without issue.
        B. Remove the default method modifier and method implementation on line 1.
        C. Remove the default method modifier and method implementation on line 2.
        D. Remove the default method modifier and method implementation on lines 1 and 2.
        E. Change the return value on line 1 from null to "Animal".
F. Override the getName() method with an abstract method in the Otter class.
G. Override the getName() method with a concrete method in the Otter class.
        */


//correct are 'DFG'

