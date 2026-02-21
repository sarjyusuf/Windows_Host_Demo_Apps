package com.legacybridge.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Document Processor - Spring Boot application that listens on ActiveMQ JMS queue
 * "document.process" for document processing requests. Orchestrates calls to Tika
 * for text extraction, Lucene for indexing, and the REST API for status updates.
 *
 * Runs on port 8083.
 */
@SpringBootApplication
public class ProcessorApplication {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorApplication.class);

    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("Starting LegacyBridge Document Processor");
        logger.info("========================================");
        logger.info("JMS Queue: document.process");
        logger.info("HTTP Port: 8083");
        logger.info("Tika endpoint: http://localhost:8081/parse");
        logger.info("Lucene endpoint: http://localhost:8082/index");
        logger.info("REST API endpoint: http://localhost:8080/api/documents");

        SpringApplication app = new SpringApplication(ProcessorApplication.class);
        app.run(args);

        logger.info("Document Processor started successfully and listening for messages.");
    }
}
