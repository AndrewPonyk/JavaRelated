package com.ehrplatform.hl7.listener;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.HL7Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * MLLP listener lifecycle. On startup it binds a HAPI {@link HL7Service} to the
 * configured port and routes every inbound message to
 * {@link Hl7ReceivingApplication}; on shutdown it stops cleanly.
 *
 * <p>Managed as a Spring {@link SmartLifecycle} so it starts after the context
 * is ready and stops gracefully. Disable with {@code ehr.hl7.mllp.enabled=false}
 * (e.g. in tests).
 */
@Component
public class Hl7MllpListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(Hl7MllpListener.class);

    private final Hl7ReceivingApplication receivingApplication;
    private final int port;
    private final boolean enabled;

    private HapiContext hapiContext;
    private HL7Service server;
    private volatile boolean running;

    public Hl7MllpListener(Hl7ReceivingApplication receivingApplication,
                           @Value("${ehr.hl7.mllp.port:2575}") int port,
                           @Value("${ehr.hl7.mllp.enabled:true}") boolean enabled) {
        this.receivingApplication = receivingApplication;
        this.port = port;
        this.enabled = enabled;
    }

    @Override
    public void start() {
        if (!enabled || running) {
            return;
        }
        hapiContext = new DefaultHapiContext();
        server = hapiContext.newServer(port, false);
        server.registerApplication("*", "*", receivingApplication); // route all message types
        server.start();
        running = true;
        log.info("HL7 MLLP listener started on port {}", port);
    }

    @Override
    public void stop() {
        running = false;
        if (server != null) {
            server.stopAndWait();
        }
        if (hapiContext != null) {
            try {
                hapiContext.close();
            } catch (java.io.IOException e) {
                log.warn("Error closing HAPI context", e);
            }
        }
        log.info("HL7 MLLP listener stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE; // start last, stop first
    }
}
