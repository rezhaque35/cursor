#!/bin/bash

# Create the table
aws dynamodb create-table \
    --cli-input-json file://wifi-access-point-location-schema.json \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

# Wait for table to be active
echo "Waiting for table to be created..."
aws dynamodb wait table-exists \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

# Load the test data
echo "Loading test data..."
source load-test-data.sh

echo "Setup complete!" 