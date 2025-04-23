# WiFi Positioning Service - Repository Testing Guide

## Overview

This guide explains our approach to testing the WiFi access point repository layer, focusing on:

1. Using in-memory repositories for unit testing
2. Isolating tests from external dependencies
3. Supporting comprehensive test scenarios

## Repository Architecture

The repository layer has been simplified to focus only on the essential method needed for the positioning service:

```java
public interface WifiAccessPointRepository {
    List<WifiAccessPoint> findByMacAddress(String macAddress);
}
```

We provide two implementations:

1. **DynamoWifiAccessPointRepository** - Production implementation using DynamoDB
2. **InMemoryWifiAccessPointRepository** - Test implementation using in-memory storage

## Testing Approach

### Benefits of In-Memory Testing

- No external dependencies on AWS or DynamoDB
- Fast execution and consistent results
- Easy setup for different test scenarios
- No special credentials or infrastructure needed

### Test Scenarios

The in-memory repository supports loading various test scenarios:

- Single AP (Proximity Detection)
- Dual AP (RSSI Ratio Method)
- Three APs (Trilateration)
- Weak Signals
- Collinear APs
- Multiple AP clusters

### Running Repository Tests

To run just the repository tests:

```shell
./mvnw test -Dtest=WifiAccessPointRepositoryTest
```

### Adding New Test Scenarios

1. Add a new loading method to `InMemoryWifiAccessPointRepository`:

```java
public void loadNewScenario() {
    WifiAccessPoint ap = WifiAccessPoint.builder()
            .macAddress("00:11:22:33:44:XY")
            .version("test-version")
            // Add other properties
            .build();
    
    addAccessPoint(ap);
}
```

2. Create a test method in `WifiAccessPointRepositoryTest`:

```java
@Test
void findByMacAddress_shouldReturnCorrectData_forNewScenario() {
    // Arrange
    inMemoryRepository.loadNewScenario();
    
    // Act
    List<WifiAccessPoint> found = repository.findByMacAddress("00:11:22:33:44:XY");
    
    // Assert
    assertFalse(found.isEmpty());
    // Add more assertions
}
```

## Configuration

The test configuration is set up in `TestDynamoDBConfig.java`, which provides the in-memory implementation for tests:

```java
@TestConfiguration
@Profile("test")
public class TestDynamoDBConfig {
    @Bean
    @Primary
    public WifiAccessPointRepository wifiAccessPointRepository() {
        return new InMemoryWifiAccessPointRepository();
    }
}
```

## Profile-Based Selection

- `@Profile("test")` annotation is used for the in-memory implementation
- `@Profile("!test")` annotation is used for the DynamoDB implementation
- Tests run with the `test` profile activated

## Test Data Sources

The test data is based on our load-test-data.sh script, which provides realistic WiFi access point data for various positioning algorithms and scenarios.

## Isolation from Other Tests

Tests that rely on the repository (such as service or controller tests) will automatically use the in-memory implementation when running with the `test` profile. This ensures consistent behavior across all tests and avoids dependency on external services.

## Future Enhancements

1. Add support for more complex test scenarios
2. Extend in-memory implementation with additional helper methods 
3. Add performance benchmarking capabilities 