package com.optimagrowth.gateway.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

import reactor.core.publisher.Mono;

@Configuration
public class ResponseFilter {
 
    final Logger logger =LoggerFactory.getLogger(ResponseFilter.class);
    
    @Autowired
	FilterUtils filterUtils;
    
    /**
     * This class implements a response filter that adds correlation IDs to outbound requests.
     * It works as follows:
     * 1. Intercepts the response after the request is processed
     * 2. Gets the correlation ID from the incoming request headers
     * 3. Adds the correlation ID to the outbound response headers
     * 4. Logs the correlation ID and request completion
     *
     * The correlation ID helps with distributed tracing by linking requests as they flow through 
     * different microservices. This is particularly useful for debugging and monitoring in a 
     * microservices architecture.
     */
    @Bean
    public GlobalFilter postGlobalFilter() {
        return (exchange, chain) -> {
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            	  HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
            	  String correlationId = filterUtils.getCorrelationId(requestHeaders);
            	  logger.debug("Adding the correlation id to the outbound headers. {}", correlationId);
                  exchange.getResponse().getHeaders().add(FilterUtils.CORRELATION_ID, correlationId);
                  logger.debug("Completing outgoing request for {}.", exchange.getRequest().getURI());
              }));
        };
    }
}
