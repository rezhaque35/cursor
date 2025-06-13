package com.wifi.scan.consume.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * SSL configuration component for Kafka SSL/TLS connectivity.
 * Handles certificate loading from both classpath and external locations.
 */
@Slf4j
@Component
public class SslConfiguration {

    private final ResourceLoader resourceLoader;

    public SslConfiguration(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public SslConfiguration() {
        this.resourceLoader = null;
    }

    /**
     * Loads keystore from the configured location.
     */
    public KeyStore loadKeystore(KafkaProperties.Keystore keystoreConfig) {
        try {
            log.debug("Loading keystore from location: {}", keystoreConfig.getLocation());
            
            String location = keystoreConfig.getLocation();
            String password = keystoreConfig.getPassword();
            String type = keystoreConfig.getType();

            if (location.startsWith("classpath:")) {
                return loadKeystoreFromClasspath(location, password, type);
            } else {
                return loadKeystoreFromExternalPath(location, password, type);
            }
        } catch (Exception e) {
            log.error("Failed to load keystore from location: {}", keystoreConfig.getLocation(), e);
            throw new RuntimeException("Failed to load keystore", e);
        }
    }

    /**
     * Loads truststore from the configured location.
     */
    public KeyStore loadTruststore(KafkaProperties.Truststore truststoreConfig) {
        try {
            log.debug("Loading truststore from location: {}", truststoreConfig.getLocation());
            
            String location = truststoreConfig.getLocation();
            String password = truststoreConfig.getPassword();
            String type = truststoreConfig.getType();

            if (location.startsWith("classpath:")) {
                return loadTruststoreFromClasspath(location, password, type);
            } else {
                return loadTruststoreFromExternalPath(location, password, type);
            }
        } catch (Exception e) {
            log.error("Failed to load truststore from location: {}", truststoreConfig.getLocation(), e);
            throw new RuntimeException("Failed to load truststore", e);
        }
    }

    /**
     * Loads keystore from external file path.
     */
    public KeyStore loadKeystoreFromExternalPath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        log.debug("Loading keystore from external path: {}", location);
        
        if (resourceLoader != null) {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new RuntimeException("Keystore file not found at location: " + location);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        } else {
            // Direct file system access
            Path keystorePath = Paths.get(location);
            if (!Files.exists(keystorePath)) {
                throw new RuntimeException("Keystore file not found at location: " + location);
            }
            try (InputStream inputStream = Files.newInputStream(keystorePath)) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        }
    }

    /**
     * Loads truststore from external file path.
     */
    public KeyStore loadTruststoreFromExternalPath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        log.debug("Loading truststore from external path: {}", location);
        
        if (resourceLoader != null) {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new RuntimeException("Truststore file not found at location: " + location);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        } else {
            // Direct file system access
            Path truststorePath = Paths.get(location);
            if (!Files.exists(truststorePath)) {
                throw new RuntimeException("Truststore file not found at location: " + location);
            }
            try (InputStream inputStream = Files.newInputStream(truststorePath)) {
                return loadKeystoreFromStream(inputStream, password, type);
            }
        }
    }

    /**
     * Creates SSL context from Kafka properties.
     */
    public SSLContext createSSLContext(KafkaProperties kafkaProperties) {
        try {
            log.debug("Creating SSL context for Kafka connection");
            
            KafkaProperties.Ssl sslConfig = kafkaProperties.getSsl();
            
            // Load keystore
            KeyStore keyStore = loadKeystore(sslConfig.getKeystore());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, sslConfig.getKeyPassword().toCharArray());
            KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

            // Load truststore  
            KeyStore trustStore = loadTruststore(sslConfig.getTruststore());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

            // Create SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            
            log.info("SSL context created successfully");
            return sslContext;
            
        } catch (Exception e) {
            log.error("Failed to create SSL context", e);
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    /**
     * Validates SSL certificates are accessible.
     */
    public void validateCertificates(KafkaProperties kafkaProperties) {
        KafkaProperties.Ssl sslConfig = kafkaProperties.getSsl();
        
        if (!sslConfig.isEnabled()) {
            throw new IllegalStateException("SSL is not enabled but certificate validation was requested");
        }
        
        try {
            log.debug("Validating SSL certificates");
            
            // Validate keystore
            KeyStore keyStore = loadKeystore(sslConfig.getKeystore());
            log.debug("Keystore loaded and validated successfully");
            
            // Validate truststore
            KeyStore trustStore = loadTruststore(sslConfig.getTruststore());
            log.debug("Truststore loaded and validated successfully");
            
            log.info("SSL certificate validation completed successfully");
            
        } catch (Exception e) {
            log.error("SSL certificate validation failed", e);
            throw new RuntimeException("SSL certificate validation failed", e);
        }
    }

    /**
     * Builds SSL properties map for Kafka consumer configuration.
     */
    public Map<String, Object> buildSslProperties(KafkaProperties kafkaProperties) {
        Map<String, Object> sslProps = new HashMap<>();
        
        KafkaProperties.Ssl sslConfig = kafkaProperties.getSsl();
        
        if (!sslConfig.isEnabled()) {
            log.debug("SSL is disabled, returning empty SSL properties");
            return sslProps;
        }
        
        log.debug("Building SSL properties for Kafka consumer");
        
        sslProps.put("security.protocol", sslConfig.getProtocol());
        sslProps.put("ssl.keystore.location", resolveKeystoreLocation(sslConfig.getKeystore().getLocation()));
        sslProps.put("ssl.keystore.password", sslConfig.getKeystore().getPassword());
        sslProps.put("ssl.keystore.type", sslConfig.getKeystore().getType());
        sslProps.put("ssl.truststore.location", resolveTruststoreLocation(sslConfig.getTruststore().getLocation()));
        sslProps.put("ssl.truststore.password", sslConfig.getTruststore().getPassword());
        sslProps.put("ssl.truststore.type", sslConfig.getTruststore().getType());
        sslProps.put("ssl.key.password", sslConfig.getKeyPassword());
        
        log.debug("SSL properties configured: security.protocol={}, keystore.type={}, truststore.type={}", 
                sslConfig.getProtocol(), sslConfig.getKeystore().getType(), sslConfig.getTruststore().getType());
        
        return sslProps;
    }

    /**
     * Determines the appropriate certificate location.
     */
    public String determineCertificateLocation(String classpathLocation, String externalLocation) {
        // Priority: check external location first, then classpath
        if (externalLocation != null && !externalLocation.isEmpty()) {
            Path externalPath = Paths.get(externalLocation);
            if (Files.exists(externalPath)) {
                log.debug("Using external certificate location: {}", externalLocation);
                return externalLocation;
            }
        }
        
        if (classpathLocation != null && !classpathLocation.isEmpty()) {
            if (classpathLocation.startsWith("classpath:")) {
                String resourcePath = classpathLocation.substring("classpath:".length());
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (resource.exists()) {
                    log.debug("Using classpath certificate location: {}", classpathLocation);
                    return classpathLocation;
                }
            }
        }
        
        throw new RuntimeException("No valid certificate location found");
    }

    private KeyStore loadKeystoreFromClasspath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        String resourcePath = location.substring("classpath:".length());
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            throw new RuntimeException("Keystore resource not found in classpath: " + resourcePath);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            return loadKeystoreFromStream(inputStream, password, type);
        }
    }

    private KeyStore loadTruststoreFromClasspath(String location, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        String resourcePath = location.substring("classpath:".length());
        ClassPathResource resource = new ClassPathResource(resourcePath);
        
        if (!resource.exists()) {
            throw new RuntimeException("Truststore resource not found in classpath: " + resourcePath);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            return loadKeystoreFromStream(inputStream, password, type);
        }
    }

    private KeyStore loadKeystoreFromStream(InputStream inputStream, String password, String type) 
            throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException {
        
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(inputStream, password.toCharArray());
        return keyStore;
    }

    private String resolveKeystoreLocation(String location) {
        return resolveResourceLocation(location, "keystore");
    }

    private String resolveTruststoreLocation(String location) {
        return resolveResourceLocation(location, "truststore");
    }

    /**
     * Resolves a resource location using Spring's ResourceLoader abstraction.
     * This method is agnostic to whether the location is a classpath resource, file system path, or URL.
     * 
     * Supported prefixes:
     * - classpath: for classpath resources
     * - file: for file system resources  
     * - http:/https: for URL resources
     * - No prefix: treated as file system path (relative to working directory)
     */
    private String resolveResourceLocation(String location, String resourceType) {
        try {
            log.debug("Resolving {} location: {}", resourceType, location);
            
            Resource resource;
            if (resourceLoader != null) {
                // Use injected ResourceLoader when available
                resource = resourceLoader.getResource(normalizeResourceLocation(location));
            } else {
                // Fallback: create appropriate resource based on location prefix
                if (location.startsWith("classpath:")) {
                    resource = new ClassPathResource(location.substring("classpath:".length()));
                } else {
                    // For file paths, use DefaultResourceLoader with proper file: prefix
                    String normalizedLocation = normalizeResourceLocation(location);
                    resource = new org.springframework.core.io.DefaultResourceLoader().getResource(normalizedLocation);
                }
            }
            
            if (!resource.exists()) {
                throw new RuntimeException(String.format("%s resource not found: %s", 
                    resourceType, location));
            }
            
            // Try to get the file directly first (works for file system and exploded classpath)
            try {
                File file = resource.getFile();
                String absolutePath = file.getAbsolutePath();
                log.debug("Using {} file directly: {}", resourceType, absolutePath);
                return absolutePath;
            } catch (IOException e) {
                // Resource is not a file (e.g., inside JAR), extract to temporary file
                log.debug("Resource {} is not accessible as file, extracting to temporary location", location);
                return extractResourceToTempFile(resource, resourceType).toString();
            }
            
        } catch (Exception e) {
            log.error("Failed to resolve {} location: {}", resourceType, location, e);
            throw new RuntimeException(String.format("Failed to resolve %s location: %s", resourceType, location), e);
        }
    }

    /**
     * Normalizes a resource location to ensure proper Spring Resource prefixes.
     * This ensures that file system paths have the 'file:' prefix for proper resolution.
     */
    private String normalizeResourceLocation(String location) {
        if (location.startsWith("classpath:") || 
            location.startsWith("file:") || 
            location.startsWith("http:") || 
            location.startsWith("https:")) {
            // Already has a proper prefix
            return location;
        }
        
        // Assume it's a file system path and add file: prefix
        return "file:" + location;
    }

    /**
     * Extracts a Spring Resource to a temporary file.
     * This handles resources that cannot be accessed as direct files (e.g., inside JARs).
     */
    private Path extractResourceToTempFile(Resource resource, String prefix) {
        try {
            // Create temporary file
            Path tempFile = Files.createTempFile(prefix, ".p12");
            
            // Copy resource content to temporary file
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Delete temp file on JVM exit
            tempFile.toFile().deleteOnExit();
            
            log.debug("Extracted resource {} to temporary file: {}", resource.getDescription(), tempFile);
            return tempFile;
            
        } catch (IOException e) {
            log.error("Failed to extract resource to temporary file: {}", resource.getDescription(), e);
            throw new RuntimeException("Failed to extract resource: " + resource.getDescription(), e);
        }
    }

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use {@link #extractResourceToTempFile(Resource, String)} instead
     */
    @Deprecated
    private Path extractResourceToTempFile(ClassPathResource resource, String prefix, String suffix) {
        try {
            if (!resource.exists()) {
                throw new RuntimeException("Resource not found in classpath: " + resource.getPath());
            }
            
            // Create temporary file
            Path tempFile = Files.createTempFile(prefix, suffix);
            
            // Copy resource content to temporary file
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Delete temp file on JVM exit
            tempFile.toFile().deleteOnExit();
            
            log.debug("Extracted classpath resource {} to temporary file: {}", resource.getPath(), tempFile);
            return tempFile;
            
        } catch (IOException e) {
            log.error("Failed to extract classpath resource to temporary file: {}", resource.getPath(), e);
            throw new RuntimeException("Failed to extract classpath resource: " + resource.getPath(), e);
        }
    }
} 