package part1Lambdas;

public class FunctionalInterfacesAndINHERITANCE {
    public static void main(String[]arg){
        System.out.println("Functional Interfaces And INHERITANCE ");
        AB ab = new AB();
        ab.say();
    }
}

class AB implements A,B{

    // without this we get ERROR
    @Override
    public void say() {
        A.super.say();
    }
}

interface A{
    default void say(){
        System.out.println("I am A interface");
    }

}

interface B{
    default void say(){
        System.out.println("I am B interface");
    }
}