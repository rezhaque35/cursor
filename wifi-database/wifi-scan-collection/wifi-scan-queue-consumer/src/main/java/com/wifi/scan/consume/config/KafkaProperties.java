package com.wifi.scan.consume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Kafka SSL/TLS settings.
 * Maps the kafka.* properties from application.yml to type-safe Java objects.
 */
@Data
@Component
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    private String bootstrapServers;
    private Consumer consumer = new Consumer();
    private Topic topic = new Topic();
    private Ssl ssl = new Ssl();

    @Data
    public static class Consumer {
        private String groupId;
        private String autoOffsetReset = "latest";
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";
        private boolean enableAutoCommit = true;
        private String autoCommitInterval = "1000ms";
        private int maxPollRecords = 500;
        private String sessionTimeout = "30000ms";
        private String heartbeatInterval = "3000ms";
        
        // Optimized polling configurations for continuous operation
        private String maxPollInterval = "300000ms";        // 5 minutes
        private int fetchMinBytes = 1;                       // 1 byte for immediate response
        private String fetchMaxWait = "500ms";              // Maximum wait time for fetch
        private String requestTimeout = "30000ms";          // Request timeout
        private String retryBackoff = "1000ms";             // Retry backoff time
        private String connectionsMaxIdle = "540000ms";     // 9 minutes
        private String metadataMaxAge = "300000ms";         // 5 minutes
    }

    @Data
    public static class Topic {
        private String name;
        private int partitions = 3;
        private int replicationFactor = 1;
    }

    @Data
    public static class Ssl {
        private boolean enabled = false;
        private String protocol = "SSL";
        private Keystore keystore = new Keystore();
        private Truststore truststore = new Truststore();
        private String keyPassword;
    }

    @Data
    public static class Keystore {
        private String location;
        private String password;
        private String type = "JKS";
    }

    @Data
    public static class Truststore {
        private String location;
        private String password;
        private String type = "JKS";
    }
} 