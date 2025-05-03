#!/bin/bash

# Sample response from the positioning service
RESPONSE='{"result":"SUCCESS","message":"Request processed successfully","data":{"latitude":37.77499999999999,"longitude":-122.4195,"altitude":9.999999999999998,"horizontalAccuracy":2.0033627428404096E155,"verticalAccuracy":0.0,"confidence":0.18054231403013182,"bestMethod":"proximity","methodsUsed":["proximity"],"apCount":1,"metadata":{"result":"SUCCESS","positionFound":true,"returnAllMethods":true,"preferHighAccuracy":false,"calculationTimeMs":2,"timestamp":1746050384212},"alternatives":[]}}'

# Function to extract value from JSON response
extract_json_value() {
    local json=$1
    local field=$2
    
    # Extract value using grep and regex pattern matching
    local value=$(echo "$json" | grep -o "\"$field\":[^,}]*" | cut -d':' -f2 | tr -d '", ')
    echo "$value"
}

# Testing the extract function
echo "=== Testing field extraction ==="
echo "Result: $(extract_json_value "$RESPONSE" "result")"

echo "horizontalAccuracy: $(extract_json_value "$RESPONSE" "horizontalAccuracy")"
echo "confidence: $(extract_json_value "$RESPONSE" "confidence")"
echo "bestMethod: $(extract_json_value "$RESPONSE" "bestMethod")"

# Let's see the raw data section
echo "=== Raw data path ==="
echo "$RESPONSE" | grep -o '"data":{[^}]*}'

# Let's try using jq for nested access (if available)
if command -v jq &> /dev/null; then
    echo "=== Using jq for comparison ==="
    echo "$RESPONSE" | jq -r '.data.horizontalAccuracy'
    echo "$RESPONSE" | jq -r '.data.confidence'
    echo "$RESPONSE" | jq -r '.data.bestMethod'
fi 