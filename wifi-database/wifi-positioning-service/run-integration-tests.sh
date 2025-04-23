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

# Function to run test and format output
run_test() {
    local test_name=$1
    local payload=$2
    local expected_status=${3:-"SUCCESS"} # Optional parameter for expected status
    
    ((TOTAL_TESTS++))
    
    echo -e "\n${BLUE}Running Test: ${test_name}${NC}"
    echo "----------------------------------------"
    echo -e "${YELLOW}Request Payload:${NC}"
    echo "$payload" | python3 -m json.tool
    
    # Send request and capture response
    response=$(curl -s -X POST http://localhost:8080/api/positioning/calculate \
        -H "Content-Type: application/json" \
        -d "$payload")
    
    # Check if curl command was successful
    if [ $? -eq 0 ]; then
        echo -e "\n${YELLOW}Response:${NC}"
        formatted_response=$(echo "$response" | python3 -m json.tool 2>/dev/null || echo "$response")
        echo "$formatted_response"
        
        # Check if response contains expected status
        if echo "$response" | grep -q "\"result\":\"$expected_status\""; then
            echo -e "\n${GREEN}✓ Test Passed${NC}"
            ((PASSED_TESTS++))
        else
            echo -e "\n${RED}✗ Test Failed${NC}"
            echo -e "${RED}Expected status: $expected_status${NC}"
            ((FAILED_TESTS++))
        fi
    else
        echo -e "\n${RED}✗ Request Failed${NC}"
        ((FAILED_TESTS++))
    fi
    echo "----------------------------------------"
}

echo -e "${BLUE}Starting WiFi Positioning Service Integration Tests${NC}"
echo "=================================================="

# Test Case 1: Single AP - Proximity Detection
run_test "Test Case 1: Single AP - Proximity Detection" '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:01",
            "signalStrength": -65.0,
            "frequency": 2437,
            "channel": 6,
            "ssid": "SingleAP_Test"
        }
    ],
    "preferHighAccuracy": false,
    "returnAllMethods": true
}'

# Test Case 2: Two APs - RSSI Ratio Method
run_test "Test Case 2: Two APs - RSSI Ratio Method" '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:02",
            "signalStrength": -68.5,
            "frequency": 5180,
            "channel": 36,
            "ssid": "DualAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62.3,
            "frequency": 2462,
            "channel": 11,
            "ssid": "TriAP_Test"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}'

# Test Case 3: Three APs - Trilateration
run_test "Test Case 3: Three APs - Trilateration" '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:03",
            "signalStrength": -62.3,
            "frequency": 2462,
            "channel": 11,
            "ssid": "TriAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71.2,
            "frequency": 5240,
            "channel": 48,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "channel": 1,
            "ssid": "WeakSignal_Test"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}'

# Test Case 4: Multiple APs - Maximum Likelihood
run_test "Test Case 4: Multiple APs - Maximum Likelihood" '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:04",
            "signalStrength": -71.2,
            "frequency": 5240,
            "channel": 48,
            "ssid": "MultiAP_Test"
        },
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "channel": 1,
            "ssid": "WeakSignal_Test"
        },
        {
            "macAddress": "00:11:22:33:44:06",
            "signalStrength": -70.0,
            "frequency": 2437,
            "channel": 6,
            "ssid": "Collinear_Test_06"
        },
        {
            "macAddress": "00:11:22:33:44:07",
            "signalStrength": -68.0,
            "frequency": 2437,
            "channel": 6,
            "ssid": "Collinear_Test_07"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}'

# Test Case 5: Weak Signals
run_test "Test Case 5: Weak Signals" '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:05",
            "signalStrength": -85.5,
            "frequency": 2412,
            "channel": 1,
            "ssid": "WeakSignal_Test"
        }
    ],
    "preferHighAccuracy": false,
    "returnAllMethods": true
}'

# Print test summary
echo -e "\n${BLUE}Test Summary${NC}"
echo "=================================================="
echo -e "Total Tests: ${TOTAL_TESTS}"
echo -e "Passed: ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Failed: ${RED}${FAILED_TESTS}${NC}"
echo "==================================================" 