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

# Transform the data to match the required format
echo "Transforming data..."
TRANSFORMED_DATA=$(echo "$DATA" | jq -c '{
    "$table": [.Items[] | {
        "mac_addr": .mac_addr.S,
        "version": .version.S,
        "latitude": .latitude.N,
        "longitude": .longitude.N,
        "altitude": .altitude.N,
        "horizontal_accuracy": .horizontal_accuracy.N,
        "vertical_accuracy": .vertical_accuracy.N,
        "confidence": .confidence.N,
        "ssid": .ssid.S,
        "frequency": .frequency.N,
        "vendor": .vendor.S,
        "geohash": .geohash.S,
        "status": "test"
    }]
}')

# Write the transformed data to a file
echo "Writing data to output file..."
echo "$TRANSFORMED_DATA" > wifi-data-export.json

if [ $? -eq 0 ]; then
    echo -e "${GREEN}Data exported successfully to wifi-data-export.json${NC}"
    echo "Total records exported: $(echo "$TRANSFORMED_DATA" | jq '.["$table"] | length')"
else
    echo -e "${RED}Failed to write data to file${NC}"
    exit 1
fi 