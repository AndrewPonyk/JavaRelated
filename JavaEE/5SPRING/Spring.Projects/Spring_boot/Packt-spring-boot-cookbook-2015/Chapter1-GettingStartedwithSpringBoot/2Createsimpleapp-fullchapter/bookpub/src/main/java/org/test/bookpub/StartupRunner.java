package org.test.bookpub;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.time.LocalDateTime;

public class StartupRunner implements CommandLineRunner {

    protected final Log logger = LogFactory.getLog(StartupRunner.class);


    @Autowired
    DataSource ds;

    /**
     * Callback used to run the bean.
     *
     * @param args incoming main method arguments
     * @throws Exception on error
     */
    @Override
    public void run(String... args) throws Exception {
        logger.info("Hello");
        logger.info(ds.toString());
    }

    @Scheduled(initialDelay = 1000, fixedRate = 10000)
    public void run(){
        logger.info("Now: " + LocalDateTime.now());
    }
}
