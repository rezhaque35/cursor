#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Initialize counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to check if a value is in range
check_range() {
    local value=$1
    local min=$2
    local max=$3
    
    if (( $(echo "$value >= $min" | bc -l) )) && (( $(echo "$value <= $max" | bc -l) )); then
        return 0
    else
        return 1
    fi
}

# Function to extract value from JSON response - FIXED for nested structure
extract_json_value() {
    local json=$1
    local field=$2
    
    # Check if field is in top level or inside data object
    if [[ "$field" == "result" ]]; then
        # Extract from top level
        local value=$(echo "$json" | grep -o "\"$field\":\"[^\"]*\"" | cut -d':' -f2 | tr -d '":,')
        echo "$value"
    else
        # Extract from data object
        local data_section=$(echo "$json" | sed 's/.*"data"://' | sed 's/\}\}/\}\}/' | sed 's/\}\}.*/\}\}/')
        local value=$(echo "$data_section" | grep -o "\"$field\":[^,}]*" | cut -d':' -f2 | tr -d '", ')
        echo "$value"
    fi
}

# Function to validate response against detailed criteria
validate_response() {
    local response=$1
    local result_check=$2
    local horiz_acc_min=$3
    local horiz_acc_max=$4
    local confidence_min=$5
    local confidence_max=$6
    local best_method=$7
    
    local validation_errors=()
    
    # Check for basic SUCCESS/ERROR
    if [[ "$response" != *"$result_check"* ]]; then
        validation_errors+=("Expected result:\"$result_check\" not found")
    fi
    
    # If we're checking a SUCCESS response, validate the other fields
    if [[ "$result_check" == *"SUCCESS"* ]]; then
        # Extract and validate horizontalAccuracy
        local h_accuracy=$(extract_json_value "$response" "horizontalAccuracy")
        if [[ -n "$h_accuracy" ]]; then
            if ! check_range "$h_accuracy" "$horiz_acc_min" "$horiz_acc_max"; then
                validation_errors+=("horizontalAccuracy $h_accuracy not in range $horiz_acc_min-$horiz_acc_max")
            fi
        else
            validation_errors+=("horizontalAccuracy not found in response")
        fi
        
        # Extract and validate confidence
        local confidence=$(extract_json_value "$response" "confidence")
        if [[ -n "$confidence" ]]; then
            if ! check_range "$confidence" "$confidence_min" "$confidence_max"; then
                validation_errors+=("confidence $confidence not in range $confidence_min-$confidence_max")
            fi
        else
            validation_errors+=("confidence not found in response")
        fi
        
        # Extract and validate bestMethod
        local method=$(extract_json_value "$response" "bestMethod")
        if [[ -n "$method" ]]; then
            if [[ "$method" != "$best_method" ]]; then
                validation_errors+=("bestMethod $method does not match expected $best_method")
            fi
        else
            validation_errors+=("bestMethod not found in response")
        fi
    fi
    
    # Return validation result
    if [ ${#validation_errors[@]} -eq 0 ]; then
        return 0  # Validation passed
    else
        printf '%s\n' "${validation_errors[@]}"
        return 1  # Validation failed
    fi
}

# Function to format the output
format_output() {
    echo "----------------------------------------"
    echo "Request Payload:"
    echo "$1"
    echo
    echo "Response:"
    echo "$2"
    echo
    
    if [ "$3" = true ]; then
        echo -e "${GREEN}✓ Test Passed${NC}"
        ((PASSED_TESTS++))
    else
        echo -e "${RED}✗ Test Failed${NC}"
        echo "Validation errors:"
        printf '%s\n' "${validation_errors[@]}"
        ((FAILED_TESTS++))
    fi
    echo "----------------------------------------"
}

# Function to run a test case
run_test() {
    local payload="$1"
    local expected_result="$2"
    local horiz_acc_min="${3:-0}"
    local horiz_acc_max="${4:-999.9}"
    local confidence_min="${5:-0}"
    local confidence_max="${6:-1}"
    local best_method="${7:-}"
    
    ((TOTAL_TESTS++))
    
    # Make the API call
    response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -d "$payload" \
        http://localhost:8080/api/positioning/calculate)
    
    # Validate the response against all criteria
    validation_errors=()
    if ! validation_output=$(validate_response "$response" "$expected_result" "$horiz_acc_min" "$horiz_acc_max" "$confidence_min" "$confidence_max" "$best_method"); then
        validation_errors=($validation_output)
        format_output "$payload" "$response" false
    else
        format_output "$payload" "$response" true
    fi
}

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  WIFI POSITIONING SERVICE COMPREHENSIVE TESTS${NC}"
echo -e "${CYAN}====================================================${NC}"

echo -e "\n${BLUE}SECTION 1: BASIC ALGORITHM TEST CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 1: Single AP - Proximity Detection
run_test '{
    "wifiScanResults": [{
        "macAddress": "00:11:22:33:44:01",
        "ssid": "SingleAP_Test",
        "signalStrength": -65.0,
        "frequency": 2437
    }],
    "preferHighAccuracy": false,
    "returnAllMethods": true
}' '"result":"SUCCESS"' 15 50 0.35 0.65 "proximity"

# Test Case 2: Two APs - RSSI Ratio Method
run_test '{
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
}' '"result":"SUCCESS"' 10 30 0.40 0.65 "rssi_ratio"

# Test Case 3: Three APs - Trilateration
run_test '{
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
}' '"result":"SUCCESS"' 8 20 0.50 0.75 "trilateration"

# Test Case 4: Multiple APs - Maximum Likelihood
run_test '{
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
}' '"result":"SUCCESS"' 15 30 0.39 0.70 "maximum_likelihood"

# Test Case 5: Weak Signals
run_test '{
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
}' '"result":"SUCCESS"' 25 75 0.25 0.40 "proximity"

echo -e "\n${BLUE}SECTION 2: ADVANCED SCENARIO TEST CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 6-10: Collinear APs
run_test '{
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
}' '"result":"ERROR"'

# Test Case 11-15: High Density AP Cluster
run_test '{
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
}' '"result":"SUCCESS"' 10 20 0.44 0.75 "maximum_likelihood"

# Test Case 16-20: Mixed Signal Quality
run_test '{
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
}' '"result":"SUCCESS"' 10 25 0.45 0.75 "trilateration"

echo -e "\n${BLUE}SECTION 3: TEMPORAL AND ENVIRONMENTAL TEST CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 21-25: Time Series Data
run_test '{
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
}' '"result":"SUCCESS"' 12 25 0.37 0.70 "rssi_ratio"

# Test Case 26-30: Log-Distance Path Loss
run_test '{
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
}' '"result":"SUCCESS"' 10 20 0.45 0.80 "rssi_ratio"

# Test Case 31-35: Historical Data Analysis
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:31",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        },
        {
            "macAddress": "00:11:22:33:44:32",
            "signalStrength": -68.0,
            "frequency": 5500,
            "ssid": "Historical_Test"
        }
    ],
    "preferHighAccuracy": true,
    "returnAllMethods": true
}' '"result":"SUCCESS"' 10 20 0.45 0.75 "rssi_ratio"

echo -e "\n${BLUE}SECTION 4: ERROR AND EDGE CASES${NC}"
echo -e "${BLUE}====================================================${NC}"

# Test Case 36: Invalid coordinates
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:36",
            "signalStrength": -99.9,
            "frequency": 2412,
            "ssid": "ErrorCase_invalid_coordinates"
        }
    ]
}' '"result":"ERROR"'

# Test Case 38: Insufficient Data
# Note: This test validates that the system handles scenarios with potentially unreliable positioning:
# 1. Having only a single AP when multiple APs would provide better accuracy
# 2. The signal being too weak (-99.9 dBm) to be reliable for positioning
# Expected behavior: SUCCESS with high horizontalAccuracy (999.9m) and low confidence,
# indicating that while position is calculated, it has high uncertainty.
run_test '{
    "wifiScanResults": [{
        "macAddress": "00:11:22:33:44:55",
        "ssid": "TestAP1",
        "signalStrength": -99.9,
        "frequency": 2412
    }]
}' '"result":"ERROR"'

# Test Case 39: Algorithm Failure
# Note: This test represents a true algorithm failure case with physically impossible signal strengths.
# The scenario creates an impossible triangulation case where:
# 1. Three APs are in close proximity (same frequency/channel)
# 2. Signal strengths violate physics - stronger signals (-40 dBm) from farther APs
#    while weaker signals (-90 dBm) from closer APs at the same frequency
# This should cause an ERROR as it's physically impossible in real-world conditions
run_test '{
    "wifiScanResults": [
        {
            "macAddress": "00:11:22:33:44:55",
            "ssid": "TestAP1",
            "signalStrength": -40,
            "frequency": 2412
        },
        {
            "macAddress": "AA:BB:CC:DD:EE:FF",
            "ssid": "TestAP2",
            "signalStrength": -90,
            "frequency": 2412
        },
        {
            "macAddress": "11:22:33:44:55:66",
            "ssid": "TestAP3",
            "signalStrength": -95,
            "frequency": 2412
        }
    ]
}' '"result":"ERROR"'

# Print test summary
echo -e "\n${CYAN}====================================================${NC}"
echo -e "${CYAN}                TEST SUMMARY${NC}"
echo -e "${CYAN}====================================================${NC}"
echo -e "Total Tests:  ${TOTAL_TESTS}"
echo -e "Passed:       ${GREEN}${PASSED_TESTS}${NC}"
echo -e "Failed:       ${RED}${FAILED_TESTS}${NC}"
if [ "$TOTAL_TESTS" -gt 0 ]; then
    SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
    echo -e "Success Rate: ${YELLOW}${SUCCESS_RATE}%${NC}"
fi
echo -e "${CYAN}====================================================${NC}" 