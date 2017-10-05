package com.ap;


public class App {
    public static void main(String[] args) {
        testClassWithDataAnnotation();

    }

    public static void testClassWithBuilderAnnotation() {
        EntityWithBuilder entityWithBuilder = new EntityWithBuilder.
                EntityWithBuilderBuilder()
                .id(10)
                .name("Andrii")
                .build();
    }

    public static void testClassWithDataAnnotation(){
        EntityWithDataAnnotation entityWithDataAnnotation = new EntityWithDataAnnotation(1);
        entityWithDataAnnotation.setId(100);
        entityWithDataAnnotation.setName("Peter");

        System.out.println(entityWithDataAnnotation);
    }

    public static void testClassWithAllArgsConstructorAndGetterSetter(){
        EntityWithAllArgsAndGetterSetter a1 = new EntityWithAllArgsAndGetterSetter(1,"");
        a1.setName("new name");
    }
}
