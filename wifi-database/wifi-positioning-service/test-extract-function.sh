#!/bin/bash

# Sample success response with wifiPosition
SUCCESS_RESPONSE='{
  "result": "SUCCESS",
  "message": "Request processed successfully",
  "requestId": "test-request-1",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746840847024,
  "wifiPosition": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.5,
    "horizontalAccuracy": 50.0,
    "verticalAccuracy": 0.0,
    "confidence": 0.41,
    "methodsUsed": ["proximity", "weighted_centroid"],
    "apCount": 1,
    "calculationTimeMs": 11
  },
  "calculationInfo": null
}'

# Sample error response with null wifiPosition
ERROR_RESPONSE='{
  "result": "ERROR",
  "message": "Physically impossible signal strength relationships",
  "requestId": "test-request-39",
  "client": "test-client",
  "application": "wifi-positioning-test-suite",
  "timestamp": 1746840847733,
  "wifiPosition": null,
  "calculationInfo": null
}'

# Function to extract value from JSON response - improved version
extract_json_value() {
    local json=$1
    local field=$2
    
    # Handle result and message fields at top level
    if [[ "$field" == "result" || "$field" == "message" ]]; then
        local value=$(echo "$json" | grep -o "\"$field\":\"[^\"]*\"" | sed -E 's/"'$field'":"([^"]*)"/\1/')
        echo "$value"
    # Handle position-related fields in wifiPosition object
    elif [[ "$field" == "latitude" || "$field" == "longitude" || "$field" == "altitude" || 
            "$field" == "horizontalAccuracy" || "$field" == "verticalAccuracy" || 
            "$field" == "confidence" || "$field" == "apCount" || "$field" == "calculationTimeMs" ]]; then
        local wifiPosition=$(echo "$json" | sed -n 's/.*"wifiPosition":\([^,]*\)/\1/p' | sed 's/null/{}/')
        local value=$(echo "$wifiPosition" | grep -o "\"$field\":[^,}]*" | cut -d':' -f2 | tr -d '", ')
        echo "$value"
    # Handle methodsUsed which is an array in wifiPosition
    elif [[ "$field" == "methodsUsed" ]]; then
        local wifiPosition=$(echo "$json" | sed -n 's/.*"wifiPosition":\([^,]*\)/\1/p' | sed 's/null/{}/')
        local methods=$(echo "$wifiPosition" | grep -o "\"methodsUsed\":\[[^]]*\]" | sed 's/"methodsUsed":\[//' | sed 's/\]//' | tr -d '"')
        echo "$methods"
    # Handle other top-level fields
    else
        local value=$(echo "$json" | grep -o "\"$field\":[^,}]*" | cut -d':' -f2 | tr -d '", ')
        echo "$value"
    fi
}

echo "===== Testing extraction with SUCCESS response ====="
echo "result: $(extract_json_value "$SUCCESS_RESPONSE" "result")"
echo "message: $(extract_json_value "$SUCCESS_RESPONSE" "message")"
echo "requestId: $(extract_json_value "$SUCCESS_RESPONSE" "requestId")"
echo "client: $(extract_json_value "$SUCCESS_RESPONSE" "client")"
echo "application: $(extract_json_value "$SUCCESS_RESPONSE" "application")"
echo "timestamp: $(extract_json_value "$SUCCESS_RESPONSE" "timestamp")"

echo -e "\nwifiPosition fields:"
echo "latitude: $(extract_json_value "$SUCCESS_RESPONSE" "latitude")"
echo "longitude: $(extract_json_value "$SUCCESS_RESPONSE" "longitude")"
echo "altitude: $(extract_json_value "$SUCCESS_RESPONSE" "altitude")"
echo "horizontalAccuracy: $(extract_json_value "$SUCCESS_RESPONSE" "horizontalAccuracy")"
echo "verticalAccuracy: $(extract_json_value "$SUCCESS_RESPONSE" "verticalAccuracy")"
echo "confidence: $(extract_json_value "$SUCCESS_RESPONSE" "confidence")"
echo "methodsUsed: $(extract_json_value "$SUCCESS_RESPONSE" "methodsUsed")"
echo "apCount: $(extract_json_value "$SUCCESS_RESPONSE" "apCount")"

echo -e "\n===== Testing extraction with ERROR response ====="
echo "result: $(extract_json_value "$ERROR_RESPONSE" "result")"
echo "message: $(extract_json_value "$ERROR_RESPONSE" "message")"
echo "requestId: $(extract_json_value "$ERROR_RESPONSE" "requestId")"

echo -e "\nwifiPosition fields (should be empty):"
echo "latitude: $(extract_json_value "$ERROR_RESPONSE" "latitude")"
echo "horizontalAccuracy: $(extract_json_value "$ERROR_RESPONSE" "horizontalAccuracy")"
echo "confidence: $(extract_json_value "$ERROR_RESPONSE" "confidence")"
echo "methodsUsed: $(extract_json_value "$ERROR_RESPONSE" "methodsUsed")" 