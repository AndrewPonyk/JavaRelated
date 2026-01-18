package com.ap;
import java.util.*;
class Animal {
    // Private fields for encapsulation
    protected String name;
    protected int age;
    // Public constructor
    public Animal(String name, int age) { this.name = name; this.age = age; }

    // Getters for controlled access
    public String getName() { return name; }
    public int getAge() { return age; }

    // Public method
    public void speak() { System.out.println(getName() + " makes a sound"); }
}

class Dog extends Animal {
    private boolean trained; // Private field

    // Public constructor
    public Dog(String name, int age, boolean trained) {
        super(name, age);
        this.trained = trained;
    }

    public boolean isTrained() { // Getter for trained status
        return trained;
    }

    @Override // Override parent method
    public void speak() {
        System.out.println(getName() + " barks! Age: " + getAge() + ", Trained: " + trained);
    }
}
public class Demo {
    public static void main(String[] args) {
        int count = 0; // Data types
        double price = 19.99;
        char grade = 'A';
        boolean active = true;
        String text = "Hello";
        int[] nums = {1, 2, 3};
        List<Animal> animals = new ArrayList<>();

        Scanner sc = new Scanner(System.in);
        System.out.print("How many dogs? ");
        int n = sc.nextInt();
        sc.nextLine();

        for (int i = 0; i < n; i++) { // Loop + input + OOP
            System.out.print("Name: ");
            String name = sc.nextLine();
            animals.add(new Dog(name, i + 1, i % 2 == 0));
        }

        for (Animal a : animals) { // Enhanced for + polymorphism + if
            if (a instanceof Dog d && d.isTrained()) {
                System.out.print("[TRAINED] ");
            }
            a.speak();
        }

        int i = 0; // While loop
        while (i < nums.length) {
            System.out.println("Num: " + nums[i++]);
        }
        System.out.printf("Stats: count=%d, price=%.2f, grade=%c, active=%b, text=%s%n",
                count, price, grade, active, text);

        sc.close();// Properly close resources
    }}

//Java version history:
//        | Feature                     | Java Version               |
//        |-----------------------------|----------------------------|
//        | instanceof pattern matching | Java 16 (preview in 14-15) |
//        | var keyword                 | Java 10                    |
//        | Text blocks """             | Java 15                    |
//        | Records                     | Java 16                    |
//        | Sealed classes              | Java 17