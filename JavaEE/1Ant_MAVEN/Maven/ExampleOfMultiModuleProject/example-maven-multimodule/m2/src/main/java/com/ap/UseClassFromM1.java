package com.ap;

/**
 * Created by andrii on 04.12.16.
 */
public class UseClassFromM1 {

    public static void printTodaysDate(){
        System.out.println(UtilClassFromM1Module.getNow());
    }

    public static void main(String[] args) {
        printTodaysDate();
    }
}
