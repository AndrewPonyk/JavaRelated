import java.lang.reflect.Field;
import java.util.Set;

/**
 * Created by andrii on 30.11.16.
 */
public class Temp {

    private Object lock1= new String("first lock");
    private Object lock2 = new String("second lock");

    public void method1(){
        for (int i = 0; i < 1000; i++) {
            synchronized (lock1){
                synchronized (lock2){
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    public void method2(){
        for (int i = 0; i < 1000; i++) {
            synchronized (lock2){
                synchronized (lock1){
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void displayThreadNames (){
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Threads");
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread thread : threads){
            System.out.print(thread.getName() + ":" +thread.getState() + ",");
            try {
                Field blocker = thread.getClass().getDeclaredField("blockerLock");
                blocker.setAccessible(true);

                System.out.println(blocker.get(thread));
                System.out.println(thread.getStackTrace());
                System.out.println("=========");
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        System.out.println();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("My deadlock");

        Temp temp = new Temp();

        Thread t1 = new Thread(()->{
            temp.method1();
        });

        Thread t2 = new Thread(()->{
            temp.method2();
        });

        Thread monitorThrea = new Thread(()->{
           temp.displayThreadNames();
        });

        t1.setName("First thread"); t2.setName("Second thread");
        t1.start();
        t2.start();
        monitorThrea.start();

        t1.join();
        t2.join();
        monitorThrea.join();

    }
}
