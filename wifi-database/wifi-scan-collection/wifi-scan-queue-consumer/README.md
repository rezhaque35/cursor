# WiFi Scan Queue Consumer

A high-performance Spring Boot Kafka consumer application designed to process WiFi scan messages with SSL/TLS support, comprehensive health monitoring, and production-ready features.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [Scripts](#scripts)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## 🌟 Overview

The WiFi Scan Queue Consumer is a robust microservice that:
- Consumes WiFi scan data messages from Apache Kafka topics
- Provides SSL/TLS encrypted communication with Kafka brokers
- Offers comprehensive health monitoring and metrics
- Supports high-throughput message processing with configurable concurrency
- Implements production-ready logging, error handling, and observability

## ✨ Features

### Core Functionality
- **Kafka Integration**: High-performance message consumption with Spring Kafka
- **SSL/TLS Support**: Secure communication with encrypted Kafka clusters
- **Health Monitoring**: Comprehensive health checks for service components
- **Metrics & Observability**: Built-in metrics collection and monitoring endpoints
- **Error Handling**: Robust error handling with retry mechanisms

### Production Ready
- **Auto-configuration**: Spring Boot auto-configuration for easy setup
- **Environment Profiles**: Support for development, test, and production environments
- **Actuator Endpoints**: Health checks, metrics, and application information
- **Graceful Shutdown**: Proper resource cleanup and shutdown handling
- **Memory Management**: Optimized memory usage with monitoring

### Developer Experience
- **Comprehensive Testing**: Unit tests, integration tests, and test automation
- **Development Scripts**: Automated setup and validation scripts
- **Documentation**: Extensive documentation and examples
- **Lombok Integration**: Reduced boilerplate code with Lombok annotations

## 🏗️ Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Kafka Broker  │───▶│  WiFi Consumer  │───▶│  Message        │
│   (SSL/TLS)     │    │  Service        │    │  Processing     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                              │
                              ▼
                       ┌─────────────────┐
                       │  Health &       │
                       │  Metrics        │
                       └─────────────────┘
```

### Package Structure

```
com.wifi.scan.consume/
├── config/           # Configuration classes
├── controller/       # REST controllers for metrics
├── health/          # Custom health indicators
├── listener/        # Kafka message listeners
├── metrics/         # Metrics collection
└── service/         # Business logic services
```

## 📋 Prerequisites

- **Java 21** or higher
- **Apache Maven 3.8+**
- **Docker & Docker Compose** (for local Kafka)
- **curl** (for health checks)
- **jq** (for JSON processing in scripts)

### Optional
- **Apache Kafka** (if not using Docker)
- **SSL certificates** (for encrypted communication)

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone <repository-url>
cd wifi-scan-queue-consumer
mvn clean install
```

### 2. Start Local Kafka (Docker)

```bash
# Start Kafka with Docker Compose
./scripts/start-local-kafka.sh

# Create required topic
./scripts/create-test-topic.sh
```

### 3. Run the Application

```bash
# Development mode
mvn spring-boot:run

# Or run the JAR
java -jar target/wifi-scan-queue-consumer-1.0.0-SNAPSHOT.jar
```

### 4. Verify Installation

```bash
# Check health
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health

# Run validation tests
./scripts/run-test-suite.sh
```

## ⚙️ Configuration

### Application Properties

The application uses `application.yml` for configuration:

```yaml
spring:
  application:
    name: wifi-scan-queue-consumer
  profiles:
    active: development

kafka:
  bootstrap-servers: localhost:9092
  consumer:
    group-id: wifi-scan-consumer
    auto-offset-reset: earliest
  topic:
    name: wifi-scan-data
  ssl:
    enabled: false
```

### Environment-Specific Configuration

#### Development Profile
- Non-SSL Kafka connection
- Debug logging enabled
- Local Kafka broker (localhost:9092)

#### Production Profile
- SSL/TLS encryption enabled
- Optimized logging configuration
- Production Kafka cluster endpoints

#### Test Profile
- In-memory test configurations
- Mock services for unit testing
- Test-specific Kafka settings

### SSL Configuration

For production environments with SSL:

```yaml
kafka:
  ssl:
    enabled: true
    keystore:
      location: ${KAFKA_SSL_KEYSTORE_LOCATION:scripts/kafka/secrets/kafka.keystore.p12}
      password: ${KAFKA_KEYSTORE_PASSWORD:kafka123}
      type: PKCS12
    truststore:
      location: ${KAFKA_SSL_TRUSTSTORE_LOCATION:scripts/kafka/secrets/kafka.truststore.p12}
      password: ${KAFKA_TRUSTSTORE_PASSWORD:kafka123}
      type: PKCS12
```

## 💻 Development

### Project Structure

```
wifi-scan-queue-consumer/
├── src/
│   ├── main/
│   │   ├── java/com/wifi/scan/consume/
│   │   │   ├── config/              # Spring configuration
│   │   │   ├── controller/          # REST endpoints
│   │   │   ├── health/              # Health indicators
│   │   │   ├── listener/            # Kafka listeners
│   │   │   ├── metrics/             # Metrics collection
│   │   │   └── service/             # Business services
│   │   └── resources/
│   │       └── application.yml      # Configuration
│   └── test/                        # Test classes
├── scripts/                         # Development scripts
│   └── kafka/
│       └── secrets/                 # SSL certificates (local dev)
├── documents/                       # Documentation
├── target/                          # Build output
└── pom.xml                         # Maven configuration
```

### Development Workflow

1. **Setup Environment**
   ```bash
   ./scripts/setup-dev-environment.sh
   ```

2. **Run Tests**
   ```bash
   mvn test                    # Unit tests
   mvn verify                  # Integration tests
   ./scripts/run-test-suite.sh # Full validation
   ```

3. **Code Quality**
   ```bash
   mvn clean compile          # Check compilation
   mvn spotbugs:check         # Static analysis
   ```

## 🧪 Testing

### Test Categories

#### Unit Tests
- **Location**: `src/test/java`
- **Framework**: JUnit 5, Mockito
- **Coverage**: Individual components and business logic

#### Integration Tests
- **Location**: `src/test/java/*IntegrationTest.java`
- **Framework**: Spring Boot Test, TestContainers
- **Coverage**: End-to-end scenarios with real Kafka

#### Validation Tests
- **Location**: `scripts/run-test-suite.sh`
- **Framework**: Shell scripts with curl/jq
- **Coverage**: Live service validation

### Running Tests

```bash
# Unit tests only
mvn test

# All tests (unit + integration)
mvn verify

# Live service validation
./scripts/run-test-suite.sh

# Specific test scenarios
./scripts/validate-service-health.sh --count 10 --verbose
```

### Test Configuration

Tests use the `test` profile with:
- Embedded Kafka for integration tests
- Mock services for unit tests
- Temporary test data and cleanup

## 🚀 Deployment

### Docker Deployment

```bash
# Build Docker image
docker build -t wifi-scan-consumer:latest .

# Run container
docker run -d \
  --name wifi-consumer \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=production \
  wifi-scan-consumer:latest
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wifi-scan-consumer
spec:
  replicas: 3
  selector:
    matchLabels:
      app: wifi-scan-consumer
  template:
    metadata:
      labels:
        app: wifi-scan-consumer
    spec:
      containers:
      - name: consumer
        image: wifi-scan-consumer:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `development` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker endpoints | `localhost:9092` |
| `KAFKA_CONSUMER_GROUP_ID` | Consumer group ID | `wifi-scan-consumer` |
| `KAFKA_SSL_ENABLED` | Enable SSL/TLS | `false` |
| `KAFKA_KEYSTORE_PASSWORD` | Keystore password | - |
| `KAFKA_TRUSTSTORE_PASSWORD` | Truststore password | - |

## 📊 Monitoring

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/frisco-location-wifi-scan-vmb-consumer/health` | Overall application health |
| `/frisco-location-wifi-scan-vmb-consumer/health/readiness` | Readiness probe |
| `/frisco-location-wifi-scan-vmb-consumer/health/liveness` | Liveness probe |
| `/frisco-location-wifi-scan-vmb-consumer/info` | Application information |
| `/frisco-location-wifi-scan-vmb-consumer/metrics` | Application metrics |

### Custom Health Indicators

- **Kafka Consumer Health**: Monitors Kafka consumer group status
- **Message Consumption Activity**: Tracks recent message processing
- **Memory Health**: Monitors JVM memory usage
- **SSL Certificate Health**: Validates SSL certificate status
- **Topic Accessibility**: Verifies Kafka topic access

### Metrics

Available at `/frisco-location-wifi-scan-vmb-consumer/metrics/kafka`:

```json
{
  "totalMessagesConsumed": 1250,
  "totalMessagesProcessed": 1250,
  "totalMessagesFailed": 0,
  "successRate": 100.0,
  "errorRate": 0.0,
  "averageProcessingTimeMs": 15.2,
  "memoryUsagePercentage": 35.5,
  "isConsumerConnected": true,
  "isPollingActive": true
}
```

## 📜 Scripts

### Development Scripts

| Script | Description |
|--------|-------------|
| `setup-dev-environment.sh` | Setup development environment |
| `start-local-kafka.sh` | Start local Kafka with Docker |
| `stop-local-kafka.sh` | Stop local Kafka containers |
| `create-test-topic.sh` | Create required Kafka topics |

### Testing Scripts

| Script | Description |
|--------|-------------|
| `run-test-suite.sh` | Comprehensive test suite |
| `validate-service-health.sh` | Service health validation |
| `send-test-message.sh` | Send test messages to Kafka |
| `send-wifi-scan-messages.sh` | Send WiFi scan test data |

### SSL Scripts

| Script | Description |
|--------|-------------|
| `generate-ssl-certs.sh` | Generate SSL certificates |
| `test-ssl-connection.sh` | Test SSL connectivity |

### Usage Examples

```bash
# Run comprehensive validation
./scripts/run-test-suite.sh

# Test with specific parameters
./scripts/validate-service-health.sh \
  --count 20 \
  --interval 0.5 \
  --timeout 120 \
  --verbose

# Send custom test messages
./scripts/send-wifi-scan-messages.sh \
  --count 100 \
  --interval 1 \
  --topic wifi-scan-data
```

## 🔧 Troubleshooting

### Common Issues

#### Service Won't Start
```bash
# Check Java version
java -version  # Should be 21+

# Check port availability
netstat -an | grep 8080

# Check Kafka connectivity
./scripts/test-ssl-connection.sh
```

#### Kafka Connection Issues
```bash
# Verify Kafka is running
docker ps | grep kafka

# Check topic exists
./scripts/create-test-topic.sh

# Test basic connectivity
curl http://localhost:8080/frisco-location-wifi-scan-vmb-consumer/health
```

#### SSL/TLS Problems
```bash
# Regenerate certificates
./scripts/generate-ssl-certs.sh

# Test SSL connection
./scripts/test-ssl-connection.sh

# Check certificate validity
openssl x509 -in secrets/kafka.cert.pem -text -noout
```

### Debug Mode

Enable debug logging:

```yaml
logging:
  level:
    com.wifi.scan.consume: DEBUG
    org.springframework.kafka: DEBUG
    org.apache.kafka: INFO
```

### Performance Tuning

For high-throughput scenarios:

```yaml
kafka:
  consumer:
    max-poll-records: 500
    fetch-min-size: 1024
    fetch-max-wait: 500
  listener:
    concurrency: 5
    poll-timeout: 3000
```

## 🤝 Contributing

### Development Guidelines

1. **Code Style**: Follow Spring Boot and Java best practices
2. **Testing**: Maintain >80% test coverage
3. **Documentation**: Update README for significant changes
4. **Commits**: Use conventional commit messages

### Pull Request Process

1. Create feature branch from `main`
2. Implement changes with tests
3. Run full test suite: `./scripts/run-test-suite.sh`
4. Update documentation as needed
5. Submit pull request with description

### Local Development Setup

```bash
# Clone repository
git clone <repository-url>
cd wifi-scan-queue-consumer

# Setup development environment
./scripts/setup-dev-environment.sh

# Run tests to verify setup
mvn clean verify
./scripts/run-test-suite.sh
```

---

## 📞 Support

For support and questions:
- 📧 **Email**: [support@example.com]
- 📋 **Issues**: [GitHub Issues](https://github.com/your-org/wifi-scan-queue-consumer/issues)
- 📖 **Documentation**: [Wiki](https://github.com/your-org/wifi-scan-queue-consumer/wiki)

---

**Version**: 1.0.0-SNAPSHOT  
**Last Updated**: December 2024  
**License**: [MIT License](LICENSE) 