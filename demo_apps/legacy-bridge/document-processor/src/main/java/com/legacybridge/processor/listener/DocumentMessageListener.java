package com.legacybridge.processor.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacybridge.processor.service.ProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

/**
 * JMS Message Listener that receives document processing requests from the
 * "document.process" ActiveMQ queue. Each message contains JSON with document
 * details (id, name, content as base64). Delegates actual processing to
 * ProcessingService.
 */
@Component
public class DocumentMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(DocumentMessageListener.class);

    private final ProcessingService processingService;
    private final ObjectMapper objectMapper;

    @Autowired
    public DocumentMessageListener(ProcessingService processingService) {
        this.processingService = processingService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Listens on the "document.process" queue for incoming document processing requests.
     * Expects a TextMessage containing JSON with fields: id, name, content (base64 encoded).
     *
     * @param message the JMS message received from the queue
     */
    @JmsListener(destination = "document.process", containerFactory = "jmsListenerContainerFactory")
    public void onMessage(Message message) {
        logger.info("Received message from document.process queue");
        logger.debug("Message type: {}", message.getClass().getSimpleName());

        try {
            if (!(message instanceof TextMessage)) {
                logger.warn("Received non-text message, ignoring. Type: {}", message.getClass().getName());
                return;
            }

            TextMessage textMessage = (TextMessage) message;
            String jsonPayload = textMessage.getText();
            logger.debug("Message payload length: {} characters", jsonPayload.length());
            logger.debug("Message payload: {}", jsonPayload);

            // Parse the JSON payload
            JsonNode documentNode = objectMapper.readTree(jsonPayload);

            String documentId = documentNode.get("id").asText();
            String documentName = documentNode.get("name").asText();
            String contentBase64 = documentNode.get("content").asText();

            logger.info("Processing document - ID: {}, Name: {}, Content size: {} chars (base64)",
                    documentId, documentName, contentBase64.length());

            // Delegate to the processing service
            processingService.processDocument(documentId, documentName, contentBase64);

            logger.info("Successfully processed document ID: {}", documentId);

        } catch (JMSException e) {
            logger.error("JMS error while reading message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read JMS message", e);
        } catch (Exception e) {
            logger.error("Error processing document message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process document", e);
        }
    }
}
