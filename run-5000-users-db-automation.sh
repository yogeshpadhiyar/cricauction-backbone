#!/usr/bin/env bash
# ==============================================================================
# CricAuction Automation Task: 5,000 Concurrent Dummy Users Full API Flow
# Targets Actual Local PostgreSQL Database (jdbc:postgresql://localhost:5432/cricauction)
# Database entries are KEPT INTACT for cross-verification.
# ==============================================================================

set -e

# Set OpenJDK 23
if [ -d "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/opt/homebrew/opt/openjdk" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "================================================================================"
echo " CricAuction Backbone: 5,000 Concurrent Users Actual DB Automation Task"
echo "================================================================================"
echo "Target DB: cricauction (PostgreSQL on localhost:5432)"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Mode: Actual Database Transactions (Data will NOT be cleared)"
echo "Simulating 5,000 Concurrent Users:"
echo "  - 1,250 Users: Google Direct Sign-In/Up -> Role Switch (Captain)"
echo "  - 1,250 Users: Mobile + OTP Passwordless Login -> Profile Check (/me)"
echo "  - 1,250 Users: Email + OTP Passwordless Login -> Role Switch (Organizer)"
echo "  - 1,250 Users: Standard Sign-Up + Password Sign-In -> Token Refresh"
echo "================================================================================"
echo ""

mvn test -Dtest=FullFlow5000UsersActualDbTest

echo ""
echo "================================================================================"
echo " Verification: Querying Actual PostgreSQL Database"
echo "================================================================================"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_users FROM users;"
psql -U $(whoami) -d cricauction -c "SELECT auth_provider, count(*) FROM users GROUP BY auth_provider;"
psql -U $(whoami) -d cricauction -c "SELECT r.name AS role, count(*) FROM user_roles ur JOIN roles r ON r.id = ur.role_id GROUP BY r.name;"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_refresh_tokens FROM refresh_tokens;"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_otps FROM otp_verifications;"
echo ""
echo "--- OTP Verifications (Email & Mobile Entries) ---"
psql -U $(whoami) -d cricauction -c "SELECT identifier, purpose, email, mobile, otp_code, attempts, expires_at FROM otp_verifications ORDER BY created_at DESC LIMIT 10;"
echo ""
echo "--- Recent Users ---"
psql -U $(whoami) -d cricauction -c "SELECT id, full_name, email, mobile, auth_provider, is_email_verified, is_mobile_verified FROM users ORDER BY id DESC LIMIT 5;"
echo "================================================================================"
echo " Automation Task Completed Successfully! All data preserved in PostgreSQL."
echo "================================================================================"
