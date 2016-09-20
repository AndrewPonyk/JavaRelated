package book808questions.chap1;

/**
 When writing code that initializes fi elds in multiple places, you have to keep track of the
 order of initialization. We’ll add some more rules to the order of initialization in Chapters 4
 and 5. In the meantime, you need to remember:
 Fields and instance initializer blocks are run in the order in which they appear in
 the file.
 The constructor runs after all fields and instance initializer blocks have run.
 */

public class ClassInitializersExample {
    private int someval;
    private String someString;
    {
        System.out.println(someString);
        System.out.println("Init block 1");
        someval = 10;
    }


    public static void main(String[] args) {
        ClassInitializersExample classInitializers = new ClassInitializersExample();
        System.out.println(classInitializers.getSomeval());
    }

    {
        System.out.println("Init block 2");
    }

    public ClassInitializersExample(){
        System.out.println("COnstructor");
    }

    public int getSomeval() {
        return someval;
    }

    public void setSomeval(int someval) {
        this.someval = someval;
    }
}
