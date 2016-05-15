package gc.referencetypes;

/**
 * Created by andrii on 24.02.16.
 */
public class ClassStrong {

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
        System.out.println("Creating strong references");

        Referred strong = new Referred();

        ClassStrong.collect(); // attempt to claim a suggested reference

        System.out.println("Removing reference");
        // the object may now be colected
        strong = null;
        ClassStrong.collect();

        System.out.println("Done");
    }

}
