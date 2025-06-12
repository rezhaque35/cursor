package com.wifi.scan.consume.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Admin client configuration for health monitoring and administrative operations.
 */
@Slf4j
@Configuration
public class KafkaAdminConfiguration {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private SslConfiguration sslConfiguration;

    /**
     * Creates Kafka AdminClient for health monitoring and administrative operations.
     */
    @Bean
    public AdminClient kafkaAdminClient() {
        log.debug("Creating Kafka AdminClient");
        
        Map<String, Object> adminProps = new HashMap<>();
        adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        adminProps.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        adminProps.put(AdminClientConfig.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG, 5000);
        
        // Add SSL properties if enabled
        if (kafkaProperties.getSsl().isEnabled()) {
            Map<String, Object> sslProps = sslConfiguration.buildSslProperties(kafkaProperties);
            adminProps.putAll(sslProps);
            log.debug("AdminClient configured with SSL enabled");
        }
        
        log.info("Kafka AdminClient created successfully");
        return AdminClient.create(adminProps);
    }
} 