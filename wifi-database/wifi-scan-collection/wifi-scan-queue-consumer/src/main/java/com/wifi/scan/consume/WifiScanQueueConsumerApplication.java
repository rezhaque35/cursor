package com.wifi.scan.consume;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * Main Spring Boot application class for WiFi Scan Queue Consumer.
 * 
 * This application consumes messages from Apache Kafka with SSL/TLS support
 * and processes WiFi scan data for further analysis and storage.
 */
@Slf4j
@SpringBootApplication
public class WifiScanQueueConsumerApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(WifiScanQueueConsumerApplication.class);
        
        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down WiFi Scan Queue Consumer application gracefully...");
        }));
        
        try {
            app.run(args);
        } catch (Exception e) {
            log.error("Failed to start WiFi Scan Queue Consumer application", e);
            System.exit(1);
        }
    }

    /**
     * Application ready event handler.
     * Logs application startup information including active profiles and configuration.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        
        log.info("=========================================");
        log.info("WiFi Scan Queue Consumer Started Successfully");
        log.info("=========================================");
        log.info("Application Name: {}", env.getProperty("spring.application.name"));
        log.info("Server Port: {}", env.getProperty("server.port", "8080"));
        log.info("Kafka Bootstrap Servers: {}", env.getProperty("kafka.bootstrap-servers"));
        log.info("Kafka Consumer Group: {}", env.getProperty("kafka.consumer.group-id"));
        log.info("Kafka Topic: {}", env.getProperty("kafka.topic.name"));
        log.info("SSL Enabled: {}", env.getProperty("kafka.ssl.enabled", "false"));
        log.info("Management Endpoints: {}", env.getProperty("management.endpoints.web.exposure.include"));
        log.info("=========================================");
        log.info("Application is ready to consume messages from Kafka");
        log.info("Health check available at: http://{}:{}/frisco-location-wifi-scan-vmb-consumer/health", 
                env.getProperty("server.host", "localhost"),
                env.getProperty("server.port", "8080"));
        log.info("=========================================");
    }
} 