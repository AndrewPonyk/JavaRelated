package interruptThread;

public class InterruptThreadApp {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("Test thread interrupt");

        Thread t = new Thread(new MyThread());
        t.start();

        //allow to run 300ms
        Thread.sleep(300);
        t.interrupt(); // so we need to handle Exception inside thread (have full control over - can stop inside or continue processing)
    }
}
