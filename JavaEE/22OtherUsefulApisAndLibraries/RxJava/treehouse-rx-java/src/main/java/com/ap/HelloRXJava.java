package com.ap;


import rx.Observable;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;

public class HelloRXJava {
    public static void main(String[] args) {
        String[] names = new String[]{"Andrii", "Ivan"};



    }

    public static void hello(String... names) {
        Subscription subscribe = Observable.from(names).subscribe(new Observer<String>() {
            public void onCompleted() {
                System.out.println("completed");
            }

            public void onError(Throwable throwable) {
                System.out.println("error");
            }

            public void onNext(String s) {
                System.out.println("next");
            }
        });

    }
}

