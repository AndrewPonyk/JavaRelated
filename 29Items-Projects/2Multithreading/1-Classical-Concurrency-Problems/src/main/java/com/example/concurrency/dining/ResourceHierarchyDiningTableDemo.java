package com.example.concurrency.dining;

/** Standalone resource-hierarchy Dining Philosophers demonstration. */
public final class ResourceHierarchyDiningTableDemo {
    private ResourceHierarchyDiningTableDemo() {
    }

    public static void main(String[] args) throws Exception {
        DiningDemoRunner.run("resource hierarchy", new ResourceHierarchyDiningTable(5));
    }
}
