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

# Check if jq is installed
if ! command -v jq &> /dev/null; then
  echo "jq is not installed. Please install it with:"
  echo "  brew install jq"
  exit 1
fi

echo "===== Parsing SUCCESS response with jq ====="
echo "result: $(echo $SUCCESS_RESPONSE | jq -r '.result')"
echo "message: $(echo $SUCCESS_RESPONSE | jq -r '.message')"
echo "requestId: $(echo $SUCCESS_RESPONSE | jq -r '.requestId')"
echo "client: $(echo $SUCCESS_RESPONSE | jq -r '.client')"
echo "application: $(echo $SUCCESS_RESPONSE | jq -r '.application')"
echo "timestamp: $(echo $SUCCESS_RESPONSE | jq -r '.timestamp')"

echo -e "\nwifiPosition fields:"
echo "latitude: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.latitude')"
echo "longitude: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.longitude')"
echo "altitude: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.altitude')"
echo "horizontalAccuracy: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.horizontalAccuracy')"
echo "verticalAccuracy: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.verticalAccuracy')"
echo "confidence: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.confidence')"
echo "methodsUsed: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.methodsUsed | join(", ")')"
echo "apCount: $(echo $SUCCESS_RESPONSE | jq -r '.wifiPosition.apCount')"

echo -e "\n===== Parsing ERROR response with jq ====="
echo "result: $(echo $ERROR_RESPONSE | jq -r '.result')"
echo "message: $(echo $ERROR_RESPONSE | jq -r '.message')"
echo "requestId: $(echo $ERROR_RESPONSE | jq -r '.requestId')"

echo -e "\nwifiPosition fields (should be null):"
echo "wifiPosition is null: $(echo $ERROR_RESPONSE | jq -r '.wifiPosition == null')"
echo "latitude (should be null): $(echo $ERROR_RESPONSE | jq -r '.wifiPosition.latitude // "null"')" 