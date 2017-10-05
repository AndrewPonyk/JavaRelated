package com.a;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hello world!
 */
public class App {
    static final Logger logger = LoggerFactory.getLogger(App.class);
    static final Logger logger2 = LoggerFactory.getLogger("com.javacodegeeks.examples.logbackexample.beans");

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            logger.info("Msg #1");
            logger.warn("Msg #2");
            logger.error("Msg #3");
            logger.debug("Msg #4");

            // another logger, look logback.xml
            logger2.info("Warn message from another logger");
        }
    }
}

/*
Levels in order

OFF
FATAL
ERROR
WARN
INFO
DEBUG
TRACE
ALL
 */