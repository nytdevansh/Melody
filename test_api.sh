#!/bin/bash

# Melody Backend API Test Script
# Usage: ./test-api.sh [base_url]

BASE_URL=${1:-"http://localhost:8080"}

echo "🎵 Testing Melody Backend API at $BASE_URL"
echo "=============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to make API calls and check status
test_endpoint() {
    local method=$1
    local endpoint=$2
    local expected_status=$3
    local description=$4
    local data=$5
    
    echo -e "\n${BLUE}Testing: $description${NC}"
    echo "Endpoint: $method $endpoint"
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" "$BASE_URL$endpoint")
    elif [ "$method" = "POST" ] && [ -n "$data" ]; then
        response=$(curl -s -w "HTTPSTATUS:%{http_code}" -X POST \
            -H "Content-Type: application/json" \
            -d "$data" \
            "$BASE_URL$endpoint")
    fi
    
    http_code=$(echo $response | tr -d '\n' | sed -e 's/.*HTTPSTATUS://')
    body=$(echo $response | sed -e 's/HTTPSTATUS:.*//g')
    
    if [ "$http_code" -eq "$expected_status" ]; then
        echo -e "${GREEN}✅ PASS${NC} (Status: $http_code)"
        echo "Response: $body" | jq '.' 2>/dev/null || echo "Response: $body"
    else
        echo -e "${RED}❌ FAIL${NC} (Expected: $expected_status, Got: $http_code)"
        echo "Response: $body"
    fi
}

# Test 1: Health Check
test_endpoint "GET" "/health" 200 "Health check endpoint"

# Test 2: API Root
test_endpoint "GET" "/" 200 "API root endpoint"

# Test 3: Get all songs (empty initially)
test_endpoint "GET" "/api/music/songs" 200 "Get all songs"

# Test 4: Search songs (empty query)
test_endpoint "GET" "/api/music/search?q=test" 200 "Search songs with query 'test'"

# Test 5: Check if song exists (should not exist)
test_endpoint "GET" "/api/music/exists/nonexistent_hash" 200 "Check if non-existent song exists"

# Test 6: Get stream URL for non-existent song
test_endpoint "GET" "/api/music/stream/nonexistent_id" 404 "Get stream URL for non-existent song"

# Test 7: Get popular artists (empty initially)