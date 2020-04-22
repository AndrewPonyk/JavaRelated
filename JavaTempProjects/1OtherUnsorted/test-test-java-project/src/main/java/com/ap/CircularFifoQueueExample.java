package com.ap;

import org.apache.commons.collections4.queue.CircularFifoQueue;

public class CircularFifoQueueExample {
    public static void main(String[] args) {
        CircularFifoQueue<String> queue = new CircularFifoQueue<>(3);
        queue.add("1");
        queue.add("2");
        queue.add("3");
        queue.add("4");

        System.out.println(queue.toString());

    }
}
