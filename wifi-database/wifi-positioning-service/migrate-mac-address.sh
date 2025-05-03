#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting migration process...${NC}"

# Step 1: Create new table
echo -e "\n${YELLOW}Creating new table with updated schema...${NC}"
aws dynamodb create-table \
    --cli-input-json file://wifi-access-points-new-schema.json \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to create new table${NC}"
    exit 1
fi

echo -e "${GREEN}New table created successfully${NC}"

# Step 2: Get all items from old table
echo -e "\n${YELLOW}Fetching data from old table...${NC}"
OLD_DATA=$(aws dynamodb scan \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local)

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to fetch data from old table${NC}"
    exit 1
fi

# Step 3: Process and migrate each item
echo -e "\n${YELLOW}Migrating data to new table...${NC}"
echo "$OLD_DATA" | jq -c '.Items[]' | while read -r item; do
    # Replace mac_address with mac_addr in the item
    NEW_ITEM=$(echo "$item" | jq '. | del(.mac_address) + {mac_addr: .mac_address}')
    
    # Put item in new table
    aws dynamodb put-item \
        --table-name wifi_access_points_new \
        --item "$NEW_ITEM" \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Migrated item successfully${NC}"
    else
        echo -e "${RED}Failed to migrate item${NC}"
        exit 1
    fi
done

# Step 4: Verify migration
echo -e "\n${YELLOW}Verifying migration...${NC}"
OLD_COUNT=$(aws dynamodb scan \
    --table-name wifi_access_points \
    --select COUNT \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --query 'Count' \
    --output text)

NEW_COUNT=$(aws dynamodb scan \
    --table-name wifi_access_points_new \
    --select COUNT \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --query 'Count' \
    --output text)

if [ "$OLD_COUNT" = "$NEW_COUNT" ]; then
    echo -e "${GREEN}Migration verified successfully!${NC}"
    echo -e "Old table count: $OLD_COUNT"
    echo -e "New table count: $NEW_COUNT"
else
    echo -e "${RED}Migration verification failed!${NC}"
    echo -e "Old table count: $OLD_COUNT"
    echo -e "New table count: $NEW_COUNT"
    exit 1
fi

# Step 5: Delete old table
echo -e "\n${YELLOW}Deleting old table...${NC}"
aws dynamodb delete-table \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to delete old table${NC}"
    exit 1
fi

# Step 6: Rename new table to original name
echo -e "\n${YELLOW}Renaming new table...${NC}"
aws dynamodb delete-table \
    --table-name wifi_access_points_new \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to delete new table for renaming${NC}"
    exit 1
fi

# Create final table with original name but new schema
aws dynamodb create-table \
    --cli-input-json "$(cat wifi-access-points-new-schema.json | jq '.TableName = "wifi_access_points"')" \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local

if [ $? -ne 0 ]; then
    echo -e "${RED}Failed to create final table${NC}"
    exit 1
fi

# Migrate data to final table
echo "$OLD_DATA" | jq -c '.Items[]' | while read -r item; do
    # Replace mac_address with mac_addr in the item
    NEW_ITEM=$(echo "$item" | jq '. | del(.mac_address) + {mac_addr: .mac_address}')
    
    # Put item in final table
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --item "$NEW_ITEM" \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}Migrated item to final table successfully${NC}"
    else
        echo -e "${RED}Failed to migrate item to final table${NC}"
        exit 1
    fi
done

echo -e "\n${GREEN}Migration completed successfully!${NC}" 