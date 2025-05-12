# WiFi Positioning Service

A service that provides indoor positioning using WiFi access points. The system uses a hybrid approach combining multiple positioning algorithms to provide the most accurate position estimation based on WiFi scan results.

## Key Features

- Combines multiple positioning algorithms for optimal results
- Adapts to different scenarios (signal strength, AP density, geometric distribution)
- Provides accuracy metrics and confidence levels
- Works with single measurements (no historical data needed)
- Supports both 2D and 3D positioning
- GDOP (Geometric Dilution of Precision) implementation for better accuracy estimation

## Architecture

The service is structured around the following components:

### Core Components

1. **Controller Layer**
   - `PositioningController` - Handles HTTP requests/responses for position calculation

2. **Service Layer**
   - `PositioningService` - Orchestrates the positioning process
   - Converts DTOs to internal models

3. **Algorithm Layer**
   - `GPSPositioningCalculator` - Implements the core positioning algorithms
   - `GPSPositioningCalculatorAdapter` - Adapts API data to calculator input format
   - Includes implementations of multiple positioning methods:
     - Proximity Detection
     - RSSI Ratio Method
     - Log-Distance Path Loss Model
     - Weighted Centroid
     - Modified Trilateration
     - Maximum Likelihood

4. **Repository Layer**
   - `WifiAccessPointRepository` - Interface for access point data access
   - `DynamoWifiAccessPointRepository` - DynamoDB implementation

## Database Integration

The system uses Amazon DynamoDB to store information about known WiFi access points. When calculating positions, the system:

1. Takes WiFi scan results from client devices
2. Looks up MAC addresses in the DynamoDB database
3. Retrieves information about known access points (location, signal characteristics)
4. Uses this information to enhance position calculations

The access point data is stored with the following structure:

- Primary Key: `mac_address` (partition key) + `version` (sort key)
- GSI: `GeohashIndex` - For geographic area searches
- Other fields include: latitude, longitude, altitude, accuracy, confidence, etc.
- Status field values: active, error, expired, warning, wifi-hotspot
  - Only access points with status "active" or "warning" are used for calculations

## API Usage

The service exposes a single API endpoint:

```
POST /api/positioning/calculate
```

### Request Format

```json
{
  "wifiScanResults": [
    {
      "macAddress": "00:11:22:33:44:55",
      "signalStrength": -65,
      "frequency": 2437,
      "ssid": "MyWiFi"
    },
    {
      "macAddress": "AA:BB:CC:DD:EE:FF",
      "signalStrength": -72,
      "frequency": 5240,
      "ssid": "MyWiFi5G"
    }
  ],
  "client": "test-client",
  "requestId": "test-request-123",
  "application": "wifi-positioning-test-suite",
  "calculationDetail": true
}
```

### Response Format

```json
{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-123",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746821320281,

  "wifiPosition": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.0,
    "horizontalAccuracy": 25.0,
    "verticalAccuracy": 0.0,
    "confidence": 0.5,
    "methodsUsed": ["weighted_centroid", "rssi_ratio"],
    "apCount": 3,
    "calculationTimeMs": 42
  },
  "calculationInfo": "Detailed calculation information"
}
```

Note: `calculationInfo` is only present when `calculationDetail=true` is set in the request.

## Hybrid Algorithm Selection Framework

The system dynamically selects positioning algorithms based on available data through a three-phase process:

1. **Hard Constraints (Disqualification Phase)**
   - Single AP → Only Proximity and Log Distance methods
   - Two APs → Remove Trilateration and Maximum Likelihood
   - Collinear APs → Remove Trilateration
   - Very weak signals → Only Proximity method

2. **Algorithm Weighting (Ranking Phase)**
   - Base weights assigned by AP count
   - Adjustments for signal quality, geometric quality, and distribution

3. **Finalist Selection (Combination Phase)**
   - Final algorithm selection based on adjusted weights

### Algorithm Selection Examples

- Single AP with strong signal → Proximity detection
- Two APs with good signals → Weighted Centroid and RSSI Ratio methods
- Three+ APs with good geometry → Modified Trilateration and Weighted Centroid
- Multiple APs with poor geometry → Weighted Centroid
- Multiple APs with strong signals → Maximum Likelihood and Weighted Centroid

## Development

To build and run the service:

```
./mvnw clean package
java -jar target/wifi-positioning-service-1.0.0.jar
```

For development with local DynamoDB:

```
docker-compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Docker (for local DynamoDB)
- AWS CLI v2

## Project Structure

```
wifi-positioning-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/wifi/positioning/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── algorithm/
│   │   │       ├── repository/
│   │   │       ├── model/
│   │   │       └── util/
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/
│       │   └── com/wifi/positioning/
│       │       ├── controller/
│       │       ├── service/
│       │       ├── algorithm/
│       │       └── repository/
│       └── resources/
│           └── application-test.yml
├── pom.xml
└── README.md
```

## Setup & Running

1. Start Local DynamoDB:
```bash
docker run -p 8000:8000 amazon/dynamodb-local
```

2. Load Test Data:
```bash
./load-test-data.sh
```

3. Build the project:
```bash
mvn clean install
```

4. Run the application:
```bash
mvn spring-boot:run
```

The application will be available at http://localhost:8080

## API Documentation

Swagger UI is available at: http://localhost:8080/swagger-ui.html
API docs are available at: http://localhost:8080/api-docs

## Testing

Run unit tests:
```bash
mvn test
```

Run integration tests:
```bash
mvn verify
```

Run comprehensive tests (includes algorithm performance tests):
```bash
./run-comprehensive-tests.sh
```

## Test Coverage

- Unit Tests: 114 tests covering repository, utility, algorithm implementation, and service layers
- Integration Tests: 14 tests covering basic algorithms, advanced scenarios, and error cases
- All tests currently passing with 100% success rate

## Profiles

- local: Default profile for local development
- test: Profile for running tests with in-memory database
- prod: Production profile (requires AWS credentials)