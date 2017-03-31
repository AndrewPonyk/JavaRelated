package com.ap;

import org.apache.log4j.Logger;

/**
 * Hello world!
 *
 */
public class HelloExample
{
    final static Logger logger = Logger.getLogger(HelloExample.class);
    public static void main( String[] args )
    {
        HelloExample obj = new HelloExample();
        obj.runMe("mkyong");
    }

    private void runMe(String parameter){

        if(logger.isDebugEnabled()){
            logger.debug("This is debug : " + parameter);
        }

        if(logger.isInfoEnabled()){
            logger.info("This is info : " + parameter);
        }

        logger.warn("This is warn : " + parameter);
        logger.error("This is error : " + parameter);
        logger.fatal("This is fatal : " + parameter);
    }
}

  //  A log request of level p in a logger with level q is enabled if p >= q. This rule is at the heart of log4j.
// It assumes that levels are ordered. For the standard levels, we have ALL < DEBUG < INFO < WARN < ERROR < FATAL < OFF.