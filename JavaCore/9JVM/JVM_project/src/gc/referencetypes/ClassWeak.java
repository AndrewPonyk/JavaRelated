package gc.referencetypes;

import java.lang.ref.WeakReference;

/**
 * Created by andrii on 24.02.16.
 */
public class ClassWeak {

    public static class Referred {
        protected void finalize() {
            System.out.println("Good bye cruel world");
        }
    }

    public static void collect() throws InterruptedException {
        System.out.println("Suggesting collection");
        System.gc();
        System.out.println("Sleeping");
        Thread.sleep(5000);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Creating weak references");

        // the object will be collected only if no strong references
        Referred strong = new Referred();
        WeakReference<Referred> weak  = new WeakReference<Referred>(strong);

        ClassSoft.collect();

        strong = null;

        ClassSoft.collect();
    }


}
