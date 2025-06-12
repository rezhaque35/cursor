package com.wifi.scan.consume.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for SSL/TLS enabled Kafka connectivity.
 * Configures consumer factory and listener container factory.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfiguration {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private SslConfiguration sslConfiguration;

    /**
     * Creates Kafka consumer factory with SSL/TLS configuration.
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return consumerFactory(kafkaProperties, sslConfiguration);
    }

    /**
     * Creates Kafka consumer factory with provided properties and SSL configuration.
     */
    public ConsumerFactory<String, String> consumerFactory(KafkaProperties kafkaProperties, SslConfiguration sslConfiguration) {
        log.debug("Creating Kafka consumer factory");
        
        Map<String, Object> consumerProps = buildConsumerProperties(kafkaProperties);
        
        // Merge SSL properties if enabled
        if (kafkaProperties.getSsl().isEnabled()) {
            Map<String, Object> mergedProps = mergeConsumerProperties(consumerProps, kafkaProperties, sslConfiguration);
            log.info("Kafka consumer factory created with SSL enabled");
            return new DefaultKafkaConsumerFactory<>(mergedProps);
        } else {
            log.info("Kafka consumer factory created without SSL");
            return new DefaultKafkaConsumerFactory<>(consumerProps);
        }
    }

    /**
     * Creates Kafka listener container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        return kafkaListenerContainerFactory(consumerFactory());
    }

    /**
     * Creates Kafka listener container factory with provided consumer factory.
     */
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        
        log.debug("Creating Kafka listener container factory");
        
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        
        // Configure container properties
        ContainerProperties containerProps = factory.getContainerProperties();
        containerProps.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProps.setSyncCommits(true);
        
        log.info("Kafka listener container factory created successfully");
        return factory;
    }

    /**
     * Builds base consumer properties from Kafka configuration.
     */
    public Map<String, Object> buildConsumerProperties(KafkaProperties kafkaProperties) {
        log.debug("Building Kafka consumer properties");
        
        if (kafkaProperties.getBootstrapServers() == null || kafkaProperties.getBootstrapServers().isEmpty()) {
            throw new IllegalArgumentException("Kafka bootstrap servers must be provided");
        }
        
        if (kafkaProperties.getConsumer().getGroupId() == null || kafkaProperties.getConsumer().getGroupId().isEmpty()) {
            throw new IllegalArgumentException("Kafka consumer group ID must be provided");
        }
        
        Map<String, Object> props = new HashMap<>();
        
        // Basic consumer configuration
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.getConsumer().getAutoOffsetReset());
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Disabled for manual acknowledgment
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        // Timeout configurations
        if (kafkaProperties.getConsumer().getSessionTimeout() != null) {
            props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getSessionTimeout()));
        }
        
        if (kafkaProperties.getConsumer().getHeartbeatInterval() != null) {
            props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getHeartbeatInterval()));
        }
        
        if (kafkaProperties.getConsumer().getAutoCommitInterval() != null) {
            props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, parseTimeoutMilliseconds(kafkaProperties.getConsumer().getAutoCommitInterval()));
        }
        
        // Performance configurations
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, kafkaProperties.getConsumer().getMaxPollRecords());
        
        log.debug("Consumer properties built: bootstrap.servers={}, group.id={}, auto.offset.reset={}", 
                kafkaProperties.getBootstrapServers(), 
                kafkaProperties.getConsumer().getGroupId(),
                kafkaProperties.getConsumer().getAutoOffsetReset());
        
        return props;
    }

    /**
     * Merges consumer properties with SSL properties.
     */
    public Map<String, Object> mergeConsumerProperties(Map<String, Object> baseProps, 
            KafkaProperties kafkaProperties, SslConfiguration sslConfiguration) {
        
        log.debug("Merging consumer properties with SSL configuration");
        
        Map<String, Object> mergedProps = new HashMap<>(baseProps);
        
        if (kafkaProperties.getSsl().isEnabled()) {
            Map<String, Object> sslProps = sslConfiguration.buildSslProperties(kafkaProperties);
            mergedProps.putAll(sslProps);
            
            log.debug("SSL properties merged: security.protocol={}", sslProps.get("security.protocol"));
        }
        
        return mergedProps;
    }

    /**
     * Parses timeout string (e.g., "30000ms") to milliseconds integer.
     */
    public int parseTimeoutMilliseconds(String timeoutString) {
        if (timeoutString == null || timeoutString.isEmpty()) {
            throw new IllegalArgumentException("Timeout string cannot be null or empty");
        }
        
        try {
            if (timeoutString.endsWith("ms")) {
                return Integer.parseInt(timeoutString.substring(0, timeoutString.length() - 2));
            } else if (timeoutString.endsWith("s")) {
                return Integer.parseInt(timeoutString.substring(0, timeoutString.length() - 1)) * 1000;
            } else {
                // Assume milliseconds if no unit specified
                return Integer.parseInt(timeoutString);
            }
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid timeout format: " + timeoutString + ". Expected format: '30000ms' or '30s'");
        }
    }
} 