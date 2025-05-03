# WiFi Positioning Service

A service that provides indoor positioning using WiFi access points. The system uses a hybrid approach combining multiple positioning algorithms to provide the most accurate position estimation based on WiFi scan results.

### Key Features

- Combines multiple positioning algorithms for optimal results
- Adapts to different scenarios (signal strength, AP density)
- Provides accuracy metrics and confidence levels
- Works with single measurements (no historical data needed)
- Supports both 2D and 3D positioning

### Architecture

The service is structured around the following components:

#### Core Components

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

### Database Integration

The system uses Amazon DynamoDB to store information about known WiFi access points. When calculating positions, the system:

1. Takes WiFi scan results from client devices
2. Looks up MAC addresses in the DynamoDB database
3. Retrieves information about known access points (location, signal characteristics)
4. Uses this information to enhance position calculations

The access point data is stored with the following structure:

- Primary Key: `mac_address` (partition key) + `version` (sort key)
- GSI: `GeohashIndex` - For geographic area searches
- Other fields include: latitude, longitude, altitude, accuracy, confidence, etc.

### API Usage

The service exposes a single API endpoint:

```
POST /api/positioning/calculate
```

Example request:

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
  "preferHighAccuracy": true,
  "returnAllMethods": false
}
```

Example response:

```json
{
  "status": "success",
  "data": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.5,
    "horizontalAccuracy": 5.2,
    "verticalAccuracy": 3.1,
    "confidence": 0.87,
    "bestMethod": "trilateration",
    "methodsUsed": ["trilateration", "weightedCentroid"],
    "apCount": 2,
    "metadata": {
      "calculationTimeMs": 45,
      "timestamp": "2023-04-17T15:23:41.123Z"
    },
    "alternatives": [
      {
        "latitude": 37.7748,
        "longitude": -122.4195,
        "altitude": 10.0,
        "horizontalAccuracy": 8.1,
        "verticalAccuracy": 4.2,
        "confidence": 0.72,
        "method": "weightedCentroid"
      }
    ]
  }
}
```

### Algorithm Selection Logic

The system dynamically selects positioning algorithms based on available data:

- Single AP → Proximity detection
- Two APs → RSSI ratio method
- Three+ APs with good geometry → Modified trilateration
- Multiple APs with poor geometry → Weighted centroid
- Multiple APs with strong signals → Maximum likelihood

Algorithm selection can be influenced by:

- Number of visible APs
- Signal strength quality
- AP geometric distribution
- Frequency diversity
   - Channel width available → Modified Trilateration

### Development

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
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       ├── java/
│       │   └── com/wifi/positioning/
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

## Profiles

- local: Default profile for local development
- test: Profile for running tests with in-memory database
- prod: Production profile (requires AWS credentials) 