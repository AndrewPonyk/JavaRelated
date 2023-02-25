public class TestWaitNotify {

    public static Object lock = new Object();

    public static void main(String[] args) {
        System.out.println("Test wait notify");

        Thread operation = new Thread(new Operation());
        Thread notificator = new Thread(new Notificator());

        operation.start();
        notificator.start();
    }


    static class Notificator implements Runnable{

        public void run() {
            synchronized (lock){
                System.out.println("releasing lock");
                lock.notify();
            }
        }
    }

    static class Operation extends Thread{

        public void run() {
            synchronized (lock){
                System.out.println("Inside operation holding lock");
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("Inside operation - lock released");
            }
        }
    }



}
