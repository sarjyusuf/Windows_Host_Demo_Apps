package com.legacybridge.processor.config;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.ConnectionFactory;
import java.util.Arrays;

/**
 * JMS Configuration for ActiveMQ connectivity.
 * Configures the ConnectionFactory, JmsListenerContainerFactory, and JmsTemplate
 * for communicating with ActiveMQ broker at tcp://localhost:61616.
 */
@Configuration
@EnableJms
public class JmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(JmsConfig.class);

    @Value("${spring.activemq.broker-url:tcp://localhost:61616}")
    private String brokerUrl;

    /**
     * Creates an ActiveMQ ConnectionFactory configured for the local broker.
     * Trusts all packages for object message deserialization (demo purposes).
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        logger.info("Configuring ActiveMQ ConnectionFactory with broker URL: {}", brokerUrl);
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory();
        factory.setBrokerURL(brokerUrl);
        factory.setTrustedPackages(Arrays.asList(
                "com.legacybridge",
                "java.lang",
                "java.util"
        ));
        logger.debug("ActiveMQ ConnectionFactory created successfully");
        return factory;
    }

    /**
     * Configures the DefaultJmsListenerContainerFactory for processing messages.
     * Sets concurrency to 1-3 consumers for parallel processing capability.
     */
    @Bean
    public JmsListenerContainerFactory<?> jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        logger.info("Configuring JMS Listener Container Factory");
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency("1-3");
        factory.setErrorHandler(t -> {
            logger.error("Error in JMS listener: {}", t.getMessage(), t);
        });
        factory.setSessionTransacted(true);
        logger.debug("JMS Listener Container Factory configured with concurrency 1-3");
        return factory;
    }

    /**
     * Creates a JmsTemplate for sending messages to ActiveMQ queues.
     */
    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        logger.info("Creating JmsTemplate");
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setDeliveryPersistent(true);
        return template;
    }
}
