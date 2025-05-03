#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Test statistics
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to check if service is running
check_service() {
    curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || echo "000"
}

# Function to make HTTP request and validate response
make_request() {
    local payload=$1
    local test_name=$2
    
    # Make the HTTP request and capture both response and HTTP code
    response=$(curl -s -w "\n%{http_code}" -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        http://localhost:8080/api/v1/position)
    
    # Split response into body and status code
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    echo "Running Test: $test_name"
    echo "----------------------------------------"
    echo "Request Payload:"
    echo "$payload" | python3 -m json.tool
    echo

    # If we can't connect to the service, mark as failure
    if [[ $http_code == "000" ]]; then
        echo "✗ Service is not accessible"
        echo "----------------------------------------"
        return 1
    fi

    # If we got a response, print it
    if [[ -n "$body" ]]; then
        echo "Response:"
        echo "$body" | python3 -m json.tool
        echo
    fi

    # Validate response based on HTTP code
    case $http_code in
        200)
            # For success cases, validate the response structure
            if echo "$body" | jq -e . >/dev/null 2>&1; then
                echo "✓ Test Passed"
        else
                echo "✗ Invalid JSON response"
                return 1
            fi
            ;;
        400|404|500)
            echo "✗ Unexpected error response"
            return 1
            ;;
        *)
            echo "✗ Unexpected HTTP status code: $http_code"
            return 1
            ;;
    esac
    
    echo "----------------------------------------"
    return 0
}

echo "Running integration tests..."

# Wait for service to start
echo "Waiting for service to be ready..."
attempts=0
max_attempts=30
until [[ $(check_service) == "200" ]] || [[ $attempts -ge $max_attempts ]]; do
    attempts=$((attempts + 1))
    echo "Attempt $attempts/$max_attempts: Service not ready yet, waiting..."
    sleep 2
done

if [[ $attempts -ge $max_attempts ]]; then
    echo "Service failed to start after $max_attempts attempts"
    exit 1
fi

echo "Service is ready, starting tests..."

# Test Case 1: Single AP - Proximity Detection
payload='{
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
make_request "$payload" "Test Case 1: Single AP - Proximity Detection"

# Test Case 2: Two APs - RSSI Ratio Method
payload='{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:02",
            "signalStrength": -68,
            "frequency": 5180,
            "ssid": "DualAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62,
            "frequency": 2462,
            "ssid": "TriAP_Test"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}'
make_request "$payload" "Test Case 2: Two APs - RSSI Ratio Method"

# Test Case 3: Three APs - Trilateration
payload='{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62,
            "frequency": 2462,
            "ssid": "TriAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71,
            "frequency": 5240,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}'
make_request "$payload" "Test Case 3: Three APs - Trilateration"

# Test Case 4: Multiple APs - Maximum Likelihood
payload='{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71,
            "frequency": 5240,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        },
        {
            "macAddress": "00:11:22:33:44:06",
            "signalStrength": -70,
            "frequency": 2437,
            "ssid": "Collinear_Test_06"
        },
        {
            "macAddress": "00:11:22:33:44:07",
            "signalStrength": -68,
            "frequency": 2437,
            "ssid": "Collinear_Test_07"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}'
make_request "$payload" "Test Case 4: Multiple APs - Maximum Likelihood"

# Test Case 5: Weak Signals
payload='{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85,
            "frequency": 2412,
            "ssid": "WeakSignal_Test"
        }
    ],
    "preferHighAccuracy": false,
    "returnAllMethods": true
}'
make_request "$payload" "Test Case 5: Weak Signals"

# Print test summary
echo -e "\n${BLUE}Test Summary${NC}"
echo "=================================================="
echo -e "Total Tests: ${TOTAL_TESTS}"
echo -e "Passed: ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Failed: ${RED}${FAILED_TESTS}${NC}"
echo "==================================================" 