#!/bin/bash

# Test response
TEST_RESPONSE='{"result":"SUCCESS","message":"Request processed successfully","data":{"latitude":37.775,"longitude":-122.4195,"altitude":10.0,"horizontalAccuracy":2.0033627428404096E155,"verticalAccuracy":0.0,"confidence":0.18054231403013185,"bestMethod":"proximity","methodsUsed":["proximity"],"apCount":1,"metadata":{"result":"SUCCESS","positionFound":true,"returnAllMethods":true,"preferHighAccuracy":false,"calculationTimeMs":17,"timestamp":1746051446707},"alternatives":[]}}'

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

echo "Testing JSON parsing..."
echo "1. Result: $(extract_json_value "$TEST_RESPONSE" "result")"
echo "2. Horizontal Accuracy: $(extract_json_value "$TEST_RESPONSE" "horizontalAccuracy")"
echo "3. Confidence: $(extract_json_value "$TEST_RESPONSE" "confidence")"
echo "4. Best Method: $(extract_json_value "$TEST_RESPONSE" "bestMethod")" 