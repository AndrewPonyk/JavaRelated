/**
 * Created by andrii on 03.01.17.
 */
public class VerySimpleDeadlockImplementation {
    private Object lock1 = new Object();
    private Object lock2 = new Object();

    public void thread1() {
        for (int i = 0; i < 10000; i++) {

            synchronized (lock1) {
                synchronized (lock2) {

                }
            }

        }
    }

    public void thread2() {
        for (int i = 0; i < 10000; i++) {
            synchronized (lock2) {
                synchronized (lock1) {

                }
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("Deadlock example");
        VerySimpleDeadlockImplementation app = new VerySimpleDeadlockImplementation();

        new Thread(() -> {
            app.thread1();
        }).start();
        new Thread(() -> {
            app.thread2();
        }).start();
    }

}
