#!/bin/bash

# setup-dev-environment.sh
# This script sets up the complete development environment for the Kafka SSL consumer service
# on a new Mac development machine.

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Print with color
print_step() {
    echo -e "${GREEN}==>${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}Warning:${NC} $1"
}

print_error() {
    echo -e "${RED}Error:${NC} $1"
}

# Check if a command exists
check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed. Please install it first."
        exit 1
    fi
}

# Check prerequisites
check_prerequisites() {
    print_step "Checking prerequisites..."
    
    # Check Docker
    check_command docker
    if ! docker info &> /dev/null; then
        print_error "Docker is not running. Please start Docker Desktop."
        exit 1
    fi
    
    # Check Docker Compose
    check_command docker-compose
    
    # Check Java
    check_command java
    java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    if [[ ! $java_version == *"21"* ]]; then
        print_error "Java 21 is required. Found version: $java_version"
        print_warning "Please install Java 21 using: brew install openjdk@21"
        exit 1
    fi
    
    # Check Maven
    check_command mvn
    
    # Check keytool
    check_command keytool
    
    # Check openssl
    check_command openssl
    
    print_step "All prerequisites are satisfied!"
}

# Make scripts executable
make_scripts_executable() {
    print_step "Making scripts executable..."
    chmod +x *.sh
}

# Clean up any existing environment
cleanup_environment() {
    print_step "Cleaning up any existing environment..."
    ./stop-local-kafka.sh 2>/dev/null || true
    rm -rf kafka/ 2>/dev/null || true
    rm -rf ../src/main/resources/secrets/ 2>/dev/null || true
    docker system prune -f
}

# Main setup process
main() {
    print_step "Starting development environment setup..."
    
    # Check prerequisites
    check_prerequisites
    
    # Make scripts executable
    make_scripts_executable
    
    # Clean up existing environment
    cleanup_environment
    
    # Run the complete setup
    print_step "Setting up local Kafka environment..."
    ./setup-local-kafka.sh
    
    # Start Kafka cluster
    print_step "Starting Kafka cluster..."
    ./start-local-kafka.sh
    
    # Test the setup
    print_step "Testing SSL connection..."
    ./test-ssl-connection.sh
    
    print_step "Creating test topic..."
    ./create-test-topic.sh
    
    print_step "Sending test message..."
    ./send-test-message.sh "Test message from setup script"
    
    print_step "Consuming test message..."
    ./consume-test-messages.sh
    
    print_step "Development environment setup completed successfully!"
    echo -e "\n${GREEN}Next steps:${NC}"
    echo "1. Start your Spring Boot application"
    echo "2. Monitor the application logs"
    echo "3. Use the test scripts to verify message flow"
    echo -e "\nTo stop the environment, run: ${YELLOW}./stop-local-kafka.sh${NC}"
}

# Run main function
main 