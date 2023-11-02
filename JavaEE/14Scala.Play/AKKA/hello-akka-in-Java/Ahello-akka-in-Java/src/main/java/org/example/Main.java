package org.example;

import akka.actor.ActorSystem;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        ActorSystem system = ActorSystem.create("test-system");

    }
}