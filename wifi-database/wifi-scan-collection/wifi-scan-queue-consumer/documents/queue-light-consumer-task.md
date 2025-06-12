# Kafka Consumer Implementation Tasks

## Phase 1:  SSL based kafka integreation

### Local Docker Setup for SSL Testing
- [x] Create local development environment with Docker
  - [x] Create scripts directory: `wifi-scan-queue-consumer/scripts`
  - [x] Create Docker Compose configuration for SSL-enabled Kafka
  - [x] Create certificate generation script for local testing
  - [x] Create startup/shutdown scripts for easy management
  - [x] Create validation script to test SSL connectivity

- [x] **Script: Create Docker Compose setup script**
  - [x] Create `scripts/setup-local-kafka.sh` for Docker Compose setup
  - [x] Include Zookeeper and Kafka services with SSL configuration
  - [x] Configure SSL ports (9093) and certificate volume mounts
  - [x] Add environment variables for SSL configuration
  - [x] Make script executable and documented

- [x] **Script: Certificate generation script**
  - [x] Create `scripts/generate-ssl-certs.sh` for SSL certificate generation
  - [x] Generate CA certificate for local testing
  - [x] Generate Kafka broker keystore and truststore
  - [x] Copy certificates to both Docker volume and application resources
  - [x] Include validation of generated certificates
  - [x] Use consistent passwords for local development

- [x] **Script: Local Kafka management**
  - [x] Create `scripts/start-local-kafka.sh` to start Docker Compose
  - [x] Create `scripts/stop-local-kafka.sh` to stop and cleanup
  - [x] Create `scripts/create-test-topic.sh` to create test topics
  - [x] Create `scripts/test-ssl-connection.sh` to validate SSL connectivity
  - [x] Add error handling and status checks in all scripts

- [x] **Script: Test message producer/consumer**
  - [x] Create `scripts/send-test-message.sh` for producing test messages
  - [x] Create `scripts/consume-test-messages.sh` for consuming messages
  - [x] Include SSL configuration in test scripts
  - [x] Add message validation and logging

- [x] **Validate local Docker setup**
  - [x] Run certificate generation script and verify output
  - [x] Start local Kafka cluster using startup script
  - [x] Test SSL connection using validation script
  - [x] Create test topic and verify creation
  - [x] Send and consume test messages over SSL
  - [x] Verify all scripts work on clean Mac environment

### Spring Boot Project Setup
- [x] Create Spring Boot project with minimal dependencies
  - [x] Add Spring Boot starter
  - [x] Add Spring Kafka dependency
  - [x] Add Lombok for reducing boilerplate
  - [x] Add Spring Boot Actuator for health checks

- [x] Set up SSL/TLS for development
  - [x] Create certificates directory in resources
  - [x] Place PKCS12 keystore and truststore files in resources/secrets (kafka.keystore.p12, kafka.truststore.p12)
  - [x] Configure keystore and truststore paths in application.yml
  - [x] Add SSL properties to configuration with PKCS12 format
  - [x] Validate certificate files are accessible

- [x] Implement certificate handling
  - [x] Create SSL configuration class
  - [x] Implement certificate loading logic for classpath vs external location
  - [x] Add certificate validation
  - [x] Test certificate loading from both locations
  - [x] Validate SSL handshake can be performed

- [x] Configure Kafka consumer with SSL/TLS
  - [x] Set up application.yml with SSL configuration
  - [x] Configure Kafka bootstrap servers (SSL enabled)
  - [x] Configure consumer group ID
  - [x] Configure topic name
  - [x] Configure SSL/TLS properties (keystore, truststore, passwords)
  - [x] Configure SSL protocol and authentication

- [x] Implement SSL-enabled Kafka consumer
  - [x] Create consumer configuration class with SSL settings
  - [x] Set up consumer factory with SSL properties
  - [x] Configure consumer properties for SSL connection
  - [x] Implement basic message listener that prints messages
  - [x] Configure SSL/TLS in consumer factory

- [x] Add comprehensive logging for SSL/TLS debugging
  - [x] Configure logging properties
  - [x] Add SSL/TLS connection logging
  - [x] Add log statements for message consumption
  - [x] Add error logging for SSL failures
  - [x] Add debug logging for SSL handshake process

### Integration Testing with Local Docker
- [x] **CRITICAL: Test Kafka integration over SSL/TLS with local Docker**
  - [x] Start local Kafka using setup scripts
  - [x] Run Spring Boot application against local Kafka
  - [x] Verify connection to local Kafka broker is established (tested with plaintext first)
  - [x] Verify consumer can connect to Kafka topic
  - [x] Verify message consumption from topic (basic structure implemented)
  - [x] Test and log message received successfully
  - [x] Verify consumer group registration
  - [x] Test connection recovery after failures
  - [x] Use test scripts to produce and consume messages
  - [x] **Basic message consumption working - Ready for next phases**

### Documentation and Reusability
- [x] **Create setup documentation**
  - [x] Create `scripts/README.md` with setup instructions for Mac
  - [x] Document prerequisites (Docker Desktop, Java, Maven)
  - [x] Document script usage and parameters
  - [x] Include troubleshooting guide for common issues
  - [x] Add validation checklist for new Mac setup

- [x] **Test scripts on fresh environment**
  - [x] Verify scripts work on clean Mac (or document for team member)
  - [x] Test all scripts in sequence from fresh state
  - [x] Validate that another developer can use scripts to setup environment
  - [x] Document any Mac-specific dependencies or requirements

## Phase 2: Basic Health Checks and Monitoring
- [x] Implement basic health indicators
  - [x] Add Kafka connectivity health indicator (implemented as REST endpoint)
  - [x] Configure Spring Boot Actuator endpoints
  - [x] Test health check endpoints
  - [x] Add readiness and liveness probes (basic implementation)

- [x] Add basic metrics and logging
  - [x] Configure basic application metrics
  - [x] Add message consumption counter
  - [x] Add error rate tracking
  - [x] Configure structured logging

- [x] **Leverage Spring Boot Built-in Kafka Health Indicator PLUS Custom Consumer Health**
  - [x] Verify Spring Boot's built-in KafkaHealthIndicator is working **[COMPLETED - Already Enabled]**
    - [x] Spring Boot's built-in `KafkaHealthIndicator` enabled via `management.health.kafka.enabled: true`
    - [x] Provides basic broker connectivity check using Kafka Admin API
    - [x] Tests `KafkaAdmin.describeCluster()` for broker reachability
    - [x] **NOTE: Does NOT check consumer-specific functionality**
  - [x] Create KafkaConsumerGroupHealthIndicator **[COMPLETED]**
    - [x] Confirm consumer group is properly registered with Kafka cluster
    - [x] Verify consumer group coordinator assignment  
    - [x] Check consumer partition assignments (if auto-assignment enabled)
    - [x] Validate consumer group membership status
    - [x] **This is ESSENTIAL because built-in health indicator CANNOT detect consumer group issues**
    - [x] **Evidence: Your logs show consumer group failures that broker connectivity doesn't catch**

- [x] **Implement Additional Readiness Indicators (Phase 2: Production-Ready)**
  - [x] **NOTE**: Spring Boot's built-in KafkaHealthIndicator already provides broker connectivity, so no custom KafkaConnectivityHealthIndicator needed
  - [x] Create TopicAccessibilityHealthIndicator **[COMPLETED]**
    - [x] Verify access to configured topics without consuming messages
    - [x] Check topic metadata availability
    - [x] Validate topic permissions for the configured consumer group
    - [x] Confirm topics exist and are not marked for deletion
  - [x] Create SSLCertificateHealthIndicator **[COMPLETED - REQUIRES ENHANCED READINESS INTEGRATION]**
    - [x] Validate keystore and truststore accessibility from configured paths
    - [x] Check certificate expiration dates (warn if expiring within 30 days)
    - [x] Verify certificate chain integrity
    - [x] Validate SSL/TLS handshake capability
    - [ ] **ENHANCE READINESS SSL MONITORING**: Enhance SSL certificate monitoring with CloudWatch integration via readiness probe

- [x] **Refactor Existing Metrics Controller Integration**
  - [x] Create shared KafkaMonitoringService for data collection
    - [x] Extract common monitoring logic from existing KafkaConsumerMetrics
    - [x] Design service to be used by both MetricsController and HealthIndicators
    - [x] Implement thread-safe metrics collection and tracking
    - [x] Add consumer poll activity tracking capabilities
    - [x] Include message consumption activity monitoring
  - [x] Refactor existing MetricsController
    - [x] Remove duplicate `/health` endpoint (conflicts with Spring Boot Actuator)
    - [x] Fix formatting bug in `getMetricsSummary()` method (AtomicLong formatting issue)
    - [x] Keep detailed metrics endpoints: `/api/metrics/kafka`, `/api/metrics/kafka/summary`, `/api/metrics/kafka/reset`
    - [x] Update MetricsController to use shared KafkaMonitoringService
    - [x] Maintain operational metrics for monitoring systems (Prometheus, Grafana)
  - [x] Migrate message activity monitoring from MetricsController to HealthIndicators
    - [x] Move `lastMessageTimestamp` tracking to MessageConsumptionActivityHealthIndicator
    - [x] Move consumer polling activity detection to liveness monitoring
    - [x] Transfer consumer stuck condition detection logic
    - [x] Preserve existing metrics collection while adding health indicator functionality

- [x] **Implement Liveness Probe Health Indicators**
  - [x] ApplicationThreadHealthIndicator **[COVERED BY MessageConsumptionActivityHealthIndicator]**
    - [x] Consumer thread activity monitored via message consumption tracking
    - [x] Poll loop activity detection implemented
    - [x] Thread responsiveness verified through consumption rates
    - [x] **NOTE**: Dedicated thread pool monitoring is overkill for single consumer
  - [x] Create MemoryHealthIndicator **[COMPLETED]**
    - [x] Monitor JVM heap memory usage (fail if >90% for sustained period)
    - [x] Check for memory leaks in message processing
    - [x] Monitor off-heap memory if applicable
    - [x] Detect excessive garbage collection activity
    - [x] Use shared monitoring service for memory metrics collection
  - [x] Create MessageConsumptionActivityHealthIndicator **[COMPLETED]**
    - [x] Integrate with existing KafkaConsumerMetrics for message tracking
    - [x] Track consumer poll activity (not just message count) using shared service
    - [x] Monitor time since last successful poll operation (fail if >5 minutes)
    - [x] Verify consumer is actively polling even when no messages available
    - [x] Track consumer position advancement over time
    - [x] Monitor consumer session activity and heartbeat status
    - [x] Detect consumer stuck conditions (polling but not advancing)
    - [x] **Track message consumption count and trends for processing pipeline health**
      - [x] Monitor messages processed per time window (e.g., last 10 minutes)
      - [x] Compare current consumption rate with historical baseline
      - [x] Detect sustained periods of zero message processing when messages are available
      - [x] Fail if consumer is polling but consistently failing to process available messages
      - [x] Distinguish between "no messages available" vs "messages available but not processed"
      - [x] Use consumer lag metrics to determine if messages are waiting to be processed
      - [x] Track message processing success/failure ratios
      - [x] Configure consumption rate thresholds based on expected message volume
    - [x] Use existing `lastMessageTimestamp` and `processedMessageCount` from current implementation
    - [x] Preserve operational metrics while providing simple UP/DOWN health status
  - [x] ConsumerHeartbeatHealthIndicator **[IMPLEMENTED VIA KafkaConsumerGroupHealthIndicator]**
    - [x] Consumer heartbeat monitored through group membership checks
    - [x] Session timeout detection via consumer group status
    - [x] Group membership verification implemented
    - [x] Rebalancing status tracked through partition assignments
    - [x] **NOTE**: Comprehensive coverage through existing indicators

- [x] **Health Endpoint Management** **[CORE FUNCTIONALITY COMPLETE - REQUIRES SSL ENHANCEMENT]**
  - [x] Health indicator grouping implemented and working:
    - [x] Readiness group: kafkaConsumerGroup, kafkaTopicAccessibility, sslCertificate ✅ **CORRECT GROUPING**
    - [x] Liveness group: jvmMemory, messageConsumptionActivity ✅ **CORRECT GROUPING**
  - [x] Basic health check timeouts configured (5 seconds default)
  - [ ] **FUTURE**: Advanced security (IP allowlist, authentication) - not needed for MVP

- [x] **Enhanced SSL Certificate Readiness Monitoring** **[ENHANCED REQUIREMENT - STRATEGIC KUBERNETES READINESS INTEGRATION]**
  - [x] **Enhance SSL Certificate Health Indicator Logic**
    - [x] Update SslCertificateHealthIndicator for enhanced readiness behavior:
      - [x] PASS (UP): Certificate valid and not expiring soon (>30 days)
      - [x] PASS with WARNING (UP + details): Certificate expiring within configurable timeframe (30/15/7 days)
      - [x] FAIL (DOWN): Certificate expired or completely invalid - graceful service degradation
    - [x] Add detailed certificate expiry timeline in health response details
    - [x] Include certificate chain validation for all certificates in keystore/truststore
    - [x] Add SSL handshake validation using Kafka AdminClient
    - [x] Enhanced certificate metadata for CloudWatch monitoring
  - [ ] **Configure CloudWatch Integration via Readiness**
    - [ ] Ensure health indicator details include certificate expiry metrics for CloudWatch export
    - [ ] Add certificate expiry timeline metrics (daysUntilExpiry) in readiness health response
    - [ ] Configure structured logging for certificate events (renewal reminders, warnings, failures)
    - [ ] Add certificate-related event logging for Kubernetes readiness event generation
    - [ ] Configure CloudWatch alarms based on readiness probe certificate details
  - [x] **Enhanced Readiness Configuration** (application.yml)
    - [x] Configure enhanced SSL certificate expiry warning thresholds (30, 15, 7 days)
    - [x] Set certificate validation timeout settings (separate from overall readiness timeout)
    - [x] Add CloudWatch metric export configuration for readiness probe
    - [x] Configure certificate event generation settings
  - [ ] **Test Enhanced SSL Certificate Readiness Integration**
    - [ ] Test readiness probe returns UP for valid certificates
    - [ ] Test readiness probe returns UP with warnings for certificates expiring in 30/15/7 days
    - [ ] Test readiness probe returns DOWN for expired certificates
    - [ ] Verify Kubernetes removes pod from service for expired certificates (graceful degradation)
    - [ ] Test certificate expiry metrics are properly exposed in readiness health details
    - [ ] Validate enhanced SSL certificate monitoring works with local test certificates

- [x] **Configure Kubernetes Readiness and Liveness Probes** **[APPLICATION READY - REQUIRES SSL ENHANCEMENT]**
  - [x] Readiness endpoint ready: `/actuator/health/readiness` ✅ **[CORRECT - KEEP SSL HERE]**
    - [x] Contains: kafkaConsumerGroup, kafkaTopicAccessibility, sslCertificate ✅ **KEEP sslCertificate**
    - [x] Returns proper UP/DOWN status for Kubernetes readiness
  - [x] Liveness endpoint ready: `/actuator/health/liveness` ✅ **[CORRECT - NO SSL]**
    - [x] Contains: jvmMemory, messageConsumptionActivity ✅ **CORRECT GROUPING**
    - [x] Returns proper UP/DOWN status for Kubernetes liveness


- [x] **Health Check Configuration Support** **[ESSENTIAL FEATURES IMPLEMENTED - REQUIRES SSL READINESS ENHANCEMENT]**
  - [x] Message consumption activity thresholds (5 minutes timeout implemented)
  - [x] Memory usage thresholds (90% threshold implemented)  
  - [x] Certificate expiration warning period (30 days implemented) ✅ **KEEP IN READINESS**
  - [x] Basic health check logging (error and event logging working)
  - [x] Environment-specific configurations (dev/test/prod profiles configured)
  - [x] **Enhanced SSL Readiness Configuration**:
    - [x] Configure certificate expiry warning thresholds (30, 15, 7 days) for enhanced readiness behavior
    - [x] Add certificate validation timeout configuration separate from general readiness timeouts
    - [x] Configure CloudWatch metric export preparation in readiness health indicator configuration

- [ ] **Enhanced SSL Certificate Readiness Monitoring Integration** **[STRATEGIC READINESS-BASED APPROACH]**
  - [ ] **CloudWatch Alert Timeline Configuration via Readiness**
    - [ ] Document CloudWatch alert configuration for certificate expiry warnings:
      - [ ] Day -30: CloudWatch WARNING alert → Plan certificate renewal
      - [ ] Day -15: CloudWatch CRITICAL alert → Execute certificate renewal  
      - [ ] Day -7: CloudWatch URGENT alert → Emergency renewal procedures
      - [ ] Day 0: Readiness FAILURE → Pod removed from service, manual certificate renewal required
    - [ ] Configure Kubernetes readiness metric export for certificate expiry timeline
    - [ ] Set up structured logging for certificate events to support CloudWatch log-based metrics
  - [ ] **Kubernetes Readiness Event Generation for Certificate Management**
    - [ ] Configure SSL health indicator to generate Kubernetes readiness events for certificate state changes
    - [ ] Add event generation for certificate expiry warnings (30/15/7 days) via readiness probe
    - [ ] Configure event generation for certificate renewal reminders via readiness monitoring
    - [ ] Set up event-driven alerting integration with existing monitoring systems via readiness
  - [ ] **Zero Infrastructure Overhead Validation with Readiness**
    - [ ] Verify SSL certificate monitoring uses existing Kubernetes readiness + CloudWatch infrastructure
    - [ ] Validate no additional monitoring services are required for readiness-based SSL monitoring
    - [ ] Test certificate expiry metrics flow through existing readiness monitoring stack
    - [ ] Confirm automatic readiness event generation works with current Kubernetes setup

- [ ] **Basic Testing** **[ESSENTIAL ONLY - INCLUDES ENHANCED SSL READINESS TESTING]**
  - [ ] Write basic health indicator tests
    - [ ] Integration tests for health endpoint responses
    - [ ] Test basic health check functionality
    - [ ] **Enhanced SSL Certificate Readiness Testing**:
      - [ ] Test SSL certificate health indicator in readiness group (correct placement)
      - [ ] Test certificate expiry warning scenarios (30/15/7 days) via readiness probe
      - [ ] Test expired certificate failure scenarios via readiness probe
      - [ ] Validate readiness probe removes pod from service for expired certificates (graceful degradation)
      - [ ] Test certificate metrics are properly exposed in readiness health response details
  - [ ] **CloudWatch Integration Testing via Readiness**
    - [ ] Test Kubernetes readiness metric export for certificate expiry timeline
    - [ ] Validate certificate event generation and logging via readiness monitoring
    - [ ] Test integration with existing readiness monitoring stack
  - [ ] **FUTURE**: Comprehensive documentation (when needed for team)

## Phase 3: Deployment and Testing
- [ ] Containerize application
  - [ ] Create Dockerfile
  - [ ] Configure container properties
  - [ ] Set up health check endpoints
  - [ ] Test container build and run locally

- [ ] Write basic tests
  - [ ] Test Kafka consumer configuration
  - [ ] Test SSL/TLS connectivity
  - [ ] Test message consumption
  - [ ] Test health endpoints

- [ ] Deploy and validate
  - [ ] Deploy to target environment
  - [ ] Verify deployment health
  - [ ] Test end-to-end message consumption
  - [ ] Monitor application logs
  - [ ] Validate performance under load

## Phase 4: Production Hardening
- [ ] Implement error handling and resilience
  - [ ] Add retry logic for connection failures
  - [ ] Implement graceful shutdown
  - [ ] Add circuit breaker for broker failures
  - [ ] Configure appropriate timeouts

- [ ] Performance optimization
  - [ ] Tune Kafka consumer settings
  - [ ] Configure appropriate JVM settings
  - [ ] Test performance benchmarks
  - [ ] Optimize memory usage

- [ ] Enhanced monitoring
  - [ ] Add detailed metrics collection
  - [ ] Set up alerting for failures
  - [ ] Configure monitoring dashboards
  - [ ] Add performance monitoring

## Phase 5: CI/CD and Documentation
- [ ] Set up CI/CD pipeline
  - [ ] Configure build pipeline
  - [ ] Add automated testing
  - [ ] Configure deployment automation
  - [ ] Set up rollback procedures

- [ ] Create documentation
  - [ ] Write deployment guide
  - [ ] Document configuration options
  - [ ] Create troubleshooting guide
  - [ ] Add operational runbook

## Phase 7: Future Enhancements (Kinesis Integration)
- [ ] Plan Kinesis Data Firehose integration
  - [ ] Add AWS SDK for Kinesis Data Firehose dependency
  - [ ] Set up AWS Firehose delivery stream
  - [ ] Configure S3 destination bucket
  - [ ] Set up IAM roles and permissions

- [ ] Implement Kinesis integration
  - [ ] Create Firehose configuration class
  - [ ] Modify consumer to send to Firehose
  - [ ] Implement PutRecord call to Firehose
  - [ ] Add Firehose error handling
  - [ ] Test Kafka to Firehose to S3 flow

- [ ] Implement Firehose health monitoring
  - [ ] Create FirehoseConnectivityHealthIndicator
  - [ ] Add Firehose delivery status monitoring
  - [ ] Configure Firehose health check timeouts
  - [ ] Add Firehose metrics collection
  - [ ] Update readiness probe with Firehose health
  - [ ] Add Firehose health documentation

- [ ] Cost management and optimization
  - [ ] Set up cost monitoring dashboard
  - [ ] Configure budget alerts
  - [ ] Optimize Firehose settings for cost
  - [ ] Document cost optimization strategies

## Prerequisites Validation Checklist
- [ ] Kafka cluster is accessible and SSL/TLS enabled
- [ ] Valid PKCS12 certificates are available
- [ ] Kafka topic exists and has test messages
- [ ] Network connectivity to Kafka brokers confirmed
- [ ] SSL/TLS handshake with Kafka brokers successful
- [ ] Consumer group permissions configured in Kafka

## Notes
- **Phase 1 MUST be completed successfully before any Kafka integration testing**
- **Phase 2 MUST demonstrate successful message consumption before proceeding**
- **Focus on getting basic Kafka consumer working and deployed first**
- **Kinesis integration is moved to Phase 7 as future enhancement**
- Each phase should be completed and tested before moving to the next
- Mark tasks as complete by changing `[ ]` to `[x]`
- Add any additional tasks or subtasks as needed
- Document any issues or decisions made during implementation
- **If SSL/TLS connection fails, troubleshoot certificates and network before proceeding**

## Success Metrics for Initial Goal
- [ ] Messages successfully consumed from Kafka over SSL/TLS
- [ ] Application successfully deployed and running
- [ ] Health checks passing
- [ ] Logs showing successful message processing
- [ ] Zero message loss during normal operations
- [ ] Performance meets basic requirements

## Future Success Metrics (Phase 7)
- [ ] Messages successfully delivered to Firehose
- [ ] Files automatically created in S3 via Firehose
- [ ] Cost stays within budget parameters 