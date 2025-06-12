#!/bin/bash

# wifi-database/wifi-scan-collection/wifi-scan-queue-consumer/scripts/run-test-suite.sh
# Comprehensive test suite for service validation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

print_header() {
    echo -e "${MAGENTA}========================================${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}========================================${NC}"
}

print_test() {
    echo -e "${CYAN}[TEST]${NC} $1"
}

print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}[PASS]${NC} $2"
    else
        echo -e "${RED}[FAIL]${NC} $2"
    fi
}

# Test results tracking
declare -a test_results=()
declare -a test_names=()

run_test() {
    local test_name="$1"
    local test_command="$2"
    
    print_test "Running: $test_name"
    
    if eval "$test_command"; then
        test_results+=(0)
        print_result 0 "$test_name"
    else
        test_results+=(1)
        print_result 1 "$test_name"
    fi
    
    test_names+=("$test_name")
    echo ""
}

print_header "SERVICE VALIDATION TEST SUITE"

echo "Starting comprehensive service testing..."
echo ""

# Test 1: Basic Functionality
run_test "Basic Functionality (3 messages)" \
    "./scripts/validate-service-health.sh --count 3 --interval 1"

# Test 2: Quick Processing
run_test "Quick Processing (5 messages, 0.5s interval)" \
    "./scripts/validate-service-health.sh --count 5 --interval 0.5 --timeout 60"

# Test 3: Moderate Load
run_test "Moderate Load (10 messages, 1s interval)" \
    "./scripts/validate-service-health.sh --count 10 --interval 1 --timeout 120"

# Test 4: Health Monitoring
run_test "Health Monitoring (8 messages, frequent checks)" \
    "./scripts/validate-service-health.sh --count 8 --interval 1 --health-interval 2 --timeout 90"

# Test 5: High Frequency
run_test "High Frequency (15 messages, 0.3s interval)" \
    "./scripts/validate-service-health.sh --count 15 --interval 0.3 --timeout 120"

# Test 6: Verbose Monitoring
run_test "Verbose Monitoring (5 messages with detailed output)" \
    "./scripts/validate-service-health.sh --count 5 --interval 1 --verbose"

print_header "TEST RESULTS SUMMARY"

total_tests=${#test_results[@]}
passed_tests=0
failed_tests=0

for i in "${!test_results[@]}"; do
    result=${test_results[$i]}
    name=${test_names[$i]}
    
    if [ $result -eq 0 ]; then
        echo -e "${GREEN}✅ PASS${NC} - $name"
        ((passed_tests++))
    else
        echo -e "${RED}❌ FAIL${NC} - $name"
        ((failed_tests++))
    fi
done

echo ""
echo "Total Tests: $total_tests"
echo -e "Passed: ${GREEN}$passed_tests${NC}"
echo -e "Failed: ${RED}$failed_tests${NC}"

if [ $failed_tests -eq 0 ]; then
    echo -e "${GREEN}🎉 ALL TESTS PASSED!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Please check the output above.${NC}"
    exit 1
fi 