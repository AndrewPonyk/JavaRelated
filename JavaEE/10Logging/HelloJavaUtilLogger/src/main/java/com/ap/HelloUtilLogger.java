package com.ap;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HelloUtilLogger {

    private static Logger LOGGER = Logger.getLogger(HelloUtilLogger.class.toString());

    public static void main(String[] args) {
        System.out.println("Using Logger");
        LOGGER.log(Level.INFO, "Info message");

    }
}
