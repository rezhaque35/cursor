# WiFi Positioning Service Test Commands

This document contains curl commands for testing the WiFi positioning service based on the test data scenarios.

## Basic Algorithm Test Cases

### Test Case 1: Single AP - Proximity Detection
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:01",
        "signalStrength": -65,
        "frequency": 2437,
        "ssid": "SingleAP_Test"
      }
    ],
    "preferHighAccuracy": false,
    "returnAllMethods": true
  }'
```

### Test Case 2: Two APs - RSSI Ratio Method
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:02",
        "signalStrength": -68.5,
        "frequency": 5180,
        "ssid": "DualAP_Test"
      },
      {
        "macAddress": "00:11:22:33:44:03",
        "signalStrength": -62.3,
        "frequency": 2462,
        "ssid": "TriAP_Test"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

### Test Case 3: Three APs - Trilateration
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:03",
        "signalStrength": -62.3,
        "frequency": 2462,
        "ssid": "TriAP_Test"
      },
      {
        "macAddress": "00:11:22:33:44:04",
        "signalStrength": -71.2,
        "frequency": 5240,
        "ssid": "MultiAP_Test"
      },
      {
        "macAddress": "00:11:22:33:44:05",
        "signalStrength": -85.5,
        "frequency": 2412,
        "ssid": "WeakSignal_Test"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

### Test Case 4: Multiple APs - Maximum Likelihood
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:04",
        "signalStrength": -71.2,
        "frequency": 5240,
        "ssid": "MultiAP_Test"
      },
      {
        "macAddress": "00:11:22:33:44:05",
        "signalStrength": -85.5,
        "frequency": 2412,
        "ssid": "WeakSignal_Test"
      },
      {
        "macAddress": "00:11:22:33:44:06",
        "signalStrength": -70.0,
        "frequency": 2437,
        "ssid": "Collinear_Test_06"
      },
      {
        "macAddress": "00:11:22:33:44:07",
        "signalStrength": -68.0,
        "frequency": 2437,
        "ssid": "Collinear_Test_07"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

### Test Case 5: Weak Signals
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:05",
        "signalStrength": -85.5,
        "frequency": 2412,
        "ssid": "WeakSignal_Test"
      }
    ],
    "preferHighAccuracy": false,
    "returnAllMethods": true
  }'
```

## Advanced Scenario Test Cases

### Test Case 6-10: Collinear APs
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:06",
        "signalStrength": -70.0,
        "frequency": 2437,
        "ssid": "Collinear_Test_06"
      },
      {
        "macAddress": "00:11:22:33:44:07",
        "signalStrength": -68.0,
        "frequency": 2437,
        "ssid": "Collinear_Test_07"
      },
      {
        "macAddress": "00:11:22:33:44:08",
        "signalStrength": -66.0,
        "frequency": 2437,
        "ssid": "Collinear_Test_08"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

### Test Case 11-15: High Density AP Cluster
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:11",
        "signalStrength": -65.0,
        "frequency": 5320,
        "ssid": "HighDensity_Test_11"
      },
      {
        "macAddress": "00:11:22:33:44:12",
        "signalStrength": -63.5,
        "frequency": 5320,
        "ssid": "HighDensity_Test_12"
      },
      {
        "macAddress": "00:11:22:33:44:13",
        "signalStrength": -62.0,
        "frequency": 5320,
        "ssid": "HighDensity_Test_13"
      },
      {
        "macAddress": "00:11:22:33:44:14",
        "signalStrength": -60.5,
        "frequency": 5320,
        "ssid": "HighDensity_Test_14"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

### Test Case 16-20: Mixed Signal Quality
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:16",
        "signalStrength": -60.0,
        "frequency": 2412,
        "ssid": "MixedSignal_Test_16"
      },
      {
        "macAddress": "00:11:22:33:44:17",
        "signalStrength": -65.0,
        "frequency": 2417,
        "ssid": "MixedSignal_Test_17"
      },
      {
        "macAddress": "00:11:22:33:44:18",
        "signalStrength": -70.0,
        "frequency": 2422,
        "ssid": "MixedSignal_Test_18"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

## Temporal and Environmental Test Cases

### Test Case 21-25: Time Series Data
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:21",
        "signalStrength": -70.0,
        "frequency": 5500,
        "ssid": "TimeSeries_Test"
      },
      {
        "macAddress": "00:11:22:33:44:22",
        "signalStrength": -72.0,
        "frequency": 5500,
        "ssid": "TimeSeries_Test"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

### Test Case 26-30: Log-Distance Path Loss
```bash
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:26",
        "signalStrength": -50.0,
        "frequency": 2462,
        "ssid": "PathLoss_Test_26"
      },
      {
        "macAddress": "00:11:22:33:44:27",
        "signalStrength": -53.0,
        "frequency": 2462,
        "ssid": "PathLoss_Test_27"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

## Error and Edge Cases

### Test Case 36-40: System Robustness
```bash
# Invalid coordinates test
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:36",
        "signalStrength": -99.9,
        "frequency": 2412,
        "ssid": "ErrorCase_invalid_coordinates"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'

# Insufficient data test
curl -X POST http://localhost:8080/api/positioning/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "wifiScanResults": [
      {
        "macAddress": "00:11:22:33:44:38",
        "signalStrength": -99.9,
        "frequency": 2412,
        "ssid": "ErrorCase_insufficient_data"
      }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
  }'
```

## Helper Scripts

### Run All Tests
```bash
#!/bin/bash

# Function to run test and check response
run_test() {
    local name=$1
    local data=$2
    echo "Running test: $name"
    response=$(curl -s -X POST http://localhost:8080/api/positioning/calculate \
        -H "Content-Type: application/json" \
        -d "$data")
    echo "Response: $response"
    echo "----------------------------------------"
}

# Run each test case
echo "Starting WiFi Positioning Service Tests..."
echo "========================================="

# Add calls to run_test for each test case here
# Example:
# run_test "Single AP Test" '{"wifiScanResults":[...]}'

echo "Tests completed."
```

Note: All curl commands can be run directly from the command line. For better readability, the JSON payloads are formatted with newlines and spaces. When copying to use in a terminal, you may want to remove the formatting or use a script to execute the commands. 