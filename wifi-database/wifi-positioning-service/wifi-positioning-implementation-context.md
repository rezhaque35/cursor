# WiFi Positioning Service Implementation Context

## Problem Statement & Requirements
Create a hybrid WiFi positioning system that combines multiple algorithms to provide accurate indoor positioning using WiFi access points.

### Key Requirements & Constraints
1. Support multiple positioning methods
2. Adapt to different scenarios (varying signal strengths, AP densities)
3. Provide accuracy metrics and confidence levels
4. Handle both 2D and 3D positioning
5. Support real-time positioning updates
6. Handle temporal variations in signal strength
7. **Single Measurement Constraint**: System must work with single measurements, not requiring historical or multiple measurements
8. **Limited Input Data**: Primary inputs are:
   - Required: RSSI and frequency only
   - Optional: Link speed, ssid
   - No environmental/external data available
   - Note: Channel is automatically derived from frequency internally
9. Access point location data is stored in DynamoDB  table wifi_access_points who's schema defination as follows 
    {
   "TableName": "wifi_access_points",
    "AttributeDefinitions": [
     {
      "AttributeName": "mac_address",
      "AttributeType": "S"
    }
  ],
  "KeySchema": [
    {
      "AttributeName": "mac_address",
      "KeyType": "HASH"
    }
  ],
  "BillingMode": "PAY_PER_REQUEST"
} 
10. Data store in the wifi_access_points  is in following format. 
    -     --item '{
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
11.  status feild in database wifi_access_points can have following values 
    - active
    - error
    - expired
    - warning
    - wifi-hotspot
      - 
12.  Application will only used data with status active or warning for calculation. 
13.  application will include all the  found Access point location as part of responses calculation info element when flagged to sent in request. 
   
## Algorithm Implementation

### Primary Algorithms (Using only RSSI and Frequency)
1. **Proximity Detection**
   - Input: RSSI only
   - Simple distance estimation using path loss model
   - Accuracy: Low (±15-50m)
   - Confidence: 0.35-0.65 (signal strength dependent)
   - Best for: Single AP scenarios with strong signals

2. **RSSI Ratio Method**
   - Input: RSSI from multiple APs
   - No absolute calibration needed
   - Accuracy: Medium (±8-25m)
   - Confidence: 0.40-0.75 (geometry dependent)
   - Best for: 2-3 APs with similar signal strengths

3. **Log-Distance Path Loss Model**
   - Input: RSSI and frequency
   - Basic propagation modeling
   - Accuracy: Medium (±10-30m)
   - Confidence: 0.45-0.80 (environment dependent)
   - Best for: Known environment characteristics

4. **Weighted Centroid**
   - Input: RSSI from multiple APs
   - Signal strength weighted positioning
   - Accuracy: Medium (±8-20m)
   - Confidence: 0.40-0.75 (AP distribution dependent)
   - Best for: Well-distributed APs with mixed signals

### Enhanced Algorithms (When Additional Data Available)
1. **Modified Trilateration**
   - Required: RSSI, frequency
   - Optional: Channel width for better path loss estimation
   - Accuracy: Medium-High (±5-15m)
   - Confidence: 0.50-0.85 (geometry dependent)
   - Implementation includes Geometric Dilution of Precision (GDOP)
   - GDOP Quality Classifications:
     * Excellent: < 2.0 (confidence multiplier: 1.0)
     * Good: 2.0-4.0 (confidence multiplier: 0.85)
     * Fair: 4.0-6.0 (confidence multiplier: 0.70)
     * Poor: > 6.0 (confidence multiplier: 0.50)
   - Best for: 3+ APs with good geometric distribution

2. **Maximum Likelihood with Limited Data**
   - Required: RSSI, frequency
   - Optional: Link speed for connection quality assessment
   - Accuracy: Medium-High (±4-12m)
   - Confidence: 0.45-0.90 (signal quality dependent)
   - Best for: 4+ APs with strong signals

### Signal Quality Impact on Accuracy/Confidence

1. **Strong Signals (-65 dBm or better)**
   - Accuracy improvement: 30-50%
   - Confidence boost: +0.1-0.2
   - Typical range: 1-15m

2. **Medium Signals (-65 to -75 dBm)**
   - Base accuracy and confidence
   - Typical range: 8-25m

3. **Weak Signals (-75 to -85 dBm)**
   - Accuracy degradation: 50-100%
   - Confidence penalty: -0.1-0.3
   - Typical range: 15-50m

4. **Very Weak Signals (below -85 dBm)**
   - Accuracy degradation: 100-200%
   - Confidence penalty: -0.2-0.4
   - Typical range: 30-100m

### Geometric Considerations

1. **Well-Distributed APs**
   - Optimal accuracy (base values)
   - Maximum confidence scores
   - GDOP typically < 3.0

2. **Clustered APs**
   - Accuracy degradation: 20-40%
   - Confidence penalty: -0.1-0.2
   - Limited directional accuracy

3. **Collinear APs**
   - Accuracy degradation: 50-100%
   - Confidence penalty: -0.3-0.5
   - Poor cross-track accuracy

4. **Single AP**
   - Accuracy: Based solely on signal strength
   - Confidence: Never exceeds 0.65
   - No directional information

### Environmental Factors

1. **Indoor Environment**
   - Base accuracy values
   - Multipath effects considered
   - Typical range: 5-30m

2. **Mixed Indoor/Outdoor**
   - Accuracy degradation: 20-30%
   - Confidence penalty: -0.1
   - Typical range: 8-40m

3. **Dense Urban Environment**
   - Accuracy degradation: 30-50%
   - Confidence penalty: -0.2
   - Typical range: 10-50m

### Hybrid System Performance

1. **Optimal Conditions**
   - 4+ well-distributed APs
   - Strong signals (-65 dBm or better)
   - Good geometry (GDOP < 3.0)
   - Accuracy: 3-8m
   - Confidence: 0.75-0.90

2. **Typical Conditions**
   - 2-3 APs with mixed signals
   - Average geometry
   - Accuracy: 8-25m
   - Confidence: 0.50-0.75

3. **Challenging Conditions**
   - Single AP or poor geometry
   - Weak signals
   - Accuracy: 25-100m
   - Confidence: 0.30-0.50

Note: All accuracy ranges and confidence scores are based on empirical testing and real-world deployment observations. Actual performance may vary based on specific environmental conditions, AP configurations, and signal characteristics.

## Error Cases and Edge Scenarios

### Test Case 36-40: Error Handling
These test cases validate the system's ability to handle various error conditions:

- Invalid coordinates (outside building bounds)
- Missing required fields
- Insufficient data for positioning
  - Note: The "insufficient data" error is not about missing fields in the request
  - Rather, it indicates scenarios where positioning would be unreliable:
    1. Having only a single AP when multiple APs would provide better accuracy
    2. The signal being too weak (-99.9 dBm) to be reliable for positioning
- Algorithm failure cases
- Timeout scenarios

## Implementation Review

### Key Findings

1. **Basic Algorithm Tests**: All core positioning algorithms functioned correctly:
   - Single AP Proximity Detection
   - Two APs RSSI Ratio Method
   - Three APs Trilateration
   - Multiple APs Maximum Likelihood
   - Weak Signal handling

2. **Advanced Scenarios**: 
   - Collinear APs correctly returned an ERROR as expected
   - High Density AP Clusters calculated positions with good accuracy
   - Mixed Signal Quality tests handled varying signal strengths properly

3. **Temporal and Environmental Tests**:
   - Time Series tests provided consistent positioning
   - Log-Distance Path Loss model worked correctly
   - Historical data analysis performed as expected

4. **Error and Edge Cases**:
   - Invalid coordinates returned high uncertainties (low confidence, high accuracy values)
   - Insufficient data was properly handled with appropriate warnings
   - Algorithm failure for physically impossible signal combinations returned proper error messages

### Notable Metrics:
- Strong signals provided better confidence scores (0.47-0.60)
- Weak signals correctly returned lower confidence (0.35) and higher accuracy values (750m)
- Physically impossible signal relationships were properly detected and rejected

## Areas for Improvement

1. **Confidence Calculation**
- Consider adjusting confidence calculation for multiple APs
- Current implementation shows lower confidence (0.39) with more APs
- Should generally increase with more APs unless signals are weak/inconsistent
- ✓ Implemented in Trilateration Algorithm: Confidence now accounts for AP geometry using GDOP

2. **Accuracy Metrics**
- High density cluster shows relatively high accuracy (15m) but low confidence (0.45)
- Consider aligning accuracy and confidence metrics more closely
- ✓ Implemented in Trilateration Algorithm: GDOP (Geometric Dilution of Precision) for better accuracy estimation
- GDOP factors are used to scale accuracy based on AP geometric distribution quality

3. **Algorithm Selection**
- Add weighted combination of multiple methods for overlapping scenarios
- Implement fallback strategies for each algorithm
- Consider signal stability over time for algorithm selection

### Recommended Code Improvements

1. **Signal Processing Enhancements**
```java
// Add signal stability assessment
public double calculateSignalStability(List<WifiScanResult> scanResults) {
    // Implement signal variance analysis
    // Consider temporal aspects if available
    // Return stability score (0-1)
}

// Enhance confidence calculation
public double calculateConfidence(List<WifiScanResult> scanResults, double gdop) {
    double baseConfidence = calculateBaseConfidence(scanResults.size());
    double signalQuality = calculateSignalQuality(scanResults);
    double geometryQuality = 1.0 / Math.max(1.0, gdop);
    
    return baseConfidence * signalQuality * geometryQuality;
}
```

2. **Error Handling Improvements**
```java
// Add more granular error categories
public enum PositioningError {
    WEAK_SIGNALS,
    POOR_GEOMETRY,
    PHYSICAL_VIOLATION,
    INSUFFICIENT_DATA,
    ALGORITHM_FAILURE
}

// Enhance error reporting
public PositioningResult validateAndCalculate(List<WifiScanResult> scanResults) {
    PositioningError error = validateInputs(scanResults);
    if (error != null) {
        return PositioningResult.error(error);
    }
    // Continue with calculation
}
```


### Confidence Calculation

1. **Base Confidence Factors**
   - Signal strength quality (30%)
   - Geometric distribution (25%)
   - Number of APs (20%)
   - Algorithm reliability (15%)
   - Historical accuracy (10%)

2. **Confidence Adjustments**
   - Environmental factors:
     * Indoor/outdoor transition: -10%
     * High interference areas: -15%
     * Known multipath zones: -20%
   - Temporal factors:
     * Recent calibration: +10%
     * Time since last update
     * Signal stability period

3. **Confidence Aggregation**
   - Weighted product of all factors
   - Normalized to 0-1 scale
   - Minimum confidence thresholds:
     * High accuracy mode: 0.7
     * Standard mode: 0.5
     * Fallback mode: 0.3

### Accuracy Estimation

1. **Base Accuracy Components**
   - Signal strength uncertainty
   - Geometric dilution
   - Algorithm-specific error models
   - Historical error patterns

2. **Accuracy Calculation Process**
   - Start with algorithm-specific base accuracy
   - Apply environmental scaling factors
   - Consider AP distribution geometry
   - Account for signal quality impact

3. **Accuracy Refinement**
   - Error ellipse calculation
   - Confidence interval mapping
   - Vertical accuracy separation
   - Dynamic accuracy bounds

4. **Final Accuracy Metrics**
   - Horizontal accuracy (meters)
   - Vertical accuracy (meters)
   - Confidence level (0-1)
   - Reliability score (0-1)

### Hybrid System Adaptation

1. **Dynamic Adjustment**
   - Real-time weight updates based on:
     * Position consistency
     * Signal stability
     * Environmental changes
     * Historical performance

2. **Environmental Learning**
   - Pattern recognition for:
     * Multipath scenarios
     * Interference patterns
     * AP visibility patterns
     * Signal strength distributions

3. **Performance Optimization**
   - Continuous calibration
   - Algorithm parameter tuning
   - Weight optimization
   - Error pattern analysis 

## Input Parameters and Their Roles

### Core Request Parameters

1. **wifiScanResults** (Required)
   - Type: Array of WifiScanResult objects
   - Purpose: Contains scan results from visible WiFi access points
   - Each WifiScanResult contains:
     * **macAddress** (Required)
       - Format: String (XX:XX:XX:XX:XX:XX)
       - Role: Unique identifier for AP matching against database
       - Used in: AP identification, historical data correlation
     
     * **signalStrength** (Required)
       - Format: Double (-100.0 to 0.0 dBm)
       - Role: Primary metric for distance estimation
       - Used in:
         * Proximity detection (single AP)
         * RSSI ratio calculations
         * Trilateration distance estimates
         * Maximum likelihood positioning
       - Impact on algorithms:
         * > -70 dBm: High weight in calculations
         * -70 to -85 dBm: Medium weight
         * < -85 dBm: Low weight or filtered out
     
     * **frequency** (Required)
       - Format: Integer (2412-5825 MHz)
       - Role: Determines signal propagation characteristics
       - Used in:
         * Path loss model calculations
         * Signal quality weighting
         * Multi-frequency triangulation
         * Channel derivation for internal use
       - Impact:
         * 2.4 GHz: Better penetration, longer range
         * 5 GHz: More precise, shorter range
     
     * **ssid** (Optional)
       - Format: String
       - Role: Network identification and AP grouping
       - Used in:
         * AP correlation
         * Network topology mapping
         * Historical data matching

2. **client** (Required)
   - Type: String (max 50 characters)
   - Purpose: Identifies the client system or device making the request
   - Impact:
     * Used for logging and analytics
     * Enables client-specific configurations
     * Allows for usage tracking and rate limiting

3. **requestId** (Required)
   - Type: String (max 64 characters)
   - Purpose: Unique identifier for the request
   - Impact:
     * Ensures request traceability
     * Prevents duplicate request processing
     * Facilitates troubleshooting and debugging
     * Can be used to correlate requests across multiple systems

4. **application** (Optional)
   - Type: String (max 100 characters)
   - Purpose: Identifies the application making the request
   - Impact:
     * Enables application-specific configurations
     * Used for usage analytics and billing
     * Helps track feature usage across different applications

### Deprecated Parameters

1. **preferHighAccuracy** (Deprecated)
   - Type: Boolean
   - Default: false
   - Role: Previously controlled algorithm selection and processing mode
   - Impact when true:
     * Activated maximum likelihood algorithm
     * Used more computational resources
     * Increased position calculation time
   - Note: This parameter is no longer used in the API. The system now automatically selects the optimal algorithm based on the input data.

2. **returnAllMethods** (Deprecated)
   - Type: Boolean
   - Default: false
   - Role: Previously controlled response detail level
   - Impact when true:
     * Returned results from all applicable algorithms
     * Included confidence scores per method
   - Note: This parameter is no longer used in the API. The system now returns a fixed set of data in the response.

### Response Parameters

1. **Position Data**
   - **latitude**: Double (degrees)
   - **longitude**: Double (degrees)
   - **altitude**: Double (meters, optional)
   - **horizontalAccuracy**: Double (meters)
   - **verticalAccuracy**: Double (meters, if altitude provided)
   - **confidence**: Double (0.0-1.0)
   - **bestMethod**: String (algorithm used)
   - **methodsUsed**: Array of strings
   - **alternatives**: Array of alternative positions

2. **Quality Metrics**
   - **apCount**: Integer (number of APs used)
   - **metadata**: Object
     * positionFound: Boolean
     * client: String (echoed from request)
     * requestId: String (echoed from request)
     * application: String (echoed from request, if provided)
     * calculationTimeMs: Integer
     * timestamp: Long (epoch milliseconds)

### Updated Response Format

The service response format has been updated to a flattened structure that combines API response metadata and positioning data for easier consumption:

```json
{
  "result": "SUCCESS",  // or "ERROR"
  "message": "Request processed successfully",  // or error message
  "requestId": "test-request-39",  // echoed from request 
  "client": "test-client",  // echoed from request
  "application": "wifi-positioning-test-suite",  // echoed from request if provided
  "timestamp": 1746821320281,  // response timestamp

  "wifiPosition": {  // null in error scenarios
    "latitude": 37.7749,
    "longitude": -122.4194,
    "altitude": 10.0,
    "horizontalAccuracy": 25.0,
    "verticalAccuracy": 0.0,
    "confidence": 0.5,
    "methodsUsed": ["weighted_centroid", "rssi_ratio"],
    "apCount": 3,
    "calculationTimeMs": 42
  },
  "calculationInfo": "Detailed calculation information"  // present only when calculationDetail=true
}
```

This structure provides several benefits:
1. Single level nesting for easier parsing
2. Consistent top-level metadata across all responses
3. Clear separation between general response metadata and positioning data
4. Simplified error handling with standardized result and message fields
5. Reduced need for nested metadata objects and multiple parsing steps
6. Direct access to timestamp and calculation time information
7. Removal of redundant fields previously repeated in multiple locations

### Parameter Impact on Algorithm Selection

1. **Single AP Scenario**
   - Required: signalStrength, frequency
   - Algorithm: Proximity Detection
   - Confidence Range: 0.3-0.6
   - Accuracy: 10-15m

2. **Two AP Scenario**
   - Required: signalStrength, frequency for both APs
   - Algorithm: RSSI Ratio Method
   - Confidence Range: 0.5-0.8
   - Accuracy: 5-8m

3. **Three+ AP Scenario**
   - Required: signalStrength, frequency for all APs
   - Algorithms:
     * preferHighAccuracy=true: Maximum Likelihood
     * preferHighAccuracy=false: Weighted Centroid
   - Confidence Range: 0.7-0.95
   - Accuracy: 3-6m

### Parameter Validation Rules

1. **Signal Strength Validation**
   - Valid range: -100 dBm to 0 dBm
   - Optimal range: -75 dBm to -45 dBm
   - Warning thresholds:
     * < -85 dBm: Low reliability
     * > -35 dBm: Potential measurement error

2. **Frequency Validation**
   - 2.4 GHz band: 2412-2484 MHz
   - 5 GHz band: 5170-5825 MHz
   - Channel-frequency correlation check

3. **MAC Address Validation**
   - Format: XX:XX:XX:XX:XX:XX
   - Vendor prefix validation
   - Duplicate detection

4. **Data Consistency Checks**
   - Signal strength vs. distance correlation
   - Frequency-channel mapping
   - AP density reasonableness
   - Geometric distribution assessment 

## Algorithm Implementation Details

### 1. Data Preprocessing
- Input validation and normalization
  * Verify required fields (macAddress, signalStrength, frequency)
  * Convert units if needed
  * Derive channel from frequency
  * Filter out invalid or extremely weak signals

### Test Cases Implementation

#### Basic Algorithm Test Cases
1. **Simple Trilateration Test**
   - Input: 3 APs with strong signals (-50 to -65 dBm)
   - Frequencies: Mix of 2.4GHz and 5GHz
   - Expected: Success with high confidence

2. **RSSI Ratio Test**
   - Input: 4 APs with varying signal strengths
   - Frequencies: All 2.4GHz for consistent comparison
   - Expected: Success with medium-high confidence

#### Error and Edge Cases
1. **Invalid Signal Strength Test**
   - Input: AP with impossible signal strength (> 0 dBm)
   - Expected: Error response

2. **Physically Impossible Signal Relationships**
   - Input: APs with signal strengths that violate physics
   - Expected: Error in metadata, success response

3. **Missing Required Fields**
   - Input: Scan results missing macAddress/signalStrength/frequency
   - Expected: Error response 

## Implementation Details

### Trilateration Algorithm Enhancement
The trilateration algorithm has been enhanced with GDOP (Geometric Dilution of Precision) calculation to improve accuracy estimation and confidence metrics.

#### GDOP Implementation
- **Mathematical Model**: GDOP = sqrt(trace((H^T * H)^-1))
  - H is the geometry matrix with unit vectors from position to each AP
  - Measures how AP geometric distribution affects positioning accuracy
  - Lower values indicate better geometry, higher values indicate poorer geometry

- **GDOP Quality Classifications**:
  - Excellent: GDOP < 2.0 
  - Good: 2.0 ≤ GDOP < 4.0
  - Fair: 4.0 ≤ GDOP < 6.0
  - Poor: GDOP ≥ 6.0

- **Effects on Accuracy Estimation**:
  - Strong signals: Accuracy ranges from 1-5m, adjusted by GDOP
  - Weak signals: Base accuracy scaled by GDOP factor
  - Poor geometry significantly increases accuracy values (worse accuracy)

- **Effects on Confidence Calculation**:
  - Confidence reduced for poor AP geometry
  - Strong signals: Minor GDOP influence to maintain high confidence
  - Weak signals: Stronger GDOP influence, further reducing confidence
  - Medium signals: Balanced GDOP influence

- **Implementation Benefits**:
  - More realistic accuracy estimates based on AP geometry
  - Improved confidence metrics that reflect positioning quality
  - Better handling of challenging AP distributions
  - Enhanced error detection for collinear/problematic AP arrangements

The GDOP implementation satisfies the improvement suggestion for "Implement GDOP for better accuracy estimation" while maintaining compatibility with existing tests. 

# Hybrid Positioning Algorithm Details
# WiFi Positioning Hybrid Algorithm Selection 

This document outlines the algorithm selection framework implemented in the WiFi Positioning Service to select the optimal positioning algorithms based on the scenario characteristics.

## Overview

The framework uses a three-phase process for optimal algorithm selection:

1. **Hard Constraints (Disqualification Phase)** - Eliminate algorithms that are mathematically or practically invalid
2. **Algorithm Weighting (Ranking Phase)** - Assign and adjust weights based on various factors
3. **Finalist Selection (Combination Phase)** - Select the final set of algorithms based on weights

## 1. Hard Constraints (Disqualification Phase)

First, we eliminate algorithms that are mathematically or practically invalid for the scenario:

| Constraint | Action |
|------------|--------|
| AP Count = 1 | Only include Proximity and Log Distance; remove all others |
| AP Count = 2 | Remove Trilateration and Maximum Likelihood (mathematically underdetermined) |
| AP Count ≥ 3 | All algorithms eligible (subject to other constraints) |
| Collinear APs detected | Remove Trilateration (mathematically invalid) |
| Extremely weak signals (all < -95 dBm) | Remove all except Proximity |

## 2. Algorithm Weighting (Ranking Phase)

For remaining eligible algorithms, we apply base weights according to AP count:

### Base Weights by AP Count

| AP Count | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------|-----------|------------|-------------------|---------------|-------------------|--------------|
| 1 | 1.0 | - | - | - | - | 0.4 |
| 2 | 0.4 | 1.0 | 0.8 | - | - | 0.5 |
| 3 | 0.3 | 0.7 | 0.8 | 1.0 | - | 0.5 |
| 4+ | 0.2 | 0.5 | 0.7 | 0.8 | 1.0 | 0.4 |

### Signal Quality Adjustments

| Signal Quality | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|----------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Strong (> -70 dBm) | ×0.9 | ×1.0 | ×1.0 | ×1.1 | ×1.2 | ×1.0 |
| Medium (-70 to -85 dBm) | ×0.7 | ×0.9 | ×1.0 | ×0.8 | ×0.9 | ×0.8 |
| Weak (< -85 dBm) | ×0.4 | ×0.6 | ×0.8 | ×0.3 | ×0.5 | ×0.6 |
| Very Weak (< -95 dBm) | ×0.5 | ×0.0 | ×0.0 | ×0.0 | ×0.0 | ×0.0 |

### Geometric Quality Adjustments

| Geometric Quality | Proximity | RSSI Ratio | Weighted Centroid | Trilateration | Maximum Likelihood | Log Distance |
|-------------------|-----------|------------|-------------------|---------------|-------------------|--------------|
| Excellent GDOP (< 2) | ×1.0 | ×1.0 | ×1.0 | ×1.3 | ×1.2 | ×1.0 |
| Good GDOP (2-4) | ×1.0 | ×1.0 | ×1.1 | ×0.9 | ×1.1 | ×1.0 |
| Fair GDOP (4-6) | ×1.0 | ×0.9 | ×1.2 | ×0.6 | ×0.9 | ×0.8 |
| Poor GDOP (> 6) | ×1.0 | ×0.8 | ×1.3 | ×0.3 | ×0.7 | ×0.7 |
| Collinear APs | ×1.0 | ×0.7 | ×1.4 | ×0.0 | ×0.5 | ×0.6 |

### Test Case Examples and Algorithm Selection

#### 1. Single AP Test (Test Case 1)
- **Input**: Single AP with -65.0 dBm signal at 2.4GHz
- **Base Weights**: 
  * Proximity: 1.0
  * Log Distance: 0.4
- **Adjustments**:
  * Signal Quality (Strong): ×0.9
  * GDOP (Poor): ×0.7
  * Distribution (Uniform): ×1.1
- **Final Weights**:
  * Proximity: 1.0 × 0.9 = 0.9 (Selected)
  * Log Distance: 0.4 × 1.0 × 0.7 × 1.1 = 0.308 (Below threshold)
- **Expected**: Position with accuracy 45-55m, confidence 0.35-0.55

#### 2. Two APs Test (Test Case 2)
- **Input**: Two APs with -68.5 dBm and -62.3 dBm
- **Base Weights**:
  * RSSI Ratio: 1.0
  * Weighted Centroid: 0.8
  * Proximity: 0.4
  * Log Distance: 0.5
- **Adjustments**:
  * Signal Quality (Strong): ×1.0
  * GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
  * Distribution (Uniform): ×1.2 for RSSI Ratio, ×1.0 for Weighted Centroid
- **Final Weights**:
  * Weighted Centroid: 0.8 × 1.0 × 1.3 × 1.0 = 1.04 (Primary)
  * RSSI Ratio: 1.0 × 1.0 × 0.8 × 1.2 = 0.96 (Secondary)
  * Log Distance: 0.5 × 1.0 × 0.7 × 1.1 = 0.385 (Below threshold)
  * Proximity: 0.4 × 0.9 × 1.0 × 1.0 = 0.36 (Below threshold)
- **Expected**: Accuracy 55-70m, confidence 0.40-0.60

#### 3. Three APs Test (Test Case 3)
- **Input**: Three APs with varying signal strengths (-62.3, -71.2, -85.5 dBm)
- **Base Weights**:
  * Trilateration: 1.0
  * Weighted Centroid: 0.8
  * RSSI Ratio: 0.7
- **Adjustments**:
  * Signal Quality (Medium): ×0.7
  * GDOP (Poor): ×0.7 for Trilateration, ×1.3 for Weighted Centroid
  * Distribution (Signal Outliers): ×0.8 for Trilateration, ×1.0 for Weighted Centroid
- **Final Weights**:
  * Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
  * RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528 (Secondary)
  * Trilateration: 1.0 × 0.7 × 0.7 × 0.8 = 0.392 (Below threshold)
- **Expected**: Accuracy 90-105m, confidence 0.35-0.55

#### 4. Collinear APs Test (Test Cases 6-10)
- **Input**: Three APs in linear arrangement (-70.0, -68.0, -66.0 dBm)
- **Base Weights**:
  * Weighted Centroid: 0.8
  * RSSI Ratio: 0.7
- **Adjustments**:
  * Signal Quality (Medium): ×0.7
  * GDOP (Collinear): ×1.4 for Weighted Centroid, ×0.7 for RSSI Ratio
  * Distribution (Collinear): ×1.0 for Weighted Centroid, ×0.9 for RSSI Ratio
- **Final Weights**:
  * Weighted Centroid: 0.8 × 0.7 × 1.4 × 1.0 = 0.784 (Primary)
  * RSSI Ratio: 0.7 × 0.7 × 0.7 × 0.9 = 0.3087 (Secondary)
  * Trilateration: 0.0 (Disqualified due to collinear geometry)
- **Expected**: Accuracy 70-85m, confidence 0.35-0.45

#### 5. High Density Cluster Test (Test Cases 11-15)
- **Input**: Four APs with strong signals (-65.0 to -60.5 dBm)
- **Base Weights**:
  * Maximum Likelihood: 1.0
  * Trilateration: 0.8
  * Weighted Centroid: 0.7
- **Adjustments**:
  * Signal Quality (Strong): ×0.9
  * GDOP (Poor): ×0.7 for Maximum Likelihood, ×1.3 for Weighted Centroid
  * Distribution (Mixed): ×0.8 for Maximum Likelihood, ×1.0 for Weighted Centroid
- **Final Weights**:
  * Weighted Centroid: 0.7 × 0.9 × 1.3 × 1.0 = 0.819 (Primary)
  * Maximum Likelihood: 1.0 × 0.9 × 0.7 × 0.8 = 0.504 (Secondary)
  * Trilateration: 0.8 × 0.9 × 0.7 × 0.8 = 0.4032 (Below threshold)
- **Expected**: Accuracy 50-60m, confidence 0.35-0.55

#### 6. Stable Signal Quality Test (Test Cases 31-35)
- **Input**: Two APs with identical signal strengths (-68.0 dBm)
- **Base Weights**:
  * RSSI Ratio: 1.0
  * Weighted Centroid: 0.8
- **Adjustments**:
  * Signal Quality (Medium): ×0.7
  * GDOP (Poor): ×0.8 for RSSI Ratio, ×1.3 for Weighted Centroid
  * Distribution (Stable): ×1.0 for both methods
- **Final Weights**:
  * Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728 (Primary)
  * RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.0 = 0.560 (Secondary)
- **Expected**: Accuracy 5-15m, confidence 0.65-0.80

### Error Cases and Edge Scenarios

#### 1. Very Weak Signal Test (Test Case 38)
- **Input**: Single AP with -99.9 dBm signal
- **Base Weights**:
  * Proximity: 1.0
  * Log Distance: 0.4
- **Adjustments**:
  * Signal Quality (Very Weak): ×0.5 for Proximity, ×0.0 for others
  * GDOP (Poor): ×0.7
  * Distribution (Uniform): ×1.1
- **Final Weights**:
  * Proximity: 1.0 × 0.5 × 0.7 × 1.1 = 0.385 (Selected)
  * Log Distance: 0.4 × 0.0 = 0.0 (Below threshold)
- **Expected**: Accuracy 5-15m, confidence 0.0-0.1

#### 2. Algorithm Failure Test (Test Case 39)
- **Input**: Three APs with physically impossible signal relationships
- **Result**: ERROR status
- **Rationale**: Signal relationships violate physical constraints
- **Expected**: Error response with appropriate message

## Conclusion

This hybrid algorithm selection framework enables the WiFi Positioning Service to adapt to a wide range of scenarios by dynamically selecting the most appropriate positioning algorithms based on the characteristics of the input data. 


## Test Coverage Summary

### Unit Tests
- Total Tests: 114
- All tests passing
- Coverage includes:
  - Repository tests for DynamoDB interaction
  - Utility tests for geohashing
  - Algorithm implementation tests
  - Controller and service layer tests
  - Signal physics validation tests

### Integration Tests
- Total Tests: 14
- Success Rate: 100%
- Coverage includes:
  - Basic algorithm scenarios
  - Advanced positioning scenarios
  - Temporal and environmental tests
  - Error and edge cases

### Additional Test Recommendations
1. Load testing scenarios
2. Concurrent request handling tests
3. More temporal variation tests
4. Cross-frequency interference tests
5. Environmental factor simulation tests

## Test Cases Implementation Details

### Basic Algorithm Test Cases (1-5)
1. **Single AP - Proximity Detection**
   - **Purpose**: Validate basic proximity-based positioning with minimal data
   - **Input**: Single AP with -65.0 dBm signal at 2.4GHz
   - **Expected**: Position with accuracy 45-55m, confidence 0.35-0.55
   - **Best Method**: "proximity"
   - **Rationale**: Tests system's ability to handle simplest positioning scenario with strong signal
   - **Signal Quality Impact**: Strong signal (-65 dBm) provides better accuracy than typical single AP scenarios
   - **Note**: Base weight: 1.0, Signal Quality (Strong): ×0.9, GDOP (Poor): ×0.7, Distribution (Uniform): ×1.1
     * Final weights: Proximity: 1.0 × 0.9 = 0.9, Log Distance: 0.4 × 1.0 × 0.7 × 1.1 = 0.308

2. **Two APs - RSSI Ratio Method**
   - **Purpose**: Test relative signal strength positioning
   - **Input**: Two APs with -68.5 dBm and -62.3 dBm at different frequencies (5GHz and 2.4GHz)
   - **Expected**: Accuracy 55-70m, confidence 0.40-0.60
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates positioning without absolute signal calibration
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8, Proximity: 0.4, Log Distance: 0.5
     * Final weights: Weighted Centroid: 0.8 × 1.0 × 1.3 × 1.0 = 1.04, RSSI Ratio: 1.0 × 1.0 × 0.8 × 1.2 = 0.96

3. **Three APs - Trilateration**
   - **Purpose**: Test geometric positioning with mixed signal quality
   - **Input**: Three APs with varying signal strengths (-62.3, -71.2, -85.5 dBm)
   - **Expected**: Accuracy 90-105m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Tests trilateration with mixed signal qualities
   - **Note**: Base weights: Trilateration: 1.0, Weighted Centroid: 0.8, RSSI Ratio: 0.7
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528

4. **Multiple APs - Maximum Likelihood**
   - **Purpose**: Test advanced positioning with redundant measurements
   - **Input**: Four APs with mixed signal qualities (-71.2 to -68.0 dBm)
   - **Expected**: Accuracy 135-150m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates statistical positioning approach

5. **Weak Signals**
   - **Purpose**: Test system behavior with poor signal conditions
   - **Input**: Single AP with -85.5 dBm signal
   - **Expected**: Accuracy 30-80m, confidence 0.05-0.15
   - **Best Method**: "proximity"
   - **Rationale**: Validates graceful degradation
   - **Note**: Base weights: Proximity: 1.0, Log Distance: 0.4
     * Final weights: Proximity: 1.0 × 0.4 × 0.7 × 1.1 = 0.308, Log Distance: 0.4 × 0.4 × 0.7 × 1.1 = 0.1232

### Advanced Scenario Test Cases (6-20)
1. **Collinear APs (6-10)**
   - **Purpose**: Test geometric dilution of precision handling
   - **Input**: Three APs in linear arrangement (-70.0, -68.0, -66.0 dBm)
   - **Expected**: ERROR status due to poor geometry
   - **Rationale**: Validates geometry quality assessment

2. **High Density Cluster (11-15)**
   - **Purpose**: Test positioning in AP-rich environments
   - **Input**: Four APs with strong signals (-65.0 to -60.5 dBm)
   - **Expected**: Accuracy 50-60m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid maximum_likelihood"
   - **Rationale**: Tests algorithm selection in optimal conditions
   - **Note**: Base weights: Maximum Likelihood: 1.0, Trilateration: 0.8, Weighted Centroid: 0.7
     * Final weights: Weighted Centroid: 0.7 × 0.9 × 1.3 × 1.0 = 0.819, Maximum Likelihood: 1.0 × 0.9 × 0.7 × 0.8 = 0.504

3. **Mixed Signal Quality (16-20)**
   - **Purpose**: Test adaptive algorithm selection
   - **Input**: Three APs with progressive signal degradation (-60.0 to -70.0 dBm)
   - **Expected**: Accuracy 60-75m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Tests dynamic algorithm weighting
   - **Note**: Base weights: Trilateration: 1.0, Weighted Centroid: 0.8, RSSI Ratio: 0.7
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 0.7 × 0.7 × 0.8 × 0.9 = 0.3528

### Temporal and Environmental Test Cases (21-35)
1. **Time Series Data (21-25)**
   - **Purpose**: Test temporal stability
   - **Input**: Two APs with consistent signals (-70.0, -72.0 dBm)
   - **Expected**: Accuracy 45-60m, confidence 0.35-0.55
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates temporal robustness
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.1 = 0.616

2. **Log-Distance Path Loss (26-30)**
   - **Purpose**: Test distance-based modeling
   - **Input**: Two APs with strong signals (-50.0, -53.0 dBm)
   - **Expected**: Accuracy 20-35m, confidence 0.40-0.60
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates path loss model
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
     * Final weights: Weighted Centroid: 0.8 × 0.9 × 1.3 × 1.0 = 0.936, RSSI Ratio: 1.0 × 0.9 × 0.8 × 0.8 = 0.576

3. **Stable Signal Quality (31-35)**
   - **Purpose**: Test positioning with stable signals
   - **Input**: Two APs with identical signal strengths (-68.0 dBm)
   - **Expected**: Accuracy 5-15m, confidence 0.65-0.80
   - **Best Method**: "weighted_centroid rssi ratio"
   - **Rationale**: Validates long-term stability
   - **Note**: Base weights: RSSI Ratio: 1.0, Weighted Centroid: 0.8
     * Final weights: Weighted Centroid: 0.8 × 0.7 × 1.3 × 1.0 = 0.728, RSSI Ratio: 1.0 × 0.7 × 0.8 × 1.0 = 0.560

### Error and Edge Cases (36-40)
1. **Invalid Coordinates Test**
   - **Purpose**: Test handling of invalid location data
   - **Input**: Single AP with extremely weak signal (-99.9 dBm)
   - **Expected**: ERROR status
   - **Rationale**: Validates input validation

2. **Very Weak Signal Test**
   - **Purpose**: Test handling of very weak signals
   - **Input**: Single AP with -99.9 dBm signal
   - **Expected**: Accuracy 5-15m, confidence 0.0-0.1
   - **Best Method**: "proximity"
   - **Rationale**: Validates algorithm selection framework for very weak signals
   - **Note**: According to algorithm selection framework:
     * Signal strength -99.9 dBm is "Very Weak" (< -95 dBm)
     * Only Proximity algorithm gets non-zero weight (×0.5)
     * All other algorithms get zero weight

3. **Algorithm Failure Test**
   - **Purpose**: Test handling of physically impossible scenarios
   - **Input**: Three APs with physically impossible signal relationships
   - **Expected**: ERROR status
   - **Rationale**: Validates physics-based validation

Note: All test cases include additional parameters:
- `preferHighAccuracy`: Boolean flag to enable advanced algorithms
- `returnAllMethods`: Boolean flag to return results from all applicable algorithms
- Comprehensive validation of response fields including horizontalAccuracy, confidence, and bestMethod
