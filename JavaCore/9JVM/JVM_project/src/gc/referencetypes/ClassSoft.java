package gc.referencetypes;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrii on 24.02.16.
 */
public class ClassSoft {
    public static class Referred {
        protected void finalize() {
            System.out.println("...Good bye cruel world");
        }
    }

    public static void collect() throws InterruptedException {
        System.out.println("Suggesting collection");
        System.gc();
        System.out.println("Sleeping");
        Thread.sleep(5000);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Creating soft references");

        // This is now a soft reference.
        // The object will be collected only if no strong references exist and the JVM really needs the memory.
        Referred strong = new Referred();
        SoftReference<Referred> soft = new SoftReference<Referred>(strong);

        // attempt to claim a suggested reference
        ClassSoft.collect();

        System.out.println("Removing reference");
        strong = null;
        ClassSoft.collect();

        System.out.println("Consuming HEAP!!!");
        try {
            List<ClassSoft> heap = new ArrayList<ClassSoft>(100000);
            while (true){
                heap.add(new ClassSoft());
            }
        }catch (OutOfMemoryError error){
            // the soft reference shoud have been collected before this
            System.out.println("OUT of memory raised");
        }

        System.out.println("Done");
    }
}
