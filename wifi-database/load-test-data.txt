#!/bin/bash

# Test Case 1: Single AP - Proximity Detection
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_address": {"S": "00:11:22:33:44:01"},
        "version": {"S": "20240411-120000"},
        "latitude": {"N": "37.7749"},
        "longitude": {"N": "-122.4194"},
        "altitude": {"N": "10.5"},
        "horizontal_accuracy": {"N": "50.0"},
        "vertical_accuracy": {"N": "8.0"},
        "confidence": {"N": "0.65"},
        "best_method": {"S": "proximity"},
        "methods_used": {"L": [{"S": "proximity"}]},
        "sample_count": {"N": "1"},
        "first_seen": {"S": "2024-04-11T12:00:00Z"},
        "last_seen": {"S": "2024-04-11T12:00:00Z"},
        "ssid": {"S": "SingleAP_Test"},
        "frequency": {"N": "2437"},
        "channel": {"N": "6"},
        "country_code": {"S": "US"},
        "vendor": {"S": "Cisco"},
        "signal_strength_avg": {"N": "-65.0"},
        "signal_strength_std": {"N": "0.0"},
        "readings_by_hour": {"M": {
            "12-18": {"N": "1"}
        }},
        "calculation_time": {"S": "2024-04-11T12:00:00Z"},
        "ttl": {"N": "1731647730"},
        "status": {"S": "active"},
        "calculation_duration_ms": {"N": "50"},
        "calculation_version": {"S": "1.3.0"},
        "geohash": {"S": "9q8yyk"}
    }'

# Test Case 2: Two APs - RSSI Ratio Method
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_address": {"S": "00:11:22:33:44:02"},
        "version": {"S": "20240411-120100"},
        "latitude": {"N": "37.7750"},
        "longitude": {"N": "-122.4195"},
        "altitude": {"N": "12.5"},
        "horizontal_accuracy": {"N": "25.0"},
        "vertical_accuracy": {"N": "5.0"},
        "confidence": {"N": "0.78"},
        "best_method": {"S": "rssi_ratio"},
        "methods_used": {"L": [{"S": "rssi_ratio"}, {"S": "weighted_centroid"}]},
        "sample_count": {"N": "15"},
        "first_seen": {"S": "2024-04-11T12:01:00Z"},
        "last_seen": {"S": "2024-04-11T12:01:00Z"},
        "ssid": {"S": "DualAP_Test"},
        "frequency": {"N": "5180"},
        "channel": {"N": "36"},
        "country_code": {"S": "US"},
        "vendor": {"S": "Aruba"},
        "signal_strength_avg": {"N": "-68.5"},
        "signal_strength_std": {"N": "2.1"},
        "readings_by_hour": {"M": {
            "12-18": {"N": "15"}
        }},
        "calculation_time": {"S": "2024-04-11T12:01:00Z"},
        "ttl": {"N": "1731647730"},
        "status": {"S": "active"},
        "calculation_duration_ms": {"N": "75"},
        "calculation_version": {"S": "1.3.0"},
        "geohash": {"S": "9q8yyk"}
    }'

# Test Case 3: Three APs - Trilateration (Well Distributed)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_address": {"S": "00:11:22:33:44:03"},
        "version": {"S": "20240411-120200"},
        "latitude": {"N": "37.7751"},
        "longitude": {"N": "-122.4196"},
        "altitude": {"N": "15.0"},
        "horizontal_accuracy": {"N": "8.5"},
        "vertical_accuracy": {"N": "3.0"},
        "confidence": {"N": "0.92"},
        "best_method": {"S": "trilateration"},
        "methods_used": {"L": [{"S": "trilateration"}, {"S": "weighted_centroid"}, {"S": "rssi_ratio"}]},
        "sample_count": {"N": "45"},
        "first_seen": {"S": "2024-04-11T12:02:00Z"},
        "last_seen": {"S": "2024-04-11T12:02:00Z"},
        "ssid": {"S": "TriAP_Test"},
        "frequency": {"N": "2462"},
        "channel": {"N": "11"},
        "country_code": {"S": "US"},
        "vendor": {"S": "Ubiquiti"},
        "signal_strength_avg": {"N": "-62.3"},
        "signal_strength_std": {"N": "1.8"},
        "readings_by_hour": {"M": {
            "12-18": {"N": "45"}
        }},
        "calculation_time": {"S": "2024-04-11T12:02:00Z"},
        "ttl": {"N": "1731647730"},
        "status": {"S": "active"},
        "calculation_duration_ms": {"N": "120"},
        "calculation_version": {"S": "1.3.0"},
        "geohash": {"S": "9q8yyk"}
    }'

# Test Case 4: Multiple APs - Maximum Likelihood (Clustered APs)
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_address": {"S": "00:11:22:33:44:04"},
        "version": {"S": "20240411-120300"},
        "latitude": {"N": "37.7752"},
        "longitude": {"N": "-122.4197"},
        "altitude": {"N": "18.0"},
        "horizontal_accuracy": {"N": "15.5"},
        "vertical_accuracy": {"N": "4.5"},
        "confidence": {"N": "0.85"},
        "best_method": {"S": "maximum_likelihood"},
        "methods_used": {"L": [{"S": "maximum_likelihood"}, {"S": "weighted_centroid"}]},
        "sample_count": {"N": "60"},
        "first_seen": {"S": "2024-04-11T12:03:00Z"},
        "last_seen": {"S": "2024-04-11T12:03:00Z"},
        "ssid": {"S": "MultiAP_Test"},
        "frequency": {"N": "5240"},
        "channel": {"N": "48"},
        "country_code": {"S": "US"},
        "vendor": {"S": "Meraki"},
        "signal_strength_avg": {"N": "-71.2"},
        "signal_strength_std": {"N": "3.5"},
        "readings_by_hour": {"M": {
            "12-18": {"N": "60"}
        }},
        "calculation_time": {"S": "2024-04-11T12:03:00Z"},
        "ttl": {"N": "1731647730"},
        "status": {"S": "active"},
        "calculation_duration_ms": {"N": "180"},
        "calculation_version": {"S": "1.3.0"},
        "geohash": {"S": "9q8yyk"}
    }'

# Test Case 5: Weak Signals Scenario
aws dynamodb put-item \
    --table-name wifi_access_points \
    --endpoint-url http://localhost:8000 \
    --profile dynamodb-local \
    --item '{
        "mac_address": {"S": "00:11:22:33:44:05"},
        "version": {"S": "20240411-120400"},
        "latitude": {"N": "37.7753"},
        "longitude": {"N": "-122.4198"},
        "altitude": {"N": "20.0"},
        "horizontal_accuracy": {"N": "35.0"},
        "vertical_accuracy": {"N": "12.0"},
        "confidence": {"N": "0.45"},
        "best_method": {"S": "maximum_likelihood"},
        "methods_used": {"L": [{"S": "maximum_likelihood"}, {"S": "weighted_centroid"}, {"S": "rssi_ratio"}]},
        "sample_count": {"N": "30"},
        "first_seen": {"S": "2024-04-11T12:04:00Z"},
        "last_seen": {"S": "2024-04-11T12:04:00Z"},
        "ssid": {"S": "WeakSignal_Test"},
        "frequency": {"N": "2412"},
        "channel": {"N": "1"},
        "country_code": {"S": "US"},
        "vendor": {"S": "HPE-Aruba"},
        "signal_strength_avg": {"N": "-85.5"},
        "signal_strength_std": {"N": "5.2"},
        "readings_by_hour": {"M": {
            "12-18": {"N": "30"}
        }},
        "calculation_time": {"S": "2024-04-11T12:04:00Z"},
        "ttl": {"N": "1731647730"},
        "status": {"S": "active"},
        "calculation_duration_ms": {"N": "250"},
        "calculation_version": {"S": "1.3.0"},
        "geohash": {"S": "9q8yyk"}
    }'

# Test Case 6-10: Collinear APs Scenario (Testing geometric distribution impact)
for i in {06..10}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-1205'$((i-6))'0"},
            "latitude": {"N": "'$(echo "37.7754 + ($i-6)*0.0001" | bc)'"},
            "longitude": {"N": "-122.4194"},
            "altitude": {"N": "'$(echo "15.0 + ($i-6)*2" | bc)'"},
            "horizontal_accuracy": {"N": "18.5"},
            "vertical_accuracy": {"N": "6.0"},
            "confidence": {"N": "0.72"},
            "best_method": {"S": "weighted_centroid"},
            "methods_used": {"L": [{"S": "weighted_centroid"}, {"S": "rssi_ratio"}]},
            "sample_count": {"N": "'$((25 + (i-6)*5))'"},
            "first_seen": {"S": "2024-04-11T12:05:00Z"},
            "last_seen": {"S": "2024-04-11T12:05:00Z"},
            "ssid": {"S": "Collinear_Test_'$i'"},
            "frequency": {"N": "2437"},
            "channel": {"N": "6"},
            "country_code": {"S": "US"},
            "vendor": {"S": "Ruckus"},
            "signal_strength_avg": {"N": "'$(echo "-70.0 + ($i-6)*2" | bc)'"},
            "signal_strength_std": {"N": "2.8"},
            "readings_by_hour": {"M": {
                "12-18": {"N": "'$((25 + (i-6)*5))'"}
            }},
            "calculation_time": {"S": "2024-04-11T12:05:00Z"},
            "ttl": {"N": "1731647730"},
            "status": {"S": "active"},
            "calculation_duration_ms": {"N": "'$((100 + (i-6)*10))'"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
        }'
done

# Test Case 11-15: High Density AP Cluster (Testing maximum likelihood in dense environments)
for i in {11..15}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-1206'$((i-11))'0"},
            "latitude": {"N": "'$(echo "37.7760 + ($i-11)*0.0002" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4200 + ($i-11)*0.0002" | bc)'"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "12.0"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "0.88"},
            "best_method": {"S": "maximum_likelihood"},
            "methods_used": {"L": [{"S": "maximum_likelihood"}, {"S": "trilateration"}, {"S": "weighted_centroid"}]},
            "sample_count": {"N": "'$((80 + (i-11)*10))'"},
            "first_seen": {"S": "2024-04-11T12:06:00Z"},
            "last_seen": {"S": "2024-04-11T12:06:00Z"},
            "ssid": {"S": "HighDensity_Test_'$i'"},
            "frequency": {"N": "5320"},
            "channel": {"N": "64"},
            "country_code": {"S": "US"},
            "vendor": {"S": "Aruba"},
            "signal_strength_avg": {"N": "'$(echo "-65.0 + ($i-11)*1.5" | bc)'"},
            "signal_strength_std": {"N": "1.5"},
            "readings_by_hour": {"M": {
                "12-18": {"N": "'$((80 + (i-11)*10))'"}
            }},
            "calculation_time": {"S": "2024-04-11T12:06:00Z"},
            "ttl": {"N": "1731647730"},
            "status": {"S": "active"},
            "calculation_duration_ms": {"N": "'$((150 + (i-11)*15))'"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
        }'
done

# Test Case 16-20: Mixed Signal Quality Scenario (Testing algorithm selection based on signal quality)
for i in {16..20}; do
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-1207'$((i-16))'0"},
            "latitude": {"N": "'$(echo "37.7770 + ($i-16)*0.0003" | bc)'"},
            "longitude": {"N": "'$(echo "-122.4210 + ($i-16)*0.0001" | bc)'"},
            "altitude": {"N": "'$(echo "30.0 + ($i-16)*1.5" | bc)'"},
            "horizontal_accuracy": {"N": "'$(echo "15.0 + ($i-16)*3" | bc)'"},
            "vertical_accuracy": {"N": "'$(echo "5.0 + ($i-16)" | bc)'"},
            "confidence": {"N": "'$(echo "0.90 - ($i-16)*0.1" | bc)'"},
            "best_method": {"S": "'$(if [ $((i-16)) -lt 2 ]; then echo "trilateration"; elif [ $((i-16)) -lt 4 ]; then echo "weighted_centroid"; else echo "maximum_likelihood"; fi)'"},
            "methods_used": {"L": [{"S": "trilateration"}, {"S": "weighted_centroid"}, {"S": "maximum_likelihood"}]},
            "sample_count": {"N": "'$((50 + (i-16)*8))'"},
            "first_seen": {"S": "2024-04-11T12:07:00Z"},
            "last_seen": {"S": "2024-04-11T12:07:00Z"},
            "ssid": {"S": "MixedSignal_Test_'$i'"},
            "frequency": {"N": "'$((2412 + (i-16)*5))'"},
            "channel": {"N": "'$((1 + (i-16)))'"},
            "country_code": {"S": "US"},
            "vendor": {"S": "'$(if [ $((i-16)) -lt 2 ]; then echo "Cisco"; elif [ $((i-16)) -lt 4 ]; then echo "Meraki"; else echo "Ubiquiti"; fi)'"},
            "signal_strength_avg": {"N": "'$(echo "-60.0 - ($i-16)*5" | bc)'"},
            "signal_strength_std": {"N": "'$(echo "2.0 + ($i-16)*0.5" | bc)'"},
            "readings_by_hour": {"M": {
                "12-18": {"N": "'$((50 + (i-16)*8))'"}
            }},
            "calculation_time": {"S": "2024-04-11T12:07:00Z"},
            "ttl": {"N": "1731647730"},
            "status": {"S": "active"},
            "calculation_duration_ms": {"N": "'$((120 + (i-16)*20))'"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
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
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-'$hour'0000"},
            "latitude": {"N": "37.7780"},
            "longitude": {"N": "-122.4220"},
            "altitude": {"N": "22.0"},
            "horizontal_accuracy": {"N": "'$(echo "10.0 + ($i-21)*2" | bc)'"},
            "vertical_accuracy": {"N": "4.0"},
            "confidence": {"N": "'$(echo "0.85 - ($i-21)*0.05" | bc)'"},
            "best_method": {"S": "trilateration"},
            "methods_used": {"L": [{"S": "trilateration"}, {"S": "weighted_centroid"}]},
            "sample_count": {"N": "'$((40 + (i-21)*15))'"},
            "first_seen": {"S": "2024-04-11T'$hour':00:00Z"},
            "last_seen": {"S": "2024-04-11T'$hour':59:59Z"},
            "ssid": {"S": "TimeSeries_Test"},
            "frequency": {"N": "5500"},
            "channel": {"N": "100"},
            "country_code": {"S": "US"},
            "vendor": {"S": "Cisco"},
            "signal_strength_avg": {"N": "'$(echo "-70.0 + ($hour-12)*2" | bc)'"},
            "signal_strength_std": {"N": "'$(echo "2.5 + ($i-21)*0.3" | bc)'"},
            "readings_by_hour": {"M": {
                "'$hour'-'$((hour+1))'": {"N": "'$((40 + (i-21)*15))'"}
            }},
            "calculation_time": {"S": "2024-04-11T'$hour':59:59Z"},
            "ttl": {"N": "1731647730"},
            "status": {"S": "active"},
            "calculation_duration_ms": {"N": "'$((100 + (i-21)*10))'"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
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
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-1208'$((i-26))'0"},
            "latitude": {"N": "'$(echo "37.7790 + ($distance*0.0001)" | bc)'"},
            "longitude": {"N": "-122.4230"},
            "altitude": {"N": "20.0"},
            "horizontal_accuracy": {"N": "'$(echo "5.0 + ($distance*2)" | bc)'"},
            "vertical_accuracy": {"N": "3.0"},
            "confidence": {"N": "'$(echo "0.95 - ($distance*0.05)" | bc)'"},
            "best_method": {"S": "log_distance_path_loss"},
            "methods_used": {"L": [{"S": "log_distance_path_loss"}, {"S": "weighted_centroid"}]},
            "sample_count": {"N": "'$((100 - $distance*10))'"},
            "first_seen": {"S": "2024-04-11T12:08:00Z"},
            "last_seen": {"S": "2024-04-11T12:08:00Z"},
            "ssid": {"S": "PathLoss_Test_'$i'"},
            "frequency": {"N": "2462"},
            "channel": {"N": "11"},
            "country_code": {"S": "US"},
            "vendor": {"S": "Cisco"},
            "signal_strength_avg": {"N": "'$(echo "-50.0 - ($distance*3)" | bc)'"},
            "signal_strength_std": {"N": "'$(echo "1.0 + ($distance*0.2)" | bc)'"},
            "readings_by_hour": {"M": {
                "12-18": {"N": "'$((100 - $distance*10))'"}
            }},
            "calculation_time": {"S": "2024-04-11T12:08:00Z"},
            "ttl": {"N": "1731647730"},
            "status": {"S": "active"},
            "calculation_duration_ms": {"N": "'$((80 + $distance*5))'"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
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
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "'$(date -v-${days_ago}d +%Y%m%d)'-120000"},
            "latitude": {"N": "37.7800"},
            "longitude": {"N": "-122.4240"},
            "altitude": {"N": "25.0"},
            "horizontal_accuracy": {"N": "8.0"},
            "vertical_accuracy": {"N": "3.0"},
            "confidence": {"N": "0.88"},
            "best_method": {"S": "hybrid"},
            "methods_used": {"L": [{"S": "maximum_likelihood"}, {"S": "trilateration"}, {"S": "weighted_centroid"}]},
            "sample_count": {"N": "200"},
            "first_seen": {"S": "'$(date -v-${days_ago}d +%Y-%m-%d)'T12:00:00Z"},
            "last_seen": {"S": "'$(date -v-${days_ago}d +%Y-%m-%d)'T23:59:59Z"},
            "ssid": {"S": "Historical_Test"},
            "frequency": {"N": "5500"},
            "channel": {"N": "100"},
            "country_code": {"S": "US"},
            "vendor": {"S": "Aruba"},
            "signal_strength_avg": {"N": "-68.0"},
            "signal_strength_std": {"N": "2.0"},
            "readings_by_hour": {"M": {
                "00-06": {"N": "50"},
                "06-12": {"N": "50"},
                "12-18": {"N": "50"},
                "18-24": {"N": "50"}
            }},
            "calculation_time": {"S": "'$(date -v-${days_ago}d +%Y-%m-%d)'T23:59:59Z"},
            "ttl": {"N": "'$(date -v+30d +%s)'"},
            "status": {"S": "active"},
            "calculation_duration_ms": {"N": "150"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
        }'
done

# Test Case 36-40: Error Cases and Edge Scenarios
for i in {36..40}; do
    case $((i-35)) in
        1)  # Invalid coordinates
            lat="91.0000"
            lon="-182.0000"
            status="error"
            error_type="invalid_coordinates"
            ;;
        2)  # Expired TTL
            lat="37.7810"
            lon="-122.4250"
            status="expired"
            error_type="ttl_expired"
            ;;
        3)  # Insufficient data
            lat="37.7811"
            lon="-122.4251"
            status="error"
            error_type="insufficient_data"
            ;;
        4)  # Algorithm failure
            lat="37.7812"
            lon="-122.4252"
            status="error"
            error_type="algorithm_failure"
            ;;
        5)  # Calibration required
            lat="37.7813"
            lon="-122.4253"
            status="warning"
            error_type="calibration_needed"
            ;;
    esac
    
    aws dynamodb put-item \
        --table-name wifi_access_points \
        --endpoint-url http://localhost:8000 \
        --profile dynamodb-local \
        --item '{
            "mac_address": {"S": "00:11:22:33:44:'$i'"},
            "version": {"S": "20240411-120900"},
            "latitude": {"N": "'$lat'"},
            "longitude": {"N": "'$lon'"},
            "altitude": {"N": "0.0"},
            "horizontal_accuracy": {"N": "999.9"},
            "vertical_accuracy": {"N": "999.9"},
            "confidence": {"N": "0.1"},
            "best_method": {"S": "none"},
            "methods_used": {"L": []},
            "sample_count": {"N": "0"},
            "first_seen": {"S": "2024-04-11T12:09:00Z"},
            "last_seen": {"S": "2024-04-11T12:09:00Z"},
            "ssid": {"S": "ErrorCase_'$error_type'"},
            "frequency": {"N": "2412"},
            "channel": {"N": "1"},
            "country_code": {"S": "US"},
            "vendor": {"S": "Unknown"},
            "signal_strength_avg": {"N": "-99.9"},
            "signal_strength_std": {"N": "9.9"},
            "readings_by_hour": {"M": {
                "12-18": {"N": "0"}
            }},
            "calculation_time": {"S": "2024-04-11T12:09:00Z"},
            "ttl": {"N": "'$(if [ "$status" = "expired" ]; then echo "1712345678"; else echo "1731647730"; fi)'"},
            "status": {"S": "'$status'"},
            "error_type": {"S": "'$error_type'"},
            "calculation_duration_ms": {"N": "999"},
            "calculation_version": {"S": "1.3.0"},
            "geohash": {"S": "9q8yyk"}
        }'
done 