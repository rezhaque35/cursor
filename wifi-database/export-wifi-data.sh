#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting to export WiFi data from DynamoDB Local...${NC}"

# Scan the table and get all items
echo "Fetching data from DynamoDB..."
DATA=$(aws dynamodb scan \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local)

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to fetch data from DynamoDB${NC}"
    exit 1
fi

# Transform the data to match the required format with "status" set to "test"
echo "Transforming data..."
TRANSFORMED_DATA=$(echo "$DATA" | jq '{
    "$table": [.Items[] | {
        "mac_addr": .mac_addr.S,
        "version": .version.S,
        "latitude": (if .latitude.N != null then (.latitude.N | tonumber) else null end),
        "longitude": (if .longitude.N != null then (.longitude.N | tonumber) else null end),
        "altitude": (if .altitude.N != null then (.altitude.N | tonumber) else null end),
        "horizontal_accuracy": (if .horizontal_accuracy.N != null then (.horizontal_accuracy.N | tonumber) else null end),
        "vertical_accuracy": (if .vertical_accuracy.N != null then (.vertical_accuracy.N | tonumber) else null end),
        "confidence": (if .confidence.N != null then (.confidence.N | tonumber) else null end),
        "ssid": .ssid.S,
        "frequency": (if .frequency.N != null then (.frequency.N | tonumber) else null end),
        "vendor": .vendor.S,
        "geohash": .geohash.S,
        "status": "test"
    }]
}')

# Define output file
OUTPUT_FILE="wifi-access-points-test-data.json"

# Write the transformed data to a file with pretty printing
echo "Writing pretty JSON data to output file..."
echo "$TRANSFORMED_DATA" | jq '.' > "$OUTPUT_FILE"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Data exported successfully to $OUTPUT_FILE${NC}"
    echo "Total records exported: $(echo "$TRANSFORMED_DATA" | jq '.["$table"] | length')"
else
    echo -e "${RED}Failed to write data to file${NC}"
    exit 1
fi 