# Kafka to Firehose Consumer Application Requirements

## Project Overview
A Spring Boot application that consumes messages from Apache Kafka (with SSL/TLS security) and delivers them to AWS Kinesis Data Firehose, which automatically handles buffering, batching, and writing to S3.

## Technical Requirements

### Core Technologies
- Java 21
- Spring Boot 3.2.x
- Apache Kafka 3.6.x
- AWS SDK for Java 2.x (Kinesis Data Firehose)
- Maven for dependency management

### Security Requirements
1. Kafka SSL/TLS Configuration
   - PKCS12 format for certificates
   - Support for both development and production certificate locations
   - Development: Certificates stored in classpath
   - Production: Certificates mounted in known location
   - Secure handling of keystore/truststore passwords

2. AWS Firehose Security
   - IAM role-based access
   - Encryption at rest and in transit
   - Secure credential management

### Functional Requirements

1. Kafka Consumer
   - Multi-threaded message consumption
   - Configurable consumer groups
   - Manual acknowledgment mode
   - Error handling and retry mechanisms
   - Offset management
   - SSL/TLS connection support

2. Firehose Integration
   - Direct message delivery to Kinesis Data Firehose
   - Configurable delivery stream name
   - Error handling for Firehose failures
   - Retry mechanisms for failed deliveries
   - Message format validation
   - Batch delivery optimization
   - Support for multiple delivery streams

3. Message Processing
   - Message validation and sanitization
   - Optional message transformation
   - Error message handling
   - Dead letter queue for failed messages
   - Message deduplication (if required)

### Non-Functional Requirements

1. Performance
   - High throughput message processing
   - Low latency message delivery
   - Efficient memory usage
   - Configurable thread pool sizes
   - Optimized Firehose delivery

2. Scalability
   - Horizontal scaling support
   - Configurable consumer threads
   - Dynamic throughput adjustment
   - Efficient resource utilization

3. Reliability
   - Fault tolerance
   - Message delivery guarantees
   - Error recovery mechanisms
   - Data consistency checks
   - Monitoring and alerting

4. Monitoring
   - Health check endpoints
   - Metrics collection
   - Performance monitoring
   - Error tracking
   - Firehose delivery status monitoring
   - Comprehensive Spring Boot Actuator health indicators (detailed requirements below)

### Health Indicator Requirements

#### Readiness Probe Requirements
**Purpose**: Determine if the application is ready to receive traffic and process messages

##### Core Readiness Checks:
1. **Spring Boot Built-in Kafka Health Indicator** *(Already Enabled)*
   - Spring Boot's built-in `KafkaHealthIndicator` provides basic broker connectivity checking
   - Uses Kafka Admin API `describeCluster()` for broker reachability testing
   - Enabled via `management.health.kafka.enabled: true` (already configured)
   - Provides cluster ID, node count, and basic connection status
   - **Note**: Does NOT check consumer-specific functionality or SSL certificates

2. **Kafka Consumer Group Registration Health Indicator**
   - Confirm consumer group is properly registered with Kafka cluster
   - Verify consumer group coordinator assignment
   - Check that consumer has been assigned partitions (if auto-assignment enabled)
   - Validate consumer group membership status

3. **Topic Accessibility Health Indicator**
   - Verify access to configured topics without consuming messages
   - Check topic metadata availability
   - Validate topic permissions for the configured consumer group
   - Confirm topics exist and are not marked for deletion

4. **Enhanced SSL/TLS Certificate Health Indicator**
   - **Strategic Integration with Kubernetes Readiness Monitoring**: Leverage readiness probe for SSL certificate validation and CloudWatch integration
   - **CloudWatch Integration via Readiness**: Use Kubernetes readiness metrics flowing to CloudWatch for automated certificate expiry alerting
   - **Certificate Expiry Timeline Management**:
     - PASS (UP): Certificate valid and not expiring soon (>30 days)
     - PASS with WARNING (UP + details): Certificate expiring within configurable timeframe (30/15/7 days)
     - FAIL (DOWN): Certificate expired or completely invalid - removes pod from service traffic
   - **Proactive Alert Timeline via Readiness**:
     - 30 days: CloudWatch warning alert → Plan certificate renewal
     - 15 days: CloudWatch critical alert → Execute certificate renewal
     - 7 days: CloudWatch urgent alert → Emergency certificate renewal procedures
     - 0 days (expired): Readiness failure → Pod removed from service, manual certificate renewal
   - **Validate keystore and truststore accessibility from configured paths**
   - **Check certificate chain integrity and SSL/TLS handshake capability**
   - **Zero Infrastructure Overhead**: Uses existing Kubernetes readiness probe and CloudWatch monitoring stack
   - **Graceful Degradation**: Pod removed from traffic but stays running, allowing time for certificate renewal

##### Readiness Configuration Requirements:
- Configurable timeout for each health check (default: 10 seconds total)
- Configurable retry attempts for transient failures
- Ability to disable specific health checks via configuration
- Fail-fast behavior when critical dependencies are unavailable
- Health check caching to prevent excessive broker calls (cache TTL: 30 seconds)
- **Enhanced SSL Certificate Configuration**:
  - Configurable warning thresholds (default: 30, 15, 7 days before expiry)
  - Configurable certificate check intervals within readiness probe frequency
  - Certificate validation timeout settings (separate from overall readiness timeout)
  - Enable/disable certificate expiry warnings vs immediate failures
- **CloudWatch Integration Configuration**:
  - Kubernetes readiness metric export configuration for certificate expiry timeline
  - CloudWatch alert threshold configuration for certificate warnings
  - Event-based alerting setup for certificate state changes via readiness probe

#### Liveness Probe Requirements
**Purpose**: Determine if the application is still running and healthy

##### Core Liveness Checks:
1. **Application Thread Health Indicator**
   - Monitor Kafka consumer thread pool status
   - Detect deadlocked or hanging consumer threads
   - Verify consumer poll loop is active and responsive
   - Check for consumer thread exceptions or crash detection

2. **Memory Health Indicator**
   - Monitor JVM heap memory usage (fail if >90% for sustained period)
   - Check for memory leaks in message processing
   - Monitor off-heap memory if applicable
   - Detect excessive garbage collection activity

3. **Message Consumption Activity Health Indicator**
   - Track consumer poll activity (not just message count)
   - Monitor time since last successful poll operation (fail if >configurable threshold, default: 5 minutes)
   - Verify consumer is actively polling even when no messages available
   - Track consumer position advancement over time
   - Monitor consumer session activity and heartbeat status
   - Detect consumer stuck conditions (polling but not advancing)
   - **Track message consumption count and trends for processing pipeline health**
     - Monitor messages processed per time window (e.g., last 10 minutes)
     - Compare current consumption rate with historical baseline
     - Detect sustained periods of zero message processing when messages are available
     - Fail if consumer is polling but consistently failing to process available messages
     - Distinguish between "no messages available" vs "messages available but not processed"
     - Use consumer lag metrics to determine if messages are waiting to be processed
     - Track message processing success/failure ratios
     - Configure consumption rate thresholds based on expected message volume

4. **Consumer Heartbeat Health Indicator**
   - Monitor Kafka consumer heartbeat status
   - Detect consumer session timeouts
   - Verify consumer is still part of the consumer group
   - Check for rebalancing issues



##### Liveness Configuration Requirements:
- Less frequent checks than readiness (default: every 30 seconds)
- Higher tolerance for temporary failures
- Configurable thresholds for memory usage
- Configurable timeout for message consumption activity (default: 5 minutes without poll activity)
- Configurable message consumption rate thresholds (messages per time window)
- Configurable baseline period for establishing consumption rate patterns
- Graceful degradation for non-critical failures

#### Health Endpoint Configuration Requirements

##### Endpoint Security:
- Separate security configuration for health endpoints
- Read-only access for monitoring systems
- IP allowlist for internal health checks
- Optional authentication for detailed health information

##### Response Format Requirements:
- Standard Spring Boot Actuator health response format
- Detailed status information in response body
- Timestamp of last successful check
- Error details for failed health checks
- Performance metrics in health response
- Message consumption activity metrics

#### Kubernetes Integration Requirements:
1. **Readiness Probe Configuration**
   - HTTP endpoint: `/frisco-location-wifi-scan-vmb-consumer/health/readiness`
   - Initial delay: 30 seconds (allow for Kafka connection establishment)
   - Period: 10 seconds
   - Timeout: 5 seconds
   - Failure threshold: 3 consecutive failures
   - Success threshold: 1 success to mark ready
   - **Enhanced SSL Certificate Integration**: Include SSL certificate health as part of readiness monitoring
   - **CloudWatch Metrics Export**: Configure automatic export of certificate expiry metrics to CloudWatch via readiness probe
   - **Event Generation**: Enable Kubernetes event generation for certificate state changes via readiness monitoring

2. **Liveness Probe Configuration**
   - HTTP endpoint: `/frisco-location-wifi-scan-vmb-consumer/health/liveness`
   - Initial delay: 60 seconds (allow for application startup)
   - Period: 30 seconds
   - Timeout: 10 seconds
   - Failure threshold: 3 consecutive failures
   - Success threshold: 1 success to mark healthy

#### Custom Health Indicator Requirements

##### Kafka-Specific Health Indicators:
**Note**: Spring Boot's built-in `KafkaHealthIndicator` already provides basic broker connectivity checking via Kafka Admin API (enabled via `management.health.kafka.enabled: true`), so custom connectivity indicators are not needed.

1. **KafkaConsumerHealthIndicator**
   - Monitors consumer group status
   - Tracks partition assignments
   - Reports consumer lag metrics
   - Includes last poll timestamp and message consumption activity

2. **KafkaSSLHealthIndicator** *(Enhanced Readiness Monitoring)*
   - **Strategic Kubernetes Integration**: Leverages readiness probe monitoring for certificate management
   - **CloudWatch-Driven Alerting**: Uses Kubernetes readiness metric export for automated certificate expiry alerts
   - **Validates SSL/TLS configuration and certificate accessibility**
   - **Checks certificate expiration with configurable warning timeline (30/15/7 days)**
   - **Reports SSL handshake status and cipher suite information**
   - **Graceful service degradation for expired certificates (pod removed from traffic)**
   - **Zero additional monitoring infrastructure required**

3. **MessageConsumptionActivityHealthIndicator**
   - Tracks consumer polling activity
   - Monitors message processing throughput and count trends
   - Reports time since last poll operation
   - Includes consumer position advancement metrics
   - Reports message consumption rate and baseline comparisons

#### Health Indicator Grouping:
- **Readiness Group**: Include connectivity and dependency checks (kafkaConsumerGroup, kafkaTopicAccessibility, sslCertificate)
- **Liveness Group**: Include essential application health checks (messageConsumptionActivity, jvmMemory)
- **SSL Certificate Strategic Placement**: Remains in readiness for proper Kubernetes behavior and CloudWatch integration
- Custom groups for specific monitoring requirements

#### Monitoring and Alerting Requirements:

##### Metrics Integration:
- Export health check results as metrics (Micrometer/Prometheus)
- Track health check execution time
- Monitor health check failure rates
- Alert on sustained health check failures
- Export message consumption activity metrics and trends
- **SSL Certificate CloudWatch Integration**:
  - Export certificate expiry timeline metrics from Kubernetes readiness probes
  - Configure CloudWatch alarms for certificate expiry warnings (30/15/7 days)
  - Monitor certificate renewal events and automation success rates
  - Track certificate-related readiness failures and service degradation events
  - **Operational Alert Timeline**:
    - Day -30: CloudWatch WARNING → Plan certificate renewal
    - Day -15: CloudWatch CRITICAL → Execute certificate renewal
    - Day -7: CloudWatch URGENT → Emergency renewal procedures
    - Day 0: Readiness FAILURE → Pod removed from service, manual certificate renewal required

##### Logging Requirements:
- Log health check failures with detailed error information
- Structured logging for monitoring system integration
- Configurable log levels for health check events
- Performance logging for slow health checks
- Log message consumption activity events and rate changes

#### Error Handling Requirements:
- Graceful degradation when health checks fail
- Circuit breaker pattern for external dependency checks
- Retry logic with exponential backoff
- Fallback responses for critical health check failures

### Configuration Requirements

1. Environment-Specific Configurations
   - Development
   - Testing
   - Production
   - Support for different regions
   - Configurable endpoints

2. Kafka Configuration
   - Bootstrap servers
   - Consumer group ID
   - SSL/TLS settings
   - Topic configuration
   - Partition assignment
   - Consumer batch size
   - Poll timeout settings

3. Firehose Configuration
   - Delivery stream name
   - AWS region
   - Retry configuration
   - Error handling settings
   - Batch delivery settings
   - Timeout configurations

4. Cost Management Configuration
   - Delivery optimization settings
   - Compression preferences
   - Monitoring cost metrics
   - Budget alerts

### Deployment Requirements

1. Container Support
   - Docker compatibility
   - Kubernetes deployment support
   - Resource limits and requests
   - Health check endpoints
   - Readiness probes
     - Spring Boot Actuator readiness state
     - Kafka connectivity check
     - Firehose connectivity check
   - Liveness probes
     - Spring Boot Actuator liveness state
     - Application thread health
     - Memory usage monitoring
     - Custom health indicators status

2. Environment Variables
   - Security credentials
   - Endpoint configurations
   - Monitoring configurations
   - SSL/TLS settings
   - Firehose settings

3. Logging
   - Structured logging
   - Log levels configuration
   - Log rotation
   - Error tracking
   - Performance metrics
   - Cost tracking logs

### Development Requirements

1. Code Quality
   - Unit test coverage
   - Integration tests
   - Code style guidelines
   - Documentation
   - Performance benchmarks

2. Build and Deployment
   - CI/CD pipeline support
   - Version management
   - Release process
   - Rollback procedures
   - Environment promotion

### Operational Requirements

1. Maintenance
   - Zero-downtime updates
   - Configuration management
   - Certificate rotation
   - Backup procedures
   - Disaster recovery

2. Monitoring
   - Application metrics
   - System metrics
   - Business metrics
   - Cost metrics
   - Alert thresholds
   - Dashboard requirements

3. Cost Management
   - Monthly cost tracking
   - Usage optimization
   - Budget alerts
   - Cost per message metrics
   - Firehose delivery efficiency metrics

## AWS Firehose Configuration

### Delivery Stream Settings
- Buffer size: 128 MB (configurable)
- Buffer interval: 60 seconds (configurable)
- Compression: GZIP (recommended)
- Destination: S3 bucket with organized path structure
- Error handling: Separate S3 bucket for failed records
- Format conversion: Optional (JSON to Parquet/ORC)

### Cost Considerations
- Data ingestion cost: $0.029 per GB
- Additional format conversion: +$0.018 per GB (if used)
- S3 storage costs: Standard S3 pricing
- Estimated monthly cost calculation based on data volume

## Success Criteria
1. Successful message consumption from Kafka with SSL/TLS
2. Reliable delivery to Kinesis Data Firehose
3. Automatic buffering and S3 delivery via Firehose
4. Configurable performance parameters
5. Comprehensive monitoring and alerting
6. Secure handling of credentials and certificates
7. Scalable and maintainable architecture
8. Cost-effective operation within budget constraints

## Constraints
1. Java 21 compatibility
2. SSL/TLS security requirements
3. Performance SLAs
4. Resource limitations
5. Compliance requirements
6. AWS cost budget limitations

## Dependencies
1. Apache Kafka cluster with SSL/TLS
2. AWS Kinesis Data Firehose delivery stream
3. AWS S3 bucket (configured via Firehose)
4. SSL/TLS certificates
5. Monitoring infrastructure
6. CI/CD pipeline

## Architecture Benefits
1. **Simplified Design**: No custom buffering logic required
2. **Built-in Reliability**: Firehose handles retries and error recovery
3. **Cost Predictability**: Clear AWS pricing model
4. **Operational Simplicity**: Less infrastructure to manage
5. **Automatic Scaling**: Firehose scales automatically
6. **Built-in Monitoring**: AWS CloudWatch integration

## Future Considerations
1. Support for additional message formats
2. Enhanced monitoring capabilities
3. Additional Firehose destinations
4. Advanced message transformation
5. Cost optimization strategies
6. Multi-region deployment 