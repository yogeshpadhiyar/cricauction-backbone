#!/usr/bin/env bash
# ==============================================================================
# CricAuction 2,000 Concurrent Users Load Test Runner
# ==============================================================================

set -e

# Set OpenJDK 23
if [ -d "/opt/homebrew/opt/openjdk" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "================================================================================"
echo " CricAuction Backbone: 2,000 Concurrent Users Concurrency Test Suite"
echo "================================================================================"
echo "Using Java: $(java -version 2>&1 | head -n 1)"
echo "Using Maven: $(mvn -version 2>&1 | head -n 1)"
echo "Target: 2,000 Concurrent Virtual Users per Scenario"
echo "Scenarios:"
echo "  1. 2,000 Concurrent Users Sign-In (POST /api/v1/auth/signin)"
echo "  2. 2,000 Concurrent Users Profile Requests (GET /api/v1/auth/me)"
echo "  3. 2,000 Concurrent Users Google OAuth Sign-In (POST /api/v1/auth/google)"
echo "================================================================================"
echo ""

mvn test -Dtest=AuthConcurrencyLoadTest

echo ""
echo "================================================================================"
echo " Concurrency Load Test Completed Successfully!"
echo "================================================================================"
