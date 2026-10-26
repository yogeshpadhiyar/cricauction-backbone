#!/usr/bin/env bash
# ==============================================================================
# CricAuction Automation Task: 5,000 Concurrent Users Module 5 (Team & Captain Management)
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
echo " CricAuction Backbone: 5,000 Concurrent Operations (Module 5: Team & Captain Management)"
echo "================================================================================"
echo "Target DB: cricauction (PostgreSQL on localhost:5432)"
echo "Java: $(java -version 2>&1 | head -n 1)"
echo "Mode: Actual Database Transactions (Data will NOT be cleared)"
echo "Simulating 5,000 Concurrent Operations:"
echo "  - 1,250 Users: POST /tournaments/{id}/teams (Add Team within tournament capacity - Screen 18)"
echo "  - 1,250 Users: POST /tournaments/{id}/teams/{teamId}/invite (Dispatch WhatsApp / SMS Captain Invites - Screen 19)"
echo "  - 1,250 Users: POST /teams/join (Concurrent Captain Claiming - Optimistic Lock, Multi-Role & Squad Injection - Screen 20)"
echo "  - 1,250 Users: GET /teams/{teamId}/squad & GET /tournaments/{id}/teams (High-Speed Squad Roster & Purse Reads - Screen 21)"
echo "================================================================================"
echo ""

mvn test -Dtest=TeamConcurrency5000UsersActualDbTest

echo ""
echo "================================================================================"
echo " Verification: Querying Actual PostgreSQL Database (Module 5 Tables)"
echo "================================================================================"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_teams FROM teams;"
psql -U $(whoami) -d cricauction -c "SELECT COUNT(*) AS total_persisted_squad_members FROM squad_members;"
psql -U $(whoami) -d cricauction -c "SELECT status, count(*) FROM teams GROUP BY status;"
echo ""
echo "--- Sample Persisted Teams ---"
psql -U $(whoami) -d cricauction -c "SELECT id, tournament_id, name, short_code, captain_name, captain_mobile, status, remaining_budget, invite_code FROM teams ORDER BY id DESC LIMIT 5;"
echo ""
echo "--- Sample Persisted Squad Members (Captains Auto-Injected) ---"
psql -U $(whoami) -d cricauction -c "SELECT id, team_id, player_user_id, player_name, playing_role, purchase_price, is_captain FROM squad_members ORDER BY id DESC LIMIT 5;"
echo "================================================================================"
echo " Automation Task Completed Successfully! All data preserved in PostgreSQL."
echo "================================================================================"
