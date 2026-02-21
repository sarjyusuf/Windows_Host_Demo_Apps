package com.legacybridge.api.service;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Singleton service for sending JMS messages via Apache ActiveMQ.
 * Creates a ConnectionFactory pointing to the ActiveMQ broker at tcp://localhost:61616.
 * Properly manages Connection, Session, and MessageProducer lifecycle for each message send.
 */
public class JmsService {

    private static final Logger logger = LoggerFactory.getLogger(JmsService.class);

    private static final String BROKER_URL = "tcp://localhost:61616";
    private static final String BROKER_USERNAME = "admin";
    private static final String BROKER_PASSWORD = "admin";

    private static JmsService instance;
    private ActiveMQConnectionFactory connectionFactory;

    private JmsService() {
        logger.info("Initializing JMS Service with broker URL: {}", BROKER_URL);
        connectionFactory = new ActiveMQConnectionFactory(BROKER_USERNAME, BROKER_PASSWORD, BROKER_URL);

        // Configure trusted packages for serialization
        connectionFactory.setTrustAllPackages(true);

        logger.info("JMS ConnectionFactory created for broker: {}", BROKER_URL);
    }

    /**
     * Returns the singleton instance of JmsService.
     */
    public static synchronized JmsService getInstance() {
        if (instance == null) {
            instance = new JmsService();
        }
        return instance;
    }

    /**
     * Sends a text message to the specified JMS queue.
     * Creates a new Connection, Session, and MessageProducer for each send operation,
     * ensuring proper resource cleanup.
     *
     * @param queueName   the name of the destination queue
     * @param messageBody the text content of the message
     * @throws JMSException if there is an error communicating with the broker
     */
    public void sendMessage(String queueName, String messageBody) throws JMSException {
        Connection connection = null;
        Session session = null;
        MessageProducer producer = null;

        try {
            logger.debug("Sending JMS message to queue '{}': {}", queueName,
                    messageBody.length() > 200 ? messageBody.substring(0, 200) + "..." : messageBody);

            // Create connection
            connection = connectionFactory.createConnection();
            connection.start();

            // Create session (non-transacted, auto-acknowledge)
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Create the destination queue
            Destination destination = session.createQueue(queueName);

            // Create producer
            producer = session.createProducer(destination);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);

            // Create and send message
            TextMessage textMessage = session.createTextMessage(messageBody);
            textMessage.setStringProperty("source", "rest-api");
            textMessage.setLongProperty("timestamp", System.currentTimeMillis());

            producer.send(textMessage);

            logger.info("JMS message sent to queue '{}' (message ID: {})",
                    queueName, textMessage.getJMSMessageID());

        } catch (JMSException e) {
            logger.error("Failed to send JMS message to queue '{}': {}", queueName, e.getMessage());
            throw e;
        } finally {
            // Close resources in reverse order
            closeQuietly(producer);
            closeQuietly(session);
            closeQuietly(connection);
        }
    }

    private void closeQuietly(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (JMSException e) {
                logger.warn("Error closing JMS MessageProducer", e);
            }
        }
    }

    private void closeQuietly(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException e) {
                logger.warn("Error closing JMS Session", e);
            }
        }
    }

    private void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                logger.warn("Error closing JMS Connection", e);
            }
        }
    }
}
