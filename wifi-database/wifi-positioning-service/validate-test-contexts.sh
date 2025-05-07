#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}====================================================${NC}"
echo -e "${CYAN}  WIFI POSITIONING TEST CONTEXT VALIDATION${NC}"
echo -e "${CYAN}====================================================${NC}"

# Function to validate signal quality
validate_signal_quality() {
    local avg_signal=$1
    local expected_factor=$2
    
    if (( $(echo "$avg_signal > -70" | bc -l) )); then
        actual_factor="STRONG_SIGNAL"
    elif (( $(echo "$avg_signal >= -85" | bc -l) )); then
        actual_factor="MEDIUM_SIGNAL"
    else
        actual_factor="WEAK_SIGNAL"
    fi
    
    if [ "$actual_factor" = "$expected_factor" ]; then
        echo -e "${GREEN}✓ Signal Quality: $actual_factor${NC}"
    else
        echo -e "${RED}✗ Signal Quality: Expected $expected_factor but got $actual_factor${NC}"
    fi
}

# Function to validate signal distribution
validate_signal_distribution() {
    local std_dev=$1
    local expected_factor=$2
    
    if (( $(echo "$std_dev < 3.0" | bc -l) )); then
        actual_factor="UNIFORM_SIGNALS"
    elif (( $(echo "$std_dev < 10.0" | bc -l) )); then
        actual_factor="MIXED_SIGNALS"
    else
        actual_factor="SIGNAL_OUTLIERS"
    fi
    
    if [ "$actual_factor" = "$expected_factor" ]; then
        echo -e "${GREEN}✓ Signal Distribution: $actual_factor${NC}"
    else
        echo -e "${RED}✗ Signal Distribution: Expected $expected_factor but got $actual_factor${NC}"
    fi
}

# Function to validate geometric quality
validate_geometric_quality() {
    local gdop=$1
    local expected_factor=$2
    
    if (( $(echo "$gdop < 2.0" | bc -l) )); then
        actual_factor="EXCELLENT_GDOP"
    elif (( $(echo "$gdop < 4.0" | bc -l) )); then
        actual_factor="GOOD_GDOP"
    elif (( $(echo "$gdop < 6.0" | bc -l) )); then
        actual_factor="FAIR_GDOP"
    else
        actual_factor="POOR_GDOP"
    fi
    
    if [ "$actual_factor" = "$expected_factor" ]; then
        echo -e "${GREEN}✓ Geometric Quality: $actual_factor${NC}"
    else
        echo -e "${RED}✗ Geometric Quality: Expected $expected_factor but got $actual_factor${NC}"
    fi
}

# Function to validate AP count factor
validate_ap_count() {
    local count=$1
    case $count in
        1) echo "SINGLE_AP" ;;
        2) echo "TWO_APS" ;;
        3) echo "THREE_APS" ;;
        *) echo "FOUR_PLUS_APS" ;;
    esac
}

# Function to calculate standard deviation
calculate_std_dev() {
    local signals=("$@")
    local sum=0
    local count=${#signals[@]}
    
    # Calculate mean
    for signal in "${signals[@]}"; do
        sum=$(echo "$sum + $signal" | bc -l)
    done
    local mean=$(echo "scale=2; $sum / $count" | bc -l)
    
    # Calculate variance
    local variance=0
    for signal in "${signals[@]}"; do
        local diff=$(echo "$signal - $mean" | bc -l)
        local squared=$(echo "$diff * $diff" | bc -l)
        variance=$(echo "$variance + $squared" | bc -l)
    done
    variance=$(echo "scale=2; $variance / $count" | bc -l)
    
    # Calculate standard deviation
    echo "scale=2; sqrt($variance)" | bc -l
}

# Function to calculate average signal strength
calculate_avg_signal() {
    local signals=("$@")
    local sum=0
    local count=${#signals[@]}
    
    for signal in "${signals[@]}"; do
        sum=$(echo "$sum + $signal" | bc -l)
    done
    
    echo "scale=2; $sum / $count" | bc -l
}

# Function to validate algorithm weights
validate_algorithm_weights() {
    local ap_count=$1
    local signal_quality=$2
    local geometric_quality=$3
    local signal_distribution=$4
    
    echo -e "\n${YELLOW}Validating algorithm weights for:${NC}"
    echo "AP Count: $ap_count"
    echo "Signal Quality: $signal_quality"
    echo "Geometric Quality: $geometric_quality"
    echo "Signal Distribution: $signal_distribution"
    
    # Calculate expected weights based on framework
    case $ap_count in
        1)
            proximity_weight=1.0
            log_distance_weight=0.4
            ;;
        2)
            proximity_weight=0.4
            rssi_ratio_weight=1.0
            weighted_centroid_weight=0.8
            log_distance_weight=0.5
            ;;
        3)
            proximity_weight=0.3
            rssi_ratio_weight=0.7
            weighted_centroid_weight=0.8
            trilateration_weight=1.0
            log_distance_weight=0.5
            ;;
        *)
            proximity_weight=0.2
            rssi_ratio_weight=0.5
            weighted_centroid_weight=0.7
            trilateration_weight=0.8
            maximum_likelihood_weight=1.0
            log_distance_weight=0.4
            ;;
    esac
    
    # Apply signal quality adjustments
    case $signal_quality in
        "STRONG_SIGNAL")
            proximity_weight=$(echo "$proximity_weight * 0.9" | bc -l)
            log_distance_weight=$(echo "$log_distance_weight * 1.0" | bc -l)
            ;;
        "MEDIUM_SIGNAL")
            proximity_weight=$(echo "$proximity_weight * 0.7" | bc -l)
            log_distance_weight=$(echo "$log_distance_weight * 0.8" | bc -l)
            ;;
        "WEAK_SIGNAL")
            proximity_weight=$(echo "$proximity_weight * 0.4" | bc -l)
            log_distance_weight=$(echo "$log_distance_weight * 0.6" | bc -l)
            ;;
    esac
    
    # Apply geometric quality adjustments
    case $geometric_quality in
        "EXCELLENT_GDOP")
            log_distance_weight=$(echo "$log_distance_weight * 1.0" | bc -l)
            ;;
        "GOOD_GDOP")
            log_distance_weight=$(echo "$log_distance_weight * 1.0" | bc -l)
            ;;
        "FAIR_GDOP")
            log_distance_weight=$(echo "$log_distance_weight * 0.8" | bc -l)
            ;;
        "POOR_GDOP")
            log_distance_weight=$(echo "$log_distance_weight * 0.7" | bc -l)
            ;;
    esac
    
    # Apply signal distribution adjustments
    case $signal_distribution in
        "UNIFORM_SIGNALS")
            log_distance_weight=$(echo "$log_distance_weight * 1.1" | bc -l)
            ;;
        "MIXED_SIGNALS")
            log_distance_weight=$(echo "$log_distance_weight * 0.8" | bc -l)
            ;;
        "SIGNAL_OUTLIERS")
            log_distance_weight=$(echo "$log_distance_weight * 0.8" | bc -l)
            ;;
    esac
    
    echo -e "\n${YELLOW}Expected Weights:${NC}"
    if [ $ap_count -eq 1 ]; then
        echo "Proximity: $proximity_weight"
        echo "Log Distance: $log_distance_weight"
    fi
}

# Function to validate test case
validate_test_case() {
    local test_name=$1
    local signals=("${@:2}")
    
    echo -e "\n${BLUE}Validating Test Case: $test_name${NC}"
    echo "Signal Strengths: ${signals[*]}"
    
    # Calculate metrics
    local avg_signal=$(calculate_avg_signal "${signals[@]}")
    local std_dev=$(calculate_std_dev "${signals[@]}")
    local ap_count=${#signals[@]}
    
    # Determine factors
    local signal_quality=$(validate_signal_quality "$avg_signal" "$2")
    local signal_distribution=$(validate_signal_distribution "$std_dev" "$3")
    local geometric_quality=$(validate_geometric_quality "$4" "$5")
    
    echo "AP Count: $ap_count ($ap_count_factor)"
    echo "Average Signal: $avg_signal dBm ($signal_quality)"
    echo "Signal Std Dev: $std_dev dBm ($signal_distribution)"
    
    # Validate against framework requirements
    echo -e "\n${YELLOW}Framework Validation:${NC}"
    
    # Check AP count constraints
    if [ "$ap_count" -eq 1 ]; then
        echo "✓ Only Proximity and Log Distance should be eligible"
    elif [ "$ap_count" -eq 2 ]; then
        echo "✓ Trilateration and Maximum Likelihood should be disqualified"
    elif [ "$ap_count" -ge 3 ]; then
        echo "✓ All algorithms eligible (subject to other constraints)"
    fi
    
    # Check signal quality constraints
    if [ "$signal_quality" = "VERY_WEAK_SIGNAL" ]; then
        echo "✓ Only Proximity should be allowed for very weak signals"
    fi
    
    # Check signal distribution impact
    if [ "$signal_distribution" = "SIGNAL_OUTLIERS" ]; then
        echo "✓ Maximum Likelihood should be favored for signal outliers"
    elif [ "$signal_distribution" = "UNIFORM_SIGNALS" ]; then
        echo "✓ RSSI Ratio should be favored for uniform signals"
    fi
    
    # Expected algorithm weights based on framework
    echo -e "\n${YELLOW}Expected Algorithm Weights:${NC}"
    case "$ap_count_factor" in
        "SINGLE_AP")
            echo "Proximity: 1.0"
            echo "Log Distance: 0.4"
            ;;
        "TWO_APS")
            echo "RSSI Ratio: 1.0"
            echo "Weighted Centroid: 0.8"
            echo "Proximity: 0.4"
            echo "Log Distance: 0.5"
            ;;
        "THREE_APS")
            echo "Trilateration: 1.0"
            echo "Weighted Centroid: 0.8"
            echo "RSSI Ratio: 0.7"
            echo "Proximity: 0.3"
            echo "Log Distance: 0.5"
            ;;
        "FOUR_PLUS_APS")
            echo "Maximum Likelihood: 1.0"
            echo "Trilateration: 0.8"
            echo "Weighted Centroid: 0.7"
            echo "RSSI Ratio: 0.5"
            echo "Proximity: 0.2"
            echo "Log Distance: 0.4"
            ;;
    esac
    
    # Signal quality adjustments
    echo -e "\n${YELLOW}Signal Quality Adjustments:${NC}"
    case "$signal_quality" in
        "STRONG_SIGNAL")
            echo "Proximity: ×0.9"
            echo "RSSI Ratio: ×1.0"
            echo "Weighted Centroid: ×1.0"
            echo "Trilateration: ×1.1"
            echo "Maximum Likelihood: ×1.2"
            echo "Log Distance: ×1.0"
            ;;
        "MEDIUM_SIGNAL")
            echo "Proximity: ×0.7"
            echo "RSSI Ratio: ×0.9"
            echo "Weighted Centroid: ×1.0"
            echo "Trilateration: ×0.8"
            echo "Maximum Likelihood: ×0.9"
            echo "Log Distance: ×0.8"
            ;;
        "WEAK_SIGNAL")
            echo "Proximity: ×0.4"
            echo "RSSI Ratio: ×0.6"
            echo "Weighted Centroid: ×0.8"
            echo "Trilateration: ×0.3"
            echo "Maximum Likelihood: ×0.5"
            echo "Log Distance: ×0.6"
            ;;
        "VERY_WEAK_SIGNAL")
            echo "Proximity: ×0.5"
            echo "Others: ×0.0"
            ;;
    esac
}

# Validate each test case from the test data
echo -e "\n${BLUE}Validating Test Cases...${NC}"

# Test Case 1: Single AP
validate_test_case "Single AP - Proximity Detection" -65.0 "STRONG_SIGNAL" "UNIFORM_SIGNALS" 26.084 "POOR_GDOP"

# Test Case 2: Two APs
validate_test_case "Two APs - RSSI Ratio Method" -68.5 -62.3 "STRONG_SIGNAL" "MIXED_SIGNALS" 27.849 "POOR_GDOP"

# Test Case 3: Three APs
validate_test_case "Three APs - Trilateration" -62.3 -71.2 -85.5 "MEDIUM_SIGNAL" "SIGNAL_OUTLIERS" 1.152 "EXCELLENT_GDOP"

# Test Case 4: Multiple APs
validate_test_case "Multiple APs - Maximum Likelihood" -71.2 -85.5 -70.0 -68.0 "MEDIUM_SIGNAL" "MIXED_SIGNALS" 2.5 "GOOD_GDOP"

# Test Case 5: Weak Signals
validate_test_case "Weak Signals" -85.5 "WEAK_SIGNAL" "MIXED_SIGNALS" 3.8 "GOOD_GDOP"

# Test Case 6-10: Collinear APs
validate_test_case "Collinear APs" -70.0 -68.0 -66.0 "STRONG_SIGNAL" "UNIFORM_SIGNALS" 26.084 "POOR_GDOP"

# Test Case 11-15: High Density AP Cluster
validate_test_case "High Density AP Cluster" -65.0 -63.5 -62.0 -60.5 "STRONG_SIGNAL" "MIXED_SIGNALS" 27.849 "POOR_GDOP"

# Test Case 16-20: Mixed Signal Quality
validate_test_case "Mixed Signal Quality" -60.0 -65.0 -70.0 "MEDIUM_SIGNAL" "MIXED_SIGNALS" 2.5 "GOOD_GDOP"

# Test Case 21-25: Time Series Data
validate_test_case "Time Series Data" -70.0 -72.0 "STRONG_SIGNAL" "UNIFORM_SIGNALS" 26.084 "POOR_GDOP"

# Test Case 26-30: Log-Distance Path Loss
validate_test_case "Log-Distance Path Loss" -50.0 -53.0 "STRONG_SIGNAL" "MIXED_SIGNALS" 27.849 "POOR_GDOP"

# Test Case 31-35: Historical Data Analysis
validate_test_case "Historical Data Analysis" -68.0 -68.0 "MEDIUM_SIGNAL" "MIXED_SIGNALS" 2.5 "GOOD_GDOP"

echo -e "\n${CYAN}====================================================${NC}"
echo -e "${CYAN}  VALIDATION COMPLETE${NC}"
echo -e "${CYAN}====================================================${NC}" 