#!/bin/bash

echo "Verifying DynamoDB Local data..."

# Check if table exists
echo "Checking table existence..."
TABLE_CHECK=$(aws dynamodb describe-table \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local 2>/dev/null)

if [ $? -ne 0 ]; then
    echo "Error: wifi_access_points table does not exist!"
    exit 1
fi

echo "Table exists. Scanning for data..."

# Scan the table and count items
SCAN_RESULT=$(aws dynamodb scan \
    --table-name wifi_access_points \
    --select COUNT \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local)

ITEM_COUNT=$(echo $SCAN_RESULT | jq -r '.Count')

echo "Total items in table: $ITEM_COUNT"

# Get sample data from different test cases
echo -e "\nChecking sample data from different test cases..."

# Function to get item by MAC address
get_item() {
    local mac=$1
    aws dynamodb get-item \
        --table-name wifi_access_points \
        --key "{\"mac_addr\":{\"S\":\"$mac\"}}" \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local
}

# Check Single AP Test Case
echo -e "\n1. Single AP Test Case:"
get_item "00:11:22:33:44:01" | jq -r '.Item | {mac_addr: .mac_addr.S, ssid: .ssid.S, signal_strength_avg: .signal_strength_avg.N, best_method: .best_method.S}'

# Check High Density Test Case
echo -e "\n2. High Density AP Test Case:"
get_item "00:11:22:33:44:11" | jq -r '.Item | {mac_addr: .mac_addr.S, ssid: .ssid.S, signal_strength_avg: .signal_strength_avg.N, best_method: .best_method.S}'

# Check Weak Signal Test Case
echo -e "\n3. Weak Signal Test Case:"
get_item "00:11:22:33:44:05" | jq -r '.Item | {mac_addr: .mac_addr.S, ssid: .ssid.S, signal_strength_avg: .signal_strength_avg.N, best_method: .best_method.S}'

# Check Error Case
echo -e "\n4. Error Case:"
get_item "00:11:22:33:44:36" | jq -r '.Item | {mac_addr: .mac_addr.S, ssid: .ssid.S, status: .status.S, error_type: .error_type.S}'

echo -e "\nVerification complete!" 