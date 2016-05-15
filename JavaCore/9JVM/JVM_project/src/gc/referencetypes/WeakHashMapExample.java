package gc.referencetypes;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by andrii on 24.02.16.
 */
public class WeakHashMapExample {

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

        // This is now a weak reference.
        // The object will be collected only if no strong references.
        Referred strong = new Referred();
        Map<Referred,String> metadata = new WeakHashMap<Referred,String>();
        metadata.put(strong, "WeakHashMap's make my world go around");

        // Attempt to claim a suggested reference.
        WeakHashMapExample.collect();
        System.out.println("Still has metadata entry? " + (metadata.size() == 1));
        System.out.println("Removing reference");
        // The object may be collected.
        strong = null;
        WeakHashMapExample.collect();

        System.out.println("Still has metadata entry? " + (metadata.size() == 1));

        System.out.println("Done");
    }
}
