package javautilconcurent.atomics;


public class Example1WithoutAtomics {
    public static void  main(String[] arg) throws InterruptedException {
        System.out.println("Use atomics");

        ProcessingThread pt = new ProcessingThread();

        Thread t1 = new Thread(pt, "t1");
        t1.start();
        Thread t2 = new Thread(pt, "t2");
        t2.start();

        t1.join();
        t2.join();

        System.out.println("Processing count=" + pt.getCount()); // output can be 5 or 6 or 7 or 8
    }
}

class ProcessingThread implements Runnable{
    private int count;

    @Override
    public void run() {
        for(int i = 1 ;i<5;i++){
            processSomething(i);
            count++;
        }
    }

    public int getCount(){
        return this.count;
    }

    private void processSomething(int i){
        try {
            Thread.sleep(i * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
