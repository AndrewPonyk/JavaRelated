package book808questions.chap6Exceptions;

/**
 * Created by 4G-PC on 18.09.2016.
 */
public class FinallyIsAlwaysExecuted {
    public static void main(String[] args) {
        System.out.println(methodWithFinally());
    }

    public static int methodWithFinally(){
        try {
            int a = 10/0;
            return 0;
        }catch (ArithmeticException e){
            System.out.println("Catch /0 exception");
            return -1;
        }finally {
            System.out.println("this is FINALLY block");
            return -2;
        }
    }
}


// I think -2 will be returned.

    // ONe exception about 'FINALLY block'
//System.exit
/*        There is one exception to “the finally block always runs after the catch block” rule:
        Java defi nes a method that you call as System.exit(0);. The integer parameter is the
        error code that gets returned. System.exit tells Java, “Stop. End the program right now.
        Do not pass go. Do not collect $200.” When System.exit is called in the try or catch
        block, finally does not run.*/