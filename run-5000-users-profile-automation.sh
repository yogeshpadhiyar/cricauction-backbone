#!/usr/bin/env bash
# ==============================================================================
# CricAuction Automation Task: 5,000 Concurrent Users Module 2 Profile Flow
# Targets Actual Local PostgreSQL Database (jdbc:postgresql://localhost:5432/cricauction)
# Database entries are KEPT INTACT for cross-verification.
# ==============================================================================

set -e

# Set OpenJDK 23
if [ -d "/opt/homebrew/opt/openjdk" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "================================================================================"
echo " CricAuction Backbone: Module 2 (Profile Management) 5,000 Concurrency Test"
echo "================================================================================"
echo "Target DB: cricauction (PostgreSQL on localhost:5432)"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Mode: Actual Database Transactions (Data will NOT be cleared)"
echo "Simulating 5,000 Concurrent User Profile Operations:"
echo "  - 1,250 Users: GET /api/v1/users/profile (Fast Read-Only Query)"
echo "  - 1,250 Users: PATCH /api/v1/users/profile (Bio, Name, Mobile Updates)"
echo "  - 1,250 Users: PUT /api/v1/users/player-profile (Cricket Profile Upsert)"
echo "  - 1,250 Users: POST /api/v1/users/profile/photo (Avatar Multipart Upload)"
echo "================================================================================"
echo ""

mvn test -Dtest=ProfileConcurrency5000UsersActualDbTest

echo ""
echo "================================================================================"
echo " Verification: Querying Actual PostgreSQL Database"
echo "================================================================================"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_player_profiles FROM player_profiles;"
psql -U $(whoami) -d cricauction -c "SELECT playing_role, batting_style, bowling_style, count(*) FROM player_profiles GROUP BY playing_role, batting_style, bowling_style;"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS users_with_bio FROM users WHERE bio IS NOT NULL;"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS users_with_custom_avatars FROM users WHERE profile_photo_url LIKE '%cdn.cricauction.app%';"
psql -U $(whoami) -d cricauction -c "SELECT u.id, u.full_name, u.bio, p.playing_role, p.batting_style, p.bowling_style, p.past_tournaments FROM users u JOIN player_profiles p ON u.id = p.user_id ORDER BY p.updated_at DESC LIMIT 5;"
echo "================================================================================"
echo " Module 2 Concurrency Automation Completed Successfully! All data preserved."
echo "================================================================================"
