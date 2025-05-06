#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting to load test data into DynamoDB Local...${NC}"

# Test Case 1: Single AP - Proximity Detection
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:01"},
        "version": {"S": "20240411-120000"},
        "latitude": {"N": "37.7749"},
        "longitude": {"N": "-122.4194"},
        "altitude": {"N": "10.5"},
        "horizontal_accuracy": {"N": "50.0"},
        "vertical_accuracy": {"N": "8.0"},
        "confidence": {"N": "0.65"},
        "ssid": {"S": "SingleAP_Test"},
        "frequency": {"N": "2437"},
        "vendor": {"S": "Cisco"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 2: Two APs - RSSI Ratio Method
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:02"},
        "version": {"S": "20240411-120100"},
        "latitude": {"N": "37.7750"},
        "longitude": {"N": "-122.4195"},
        "altitude": {"N": "12.5"},
        "horizontal_accuracy": {"N": "25.0"},
        "vertical_accuracy": {"N": "5.0"},
        "confidence": {"N": "0.78"},
        "ssid": {"S": "DualAP_Test"},
        "frequency": {"N": "5180"},
        "vendor": {"S": "Aruba"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 3: Three APs - Trilateration (Well Distributed)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:03"},
        "version": {"S": "20240411-120200"},
        "latitude": {"N": "37.7751"},
        "longitude": {"N": "-122.4196"},
        "altitude": {"N": "15.0"},
        "horizontal_accuracy": {"N": "8.5"},
        "vertical_accuracy": {"N": "3.0"},
        "confidence": {"N": "0.92"},
        "ssid": {"S": "TriAP_Test"},
        "frequency": {"N": "2462"},
        "vendor": {"S": "Ubiquiti"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 4: Multiple APs - Maximum Likelihood (Clustered APs)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:04"},
        "version": {"S": "20240411-120300"},
        "latitude": {"N": "37.7752"},
        "longitude": {"N": "-122.4197"},
        "altitude": {"N": "18.0"},
        "horizontal_accuracy": {"N": "15.5"},
        "vertical_accuracy": {"N": "4.0"},
        "confidence": {"N": "0.85"},
        "ssid": {"S": "MultiAP_Test"},
        "frequency": {"N": "5240"},
        "vendor": {"S": "TP-Link"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "active"}
    }'

# Test Case 5: Weak Signals Scenario
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_addr": {"S": "00:11:22:33:44:05"},
        "version": {"S": "20240411-120400"},
        "latitude": {"N": "37.7753"},
        "longitude": {"N": "-122.4198"},
        "altitude": {"N": "20.0"},
        "horizontal_accuracy": {"N": "35.0"},
        "vertical_accuracy": {"N": "10.0"},
        "confidence": {"N": "0.45"},
        "ssid": {"S": "WeakSignal_Test"},
        "frequency": {"N": "2412"},
        "vendor": {"S": "Netgear"},
        "geohash": {"S": "9q8yyk"},
        "status": {"S": "warning"}
    }'

# Test Case 6-10: Collinear APs Scenario (Testing geometric distribution impact)
for i in {06..10}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7754 + ($i-6)*0.0001" | bc)'"},
            "longitude": {"N": "-122.4194"},
            "altitude": {"N": "'$(echo "15.0 + ($i-6)*2" | bc)'"},
            "horizontal_accuracy": {"N": "18.5"},
            "vertical_accuracy": {"N": "5.0"},
            "confidence": {"N": "0.72"},
            "ssid": {"S": "Collinear_Test_'$i'"},
            "frequency": {"N": "2437"},
            "vendor": {"S": "Cisco"},
            "signal_strength_avg": {"N": "'$(echo "-70.0 + ($i-6)*2" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 11-15: High Density AP Cluster (Testing maximum likelihood in dense environments)
for i in {11..15}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7760 + ($i-11)*0.0002" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4200 + ($i-11)*0.0002" | bc)'"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "12.0"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.88"},
            "ssid": {"S": "HighDensity_Test_'$i'"},
            "frequency": {"N": "5320"},
            "vendor": {"S": "Aruba"},
            "signal_strength_avg": {"N": "'$(echo "-65.0 + ($i-11)*1.5" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 16-20: Mixed Signal Quality Scenario
for i in {16..20}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7770 + ($i-16)*0.0003" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4210 + ($i-16)*0.0001" | bc)'"},
            "altitude": {"N": "'$(echo "30.0 + ($i-16)*1.5" | bc)'"},
            "horizontal_accuracy": {"N": "'$(echo "15.0 + ($i-16)*3" | bc)'"},
            "vertical_accuracy": {"N": "6.0"},
            "confidence": {"N": "'$(echo "0.90 - ($i-16)*0.1" | bc)'"},
            "ssid": {"S": "MixedSignal_Test_'$i'"},
            "frequency": {"N": "'$((2412 + (i-16)*5))'"},
            "vendor": {"S": "Ubiquiti"},
            "signal_strength_avg": {"N": "'$(echo "-60.0 - ($i-16)*5" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 21-25: Time Series Data (Testing temporal variations)
for i in {21..25}; do
    hour=$((12 + i-21))
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "37.7780"},
            "longitude": {"N": "-122.4220"},
            "altitude": {"N": "22.0"},
            "horizontal_accuracy": {"N": "'$(echo "10.0 + ($i-21)*2" | bc)'"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "'$(echo "0.85 - ($i-21)*0.05" | bc)'"},
            "ssid": {"S": "TimeSeries_Test"},
            "frequency": {"N": "5500"},
            "vendor": {"S": "TP-Link"},
            "signal_strength_avg": {"N": "'$(echo "-70.0 + ($hour-12)*2" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 26-30: Log-Distance Path Loss Algorithm Scenarios
for i in {26..30}; do
    distance=$((i-25))  # Distance in meters from reference point
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$(echo "37.7790 + ($distance*0.0001)" | bc)'"},
            "longitude": {"N": "-122.4230"},
            "altitude": {"N": "20.0"},
            "horizontal_accuracy": {"N": "'$(echo "5.0 + ($distance*2)" | bc)'"},
            "confidence": {"N": "'$(echo "0.95 - ($distance*0.05)" | bc)'"},
            "ssid": {"S": "PathLoss_Test_'$i'"},
            "frequency": {"N": "2462"},
            "signal_strength_avg": {"N": "'$(echo "-50.0 - ($distance*3)" | bc)'"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 31-35: Historical Data Analysis Scenarios
for i in {31..35}; do
    days_ago=$((i-30))
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "37.7800"},
            "longitude": {"N": "-122.4240"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "8.0"},
            "confidence": {"N": "0.88"},
            "ssid": {"S": "Historical_Test"},
            "frequency": {"N": "5500"},
            "signal_strength_avg": {"N": "-68.0"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "active"}
        }'
done

# Test Case 36-40: Error Cases and Edge Scenarios
for i in {36..40}; do
    case $((i-35)) in
        1)  # Invalid coordinates
            lat="91.0000"
            lon="-182.0000"
            status="error"
            ;;
        2)  # Expired TTL
            lat="37.7810"
            lon="-122.4250"
            status="expired"
            ;;
        3)  # Insufficient data
            lat="37.7811"
            lon="-122.4251"
            status="error"
            ;;
        4)  # Algorithm failure
            lat="37.7812"
            lon="-122.4252"
            status="error"
            ;;
        5)  # Calibration required
            lat="37.7813"
            lon="-122.4253"
            status="warning"
            ;;
    esac

    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_addr": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120'$i'00"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "0.0"},
            "horizontal_accuracy": {"N": "999.9"},
            "confidence": {"N": "0.1"},
            "ssid": {"S": "ErrorCase_'$i'"},
            "frequency": {"N": "2412"},
            "signal_strength_avg": {"N": "-99.9"},
            "geohash": {"S": "9q8yyk"},
            "status": {"S": "'$status'"}
        }'
done

echo -e "${GREEN}Test data loaded successfully.${NC}" 