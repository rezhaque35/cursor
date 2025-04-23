# Problem Statement & Requirements
Create a hybrid WiFi positioning system that combines multiple algorithms to provide accurate indoor positioning using WiFi access points.

## Key Requirements & Constraints
1. Support multiple positioning methods
2. Adapt to different scenarios (varying signal strengths, AP densities)
3. Provide accuracy metrics and confidence levels
4. Handle both 2D and 3D positioning
5. Support real-time positioning updates
6. Handle temporal variations in signal strength
7. **Single Measurement Constraint**: System must work with single measurements, not requiring historical or multiple measurements
8. **Limited Input Data**: Primary inputs are:
   - Required: RSSI and frequency
   - Optional: Link speed, channel width
   - No environmental/external data available

# Revised Algorithm Selection Based on Available Data

## Primary Algorithms (Using only RSSI and Frequency)
1. **Proximity Detection**
   - Input: RSSI only
   - Simple distance estimation using path loss model
   - Accuracy: Low (±10-15m)

2. **RSSI Ratio Method**
   - Input: RSSI from multiple APs
   - No absolute calibration needed
   - Accuracy: Medium (±5-8m)

3. **Log-Distance Path Loss Model**
   - Input: RSSI and frequency
   - Basic propagation modeling
   - Accuracy: Medium (±6-10m)

4. **Weighted Centroid**
   - Input: RSSI from multiple APs
   - Signal strength weighted positioning
   - Accuracy: Medium (±5-7m)

## Enhanced Algorithms (When Additional Data Available)
1. **Modified Trilateration**
   - Required: RSSI, frequency
   - Optional: Channel width for better path loss estimation
   - Accuracy: Medium-High (±4-7m)

2. **Maximum Likelihood with Limited Data**
   - Required: RSSI, frequency
   - Optional: Link speed for connection quality assessment
   - Accuracy: Medium-High (±3-6m)

# Revised Hybrid Algorithm
The system dynamically selects and combines algorithms based on:
1. Number of visible APs
2. Signal quality (RSSI values)
3. AP geometry
4. Available optional parameters (link speed, channel width)

## Selection Logic:
- Single AP → Proximity Detection
- 2 APs → RSSI Ratio
- 3+ APs (good geometry) → Modified Trilateration (if channel width available) or Log-Distance Path Loss
- 3+ APs (poor geometry) → Weighted Centroid
- 5+ APs (strong signals) → Maximum Likelihood with Limited Data
- Multiple methods → Weighted combination based on signal quality and geometry

# DynamoDB Table Schema
**Table Name**: `wifi_access_points`

## Key Structure:
- Partition Key: `mac_address` (String)
- No Sort Key 

## Global Secondary Indexes:
1. **GeohashIndex**
   - Partition Key: `geohash`
   - Sort Key: `mac_address`

2. **SSIDIndex**
   - Partition Key: `ssid`
   - Sort Key: `mac_address`

## Key Attributes:
```json
{
  "mac_address": String,
  "version": String,
  "latitude": Number,
  "longitude": Number,
  "altitude": Number,
  "horizontal_accuracy": Number,
  "vertical_accuracy": Number,
  "confidence": Number,
  "best_method": String,
  "methods_used": List[String],
  "sample_count": Number,
  "signal_strength_avg": Number,
  "signal_strength_std": Number,
  "geohash": String
}
```

# Test Data Coverage
25 test cases covering various scenarios:

1. **Basic Scenarios (Cases 1-5)**
   - Single AP (Proximity)
   - Two APs (RSSI Ratio)
   - Three APs (Trilateration)
   - Multiple APs (Maximum Likelihood)
   - Weak Signals

2. **Collinear APs (Cases 6-10)**
   - Tests geometric distribution impact
   - Linear AP arrangement
   - Progressive signal strength changes
   - Tests weighted centroid method
   - Validates geometric dilution of precision handling

3. **High Density Cluster (Cases 11-15)**
   - Tests maximum likelihood in dense environments
   - 80-120 samples per AP
   - High confidence scenarios
   - Multiple overlapping APs
   - Strong signal strengths (-65 to -58 dBm)

4. **Mixed Signal Quality (Cases 16-20)**
   - Tests algorithm selection logic
   - Progressive signal degradation
   - Different vendors (Cisco, Meraki, Ubiquiti)
   - Varying confidence levels (0.9 to 0.5)
   - Multiple frequency bands

5. **Time Series Data (Cases 21-25)**
   - Tests temporal variations
   - Same location, different times
   - Signal strength variations
   - Sample count variations
   - Confidence level changes

All test data is stored in `wifi-database/load-test-data.sh` and can be reloaded into DynamoDB Local using:
```bash
./wifi-database/load-test-data.sh
```

The test data is designed to validate:
- Algorithm selection logic
- Accuracy in different scenarios
- Handling of poor geometry
- Response to signal quality variations
- Temporal stability
- Multi-vendor compatibility
- 3D positioning accuracy
- Confidence calculation
- Error handling and degraded conditions 